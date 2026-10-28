package com.ugccommunity.backend.service;

import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.dto.request.SandboxRelationRequest;
import com.ugccommunity.backend.entity.SandboxCharacter;
import com.ugccommunity.backend.entity.SandboxRelation;
import com.ugccommunity.backend.entity.SandboxRelationDismissed;
import com.ugccommunity.backend.mapper.SandboxCharacterMapper;
import com.ugccommunity.backend.mapper.SandboxRelationDismissedMapper;
import com.ugccommunity.backend.mapper.SandboxRelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

/**
 * 用内存表模拟两个 Mapper（测试里只有一个用户，查询条件只按 user_id 过滤），
 * 重点验证关系表和旧字段 relation_character_ids 的双向同步。
 */
@ExtendWith(MockitoExtension.class)
class SandboxRelationServiceTest {
    private static final long USER = 7L;
    private static final long OTHER_USER = 8L;

    @Mock private SandboxRelationMapper relationMapper;
    @Mock private SandboxCharacterMapper characterMapper;
    @Mock private SandboxRelationDismissedMapper dismissedMapper;
    private SandboxRelationService service;

    private final Map<Long, SandboxCharacter> characters = new LinkedHashMap<Long, SandboxCharacter>();
    private final Map<Long, SandboxRelation> relations = new LinkedHashMap<Long, SandboxRelation>();
    private final Map<Long, SandboxRelationDismissed> dismissed = new LinkedHashMap<Long, SandboxRelationDismissed>();
    private long relationSeq = 100;

    @BeforeEach
    void setUp() {
        service = new SandboxRelationService(relationMapper, characterMapper, dismissedMapper);
        lenient().when(dismissedMapper.selectList(any())).thenAnswer(inv -> new ArrayList<SandboxRelationDismissed>(dismissed.values()));
        lenient().when(dismissedMapper.insert(any(SandboxRelationDismissed.class))).thenAnswer(inv -> {
            SandboxRelationDismissed d = inv.getArgument(0);
            d.setId(++relationSeq);
            dismissed.put(d.getId(), d);
            return 1;
        });
        lenient().when(dismissedMapper.deleteById(anyLong())).thenAnswer(inv -> dismissed.remove((Long) inv.getArgument(0)) == null ? 0 : 1);
        lenient().when(characterMapper.selectList(any())).thenAnswer(inv -> characters.values().stream()
                .filter(c -> c.getUserId() == USER).map(this::copy).collect(Collectors.toList()));
        lenient().when(characterMapper.selectById(anyLong())).thenAnswer(inv -> {
            SandboxCharacter c = characters.get((Long) inv.getArgument(0));
            return c == null ? null : copy(c);
        });
        lenient().when(characterMapper.updateById(any(SandboxCharacter.class))).thenAnswer(inv -> {
            SandboxCharacter c = inv.getArgument(0);
            characters.put(c.getId(), copy(c));
            return 1;
        });
        lenient().when(relationMapper.selectList(any())).thenAnswer(inv -> relations.values().stream()
                .filter(r -> r.getUserId() == USER).map(this::copy).collect(Collectors.toList()));
        lenient().when(relationMapper.selectCount(any())).thenAnswer(inv -> relations.values().stream()
                .filter(r -> r.getUserId() == USER).count());
        lenient().when(relationMapper.selectById(anyLong())).thenAnswer(inv -> {
            SandboxRelation r = relations.get((Long) inv.getArgument(0));
            return r == null ? null : copy(r);
        });
        lenient().when(relationMapper.insert(any(SandboxRelation.class))).thenAnswer(inv -> {
            SandboxRelation r = inv.getArgument(0);
            r.setId(++relationSeq);
            relations.put(r.getId(), copy(r));
            return 1;
        });
        lenient().when(relationMapper.updateById(any(SandboxRelation.class))).thenAnswer(inv -> {
            SandboxRelation r = inv.getArgument(0);
            relations.put(r.getId(), copy(r));
            return 1;
        });
        lenient().when(relationMapper.deleteById(anyLong())).thenAnswer(inv -> relations.remove((Long) inv.getArgument(0)) == null ? 0 : 1);
    }

    // ---------------------------------------------------------------- 回填

