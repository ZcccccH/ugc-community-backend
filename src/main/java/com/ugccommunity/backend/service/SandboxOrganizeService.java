package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.dto.request.SandboxRecordRequest;
import com.ugccommunity.backend.dto.request.SandboxRelationRequest;
import com.ugccommunity.backend.dto.request.SandboxWorldRelationRequest;
import com.ugccommunity.backend.entity.SandboxCharacter;
import com.ugccommunity.backend.entity.SandboxOrganizeLog;
import com.ugccommunity.backend.entity.SandboxRecord;
import com.ugccommunity.backend.entity.SandboxRelation;
import com.ugccommunity.backend.entity.SandboxTimeline;
import com.ugccommunity.backend.entity.SandboxWorldRelation;
import com.ugccommunity.backend.entity.SandboxWorldview;
import com.ugccommunity.backend.mapper.SandboxCharacterMapper;
import com.ugccommunity.backend.mapper.SandboxOrganizeLogMapper;
import com.ugccommunity.backend.mapper.SandboxWorldviewMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 「整理」：从当前保存的设定文本里提取时间表达、事件、事件之间的关联、实体、角色关系和世界关系，写进时间线、设定记录和关系。
 *
 * 约束：
 * - 只认原文里有依据的内容：每一条都要带原文片段，片段在原文里找不到的不写入；
 * - 不替用户猜：名字对不上、同名多个的关系不建立，列在报告的 unresolved（没有处理的内容）里；
 *   原文没写时间的事件照样写入、时间留空，只有这次新写入的才列在报告的 notes（已写入，请留意）里，已有的事件不再提；
 * - 只增不改：已有的记录、关系、时间线事件（包括用户手动改过的）不覆盖，重复整理不产生重复内容，
 *   AI 两次整理对同一件事换了说法也认作同一条；用户删掉的事件、关联和角色关系不会被整理建回来；
 * - AI 出错、返回的内容不合格，或者整理期间设定文本被改过：什么都不写。全部写入在一个事务里。
 */
@Service
public class SandboxOrganizeService {
    public static final String SCOPE_WORLD = "world";
    public static final String SCOPE_CHARACTER = "character";
    private static final int SOURCE_MAX = 24_000;
    private static final int ITEMS_MAX = 80;
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM_PROMPT = "你是设定整理助手。用户会给你一段虚构世界的设定文本，请只依据这段文本提取结构化信息，"
            + "输出一个 JSON 对象，不要输出任何其它文字。\n"
            + "格式：{\"events\":[{\"time\":\"\",\"text\":\"\",\"quote\":\"\"}],"
            + "\"eventLinks\":[{\"from\":0,\"to\":1,\"note\":\"\",\"quote\":\"\"}],"
            + "\"entities\":[{\"category\":\"\",\"name\":\"\",\"description\":\"\",\"quote\":\"\"}],"
            + "\"characterRelations\":[{\"from\":\"\",\"to\":\"\",\"label\":\"\",\"mutual\":false,\"reverseLabel\":\"\",\"quote\":\"\"}],"
            + "\"worldRelations\":[{\"from\":\"\",\"to\":\"\",\"label\":\"\",\"mutual\":false,\"reverseLabel\":\"\",\"quote\":\"\"}]}\n"
            + "规则：\n"
            + "1. quote 必须是原文里一字不差的连续片段，用来说明这一条的依据；找不到依据的内容不要输出。\n"
            + "2. time 照抄原文里的时间写法（包括虚构历法），原文没有写时间就填空字符串，不要推算、不要换算成现实日期。\n"
            + "3. entities 的 category 只能是 creature（生物）、plant（植物）、mineral（矿物）、weather（天气）、history（历史事件）、"
            + "building（建筑）、country（国家）、city（城市）之一；人物和组织不算实体。\n"
            + "4. characterRelations 的 from、to 用原文里的人物全名；关系有方向，from 对 to 的关系写在 label；"
            + "只有原文明确写出双方互相的关系时 mutual 才为 true，并把 to 对 from 的关系写在 reverseLabel。\n"
            + "5. worldRelations 是世界观（世界）之间的关系，写法同上。\n"
            + "6. eventLinks 是两个事件之间的关联：from、to 是这两个事件在 events 数组里的序号（从 0 开始），note 用几个字写出原文说的关联"
            + "（例如「导致」「起因」「同一批人」）。只有原文明确写出这两个事件有关联时才输出，时间先后、写在同一段都不算关联。\n"
            + "7. 以「人物关系」开头的行是用户在这个角色的设定里手填的关系，格式是「人物关系：角色 → 对方：关系描述」："
            + "from 填角色，to 填对方，label 照抄关系描述。\n"
            + "8. 不要补充原文没有的人物、事件、日期或关系；没有内容的数组输出空数组。";

