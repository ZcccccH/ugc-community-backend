package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.dto.request.SandboxTimelineRequest;
import com.ugccommunity.backend.entity.SandboxCharacter;
import com.ugccommunity.backend.entity.SandboxTimeline;
import com.ugccommunity.backend.entity.SandboxWorldview;
import com.ugccommunity.backend.mapper.SandboxCharacterMapper;
import com.ugccommunity.backend.mapper.SandboxTimelineMapper;
import com.ugccommunity.backend.mapper.SandboxWorldviewMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 时间线（sandbox_timeline）。世界时间线和角色时间线结构相同。
 * 角色时间线和角色设定里的「经历」「补充时间线」是同一份内容：首次打开时从角色卡导入，
 * 之后每次保存时间线都写回角色卡；角色卡被旧数据整份覆盖时，再按时间线改回来。
 */
@Service
public class SandboxTimelineService {
    private static final int DATA_MAX = 600_000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SandboxTimelineMapper timelineMapper;
    private final SandboxWorldviewMapper worldviewMapper;
    private final SandboxCharacterMapper characterMapper;

    public SandboxTimelineService(SandboxTimelineMapper timelineMapper, SandboxWorldviewMapper worldviewMapper, SandboxCharacterMapper characterMapper) {
        this.timelineMapper = timelineMapper;
        this.worldviewMapper = worldviewMapper;
        this.characterMapper = characterMapper;
    }

    public static String worldSlot(Long worldviewId) { return "world:" + worldviewId; }
    public static String characterSlot(Long characterId) { return "char:" + characterId; }