    @Test
    void backfill_mergesMutualLegacyListsIntoOneTwoWayRelation() {
        character(1, "2,3", "师父");
        character(2, "1", "徒弟");
        character(3, "", null);

        List<SandboxRelation> list = service.listRelations(USER);

        assertEquals(2, list.size());
        SandboxRelation twoWay = pair(1, 2);
        assertEquals(SandboxRelation.TWO_WAY, twoWay.getDirection());
        assertEquals("师父", twoWay.getLabel());
        assertEquals("徒弟", twoWay.getReverseLabel());
        assertEquals(SandboxRelation.SOURCE_LEGACY, twoWay.getSource());
        SandboxRelation oneWay = pair(1, 3);
        assertEquals(SandboxRelation.ONE_WAY, oneWay.getDirection());
        assertEquals(1L, oneWay.getFromCharacterId());
        assertEquals("师父", oneWay.getLabel());
    }

    @Test
    void backfill_isIdempotent() {
        character(1, "2", "朋友");
        character(2, "", null);
        service.listRelations(USER);
        service.listRelations(USER);
        assertEquals(1, relations.size());
    }

    @Test
    void backfill_ignoresSelfLinksDirtyIdsAndOtherUsersCharacters() {
        character(1, "1, abc, 2, 99", "朋友");
        character(2, "", null);
        SandboxCharacter foreign = character(99, "", null);
        foreign.setUserId(OTHER_USER);
        service.listRelations(USER);
        assertEquals(1, relations.size());
        assertTrue(pair(1, 2) != null);
    }

    // ---------------------------------------------------------------- 新接口 → 旧字段

    @Test
    void create_writesBackLegacyFieldsOnBothEndsForTwoWay() {
        character(1, "", null);
        character(2, "", null);
        SandboxRelation r = service.createRelation(USER, request(1, 2, "宿敌", "宿敌", SandboxRelation.TWO_WAY));

        assertEquals(SandboxRelation.SOURCE_APP, r.getSource());
        assertEquals("2", characters.get(1L).getRelationCharacterIds());
        assertEquals("1", characters.get(2L).getRelationCharacterIds());
        // 旧字段关系名为空时补上
        assertEquals("宿敌", characters.get(1L).getRelationLabel());
    }

    @Test
    void create_rejectsDuplicatePairSelfLinkAndForeignCharacter() {
        character(1, "", null);
        character(2, "", null);
        SandboxCharacter foreign = character(3, "", null);
        foreign.setUserId(OTHER_USER);
        service.createRelation(USER, request(1, 2, "朋友", null, null));

        assertThrows(BusinessException.class, () -> service.createRelation(USER, request(2, 1, "朋友", null, null)));
        assertThrows(BusinessException.class, () -> service.createRelation(USER, request(1, 1, "自己", null, null)));
        assertThrows(BusinessException.class, () -> service.createRelation(USER, request(1, 3, "越权", null, null)));
    }

    @Test
    void create_validatesColorDirectionAndLength() {
        character(1, "", null);
        character(2, "", null);
        SandboxRelationRequest badColor = request(1, 2, "朋友", null, null);
        badColor.setColor("red");
        assertThrows(BusinessException.class, () -> service.createRelation(USER, badColor));
        assertThrows(BusinessException.class, () -> service.createRelation(USER, request(1, 2, "朋友", null, "sideways")));
        StringBuilder longLabel = new StringBuilder();
        for (int i = 0; i < 101; i++) longLabel.append('字');
        assertThrows(BusinessException.class, () -> service.createRelation(USER, request(1, 2, longLabel.toString(), null, null)));
    }

    @Test
    void update_switchingToOneWayRemovesReverseSideFromLegacy() {
        character(1, "", null);
        character(2, "", null);
        SandboxRelation r = service.createRelation(USER, request(1, 2, "师父", "徒弟", SandboxRelation.TWO_WAY));

        SandboxRelationRequest edit = new SandboxRelationRequest();
        edit.setDirection(SandboxRelation.ONE_WAY);
        SandboxRelation updated = service.updateRelation(USER, r.getId(), edit);

        assertNull(updated.getReverseLabel());
        assertEquals("2", characters.get(1L).getRelationCharacterIds());
        assertEquals("", characters.get(2L).getRelationCharacterIds());
    }