    private final DeepSeekClient ai;
    private final SandboxWorldviewMapper worldviewMapper;
    private final SandboxCharacterMapper characterMapper;
    private final SandboxRecordService recordService;
    private final SandboxRelationService relationService;
    private final SandboxWorldRelationService worldRelationService;
    private final SandboxTimelineService timelineService;
    private final SandboxOrganizeLogMapper logMapper;
    private final TransactionTemplate transaction;

    public SandboxOrganizeService(DeepSeekClient ai, SandboxWorldviewMapper worldviewMapper, SandboxCharacterMapper characterMapper,
                                  SandboxRecordService recordService, SandboxRelationService relationService,
                                  SandboxWorldRelationService worldRelationService, SandboxTimelineService timelineService,
                                  SandboxOrganizeLogMapper logMapper, PlatformTransactionManager transactionManager) {
        this.ai = ai;
        this.worldviewMapper = worldviewMapper;
        this.characterMapper = characterMapper;
        this.recordService = recordService;
        this.relationService = relationService;
        this.worldRelationService = worldRelationService;
        this.timelineService = timelineService;
        this.logMapper = logMapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** 最近一次整理的报告；没有整理过返回 null */
    public Map<String, Object> latest(Long userId, String scope, Long id) {
        validUser(userId);
        List<SandboxOrganizeLog> list = logMapper.selectList(new LambdaQueryWrapper<SandboxOrganizeLog>()
                .eq(SandboxOrganizeLog::getUserId, userId).eq(SandboxOrganizeLog::getScope, scope).eq(SandboxOrganizeLog::getRefId, id)
                .orderByDesc(SandboxOrganizeLog::getId).last("LIMIT 1"));
        if (list == null || list.isEmpty()) return null;
        return toMap(list.get(0));
    }

    public Map<String, Object> organize(final Long userId, final String scope, final Long id) {
        validUser(userId);
        if (id == null || !(SCOPE_WORLD.equals(scope) || SCOPE_CHARACTER.equals(scope))) throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要整理的世界观或角色");
        final String source = sourceText(userId, scope, id);
        if (source.trim().isEmpty()) throw new BusinessException(ResultCode.PARAM_ERROR, "还没有保存设定文本，没有可以整理的内容");
        if (source.length() > SOURCE_MAX) throw new BusinessException(ResultCode.PARAM_ERROR, "设定文本太长，请分段整理");
        if (ai == null || !ai.isConfigured()) throw new BusinessException(ResultCode.BUSINESS_ERROR, "AI 服务尚未配置，暂时不能整理；已保存的内容没有改动");

        // AI 调用耗时较长，放在事务外
        final String raw;
        try {
            raw = ai.generateJson(SYSTEM_PROMPT, source, 4000);
        } catch (DeepSeekClient.DeepSeekException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "AI 服务暂时不可用，请稍后重试；已保存的内容没有改动");
        }
        final JsonNode extracted = parseExtraction(raw);
        if (extracted == null) throw new BusinessException(ResultCode.BUSINESS_ERROR, "AI 返回的内容无法识别，已保存的内容没有改动，请重试");

        return transaction.execute(status -> {
            // 整理期间设定文本被改过：这份结果是按旧文本算的，不写入
            if (!source.equals(sourceText(userId, scope, id))) {
                throw new BusinessException(ResultCode.DATA_EXIST, "设定文本在整理期间被修改了，这次结果没有写入，请重新整理");
            }
            ObjectNode report = SCOPE_WORLD.equals(scope) ? applyWorld(userId, id, source, extracted) : applyCharacter(userId, id, source, extracted);
            SandboxOrganizeLog log = new SandboxOrganizeLog();
            log.setUserId(userId);
            log.setScope(scope);
            log.setRefId(id);
            log.setReport(report.toString());
            log.setCreatedAt(LocalDateTime.now());
            logMapper.insert(log);
            return toMap(log);
        });
    }