    /** 世界时间线；没有就建一条空的 */
    @Transactional
    public SandboxTimeline worldTimeline(Long userId, Long worldviewId) {
        validUser(userId);
        SandboxWorldview w = worldviewId == null ? null : worldviewMapper.selectById(worldviewId);
        if (w == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "世界观不存在");
        if (!userId.equals(w.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        SandboxTimeline existing = bySlot(userId, worldSlot(worldviewId));
        if (existing != null) return existing;
        return insert(userId, worldviewId, null, worldSlot(worldviewId), emptyDocument().toString());
    }

    /**
     * 角色时间线；没有就建一条，并把角色卡里已有的经历和补充时间线原样导入：
     * 一条经历是一个时间锚点加一个事件，时间保留用户写的原文，不补日期、不建连接。
     */
    @Transactional
    public SandboxTimeline characterTimeline(Long userId, Long characterId) {
        SandboxCharacter c = ownedCharacter(userId, characterId);
        SandboxTimeline existing = bySlot(userId, characterSlot(characterId));
        if (existing != null) return existing;
        return insert(userId, c.getWorldviewId(), characterId, characterSlot(characterId), importFromSheet(c.getContent()).toString());
    }

    @Transactional
    public SandboxTimeline update(Long userId, Long id, SandboxTimelineRequest r) {
        validUser(userId);
        SandboxTimeline x = id == null ? null : timelineMapper.selectById(id);
        if (x == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "时间线不存在");
        if (!userId.equals(x.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        if (r == null || r.getData() == null) throw new BusinessException(ResultCode.PARAM_ERROR);
        if (r.getData().length() > DATA_MAX) throw new BusinessException(ResultCode.PARAM_ERROR, "时间线内容过大");
        JsonNode document = parseObject(r.getData());
        if (document == null) throw new BusinessException(ResultCode.PARAM_ERROR, "时间线内容格式不正确");
        int current = x.getVersion() == null ? 1 : x.getVersion();
        if (r.getVersion() != null && r.getVersion() != current) throw stale();
        JsonNode previous = parseObject(x.getData());
        boolean rewritten = keepDismissedFacts(previous, (ObjectNode) document);
        if (keepFieldsOlderClientsDrop(previous, (ObjectNode) document)) rewritten = true;
        x.setData(rewritten ? document.toString() : r.getData());
        x.setVersion(current + 1);
        x.setUpdatedAt(LocalDateTime.now());
        int rows = timelineMapper.update(x, new LambdaUpdateWrapper<SandboxTimeline>()
                .eq(SandboxTimeline::getId, x.getId())
                .eq(SandboxTimeline::getUserId, userId)
                .eq(SandboxTimeline::getVersion, current));
        if (rows == 0) throw stale();
        if (x.getCharacterId() != null) mirrorToSheet(userId, x.getCharacterId(), document);
        return x;
    }

    /**
     * 角色保存之后调用：如果这个角色已经有时间线，角色卡里的经历以时间线为准重新写一遍。
     * 这样旧版客户端或带着旧数据的保存不会把时间线上改过的内容冲掉。
     */
    @Transactional
    public void reapplyToSheet(Long userId, Long characterId) {
        SandboxTimeline t = bySlot(userId, characterSlot(characterId));
        if (t == null) return;
        JsonNode document = parseObject(t.getData());
        if (document != null) mirrorToSheet(userId, characterId, document);
    }

    @Transactional
    public void onCharacterDeleted(Long userId, Long characterId) {
        validUser(userId);
        timelineMapper.delete(new LambdaQueryWrapper<SandboxTimeline>()
                .eq(SandboxTimeline::getUserId, userId).eq(SandboxTimeline::getSlot, characterSlot(characterId)));
    }

    /** 世界观删除后删掉它的世界时间线；角色时间线跟着角色走 */
    @Transactional
    public void onWorldviewDeleted(Long userId, Long worldviewId) {
        validUser(userId);
        timelineMapper.delete(new LambdaQueryWrapper<SandboxTimeline>()
                .eq(SandboxTimeline::getUserId, userId).eq(SandboxTimeline::getSlot, worldSlot(worldviewId)));
    }

    /** 时间线按顺序整理成「时间：事件」的文字，给聊天上下文用；没有时间线或没有事件时返回空列表 */
    public List<String> lines(Long userId, String slot) {
        List<String> out = new ArrayList<String>();
        SandboxTimeline t = bySlot(userId, slot);
        JsonNode document = t == null ? null : parseObject(t.getData());
        if (document == null) return out;
        for (String[] pair : orderedEvents(document)) {
            if (pair[1].isEmpty()) continue;
            out.add((pair[0].isEmpty() ? "" : pair[0] + "：") + pair[1]);
        }
        return out;
    }

    private static final int DISMISSED_FACTS_MAX = 400;

    /**
     * 保存时间线时记下用户删掉的「整理」事件的原文片段和文字（dismissedFacts），
     * 这样 AI 下次整理对同一件事换了说法也不会把它加回来。客户端不认识这个字段，每次保存都从上一版带过来。
     * 返回 next 是否被改动。
     */
    static boolean keepDismissedFacts(JsonNode previous, ObjectNode next) {
        if (previous == null) return false;
        java.util.Set<String> live = new java.util.HashSet<String>();
        for (JsonNode e : iterable(next.get("events"))) live.add(text(e, "id"));
        java.util.Map<String, JsonNode> facts = new java.util.LinkedHashMap<String, JsonNode>();
        for (JsonNode d : iterable(previous.get("dismissedFacts"))) if (!live.contains(text(d, "id"))) facts.put(text(d, "id"), d);
        for (JsonNode e : iterable(previous.get("events"))) {
            String id = text(e, "id");
            if (!id.startsWith(ORGANIZED_EVENT_PREFIX) || live.contains(id) || facts.containsKey(id)) continue;
            facts.put(id, JSON.createObjectNode().put("id", id).put("text", text(e, "text")).put("quote", text(e, "quote")));
        }
        if (facts.isEmpty()) return false;
        ArrayNode out = next.putArray("dismissedFacts");
        int skip = Math.max(0, facts.size() - DISMISSED_FACTS_MAX);
        for (JsonNode d : facts.values()) { if (skip-- > 0) continue; out.add(d); }
        return true;
    }

    /** 文档里的 format 达到这个值，说明保存它的客户端认识母事件、单个节点外观和三点圆弧 */
    static final int FORMAT_BRANCHES = 2;
    private static final String[] STYLE_FIELDS = {"fill", "border", "ink"};

    /**
     * 旧版客户端不认识母事件（parentId）、单个节点的外观（fill / border / ink）和三点圆弧（third / note2 / note3 / color），
     * 保存时会把它们丢掉。这里把上一版里的这些内容按 id 接回还在的节点、事件和跨越上；旧版客户端删掉的仍然是删掉。
     * 新版客户端写 format，1.0.38 没有 format 但会写这些字段，这两种保存原样接受。返回 next 是否被改动。
     */
    static boolean keepFieldsOlderClientsDrop(JsonNode previous, ObjectNode next) {
        if (previous == null || next.path("format").asInt(0) >= FORMAT_BRANCHES) return false;
        for (JsonNode n : iterable(next.get("nodes"))) if (hasAny(n, STYLE_FIELDS)) return false;
        for (JsonNode e : iterable(next.get("events"))) if (e.has("parentId") || hasAny(e, STYLE_FIELDS)) return false;
        for (JsonNode l : iterable(next.get("links"))) if (l.has("third") || l.has("color")) return false;

        java.util.Map<String, JsonNode> nextEvents = new java.util.HashMap<String, JsonNode>();
        java.util.Set<String> nextIds = new java.util.HashSet<String>();
        for (JsonNode n : iterable(next.get("nodes"))) nextIds.add(text(n, "id"));
        for (JsonNode e : iterable(next.get("events"))) { nextEvents.put(text(e, "id"), e); nextIds.add(text(e, "id")); }
        boolean changed = false;
        java.util.Map<String, JsonNode> oldNodes = byId(previous.get("nodes"));
        for (JsonNode n : iterable(next.get("nodes"))) {
            JsonNode old = oldNodes.get(text(n, "id"));
            if (old != null && n.isObject() && copyFields(old, (ObjectNode) n, STYLE_FIELDS)) changed = true;
        }
        java.util.Map<String, JsonNode> oldEvents = byId(previous.get("events"));
        for (JsonNode e : iterable(next.get("events"))) {
            JsonNode old = oldEvents.get(text(e, "id"));
            if (old == null || !e.isObject()) continue;
            if (copyFields(old, (ObjectNode) e, STYLE_FIELDS)) changed = true;
            // 母事件还在、并且两个事件仍然在同一个节骨点上才接回去；否则它就是一个直接挂在节骨点上的事件
            String parentId = text(old, "parentId");
            JsonNode parent = parentId.isEmpty() ? null : nextEvents.get(parentId);
            if (parent != null && text(parent, "nodeId").equals(text(e, "nodeId"))) { ((ObjectNode) e).put("parentId", parentId); changed = true; }
        }
        java.util.Map<String, JsonNode> oldLinks = byId(previous.get("links"));
        for (JsonNode l : iterable(next.get("links"))) {
            JsonNode old = oldLinks.get(text(l, "id"));
            if (old == null || !l.isObject()) continue;
            if (copyFields(old, (ObjectNode) l, new String[]{"color"})) changed = true;
            String third = text(old, "third");
            if (third.isEmpty() || !nextIds.contains(third) || !text(old, "from").equals(text(l, "from")) || !text(old, "to").equals(text(l, "to"))) continue;
            ((ObjectNode) l).put("third", third).put("note2", text(old, "note2")).put("note3", text(old, "note3"));
            changed = true;
        }
        return changed;
    }

    private static boolean hasAny(JsonNode node, String[] fields) {
        for (String f : fields) if (node.has(f)) return true;
        return false;
    }

    private static boolean copyFields(JsonNode from, ObjectNode to, String[] fields) {
        boolean changed = false;
        for (String f : fields) {
            String value = text(from, f);
            if (!value.isEmpty()) { to.put(f, value); changed = true; }
        }
        return changed;
    }

    private static java.util.Map<String, JsonNode> byId(JsonNode array) {
        java.util.Map<String, JsonNode> out = new java.util.HashMap<String, JsonNode>();
        for (JsonNode n : iterable(array)) out.put(text(n, "id"), n);
        return out;
    }

    /** 「整理」写入的事件、事件关联的 id 前缀 */
    static final String ORGANIZED_EVENT_PREFIX = "o-";
    static final String ORGANIZED_LINK_PREFIX = "ol-";

    public int[] mergeOrganized(Long userId, SandboxTimeline t, String sourceRef, List<String[]> events) {
        return mergeOrganized(userId, t, sourceRef, events, new ArrayList<String[]>());
    }

    /**
     * 把「整理」出的事件和事件之间的关联并入时间线，只增不改：
     * 已经在时间线上的事件（包括用户手动改过文字、时间的）不再添加，用户删掉过的（记在 dismissed / dismissedFacts 里）也不再添加，
     * 所以可以反复整理。AI 每次整理的措辞、标点、引用的原文长短都可能不同，所以不只比对由来源和内容算出的 id：
     * 文字去掉标点和大小写后相同，或者引用的是同一段原文，就认作同一件事（见 {@link #matchFacts}）。
     * 时间用原文里的写法；原文没写时间的事件挂在一个时间为空的锚点上，界面上显示为「未填写时间」。
     * events 每项是 [时间, 事件文字, 原文片段]。
     * links 每项是 [起点在 events 里的序号, 终点在 events 里的序号, 关联说明]，写成跨越层的连接：
     * 两端的事件都在时间线上才添加；这两个事件之间已经有连接（包括用户手动连的、改过说明的）或者用户删掉过这条连接，就不再添加。
     * 返回 [新增事件, 跳过事件, 新增关联, 已有关联]。
     */
    @Transactional
    public int[] mergeOrganized(Long userId, SandboxTimeline t, String sourceRef, List<String[]> events, List<String[]> links) {
        return mergeOrganized(userId, t, sourceRef, events, links, null);
    }

    /** 同上；addedOut 不为 null 时（长度不小于 events），这次新加到时间线上的事件对应位置记为 true，已有的、用户删掉过的保持 false */
    @Transactional
    public int[] mergeOrganized(Long userId, SandboxTimeline t, String sourceRef, List<String[]> events, List<String[]> links, boolean[] addedOut) {
        JsonNode parsed = parseObject(t.getData());
        if (parsed == null) throw new BusinessException(ResultCode.BUSINESS_ERROR, "时间线保存的内容无法读取，没有写入整理结果");
        ObjectNode document = (ObjectNode) parsed;
        ArrayNode nodes = document.has("nodes") && document.get("nodes").isArray() ? (ArrayNode) document.get("nodes") : document.putArray("nodes");
        ArrayNode list = document.has("events") && document.get("events").isArray() ? (ArrayNode) document.get("events") : document.putArray("events");
        java.util.Set<String> dismissed = new java.util.HashSet<String>();
        for (JsonNode d : iterable(document.get("dismissed"))) dismissed.add(d.asText(""));
        java.util.Set<String> known = new java.util.HashSet<String>(dismissed);
        // 已经在时间线上的事件，和用户删掉的「整理」事件：[id, 文字, 原文片段]
        List<String[]> facts = new ArrayList<String[]>();
        for (JsonNode e : list) { known.add(text(e, "id")); facts.add(new String[]{text(e, "id"), text(e, "text"), text(e, "quote")}); }
        int live = facts.size();
        for (JsonNode d : iterable(document.get("dismissedFacts"))) { known.add(text(d, "id")); facts.add(new String[]{text(d, "id"), text(d, "text"), text(d, "quote")}); }
        String[] ids = new String[events.size()];
        for (int i = 0; i < events.size(); i++) {
            String[] item = events.get(i);
            String body = item[1] == null ? "" : item[1].trim();
            if (!body.isEmpty()) ids[i] = ORGANIZED_EVENT_PREFIX + shortHash(sourceRef + "|" + (item[0] == null ? "" : item[0].trim()) + "|" + body);
        }
        int[] same = matchFacts(events, ids, facts);
        int added = 0;
        int skipped = 0;
        for (int i = 0; i < events.size(); i++) {
            String[] item = events.get(i);
            String time = item[0] == null ? "" : item[0].trim();
            String body = item[1] == null ? "" : item[1].trim();
            if (body.isEmpty()) continue;
            String id = ids[i];
            // 同一件事已经在时间线上：关联接到那个事件上；是用户删掉的：不加回来，关联也不建
            if (same[i] >= 0) { if (same[i] < live) ids[i] = facts.get(same[i])[0]; skipped++; continue; }
            if (!known.add(id)) { skipped++; continue; }
            String nodeId = null;
            for (JsonNode n : nodes) if (time.equals(text(n, "label"))) { nodeId = text(n, "id"); break; }
            if (nodeId == null) {
                nodeId = "on-" + shortHash(sourceRef + "|" + time + "|" + nodes.size());
                nodes.addObject().put("id", nodeId).put("label", time).put("note", "");
            }
            list.addObject().put("id", id).put("nodeId", nodeId).put("title", "").put("text", body).put("side", "").put("note", "")
                    .put("source", sourceRef).put("quote", item.length > 2 && item[2] != null ? item[2] : "");
            if (addedOut != null) addedOut[i] = true;
            added++;
        }

        java.util.Set<String> present = new java.util.HashSet<String>();
        for (JsonNode e : list) present.add(text(e, "id"));
        int linksAdded = 0;
        int linksExisting = 0;
        ArrayNode linkList = null;
        for (String[] link : links == null ? new ArrayList<String[]>() : links) {
            String from = organizedId(ids, link[0]);
            String to = organizedId(ids, link[1]);
            // 端点没有写入或者被用户删掉了：不留下指向空处的连接
            if (from == null || to == null || from.equals(to) || !present.contains(from) || !present.contains(to)) continue;
            if (linkList == null) linkList = document.has("links") && document.get("links").isArray() ? (ArrayNode) document.get("links") : document.putArray("links");
            boolean connected = false;
            for (JsonNode l : linkList) {
                String a = text(l, "from");
                String b = text(l, "to");
                if ((a.equals(from) && b.equals(to)) || (a.equals(to) && b.equals(from))) { connected = true; break; }
            }
            if (connected) { linksExisting++; continue; }
            String id = ORGANIZED_LINK_PREFIX + shortHash(sourceRef + "|" + from + "|" + to);
            if (dismissed.contains(id) || dismissed.contains(ORGANIZED_LINK_PREFIX + shortHash(sourceRef + "|" + to + "|" + from))) { linksExisting++; continue; }
            linkList.addObject().put("id", id).put("from", from).put("to", to).put("note", link.length > 2 && link[2] != null ? link[2].trim() : "");
            linksAdded++;
        }

        if (added + linksAdded == 0) return new int[]{0, skipped, 0, linksExisting};
        int current = t.getVersion() == null ? 1 : t.getVersion();
        t.setData(document.toString());
        t.setVersion(current + 1);
        t.setUpdatedAt(LocalDateTime.now());
        int rows = timelineMapper.update(t, new LambdaUpdateWrapper<SandboxTimeline>()
                .eq(SandboxTimeline::getId, t.getId())
                .eq(SandboxTimeline::getUserId, userId)
                .eq(SandboxTimeline::getVersion, current));
        if (rows == 0) throw stale();
        if (t.getCharacterId() != null) mirrorToSheet(userId, t.getCharacterId(), document);
        return new int[]{added, skipped, linksAdded, linksExisting};
    }

    /**
     * 这次整理出的每个事件对应到哪一条已有的事实（facts 里的序号，没有是 -1）。一条已有的事实最多对应一个新事件：
     * 同一句原文里写了两件事时，第二件不会被当成重复。优先级：id 相同，文字相同（不看标点、空白、大小写），
     * 引用的原文片段重合（重合的几条里取文字最接近的）。
     */
    static int[] matchFacts(List<String[]> events, String[] ids, List<String[]> facts) {
        int[] match = new int[events.size()];
        java.util.Arrays.fill(match, -1);
        String[] factText = new String[facts.size()];
        String[] factQuote = new String[facts.size()];
        for (int j = 0; j < facts.size(); j++) { factText[j] = plain(facts.get(j)[1]); factQuote[j] = plain(facts.get(j)[2]); }
        // [得分, 新事件序号, 已有事实序号]
        List<double[]> pairs = new ArrayList<double[]>();
        for (int i = 0; i < events.size(); i++) {
            if (ids[i] == null) continue;
            String body = plain(events.get(i)[1]);
            String quote = plain(events.get(i).length > 2 ? events.get(i)[2] : "");
            for (int j = 0; j < facts.size(); j++) {
                if (ids[i].equals(facts.get(j)[0])) pairs.add(new double[]{3, i, j});
                else if (!body.isEmpty() && body.equals(factText[j])) pairs.add(new double[]{2, i, j});
                else if (sameQuote(quote, factQuote[j])) pairs.add(new double[]{similarity(body, factText[j]), i, j});
            }
        }
        java.util.Collections.sort(pairs, (a, b) -> Double.compare(b[0], a[0]));
        boolean[] taken = new boolean[facts.size()];
        for (double[] p : pairs) {
            int i = (int) p[1];
            int j = (int) p[2];
            if (match[i] >= 0 || taken[j]) continue;
            match[i] = j;
            taken[j] = true;
        }
        return match;
    }

    /** 只留字母、数字和文字，字母转小写：标点、空白、大小写不同的两句话算相同 */
    static String plain(String s) {
        StringBuilder sb = new StringBuilder();
        if (s != null) s.codePoints().filter(Character::isLetterOrDigit).forEach(c -> sb.appendCodePoint(Character.toLowerCase(c)));
        return sb.toString();
    }

    /** 两段原文片段是不是指同一处：一段包含另一段，或者首尾相接地重合了较短一段的一半以上 */
    static boolean sameQuote(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.contains(b) || b.contains(a)) return true;
        int need = Math.max(4, (Math.min(a.length(), b.length()) + 1) / 2);
        return overlap(a, b) >= need || overlap(b, a) >= need;
    }

    /** a 的结尾和 b 的开头重合的最长长度 */
    private static int overlap(String a, String b) {
        for (int n = Math.min(a.length(), b.length()) - 1; n > 0; n--) if (a.regionMatches(a.length() - n, b, 0, n)) return n;
        return 0;
    }

    /** 相邻两字组合的重合程度，0 到 1 */
    private static double similarity(String a, String b) {
        java.util.Set<String> x = pairsOf(a);
        java.util.Set<String> y = pairsOf(b);
        if (x.isEmpty() || y.isEmpty()) return 0;
        int common = 0;
        for (String p : x) if (y.contains(p)) common++;
        return 2.0 * common / (x.size() + y.size());
    }

    private static java.util.Set<String> pairsOf(String s) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        for (int i = 0; i + 2 <= s.length(); i++) out.add(s.substring(i, i + 2));
        return out;
    }

    private static String organizedId(String[] ids, String index) {
        try {
            int i = Integer.parseInt(index == null ? "" : index.trim());
            return i >= 0 && i < ids.length ? ids[i] : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String shortHash(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-1").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", digest[i]));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ 文档

    static ObjectNode emptyDocument() {
        ObjectNode root = JSON.createObjectNode();
        root.putArray("nodes");
        root.putArray("events");
        root.putArray("links");
        root.putArray("shadows");
        root.putObject("settings");
        return root;
    }

    /** 角色卡 content JSON 里的 experiences / extraTimeline → 时间线文档 */
    static ObjectNode importFromSheet(String sheetContent) {
        ObjectNode root = emptyDocument();
        JsonNode sheet = parseObject(sheetContent);
        if (sheet == null) return root;
        ArrayNode nodes = (ArrayNode) root.get("nodes");
        ArrayNode events = (ArrayNode) root.get("events");
        ArrayNode shadows = (ArrayNode) root.get("shadows");
        int i = 0;
        for (JsonNode e : iterable(sheet.get("experiences"))) {
            String time = text(e, "time");
            String content = text(e, "content");
            if (time.isEmpty() && content.isEmpty()) continue;
            i++;
            nodes.addObject().put("id", "n-sheet-" + i).put("label", time).put("note", "");
            events.addObject().put("id", "e-sheet-" + i).put("nodeId", "n-sheet-" + i).put("title", "").put("text", content).put("side", "").put("note", "");
        }
        String extraTitle = text(sheet, "extraTimelineTitle");
        int j = 0;
        for (JsonNode e : iterable(sheet.get("extraTimeline"))) {
            String time = text(e, "time");
            String content = text(e, "content");
            if (time.isEmpty() && content.isEmpty()) continue;
            j++;
            shadows.addObject().put("id", "s-sheet-" + j).put("targetId", "").put("title", time).put("text", content)
                    .put("group", extraTitle).put("size", 14).put("bold", false).put("italic", false);
        }
        return root;
    }

    /** 按锚点顺序、锚点内按事件顺序给出 [时间, 事件文字]；没有事件的锚点给出 [时间, ""] */
    static List<String[]> orderedEvents(JsonNode document) {
        List<String[]> out = new ArrayList<String[]>();
        for (JsonNode node : iterable(document.get("nodes"))) {
            String nodeId = text(node, "id");
            String label = text(node, "label");
            boolean any = false;
            for (JsonNode e : iterable(document.get("events"))) {
                if (!nodeId.equals(text(e, "nodeId"))) continue;
                String title = text(e, "title");
                String body = text(e, "text");
                out.add(new String[]{label, title.isEmpty() ? body : (body.isEmpty() ? title : title + "：" + body)});
                any = true;
            }
            if (!any) out.add(new String[]{label, ""});
        }
        return out;
    }

    private static final java.util.regex.Pattern SHADOW_TAG = java.util.regex.Pattern.compile("</?(b|i|u|small|medium|large)>");

    /** 影子文字里的格式标记（加粗、斜体、下划线、字号）只在时间线上起作用；写进角色卡的是去掉标记后的文字 */
    static String plainShadowText(String text) {
        return text == null ? "" : SHADOW_TAG.matcher(text).replaceAll("");
    }

    /** 把时间线上的事件和不挂在任何事件上的影子层文字写回角色卡的 experiences / extraTimeline */
    private void mirrorToSheet(Long userId, Long characterId, JsonNode document) {
        SandboxCharacter c = characterMapper.selectById(characterId);
        if (c == null || !userId.equals(c.getUserId())) return;
        JsonNode parsed = parseObject(c.getContent());
        ObjectNode sheet = parsed == null ? JSON.createObjectNode() : (ObjectNode) parsed;
        ArrayNode experiences = sheet.putArray("experiences");
        for (String[] pair : orderedEvents(document)) {
            if (pair[0].isEmpty() && pair[1].isEmpty()) continue;
            experiences.addObject().put("time", pair[0]).put("content", pair[1]);
        }
        ArrayNode extra = sheet.putArray("extraTimeline");
        for (JsonNode s : iterable(document.get("shadows"))) {
            // 影子块是画布上的色块，不是补充时间线的文字
            if (!text(s, "targetId").isEmpty() || s.path("block").asBoolean(false)) continue;
            extra.addObject().put("time", text(s, "title")).put("content", plainShadowText(text(s, "text")));
        }
        String content = sheet.toString();
        if (content.equals(c.getContent())) return;
        characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, characterId)
                .eq(SandboxCharacter::getUserId, userId)
                .set(SandboxCharacter::getContent, content));
    }

    // ------------------------------------------------------------------ 工具

    private SandboxTimeline insert(Long userId, Long worldviewId, Long characterId, String slot, String data) {
        SandboxTimeline x = new SandboxTimeline();
        x.setUserId(userId);
        x.setWorldviewId(worldviewId);
        x.setCharacterId(characterId);
        x.setSlot(slot);
        x.setData(data);
        x.setVersion(1);
        x.setCreatedAt(LocalDateTime.now());
        x.setUpdatedAt(LocalDateTime.now());
        try {
            timelineMapper.insert(x);
        } catch (DuplicateKeyException e) {
            SandboxTimeline raced = bySlot(userId, slot);
            if (raced == null) throw e;
            return raced;
        }
        return x;
    }

    private SandboxTimeline bySlot(Long userId, String slot) {
        validUser(userId);
        List<SandboxTimeline> list = timelineMapper.selectList(new LambdaQueryWrapper<SandboxTimeline>()
                .eq(SandboxTimeline::getUserId, userId).eq(SandboxTimeline::getSlot, slot));
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private SandboxCharacter ownedCharacter(Long userId, Long id) {
        validUser(userId);
        SandboxCharacter c = id == null ? null : characterMapper.selectById(id);
        if (c == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "角色不存在");
        if (!userId.equals(c.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        return c;
    }

    private static JsonNode parseObject(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            JsonNode node = JSON.readTree(raw);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Iterable<JsonNode> iterable(JsonNode array) {
        return array != null && array.isArray() ? array : new ArrayList<JsonNode>();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }

    private static BusinessException stale() {
        return new BusinessException(ResultCode.DATA_EXIST, "这条时间线已在别处被修改，请刷新后再改");
    }

    private static void validUser(Long userId) {
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
    }
}