    @Test
    void update_and_delete_rejectOtherUsersRelation() {
        SandboxRelation foreign = new SandboxRelation();
        foreign.setId(500L);
        foreign.setUserId(OTHER_USER);
        foreign.setFromCharacterId(1L);
        foreign.setToCharacterId(2L);
        relations.put(500L, foreign);
        assertThrows(BusinessException.class, () -> service.updateRelation(USER, 500L, new SandboxRelationRequest()));
        assertThrows(BusinessException.class, () -> service.deleteRelation(USER, 500L));
        assertThrows(BusinessException.class, () -> service.deleteRelation(USER, 999L));
    }

    @Test
    void delete_clearsLegacyListWithEmptyStringNotNull() {
        character(1, "", null);
        character(2, "", null);
        SandboxRelation r = service.createRelation(USER, request(1, 2, "朋友", null, null));
        service.deleteRelation(USER, r.getId());
        assertEquals("", characters.get(1L).getRelationCharacterIds());
        assertTrue(relations.isEmpty());
    }

    // ---------------------------------------------------------------- 旧字段 → 新表（旧版 App 保存角色）

    @Test
    void legacySave_addingAndRemovingTargetsSyncsRelations() {
        character(1, "", "朋友");
        character(2, "", null);
        character(3, "", null);

        characters.get(1L).setRelationCharacterIds("2,3");
        service.syncFromLegacy(USER, 1L);
        assertEquals(2, relations.size());

        characters.get(1L).setRelationCharacterIds("3");
        service.syncFromLegacy(USER, 1L);
        assertEquals(1, relations.size());
        assertTrue(pair(1, 3) != null);
    }

    @Test
    void legacySave_keepsLabelsOfRelationsEditedInNewApp() {
        character(1, "", "朋友");
        character(2, "", null);
        service.createRelation(USER, request(1, 2, "青梅竹马", null, null));

        // 旧版 App 改了角色 1 的统一关系名，列表不变
        characters.get(1L).setRelationLabel("同学");
        service.syncFromLegacy(USER, 1L);

        assertEquals("青梅竹马", pair(1, 2).getLabel());
    }

    @Test
    void legacySave_otherSideListingBackTurnsOneWayIntoTwoWay() {
        character(1, "2", "师父");
        character(2, "", "徒弟");
        service.listRelations(USER);
        assertEquals(SandboxRelation.ONE_WAY, pair(1, 2).getDirection());

        characters.get(2L).setRelationCharacterIds("1");
        service.syncFromLegacy(USER, 2L);

        SandboxRelation r = pair(1, 2);
        assertEquals(SandboxRelation.TWO_WAY, r.getDirection());
        assertEquals("师父", r.getLabel());
        assertEquals("徒弟", r.getReverseLabel());
    }

    @Test
    void legacySave_removingFromSideOfAppTwoWayFlipsToRemainingDirection() {
        character(1, "", null);
        character(2, "", null);
        service.createRelation(USER, request(1, 2, "师父", "徒弟", SandboxRelation.TWO_WAY));

        // 旧版 App 在角色 1 上把 2 删掉了，只剩 2→1
        characters.get(1L).setRelationCharacterIds("");
        service.syncFromLegacy(USER, 1L);

        SandboxRelation r = pair(1, 2);
        assertEquals(SandboxRelation.ONE_WAY, r.getDirection());
        assertEquals(2L, r.getFromCharacterId());
        assertEquals("徒弟", r.getLabel());
    }

    @Test
    void characterDeleted_removesRelationsAndCleansOtherLegacyLists() {
        character(1, "", null);
        character(2, "", null);
        character(3, "", null);
        service.createRelation(USER, request(1, 2, "朋友", null, SandboxRelation.TWO_WAY));
        service.createRelation(USER, request(3, 2, "同事", null, null));

        characters.remove(2L);
        service.onCharacterDeleted(USER, 2L);

        assertTrue(relations.isEmpty());
        assertEquals("", characters.get(1L).getRelationCharacterIds());
        assertEquals("", characters.get(3L).getRelationCharacterIds());
    }

    @Test
    void parseIds_toleratesBracketsQuotesAndDuplicates() {
        assertEquals(java.util.Arrays.asList(3L, 1L), SandboxRelationService.parseIds("[\"3\", 1, 3, x]"));
        assertTrue(SandboxRelationService.parseIds(null).isEmpty());
        assertTrue(SandboxRelationService.parseIds("  ").isEmpty());
    }