    // ------------------------------------------------------------------ 写入

    private ObjectNode applyWorld(Long userId, Long worldviewId, String source, JsonNode extracted) {
        ObjectNode report = newReport();
        ArrayNode unresolved = (ArrayNode) report.get("unresolved");
        mergeEvents(userId, timelineService.worldTimeline(userId, worldviewId), "world:" + worldviewId, source, extracted, report, unresolved);
        mergeEntities(userId, worldviewId, source, extracted, report, unresolved);
        mergeCharacterRelations(userId, worldCharacters(userId, worldviewId), source, extracted, report, unresolved);
        mergeWorldRelations(userId, source, extracted, report, unresolved);
        return report;
    }

    private ObjectNode applyCharacter(Long userId, Long characterId, String source, JsonNode extracted) {
        ObjectNode report = newReport();
        ArrayNode unresolved = (ArrayNode) report.get("unresolved");
        SandboxCharacter c = ownedCharacter(userId, characterId);
        mergeEvents(userId, timelineService.characterTimeline(userId, characterId), "character:" + characterId, source, extracted, report, unresolved);
        List<String[]> typed = typedRelations(c);
        if (c.getWorldviewId() != null) {
            mergeEntities(userId, c.getWorldviewId(), source, extracted, report, unresolved);
            List<SandboxCharacter> candidates = worldCharacters(userId, c.getWorldviewId());
            // 手填的关系先写：人物和关系描述都用用户自己写的，不经过 AI 转述
            int[] fromSheet = mergeTypedRelations(userId, c, typed, candidates, unresolved);
            mergeCharacterRelations(userId, candidates, source, extracted, report, unresolved);
            report.put("relationsAdded", report.get("relationsAdded").asInt() + fromSheet[0]);
            report.put("relationsExisting", report.get("relationsExisting").asInt() + fromSheet[1]);
        } else if (count(extracted, "entities") + count(extracted, "characterRelations") + typed.size() > 0) {
            unresolved.addObject().put("kind", "entity").put("text", "实体和人物关系").put("reason", "这个角色还没有归属世界观，实体和关系没有写入");
        }
        return report;
    }

