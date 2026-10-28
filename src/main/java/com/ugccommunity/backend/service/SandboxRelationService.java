package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.dto.request.SandboxRelationRequest;
import com.ugccommunity.backend.entity.SandboxCharacter;
import com.ugccommunity.backend.entity.SandboxRelation;
import com.ugccommunity.backend.entity.SandboxRelationDismissed;
import com.ugccommunity.backend.mapper.SandboxCharacterMapper;
import com.ugccommunity.backend.mapper.SandboxRelationDismissedMapper;
import com.ugccommunity.backend.mapper.SandboxRelationMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 箱庭角色关系（sandbox_relation）与旧字段 sandbox_character.relation_* 的双向同步。
 *
 * <p>一对角色之间最多一行关系。过渡期内两边保持一致：
 * <ul>
 *   <li>旧版 App 保存角色（写 relation_character_ids）后，调用 {@link #syncFromLegacy} 把变化同步到关系表；</li>
 *   <li>新接口改关系后，调用 {@link #writeBackLegacy} 回写旧字段，让旧版 App 也能看到。</li>
 * </ul>
 * 旧字段每个角色只有一个关系名，所以回写时只保证「连了谁」一致，关系名以关系表为准。
 *
 * <p>角色卡里手填的关系文字（content JSON 的 relationships：人物 + 关系描述）和关系表也保持一致：
 * 关系改名时，原来和关系名相同的那条手填文字跟着改；关系删除时，那条手填文字一起删除，并记下这一对角色，
 * 「整理」不再把它建回来；角色卡里改了手填文字，原来和它相同的关系名跟着改。和关系名不同的手填文字是用户另写的内容，不动。
 */
@Service
public class SandboxRelationService {
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$");
    private static final int LABEL_MAX = 100;
    private static final int NOTE_MAX = 500;
    private static final int VIEW_MAX = 500;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SandboxRelationMapper relationMapper;
    private final SandboxCharacterMapper characterMapper;
    private final SandboxRelationDismissedMapper dismissedMapper;

    public SandboxRelationService(SandboxRelationMapper relationMapper, SandboxCharacterMapper characterMapper, SandboxRelationDismissedMapper dismissedMapper) {
        this.relationMapper = relationMapper;
        this.characterMapper = characterMapper;
        this.dismissedMapper = dismissedMapper;
    }

    // ------------------------------------------------------------------ 查询

    /** 列出关系；首次访问时从旧字段回填（按用户懒回填，幂等） */
    @Transactional
    public List<SandboxRelation> listRelations(Long userId) {
        validUser(userId);
        ensureBackfilled(userId);
        return relationMapper.selectList(new LambdaQueryWrapper<SandboxRelation>()
                .eq(SandboxRelation::getUserId, userId)
                .orderByAsc(SandboxRelation::getId));
    }

    /** 关系表为空、但旧字段里有关系时，按角色逐个同步一次 */
    void ensureBackfilled(Long userId) {
        Long count = relationMapper.selectCount(new LambdaQueryWrapper<SandboxRelation>().eq(SandboxRelation::getUserId, userId));
        if (count != null && count > 0) return;
        List<SandboxCharacter> characters = userCharacters(userId);
        boolean hasLegacy = characters.stream().anyMatch(c -> !parseIds(c.getRelationCharacterIds()).isEmpty());
        if (!hasLegacy) return;
        Map<Long, SandboxCharacter> byId = index(characters);
        for (SandboxCharacter c : characters) syncPairs(userId, c, byId, currentRelations(userId));
    }

    // ------------------------------------------------------------------ 新接口：增删改

    @Transactional
    public SandboxRelation createRelation(Long userId, SandboxRelationRequest r) {
        validUser(userId);
        if (r == null || r.getFromCharacterId() == null || r.getToCharacterId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择关系两端的角色");
        }
        if (r.getFromCharacterId().equals(r.getToCharacterId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能和自己建立关系");
        }
        ownedCharacter(userId, r.getFromCharacterId());
        ownedCharacter(userId, r.getToCharacterId());
        ensureBackfilled(userId);
        if (findPair(currentRelations(userId), r.getFromCharacterId(), r.getToCharacterId()) != null) {
            throw new BusinessException(ResultCode.DATA_EXIST, "这两个角色之间已经有关系了，请直接编辑");
        }
        SandboxRelation x = new SandboxRelation();
        x.setUserId(userId);
        x.setFromCharacterId(r.getFromCharacterId());
        x.setToCharacterId(r.getToCharacterId());
        x.setDirection(SandboxRelation.ONE_WAY);
        applyRequest(x, r);
        x.setSource(SandboxRelation.SOURCE_APP);
        x.setCreatedAt(LocalDateTime.now());
        x.setUpdatedAt(LocalDateTime.now());
        relationMapper.insert(x);
        // 用户重新建立了这一对角色的关系：之前的删除记录作废
        forgetDismissed(userId, x.getFromCharacterId(), x.getToCharacterId());
        writeBackLegacy(userId, setOf(x.getFromCharacterId(), x.getToCharacterId()));
        return x;
    }

    @Transactional
    public SandboxRelation updateRelation(Long userId, Long id, SandboxRelationRequest r) {
        SandboxRelation x = ownedRelation(userId, id);
        if (r == null) throw new BusinessException(ResultCode.PARAM_ERROR);
        String oldLabel = x.getLabel();
        String oldReverse = x.isTwoWay() ? x.getReverseLabel() : null;
        applyRequest(x, r);
        // 用户在新界面改过的关系，关系名以关系表为准，旧字段同步时不再覆盖
        x.setSource(SandboxRelation.SOURCE_APP);
        x.setUpdatedAt(LocalDateTime.now());
        relationMapper.updateById(x);
        if (!eq(oldLabel, x.getLabel())) rewriteTyped(userId, x.getFromCharacterId(), x.getToCharacterId(), oldLabel, x.getLabel() == null ? "" : x.getLabel());
        if (x.isTwoWay() && !isBlank(x.getReverseLabel()) && !eq(oldReverse, x.getReverseLabel())) {
            rewriteTyped(userId, x.getToCharacterId(), x.getFromCharacterId(), oldReverse, x.getReverseLabel());
        }
        writeBackLegacy(userId, setOf(x.getFromCharacterId(), x.getToCharacterId()));
        return x;
    }

    @Transactional
    public void deleteRelation(Long userId, Long id) {
        SandboxRelation x = ownedRelation(userId, id);
        relationMapper.deleteById(x.getId());
        rewriteTyped(userId, x.getFromCharacterId(), x.getToCharacterId(), x.getLabel(), null);
        if (x.isTwoWay()) rewriteTyped(userId, x.getToCharacterId(), x.getFromCharacterId(), x.getReverseLabel(), null);
        rememberDismissed(userId, x.getFromCharacterId(), x.getToCharacterId());
        writeBackLegacy(userId, setOf(x.getFromCharacterId(), x.getToCharacterId()));
    }

    // ------------------------------------------------------------------ 删掉过的关系

    /** 用户删掉过 a 与 b 之间的关系，之后没有自己重新建立 */
    public boolean isDismissed(Long userId, Long a, Long b) {
        return findDismissed(userId, a, b) != null;
    }

    private SandboxRelationDismissed findDismissed(Long userId, Long a, Long b) {
        if (a == null || b == null) return null;
        List<SandboxRelationDismissed> list = dismissedMapper.selectList(new LambdaQueryWrapper<SandboxRelationDismissed>().eq(SandboxRelationDismissed::getUserId, userId));
        if (list != null) for (SandboxRelationDismissed d : list) if (d.connects(a, b)) return d;
        return null;
    }

    private void rememberDismissed(Long userId, Long a, Long b) {
        if (findDismissed(userId, a, b) != null) return;
        SandboxRelationDismissed d = new SandboxRelationDismissed();
        d.setUserId(userId);
        d.setCharacterAId(Math.min(a, b));
        d.setCharacterBId(Math.max(a, b));
        d.setCreatedAt(LocalDateTime.now());
        dismissedMapper.insert(d);
    }

    /** 用户明确重新连上 a 与 b（例如在角色编辑里新勾上连线）：撤销「删掉过」的记录 */
    @Transactional
    public void forgetDismissedPair(Long userId, Long a, Long b) {
        forgetDismissed(userId, a, b);
    }

    private void forgetDismissed(Long userId, Long a, Long b) {
        SandboxRelationDismissed d = findDismissed(userId, a, b);
        if (d != null) dismissedMapper.deleteById(d.getId());
    }

    // ------------------------------------------------------------------ 角色卡里手填的关系文字

    /**
     * 角色保存之后调用：角色卡里某条手填的关系文字被改了，而它原来和关系名相同，关系名跟着改。
     * 手填文字被删掉不删除关系（「关联角色」之后那条文字本来就会删掉）。
     */
    @Transactional
    public void syncFromTyped(Long userId, Long characterId, String oldContent, String newContent) {
        validUser(userId);
        if (eq(oldContent, newContent)) return;
        SandboxCharacter self = characterMapper.selectById(characterId);
        if (self == null || !userId.equals(self.getUserId())) return;
        List<SandboxCharacter> candidates = sameWorld(userCharacters(userId), self);
        for (SandboxRelation x : currentRelations(userId)) {
            boolean outgoing = characterId.equals(x.getFromCharacterId());
            if (!outgoing && !(characterId.equals(x.getToCharacterId()) && x.isTwoWay())) continue;
            Long otherId = outgoing ? x.getToCharacterId() : x.getFromCharacterId();
            String name = uniqueName(candidates, otherId);
            if (name == null) continue;
            String current = outgoing ? x.getLabel() : x.getReverseLabel();
            List<String> before = typedTexts(oldContent, name);
            List<String> after = typedTexts(newContent, name);
            if (after.isEmpty() || !containsLabel(before, current) || containsLabel(after, current)) continue;
            String typed = after.get(0);
            String label = trimTo(typed, LABEL_MAX);
            if (outgoing) x.setLabel(label); else x.setReverseLabel(label);
            // 超过关系名长度的手填文字，完整内容之前放在说明里：说明没被另外改过时一起更新
            String oldNote = fullNote(before, current);
            if (isBlank(x.getNote()) ? typed.length() > LABEL_MAX : x.getNote().equals(oldNote)) x.setNote(typed.length() > LABEL_MAX ? trimTo(typed, NOTE_MAX) : null);
            x.setSource(SandboxRelation.SOURCE_APP);
            x.setUpdatedAt(LocalDateTime.now());
            relationMapper.updateById(x);
        }
    }

    /**
     * owner 的角色卡里手填、人物是 other、内容和 oldLabel 相同的关系文字：newText 为 null 时删除，否则改成 newText。
     * 只有名字唯一对应 other 时才处理，和关系名不同的手填文字不动。
     */
    private void rewriteTyped(Long userId, Long ownerId, Long otherId, String oldLabel, String newText) {
        SandboxCharacter owner = characterMapper.selectById(ownerId);
        if (owner == null || !userId.equals(owner.getUserId())) return;
        String name = uniqueName(sameWorld(userCharacters(userId), owner), otherId);
        if (name == null) return;
        JsonNode parsed = parse(owner.getContent());
        if (parsed == null || !parsed.isObject() || !parsed.path("relationships").isArray()) return;
        ArrayNode list = (ArrayNode) parsed.get("relationships");
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            JsonNode e = list.get(i);
            if (!e.isObject() || !name.equals(e.path("name").asText("").trim())) continue;
            if (!sameLabel(e.path("relation").asText(""), oldLabel)) continue;
            if (newText == null) list.remove(i); else ((ObjectNode) e).put("relation", newText);
            changed = true;
        }
        if (!changed) return;
        owner.setContent(parsed.toString());
        owner.setUpdatedAt(LocalDateTime.now());
        characterMapper.updateById(owner);
    }

    /** 手填文字和关系名是不是同一条内容：关系名最多 100 字，更长的手填文字只比较前 100 字 */
    private static boolean sameLabel(String typed, String label) {
        return eq(trimTo(typed, LABEL_MAX), trimTo(label, LABEL_MAX));
    }

    private static boolean containsLabel(List<String> typed, String label) {
        for (String t : typed) if (sameLabel(t, label)) return true;
        return false;
    }

    private static String fullNote(List<String> typed, String label) {
        for (String t : typed) if (sameLabel(t, label)) return trimTo(t, NOTE_MAX);
        return null;
    }

    private static List<String> typedTexts(String content, String name) {
        List<String> out = new ArrayList<String>();
        JsonNode parsed = parse(content);
        if (parsed == null || !parsed.path("relationships").isArray()) return out;
        for (JsonNode e : parsed.get("relationships")) if (name.equals(e.path("name").asText("").trim())) out.add(e.path("relation").asText("").trim());
        return out;
    }

    /** 和「整理」认人的范围一致：角色所在世界观里的角色；角色没有世界观时是这个用户的全部角色 */
    private static List<SandboxCharacter> sameWorld(List<SandboxCharacter> all, SandboxCharacter self) {
        Long worldviewId = self.getWorldviewId();
        if (worldviewId == null) return all;
        List<SandboxCharacter> out = new ArrayList<SandboxCharacter>();
        for (SandboxCharacter c : all) {
            boolean inWorld = worldviewId.equals(c.getWorldviewId());
            if (!inWorld && c.getWorldviewIds() != null) {
                for (String part : c.getWorldviewIds().split(",")) if (part.trim().equals(String.valueOf(worldviewId))) { inWorld = true; break; }
            }
            if (inWorld) out.add(c);
        }
        return out;
    }

    /** id 对应角色的名字；范围里有同名角色（手填文字对不上唯一的人）或没有名字时返回 null */
    private static String uniqueName(List<SandboxCharacter> candidates, Long id) {
        String name = null;
        for (SandboxCharacter c : candidates) if (c.getId().equals(id)) name = c.getName() == null ? "" : c.getName().trim();
        if (name == null || name.isEmpty()) return null;
        int same = 0;
        for (SandboxCharacter c : candidates) if (name.equals(c.getName() == null ? "" : c.getName().trim())) same++;
        return same == 1 ? name : null;
    }

    private static JsonNode parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try { return JSON.readTree(raw); } catch (Exception e) { return null; }
    }

    // ------------------------------------------------------------------ 与角色增删改的联动

    /**
     * 角色保存后调用（新建、修改）。根据该角色和对端角色的旧字段，重算这个角色参与的每一对关系。
     * 调用方需保证角色已写库；这里会重新读取，避免 updateById 跳过 null 字段导致内存与库不一致。
     */
    @Transactional
    public void syncFromLegacy(Long userId, Long characterId) {
        validUser(userId);
        List<SandboxCharacter> characters = userCharacters(userId);
        Map<Long, SandboxCharacter> byId = index(characters);
        SandboxCharacter c = byId.get(characterId);
        if (c == null) return;
        syncPairs(userId, c, byId, currentRelations(userId));
    }

    /** 角色删除后调用：删掉相关关系，并把它从其他角色的旧字段里移除 */
    @Transactional
    public void onCharacterDeleted(Long userId, Long characterId) {
        validUser(userId);
        Set<Long> affected = new LinkedHashSet<Long>();
        for (SandboxRelation x : currentRelations(userId)) {
            if (!characterId.equals(x.getFromCharacterId()) && !characterId.equals(x.getToCharacterId())) continue;
            relationMapper.deleteById(x.getId());
            affected.add(x.getFromCharacterId().equals(characterId) ? x.getToCharacterId() : x.getFromCharacterId());
        }
        // 旧字段里单方面引用了被删角色、但关系表里没有的情况（例如回填前）也一并清理
        for (SandboxCharacter other : userCharacters(userId)) {
            if (parseIds(other.getRelationCharacterIds()).contains(characterId)) affected.add(other.getId());
        }
        affected.remove(characterId);
        List<SandboxRelationDismissed> dismissed = dismissedMapper.selectList(new LambdaQueryWrapper<SandboxRelationDismissed>().eq(SandboxRelationDismissed::getUserId, userId));
        if (dismissed != null) for (SandboxRelationDismissed d : dismissed) {
            if (characterId.equals(d.getCharacterAId()) || characterId.equals(d.getCharacterBId())) dismissedMapper.deleteById(d.getId());
        }
        writeBackLegacy(userId, affected);
    }

    // ------------------------------------------------------------------ 同步核心

    private void syncPairs(Long userId, SandboxCharacter c, Map<Long, SandboxCharacter> byId, List<SandboxRelation> relations) {
        Set<Long> others = new LinkedHashSet<Long>();
        for (Long id : parseIds(c.getRelationCharacterIds())) {
            if (!id.equals(c.getId()) && byId.containsKey(id)) others.add(id);
        }
        for (SandboxRelation x : relations) {
            if (c.getId().equals(x.getFromCharacterId())) others.add(x.getToCharacterId());
            else if (c.getId().equals(x.getToCharacterId())) others.add(x.getFromCharacterId());
        }
        for (Long otherId : others) {
            SandboxCharacter o = byId.get(otherId);
            SandboxRelation row = findPair(relations, c.getId(), otherId);
            if (o == null) {
                // 对端角色已不存在
                if (row != null) {
                    relationMapper.deleteById(row.getId());
                    relations.remove(row);
                }
                continue;
            }
            SandboxRelation next = reconcile(userId, row, c, o);
            if (next == null) {
                if (row != null) {
                    relationMapper.deleteById(row.getId());
                    relations.remove(row);
                }
            } else if (row == null) {
                // 用户删掉过这一对的关系：旧字段里残留的连线（旧客户端、过期的角色保存）不能把它建回来。
                // 用户在角色编辑里新勾上连线时，SandboxService 会先撤销删除记录，这里就能正常建立
                if (isDismissed(userId, c.getId(), otherId)) continue;
                relationMapper.insert(next);
                relations.add(next);
            } else if (changed(row, next)) {
                relationMapper.updateById(next);
                relations.set(relations.indexOf(row), next);
            }
        }
    }

    /**
     * 根据两个角色旧字段里「谁列了谁」，算出这一对关系应有的状态；返回 null 表示应删除。
     * 已存在的 app 来源关系保留自己的关系名，只调整方向；legacy 来源关系的关系名跟随旧字段。
     */
    static SandboxRelation reconcile(Long userId, SandboxRelation row, SandboxCharacter a, SandboxCharacter b) {
        boolean aListsB = parseIds(a.getRelationCharacterIds()).contains(b.getId());
        boolean bListsA = parseIds(b.getRelationCharacterIds()).contains(a.getId());
        if (!aListsB && !bListsA) return null;

        if (row == null) {
            SandboxRelation x = new SandboxRelation();
            x.setUserId(userId);
            SandboxCharacter from = aListsB ? a : b;
            SandboxCharacter to = aListsB ? b : a;
            x.setFromCharacterId(from.getId());
            x.setToCharacterId(to.getId());
            x.setDirection(aListsB && bListsA ? SandboxRelation.TWO_WAY : SandboxRelation.ONE_WAY);
            x.setLabel(trimTo(from.getRelationLabel(), LABEL_MAX));
            x.setReverseLabel(x.isTwoWay() ? trimTo(to.getRelationLabel(), LABEL_MAX) : null);
            x.setColor(validColorOrNull(from.getRelationColor()));
            x.setArrow(Boolean.TRUE.equals(from.getRelationArrow()));
            x.setSource(SandboxRelation.SOURCE_LEGACY);
            x.setCreatedAt(LocalDateTime.now());
            x.setUpdatedAt(LocalDateTime.now());
            return x;
        }

        SandboxRelation x = copy(row);
        boolean fromIsA = a.getId().equals(row.getFromCharacterId());
        SandboxCharacter from = fromIsA ? a : b;
        SandboxCharacter to = fromIsA ? b : a;
        boolean fromListsTo = fromIsA ? aListsB : bListsA;
        boolean toListsFrom = fromIsA ? bListsA : aListsB;

        if (fromListsTo && toListsFrom) {
            x.setDirection(SandboxRelation.TWO_WAY);
            if (row.isLegacy()) {
                x.setLabel(trimTo(from.getRelationLabel(), LABEL_MAX));
                x.setReverseLabel(trimTo(to.getRelationLabel(), LABEL_MAX));
            } else if (isBlank(x.getReverseLabel())) {
                x.setReverseLabel(trimTo(to.getRelationLabel(), LABEL_MAX));
            }
        } else if (fromListsTo) {
            x.setDirection(SandboxRelation.ONE_WAY);
            x.setReverseLabel(null);
            if (row.isLegacy()) x.setLabel(trimTo(from.getRelationLabel(), LABEL_MAX));
        } else {
            // 只剩反方向：翻转端点，原反向名变成正向名
            x.setFromCharacterId(to.getId());
            x.setToCharacterId(from.getId());
            x.setFromView(row.getToView());
            x.setToView(row.getFromView());
            x.setDirection(SandboxRelation.ONE_WAY);
            String keep = row.isLegacy() ? to.getRelationLabel() : (isBlank(row.getReverseLabel()) ? row.getLabel() : row.getReverseLabel());
            x.setLabel(trimTo(keep, LABEL_MAX));
            x.setReverseLabel(null);
        }
        if (row.isLegacy()) {
            SandboxCharacter owner = byIdOf(x.getFromCharacterId(), a, b);
            x.setColor(validColorOrNull(owner.getRelationColor()));
            x.setArrow(Boolean.TRUE.equals(owner.getRelationArrow()));
        }
        x.setUpdatedAt(LocalDateTime.now());
        return x;
    }

    /**
     * 把关系表的结果回写到旧字段 relation_character_ids：
     * 单向关系记在起点角色上，双向关系两端都记。关系名只在旧字段为空时补上第一条关系的名字。
     */
    void writeBackLegacy(Long userId, Collection<Long> characterIds) {
        if (characterIds == null || characterIds.isEmpty()) return;
        List<SandboxRelation> relations = currentRelations(userId);
        for (Long id : characterIds) {
            SandboxCharacter c = characterMapper.selectById(id);
            if (c == null || !userId.equals(c.getUserId())) continue;
            TreeSet<Long> targets = new TreeSet<Long>();
            String firstLabel = null;
            for (SandboxRelation x : relations) {
                if (id.equals(x.getFromCharacterId())) {
                    targets.add(x.getToCharacterId());
                    if (firstLabel == null && !isBlank(x.getLabel())) firstLabel = x.getLabel();
                } else if (id.equals(x.getToCharacterId()) && x.isTwoWay()) {
                    targets.add(x.getFromCharacterId());
                    if (firstLabel == null && !isBlank(x.getReverseLabel())) firstLabel = x.getReverseLabel();
                }
            }
            String joined = targets.stream().map(String::valueOf).collect(Collectors.joining(","));
            boolean dirty = !joined.equals(normalizeIds(c.getRelationCharacterIds()));
            if (isBlank(c.getRelationLabel()) && firstLabel != null) {
                c.setRelationLabel(firstLabel);
                dirty = true;
            }
            if (dirty) {
                // 用空串而不是 null：MyBatis-Plus 默认会跳过 null 字段，清空列表时写不进库
                c.setRelationCharacterIds(joined);
                c.setUpdatedAt(LocalDateTime.now());
                characterMapper.updateById(c);
            }
        }
    }

    // ------------------------------------------------------------------ 工具

    private void applyRequest(SandboxRelation x, SandboxRelationRequest r) {
        if (r.getDirection() != null) {
            if (!SandboxRelation.ONE_WAY.equals(r.getDirection()) && !SandboxRelation.TWO_WAY.equals(r.getDirection())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "关系方向只能是单向或双向");
            }
            x.setDirection(r.getDirection());
        }
        if (r.getLabel() != null) x.setLabel(checkLength(r.getLabel(), LABEL_MAX, "关系名称"));
        if (r.getReverseLabel() != null) x.setReverseLabel(checkLength(r.getReverseLabel(), LABEL_MAX, "反向关系名称"));
        if (!x.isTwoWay()) x.setReverseLabel(null);
        if (r.getNote() != null) x.setNote(checkLength(r.getNote(), NOTE_MAX, "说明"));
        if (r.getFromView() != null) x.setFromView(blankToNull(checkLength(r.getFromView(), VIEW_MAX, "对其他角色的认知")));
        if (r.getToView() != null) x.setToView(blankToNull(checkLength(r.getToView(), VIEW_MAX, "对其他角色的认知")));
        if (r.getColor() != null) {
            String color = r.getColor().trim();
            if (!color.isEmpty() && !COLOR.matcher(color).matches()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "颜色格式应为 #RRGGBB");
            }
            x.setColor(color.isEmpty() ? null : color);
        }
        if (r.getArrow() != null) x.setArrow(r.getArrow());
        if (x.getArrow() == null) x.setArrow(Boolean.TRUE);
    }

    private SandboxRelation ownedRelation(Long userId, Long id) {
        validUser(userId);
        SandboxRelation x = id == null ? null : relationMapper.selectById(id);
        if (x == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST);
        if (!userId.equals(x.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        return x;
    }

    private SandboxCharacter ownedCharacter(Long userId, Long id) {
        SandboxCharacter c = characterMapper.selectById(id);
        if (c == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "角色不存在");
        if (!userId.equals(c.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        return c;
    }

    private List<SandboxCharacter> userCharacters(Long userId) {
        List<SandboxCharacter> list = characterMapper.selectList(new LambdaQueryWrapper<SandboxCharacter>().eq(SandboxCharacter::getUserId, userId));
        return list == null ? Collections.<SandboxCharacter>emptyList() : list;
    }

    private List<SandboxRelation> currentRelations(Long userId) {
        List<SandboxRelation> list = relationMapper.selectList(new LambdaQueryWrapper<SandboxRelation>().eq(SandboxRelation::getUserId, userId));
        return list == null ? new ArrayList<SandboxRelation>() : new ArrayList<SandboxRelation>(list);
    }

    private static Map<Long, SandboxCharacter> index(List<SandboxCharacter> characters) {
        Map<Long, SandboxCharacter> byId = new HashMap<Long, SandboxCharacter>();
        for (SandboxCharacter c : characters) byId.put(c.getId(), c);
        return byId;
    }

    static SandboxRelation findPair(List<SandboxRelation> relations, Long a, Long b) {
        for (SandboxRelation x : relations) if (x.connects(a, b)) return x;
        return null;
    }

    /** "1, 2,abc,2" → [1, 2]；保持顺序、去重、忽略非法项 */
    static List<Long> parseIds(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        LinkedHashSet<Long> ids = new LinkedHashSet<Long>();
        for (String part : raw.replace("[", "").replace("]", "").split(",")) {
            try {
                String s = part.trim().replace("\"", "");
                if (!s.isEmpty()) ids.add(Long.valueOf(s));
            } catch (NumberFormatException ignored) {
                // 旧数据里可能有脏值，跳过
            }
        }
        return new ArrayList<Long>(ids);
    }

    private static String normalizeIds(String raw) {
        return new TreeSet<Long>(parseIds(raw)).stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static SandboxCharacter byIdOf(Long id, SandboxCharacter a, SandboxCharacter b) {
        return a.getId().equals(id) ? a : b;
    }

    private static SandboxRelation copy(SandboxRelation r) {
        SandboxRelation x = new SandboxRelation();
        x.setId(r.getId()); x.setUserId(r.getUserId());
        x.setFromCharacterId(r.getFromCharacterId()); x.setToCharacterId(r.getToCharacterId());
        x.setLabel(r.getLabel()); x.setReverseLabel(r.getReverseLabel()); x.setDirection(r.getDirection());
        x.setColor(r.getColor()); x.setArrow(r.getArrow()); x.setNote(r.getNote()); x.setSource(r.getSource());
        x.setFromView(r.getFromView()); x.setToView(r.getToView());
        x.setCreatedAt(r.getCreatedAt()); x.setUpdatedAt(r.getUpdatedAt());
        return x;
    }

    private static boolean changed(SandboxRelation a, SandboxRelation b) {
        return !eq(a.getFromCharacterId(), b.getFromCharacterId()) || !eq(a.getToCharacterId(), b.getToCharacterId())
                || !eq(a.getLabel(), b.getLabel()) || !eq(a.getReverseLabel(), b.getReverseLabel())
                || !eq(a.getDirection(), b.getDirection()) || !eq(a.getColor(), b.getColor()) || !eq(a.getArrow(), b.getArrow())
                || !eq(a.getFromView(), b.getFromView()) || !eq(a.getToView(), b.getToView());
    }

    private static boolean eq(Object a, Object b) { return a == null ? b == null : a.equals(b); }

    private static String blankToNull(String s) { return s == null || s.trim().isEmpty() ? null : s.trim(); }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static String trimTo(String s, int max) {
        if (isBlank(s)) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String checkLength(String s, int max, String field) {
        String t = s.trim();
        if (t.length() > max) throw new BusinessException(ResultCode.PARAM_ERROR, field + "不能超过 " + max + " 个字");
        return t.isEmpty() ? null : t;
    }

    private static String validColorOrNull(String s) {
        return s != null && COLOR.matcher(s.trim()).matches() ? s.trim() : null;
    }

    private static Set<Long> setOf(Long a, Long b) {
        Set<Long> s = new LinkedHashSet<Long>();
        s.add(a);
        s.add(b);
        return s;
    }

    private static void validUser(Long userId) {
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
    }
}