    // ---------------------------------------------------------------- 手填的关系文字

    private static String sheet(String... nameAndRelation) {
        StringBuilder sb = new StringBuilder("{\"traits\":\"沉稳\",\"relationships\":[");
        for (int i = 0; i + 1 < nameAndRelation.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append("{\"name\":\"").append(nameAndRelation[i]).append("\",\"relation\":\"").append(nameAndRelation[i + 1]).append("\"}");
        }
        return sb.append("]}").toString();
    }

    @Test
    void update_renamesTheTypedTextThatWasThisRelation_andLeavesOtherTypedTextAlone() {
        character(1, "", null).setContent(sheet("角色2", "childhood friend", "角色2", "我另外写的一句", "角色3", "childhood friend"));
        character(2, "", null);
        character(3, "", null);
        SandboxRelation r = service.createRelation(USER, request(1, 2, "childhood friend", null, SandboxRelation.ONE_WAY));

        SandboxRelationRequest colorOnly = new SandboxRelationRequest();
        colorOnly.setColor("#112233");
        service.updateRelation(USER, r.getId(), colorOnly);
        assertEquals(sheet("角色2", "childhood friend", "角色2", "我另外写的一句", "角色3", "childhood friend"), characters.get(1L).getContent(), "关系名没变时不动角色卡");

        service.updateRelation(USER, r.getId(), request(1, 2, "friend GRAPHEDIT", null, null));
        assertEquals(sheet("角色2", "friend GRAPHEDIT", "角色2", "我另外写的一句", "角色3", "childhood friend"), characters.get(1L).getContent());
    }

    @Test
    void update_twoWayReverseLabelFollowsOnTheOtherCharactersSheet() {
        character(1, "", null).setContent(sheet("角色2", "师父"));
        character(2, "", null).setContent(sheet("角色1", "徒弟"));
        SandboxRelation r = service.createRelation(USER, request(1, 2, "师父", "徒弟", SandboxRelation.TWO_WAY));
        service.updateRelation(USER, r.getId(), request(1, 2, "恩师", "爱徒", SandboxRelation.TWO_WAY));
        assertEquals(sheet("角色2", "恩师"), characters.get(1L).getContent());
        assertEquals(sheet("角色1", "爱徒"), characters.get(2L).getContent());
    }

    @Test
    void typedText_isNotTouchedWhenTheNameIsAmbiguous() {
        character(1, "", null).setContent(sheet("同名", "朋友"));
        character(2, "", null).setName("同名");
        character(3, "", null).setName("同名");
        SandboxRelation r = service.createRelation(USER, request(1, 2, "朋友", null, SandboxRelation.ONE_WAY));
        service.updateRelation(USER, r.getId(), request(1, 2, "对手", null, null));
        service.deleteRelation(USER, r.getId());
        assertEquals(sheet("同名", "朋友"), characters.get(1L).getContent(), "同名角色不止一个：不替用户认人");
    }

    @Test
    void delete_removesItsTypedText_isRemembered_andForgottenWhenTheUserLinksAgain() {
        character(1, "", null).setContent(sheet("角色2", "childhood friend", "角色2", "我另外写的一句"));
        character(2, "", null);
        SandboxRelation r = service.createRelation(USER, request(1, 2, "childhood friend", null, SandboxRelation.ONE_WAY));
        assertFalse(service.isDismissed(USER, 1L, 2L));

        service.deleteRelation(USER, r.getId());
        assertEquals(sheet("角色2", "我另外写的一句"), characters.get(1L).getContent());
        assertTrue(service.isDismissed(USER, 1L, 2L));
        assertTrue(service.isDismissed(USER, 2L, 1L), "不分方向");
        assertFalse(service.isDismissed(USER, 1L, 3L));

        service.createRelation(USER, request(2, 1, "重新认识", null, SandboxRelation.ONE_WAY));
        assertFalse(service.isDismissed(USER, 1L, 2L));
    }