    private void mergeEvents(Long userId, SandboxTimeline timeline, String sourceRef, String source, JsonNode extracted, ObjectNode report, ArrayNode unresolved) {
        List<String[]> events = new ArrayList<String[]>();
        // 和 events 一一对应：时间留空的原因，时间正常的为 null。事件写入后才决定要不要提醒
        List<String> timeNotes = new ArrayList<String>();
        // AI 给的事件序号 → 通过校验后在 events 里的位置
        Map<Integer, Integer> kept = new java.util.HashMap<Integer, Integer>();
        int index = -1;
        for (JsonNode e : items(extracted, "events")) {
            index++;
            String body = text(e, "text", 2000);
            String quote = text(e, "quote", 500);
            String time = text(e, "time", 60);
            if (body.isEmpty()) continue;
            if (!grounded(source, quote)) { unresolved.addObject().put("kind", "event").put("text", body).put("reason", "原文中找不到依据，没有写入"); continue; }
            String timeNote = null;
            if (!time.isEmpty() && !grounded(source, time)) {
                timeNote = "给出的时间「" + time + "」不在原文中，时间已留空";
                time = "";
            } else if (time.isEmpty()) {
                timeNote = "原文没有写时间，时间已留空";
            }
            kept.put(index, events.size());
            events.add(new String[]{time, body, quote});
            timeNotes.add(timeNote);
        }
        List<String[]> links = new ArrayList<String[]>();
        for (JsonNode l : items(extracted, "eventLinks")) {
            Integer from = l.path("from").isInt() ? kept.get(l.path("from").asInt()) : null;
            Integer to = l.path("to").isInt() ? kept.get(l.path("to").asInt()) : null;
            String note = text(l, "note", 200);
            if (from == null || to == null || from.equals(to)) {
                unresolved.addObject().put("kind", "eventLink").put("text", "事件关联" + (note.isEmpty() ? "" : "：" + note)).put("reason", "关联的事件没有写入时间线，这条关联没有写入");
                continue;
            }
            String shown = events.get(from)[1] + " ↔ " + events.get(to)[1] + (note.isEmpty() ? "" : "：" + note);
            if (!grounded(source, text(l, "quote", 500))) { unresolved.addObject().put("kind", "eventLink").put("text", shown).put("reason", "原文中找不到依据，没有写入"); continue; }
            links.add(new String[]{String.valueOf(from), String.valueOf(to), note});
        }
        boolean[] added = new boolean[events.size()];
        int[] result = events.isEmpty() ? new int[]{0, 0, 0, 0} : timelineService.mergeOrganized(userId, timeline, sourceRef, events, links, added);
        // 事件已经写入，时间留空不算「没有处理」；时间线上本来就有的事件这次没有改动，不再提醒
        ArrayNode notes = (ArrayNode) report.get("notes");
        for (int i = 0; i < events.size(); i++) {
            if (added[i] && timeNotes.get(i) != null) notes.addObject().put("kind", "event").put("text", events.get(i)[1]).put("reason", timeNotes.get(i));
        }
        report.put("eventsAdded", result[0]).put("eventsExisting", result[1]);
        report.put("eventLinksAdded", result.length > 2 ? result[2] : 0).put("eventLinksExisting", result.length > 3 ? result[3] : 0);
    }

    /** 角色卡「关系」一栏里手填的 [人物, 关系描述]；人物没填的不算 */
    static List<String[]> typedRelations(SandboxCharacter c) {
        List<String[]> out = new ArrayList<String[]>();
        JsonNode sheet = parse(c.getContent());
        JsonNode list = sheet == null ? null : sheet.get("relationships");
        if (list == null || !list.isArray()) return out;
        for (JsonNode e : list) {
            if (out.size() >= ITEMS_MAX) break;
            String name = text(e, "name", 80);
            if (!name.isEmpty()) out.add(new String[]{name, text(e, "relation", 2000)});
        }
        return out;
    }

    /**
     * 手填的关系按人物名字关联到角色：这个角色指向对方，关系描述原样作为关系名称（超出长度的部分放进说明）。
     * 名字对不上、同名多个的不建立，列在报告里；两个角色之间已经有关系的不动。返回 [新增, 已有]。
     */
    private int[] mergeTypedRelations(Long userId, SandboxCharacter self, List<String[]> typed, List<SandboxCharacter> candidates, ArrayNode unresolved) {
        if (typed.isEmpty()) return new int[]{0, 0};
        List<SandboxRelation> existing = relationService.listRelations(userId);
        int added = 0;
        int kept = 0;
        for (String[] entry : typed) {
            String name = entry[0];
            String relation = entry[1];
            String shown = self.getName() + " → " + name + (relation.isEmpty() ? "" : "：" + relation);
            SandboxCharacter other = uniqueByName(candidates, name);
            if (other == null) {
                unresolved.addObject().put("kind", "relation").put("text", shown).put("reason", nameCount(candidates, name) > 1
                        ? "有多个名为「" + name + "」的角色，没有替你选择，请在关系一栏里点「关联角色」"
                        : "这个世界观里找不到名为「" + name + "」的角色，没有写入");
                continue;
            }
            if (other.getId().equals(self.getId())) continue;
            boolean present = false;
            for (SandboxRelation r : existing) {
                if ((self.getId().equals(r.getFromCharacterId()) && other.getId().equals(r.getToCharacterId()))
                        || (other.getId().equals(r.getFromCharacterId()) && self.getId().equals(r.getToCharacterId()))) { present = true; break; }
            }
            if (present) { kept++; continue; }
            if (relationService.isDismissed(userId, self.getId(), other.getId())) {
                unresolved.addObject().put("kind", "relation").put("text", shown).put("reason", "这两个角色的关系在关系网里删除过，没有重新建立；需要的话请在关系一栏里点「关联角色」");
                continue;
            }
            SandboxRelationRequest r = new SandboxRelationRequest();
            r.setFromCharacterId(self.getId());
            r.setToCharacterId(other.getId());
            r.setLabel(relation.length() > 100 ? relation.substring(0, 100) : relation);
            if (relation.length() > 100) r.setNote(relation.length() > 500 ? relation.substring(0, 500) : relation);
            r.setDirection(SandboxRelation.ONE_WAY);
            existing.add(relationService.createRelation(userId, r));
            added++;
        }
        return new int[]{added, kept};
    }

    private void mergeEntities(Long userId, Long worldviewId, String source, JsonNode extracted, ObjectNode report, ArrayNode unresolved) {
        List<SandboxRecord> existing = recordService.listRecords(userId, worldviewId, null);
        int added = 0;
        int kept = 0;
        for (JsonNode e : items(extracted, "entities")) {
            String category = text(e, "category", 20);
            String name = text(e, "name", 80);
            String description = text(e, "description", 2000);
            if (name.isEmpty()) continue;
            if (!SandboxRecordService.BUILT_IN.contains(category)) { unresolved.addObject().put("kind", "entity").put("text", name).put("reason", "类别无法识别，没有写入"); continue; }
            if (!grounded(source, text(e, "quote", 500)) || !grounded(source, name)) { unresolved.addObject().put("kind", "entity").put("text", name).put("reason", "原文中找不到依据，没有写入"); continue; }
            String key = "org:" + SandboxTimelineService.shortHash(worldviewId + "|" + category + "|" + name);
            boolean present = false;
            for (SandboxRecord r : existing) {
                if (key.equals(r.getLegacyKey()) || (category.equals(r.getCategory()) && name.equals(r.getName()))) { present = true; break; }
            }
            // 已有同名记录（可能是用户手动建的或改过的）：不动它
            if (present) { kept++; continue; }
            SandboxRecordRequest r = new SandboxRecordRequest();
            r.setWorldviewId(worldviewId);
            r.setCategory(category);
            r.setName(name);
            r.setDescription(description);
            r.setLegacyKey(key);
            existing.add(recordService.createRecord(userId, r));
            added++;
        }
        report.put("recordsAdded", added).put("recordsExisting", kept);
    }