    @Test
    void legacySave_withStaleListDoesNotRecreateDismissedRelation() {
        character(1, "", null);
        character(2, "", null);
        SandboxRelation r = service.createRelation(USER, request(1, 2, "朋友", null, SandboxRelation.ONE_WAY));
        service.deleteRelation(USER, r.getId());
        assertTrue(relations.isEmpty());

        // 旧客户端带着删除前的连线列表保存角色
        characters.get(1L).setRelationCharacterIds("2");
        service.syncFromLegacy(USER, 1L);
        assertTrue(relations.isEmpty(), "删掉过的关系不能被旧字段建回来");

        // 用户明确重新连上之后可以正常建立
        service.forgetDismissedPair(USER, 1L, 2L);
        service.syncFromLegacy(USER, 1L);
        assertEquals(1, relations.size());
    }

    @Test
    void characterDeleted_forgetsItsDismissedRelations() {
        character(1, "", null);
        character(2, "", null);
        SandboxRelation r = service.createRelation(USER, request(1, 2, "朋友", null, SandboxRelation.ONE_WAY));
        service.deleteRelation(USER, r.getId());
        characters.remove(2L);
        service.onCharacterDeleted(USER, 2L);
        assertTrue(dismissed.isEmpty());
    }

    @Test
    void sheetSave_editedTypedTextRenamesTheRelationItWasInSyncWith() {
        String before = sheet("角色2", "childhood friend", "角色3", "同事");
        character(1, "", null).setContent(before);
        character(2, "", null);
        character(3, "", null);
        service.createRelation(USER, request(1, 2, "childhood friend", null, SandboxRelation.ONE_WAY));
        service.createRelation(USER, request(1, 3, "我在关系网里另起的名字", null, SandboxRelation.ONE_WAY));

        String after = sheet("角色2", "best friend", "角色3", "老同事");
        characters.get(1L).setContent(after);
        service.syncFromTyped(USER, 1L, before, after);
        assertEquals("best friend", pair(1, 2).getLabel());
        assertEquals("我在关系网里另起的名字", pair(1, 3).getLabel(), "原来就和手填文字不同的关系名不动");

        // 手填文字被删掉（例如点了「关联角色」之后）：关系保留
        String removed = sheet();
        characters.get(1L).setContent(removed);
        service.syncFromTyped(USER, 1L, after, removed);
        assertEquals("best friend", pair(1, 2).getLabel());
        assertEquals(2, relations.size());
    }

    // ---------------------------------------------------------------- helpers

    private SandboxCharacter character(long id, String relationIds, String label) {
        SandboxCharacter c = new SandboxCharacter();
        c.setId(id);
        c.setUserId(USER);
        c.setName("角色" + id);
        c.setRelationCharacterIds(relationIds);
        c.setRelationLabel(label);
        characters.put(id, c);
        return c;
    }

    private SandboxRelationRequest request(long from, long to, String label, String reverse, String direction) {
        SandboxRelationRequest r = new SandboxRelationRequest();
        r.setFromCharacterId(from);
        r.setToCharacterId(to);
        r.setLabel(label);
        r.setReverseLabel(reverse);
        r.setDirection(direction);
        return r;
    }

    private SandboxRelation pair(long a, long b) {
        List<SandboxRelation> list = new ArrayList<SandboxRelation>(relations.values());
        return SandboxRelationService.findPair(list, a, b);
    }

    private SandboxCharacter copy(SandboxCharacter c) {
        SandboxCharacter x = new SandboxCharacter();
        x.setId(c.getId()); x.setUserId(c.getUserId()); x.setName(c.getName());
        x.setRelationCharacterIds(c.getRelationCharacterIds()); x.setRelationLabel(c.getRelationLabel());
        x.setRelationColor(c.getRelationColor()); x.setRelationArrow(c.getRelationArrow());
        x.setContent(c.getContent()); x.setWorldviewId(c.getWorldviewId()); x.setWorldviewIds(c.getWorldviewIds());
        return x;
    }

    private SandboxRelation copy(SandboxRelation r) {
        SandboxRelation x = new SandboxRelation();
        x.setId(r.getId()); x.setUserId(r.getUserId());
        x.setFromCharacterId(r.getFromCharacterId()); x.setToCharacterId(r.getToCharacterId());
        x.setLabel(r.getLabel()); x.setReverseLabel(r.getReverseLabel()); x.setDirection(r.getDirection());
        x.setColor(r.getColor()); x.setArrow(r.getArrow()); x.setNote(r.getNote()); x.setSource(r.getSource());
        return x;
    }
}