    private void mergeCharacterRelations(Long userId, List<SandboxCharacter> candidates, String source, JsonNode extracted, ObjectNode report, ArrayNode unresolved) {
        List<SandboxRelation> existing = relationService.listRelations(userId);
        int added = 0;
        int kept = 0;
        for (JsonNode e : items(extracted, "characterRelations")) {
            String from = text(e, "from", 80);
            String to = text(e, "to", 80);
            String label = text(e, "label", 100);
            String shown = from + " → " + to + (label.isEmpty() ? "" : "：" + label);
            if (from.isEmpty() || to.isEmpty()) continue;
            if (!grounded(source, text(e, "quote", 500))) { unresolved.addObject().put("kind", "relation").put("text", shown).put("reason", "原文中找不到依据，没有写入"); continue; }
            SandboxCharacter a = uniqueByName(candidates, from);
            SandboxCharacter b = uniqueByName(candidates, to);
            if (a == null || b == null) {
                String missing = a == null ? from : to;
                unresolved.addObject().put("kind", "relation").put("text", shown).put("reason", nameCount(candidates, missing) > 1
                        ? "有多个名为「" + missing + "」的角色，没有替你选择，请在关系网里手动建立"
                        : "找不到名为「" + missing + "」的角色，没有写入");
                continue;
            }
            if (a.getId().equals(b.getId())) continue;
            boolean present = false;
            for (SandboxRelation r : existing) {
                if ((a.getId().equals(r.getFromCharacterId()) && b.getId().equals(r.getToCharacterId()))
                        || (b.getId().equals(r.getFromCharacterId()) && a.getId().equals(r.getToCharacterId()))) { present = true; break; }
            }
            if (present) { kept++; continue; }
            if (relationService.isDismissed(userId, a.getId(), b.getId())) {
                unresolved.addObject().put("kind", "relation").put("text", shown).put("reason", "这两个角色的关系在关系网里删除过，没有重新建立；需要的话请在关系网里手动建立");
                continue;
            }
            SandboxRelationRequest r = new SandboxRelationRequest();
            r.setFromCharacterId(a.getId());
            r.setToCharacterId(b.getId());
            r.setLabel(label);
            boolean mutual = e.path("mutual").asBoolean(false);
            r.setDirection(mutual ? SandboxRelation.TWO_WAY : SandboxRelation.ONE_WAY);
            if (mutual) r.setReverseLabel(text(e, "reverseLabel", 100));
            existing.add(relationService.createRelation(userId, r));
            added++;
        }
        report.put("relationsAdded", added).put("relationsExisting", kept);
    }

    private void mergeWorldRelations(Long userId, String source, JsonNode extracted, ObjectNode report, ArrayNode unresolved) {
        List<SandboxWorldview> worlds = worldviewMapper.selectList(new LambdaQueryWrapper<SandboxWorldview>().eq(SandboxWorldview::getUserId, userId));
        List<SandboxWorldRelation> existing = worldRelationService.listRelations(userId);
        int added = 0;
        int kept = 0;
        for (JsonNode e : items(extracted, "worldRelations")) {
            String from = text(e, "from", 80);
            String to = text(e, "to", 80);
            String label = text(e, "label", 100);
            String shown = from + " → " + to + (label.isEmpty() ? "" : "：" + label);
            if (from.isEmpty() || to.isEmpty()) continue;
            if (!grounded(source, text(e, "quote", 500))) { unresolved.addObject().put("kind", "worldRelation").put("text", shown).put("reason", "原文中找不到依据，没有写入"); continue; }
            SandboxWorldview a = null;
            SandboxWorldview b = null;
            int countA = 0;
            int countB = 0;
            for (SandboxWorldview w : worlds == null ? new ArrayList<SandboxWorldview>() : worlds) {
                if (from.equals(w.getName())) { a = w; countA++; }
                if (to.equals(w.getName())) { b = w; countB++; }
            }
            if (countA != 1 || countB != 1) {
                String missing = countA != 1 ? from : to;
                unresolved.addObject().put("kind", "worldRelation").put("text", shown).put("reason", (countA > 1 || countB > 1)
                        ? "有多个名为「" + missing + "」的世界观，没有替你选择"
                        : "找不到名为「" + missing + "」的世界观，没有写入");
                continue;
            }
            if (a.getId().equals(b.getId())) continue;
            boolean present = false;
            for (SandboxWorldRelation r : existing) if (r.connects(a.getId(), b.getId())) { present = true; break; }
            if (present) { kept++; continue; }
            if (worldRelationService.isDismissed(userId, a.getId(), b.getId())) {
                unresolved.addObject().put("kind", "worldRelation").put("text", shown).put("reason", "这两个世界观的关系在多世界总览里删除过，没有重新建立；需要的话请在多世界总览里手动建立");
                continue;
            }
            SandboxWorldRelationRequest r = new SandboxWorldRelationRequest();
            r.setFromWorldviewId(a.getId());
            r.setToWorldviewId(b.getId());
            r.setLabel(label);
            boolean mutual = e.path("mutual").asBoolean(false);
            r.setDirection(mutual ? SandboxWorldRelation.TWO_WAY : SandboxWorldRelation.ONE_WAY);
            if (mutual) r.setReverseLabel(text(e, "reverseLabel", 100));
            existing.add(worldRelationService.createRelation(userId, r));
            added++;
        }
        report.put("worldRelationsAdded", added).put("worldRelationsExisting", kept);
    }

    // ------------------------------------------------------------------ 原文与校验

    /** 用来整理的原文：世界观是名称、简介、设定正文；角色是名称、简介、性格、背景、角色卡里的各栏文字和手填的关系 */
    String sourceText(Long userId, String scope, Long id) {
        StringBuilder sb = new StringBuilder();
        if (SCOPE_WORLD.equals(scope)) {
            SandboxWorldview w = worldviewMapper.selectById(id);
            if (w == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "世界观不存在");
            if (!userId.equals(w.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
            line(sb, "世界观", w.getName());
            line(sb, "简介", w.getDescription());
            line(sb, "设定", w.getContent());
        } else {
            SandboxCharacter c = ownedCharacter(userId, id);
            line(sb, "角色", c.getName());
            line(sb, "简介", c.getDescription());
            line(sb, "性格", c.getPersonality());
            line(sb, "背景", c.getBackground());
            JsonNode sheet = parse(c.getContent());
            if (sheet != null && sheet.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = sheet.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> f = fields.next();
                    // 名字已经写过；经历和补充时间线是整理的结果，不再当作原文
                    if (f.getValue().isTextual() && !"name".equals(f.getKey()) && !"extraTimelineTitle".equals(f.getKey())) line(sb, f.getKey(), f.getValue().asText());
                }
            }
            for (String[] entry : typedRelations(c)) line(sb, "人物关系", (c.getName() == null ? "" : c.getName().trim()) + " → " + entry[0] + (entry[1].isEmpty() ? "" : "：" + entry[1]));
        }
        return sb.toString();
    }

    /** 返回提取结果；不是 JSON 对象，或者几个数组一个合格的都没有，返回 null */
    static JsonNode parseExtraction(String raw) {
        JsonNode root = parse(raw);
        if (root == null || !root.isObject()) return null;
        boolean anyArray = false;
        for (String key : new String[]{"events", "eventLinks", "entities", "characterRelations", "worldRelations"}) {
            JsonNode v = root.get(key);
            if (v == null || v.isNull()) continue;
            if (!v.isArray()) return null;
            for (JsonNode item : v) if (!item.isObject()) return null;
            anyArray = true;
        }
        return anyArray ? root : null;
    }

    /** 片段是否出现在原文里（忽略空白差异）。空片段不算有依据 */
    static boolean grounded(String source, String quote) {
        String q = strip(quote);
        return !q.isEmpty() && strip(source).contains(q);
    }

    private static String strip(String s) { return s == null ? "" : s.replaceAll("\\s+", ""); }

    private static List<JsonNode> items(JsonNode root, String key) {
        List<JsonNode> out = new ArrayList<JsonNode>();
        JsonNode v = root.get(key);
        if (v != null && v.isArray()) for (JsonNode item : v) { if (out.size() >= ITEMS_MAX) break; out.add(item); }
        return out;
    }

    private static int count(JsonNode root, String key) { return items(root, key).size(); }

    private static String text(JsonNode node, String field, int max) {
        JsonNode v = node.get(field);
        String s = v == null || v.isNull() ? "" : v.asText("").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static JsonNode parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try { return JSON.readTree(raw); } catch (Exception e) { return null; }
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (value != null && !value.trim().isEmpty()) sb.append(label).append("：").append(value.trim()).append('\n');
    }

    private static ObjectNode newReport() {
        ObjectNode report = JSON.createObjectNode();
        report.putArray("unresolved");
        report.putArray("notes");
        return report;
    }

    /**
     * 旧版本把「时间已留空」的事件也放进 unresolved，不管事件是新写入的还是时间线上本来就有的。
     * 读出时改正：这些事件都已经在时间线上，从 unresolved 移走；这次一个事件都没新增的，说明它们都是已有的，直接去掉，
     * 否则移到 notes。没有这类条目的报告原样返回。
     */
    static String normalizeReport(String raw) {
        JsonNode root = parse(raw);
        if (root == null || !root.isObject() || !root.path("unresolved").isArray() || root.has("notes")) return raw;
        ArrayNode unresolved = (ArrayNode) root.get("unresolved");
        ArrayNode kept = JSON.createArrayNode();
        ArrayNode timeNotes = JSON.createArrayNode();
        for (JsonNode u : unresolved) {
            boolean timeNote = "event".equals(u.path("kind").asText()) && u.path("reason").asText().endsWith("时间已留空");
            (timeNote ? timeNotes : kept).add(u);
        }
        if (timeNotes.size() == 0) return raw;
        ObjectNode fixed = (ObjectNode) root;
        fixed.set("unresolved", kept);
        fixed.set("notes", root.path("eventsAdded").asInt() > 0 ? timeNotes : JSON.createArrayNode());
        return fixed.toString();
    }

    private Map<String, Object> toMap(SandboxOrganizeLog log) {
        Map<String, Object> out = new java.util.LinkedHashMap<String, Object>();
        out.put("id", log.getId());
        out.put("scope", log.getScope());
        out.put("refId", log.getRefId());
        out.put("createdAt", log.getCreatedAt() == null ? null : log.getCreatedAt().toString());
        out.put("report", normalizeReport(log.getReport()));
        return out;
    }

    // ------------------------------------------------------------------ 查找

    private List<SandboxCharacter> worldCharacters(Long userId, Long worldviewId) {
        List<SandboxCharacter> out = new ArrayList<SandboxCharacter>();
        List<SandboxCharacter> all = characterMapper.selectList(new LambdaQueryWrapper<SandboxCharacter>().eq(SandboxCharacter::getUserId, userId));
        if (all == null) return out;
        for (SandboxCharacter c : all) {
            boolean inWorld = worldviewId.equals(c.getWorldviewId());
            if (!inWorld && c.getWorldviewIds() != null) {
                for (String part : c.getWorldviewIds().split(",")) if (part.trim().equals(String.valueOf(worldviewId))) { inWorld = true; break; }
            }
            if (inWorld) out.add(c);
        }
        return out;
    }

    private static int nameCount(List<SandboxCharacter> list, String name) {
        int n = 0;
        for (SandboxCharacter c : list) if (name.equals(c.getName() == null ? "" : c.getName().trim())) n++;
        return n;
    }

    /** 只有恰好一个同名角色时才返回它 */
    private static SandboxCharacter uniqueByName(List<SandboxCharacter> list, String name) {
        SandboxCharacter found = null;
        for (SandboxCharacter c : list) {
            if (!name.equals(c.getName() == null ? "" : c.getName().trim())) continue;
            if (found != null) return null;
            found = c;
        }
        return found;
    }

    private SandboxCharacter ownedCharacter(Long userId, Long id) {
        SandboxCharacter c = characterMapper.selectById(id);
        if (c == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "角色不存在");
        if (!userId.equals(c.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        return c;
    }

    private static void validUser(Long userId) {
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
    }
}
