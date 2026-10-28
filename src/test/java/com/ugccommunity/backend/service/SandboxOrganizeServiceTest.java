package com.ugccommunity.backend.service;

import com.ugccommunity.backend.common.exception.BusinessException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SandboxOrganizeServiceTest {
    private static final long USER = 7L;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TEXT = "潮历三年，雾港建城。老陈是黎潮的师父。黎潮与苏晚互为挚友。雾鹿栖息在北境森林。"
            + "后来港口发生过一场大火。雾港纪元与星桥诸岛隔着雾海相望。";

    @Mock private DeepSeekClient ai;
    @Mock private SandboxWorldviewMapper worldviewMapper;
    @Mock private SandboxCharacterMapper characterMapper;
    @Mock private SandboxRecordService recordService;
    @Mock private SandboxRelationService relationService;
    @Mock private SandboxWorldRelationService worldRelationService;
    @Mock private SandboxTimelineService timelineService;
    @Mock private SandboxOrganizeLogMapper logMapper;
    @Mock private PlatformTransactionManager transactionManager;
    private SandboxOrganizeService service;

    private SandboxWorldview world;
    private final List<SandboxCharacter> characters = new ArrayList<SandboxCharacter>();
    private final List<SandboxRecord> records = new ArrayList<SandboxRecord>();
    private final List<SandboxRelation> relations = new ArrayList<SandboxRelation>();
    private final List<SandboxWorldRelation> worldRelations = new ArrayList<SandboxWorldRelation>();
    private final List<String[]> mergedEvents = new ArrayList<String[]>();
    private final List<String[]> mergedLinks = new ArrayList<String[]>();
    private final List<SandboxOrganizeLog> logs = new ArrayList<SandboxOrganizeLog>();
    private String aiOutput;
    /** true 时模拟事件都已经在时间线上：这次一个都没新增 */
    private boolean eventsAlreadyOnTimeline;

    @BeforeEach
    void setUp() {
        service = new SandboxOrganizeService(ai, worldviewMapper, characterMapper, recordService, relationService, worldRelationService, timelineService, logMapper, transactionManager);
        world = world(1, "雾港纪元", TEXT);
        final SandboxWorldview other = world(2, "星桥诸岛", "");
        lenient().when(worldviewMapper.selectById(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            return id == 1 ? world : id == 2 ? other : null;
        });
        lenient().when(worldviewMapper.selectList(any())).thenReturn(Arrays.asList(world, other));
        characters.add(character(69, "黎潮"));
        characters.add(character(70, "苏晚"));
        characters.add(character(71, "老陈"));
        lenient().when(characterMapper.selectList(any())).thenAnswer(inv -> new ArrayList<SandboxCharacter>(characters));
        lenient().when(ai.isConfigured()).thenReturn(true);
        lenient().when(ai.generateJson(anyString(), anyString(), anyInt())).thenAnswer(inv -> aiOutput);
        lenient().when(recordService.listRecords(eq(USER), anyLong(), any())).thenAnswer(inv -> new ArrayList<SandboxRecord>(records));
        lenient().when(recordService.createRecord(eq(USER), any(SandboxRecordRequest.class))).thenAnswer(inv -> {
            SandboxRecordRequest r = inv.getArgument(1);
            SandboxRecord x = new SandboxRecord();
            x.setId(100L + records.size()); x.setCategory(r.getCategory()); x.setName(r.getName()); x.setDescription(r.getDescription()); x.setLegacyKey(r.getLegacyKey());
            records.add(x);
            return x;
        });
        lenient().when(relationService.listRelations(USER)).thenAnswer(inv -> new ArrayList<SandboxRelation>(relations));
        lenient().when(relationService.createRelation(eq(USER), any(SandboxRelationRequest.class))).thenAnswer(inv -> {
            SandboxRelationRequest r = inv.getArgument(1);
            SandboxRelation x = new SandboxRelation();
            x.setId(200L + relations.size()); x.setFromCharacterId(r.getFromCharacterId()); x.setToCharacterId(r.getToCharacterId());
            x.setLabel(r.getLabel()); x.setReverseLabel(r.getReverseLabel()); x.setDirection(r.getDirection());
            relations.add(x);
            return x;
        });
        lenient().when(worldRelationService.listRelations(USER)).thenAnswer(inv -> new ArrayList<SandboxWorldRelation>(worldRelations));
        lenient().when(worldRelationService.createRelation(eq(USER), any(SandboxWorldRelationRequest.class))).thenAnswer(inv -> {
            SandboxWorldRelationRequest r = inv.getArgument(1);
            SandboxWorldRelation x = new SandboxWorldRelation();
            x.setFromWorldviewId(r.getFromWorldviewId()); x.setToWorldviewId(r.getToWorldviewId()); x.setLabel(r.getLabel()); x.setDirection(r.getDirection());
            worldRelations.add(x);
            return x;
        });
        lenient().when(timelineService.worldTimeline(USER, 1L)).thenReturn(new SandboxTimeline());
        lenient().when(timelineService.characterTimeline(eq(USER), anyLong())).thenReturn(new SandboxTimeline());
        lenient().when(characterMapper.selectById(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            for (SandboxCharacter c : characters) if (c.getId() == id) return c;
            return null;
        });
        lenient().when(timelineService.mergeOrganized(eq(USER), any(SandboxTimeline.class), anyString(), anyList(), anyList(), any(boolean[].class))).thenAnswer(inv -> {
            List<String[]> events = inv.getArgument(3);
            List<String[]> links = inv.getArgument(4);
            boolean[] added = inv.getArgument(5);
            if (eventsAlreadyOnTimeline) return new int[]{0, events.size(), 0, links.size()};
            mergedEvents.addAll(events);
            mergedLinks.addAll(links);
            Arrays.fill(added, true);
            return new int[]{events.size(), 0, links.size(), 0};
        });
        lenient().when(logMapper.insert(any(SandboxOrganizeLog.class))).thenAnswer(inv -> { logs.add(inv.getArgument(0)); return 1; });
    }

    @Test
    void organize_writesGroundedEventsEntitiesAndRelations_keepingFictionalDatesAndDirections() throws Exception {
        aiOutput = "{\"events\":[{\"time\":\"潮历三年\",\"text\":\"雾港建城\",\"quote\":\"潮历三年，雾港建城\"},"
                + "{\"time\":\"\",\"text\":\"港口发生大火\",\"quote\":\"后来港口发生过一场大火\"}],"
                + "\"entities\":[{\"category\":\"creature\",\"name\":\"雾鹿\",\"description\":\"栖息在北境森林\",\"quote\":\"雾鹿栖息在北境森林\"}],"
                + "\"characterRelations\":[{\"from\":\"老陈\",\"to\":\"黎潮\",\"label\":\"师父\",\"mutual\":false,\"quote\":\"老陈是黎潮的师父\"},"
                + "{\"from\":\"黎潮\",\"to\":\"苏晚\",\"label\":\"挚友\",\"mutual\":true,\"reverseLabel\":\"挚友\",\"quote\":\"黎潮与苏晚互为挚友\"}],"
                + "\"worldRelations\":[{\"from\":\"雾港纪元\",\"to\":\"星桥诸岛\",\"label\":\"隔海相望\",\"mutual\":false,\"quote\":\"雾港纪元与星桥诸岛隔着雾海相望\"}]}";
        Map<String, Object> result = service.organize(USER, "world", 1L);
        JsonNode report = JSON.readTree((String) result.get("report"));

        assertEquals(2, mergedEvents.size());
        assertEquals("潮历三年", mergedEvents.get(0)[0], "虚构历法照抄");
        assertEquals("", mergedEvents.get(1)[0], "原文没写时间就留空，不推算");
        assertEquals(2, report.get("eventsAdded").asInt());

        assertEquals(1, records.size());
        assertEquals("creature", records.get(0).getCategory());
        assertTrue(records.get(0).getLegacyKey().startsWith("org:"));

        assertEquals(2, relations.size());
        assertEquals(71L, relations.get(0).getFromCharacterId(), "方向：老陈 → 黎潮");
        assertEquals(69L, relations.get(0).getToCharacterId());
        assertEquals(SandboxRelation.ONE_WAY, relations.get(0).getDirection());
        assertEquals(SandboxRelation.TWO_WAY, relations.get(1).getDirection());
        assertEquals("挚友", relations.get(1).getReverseLabel());

        assertEquals(1, worldRelations.size());
        assertEquals(1L, worldRelations.get(0).getFromWorldviewId());

        // 没写时间的事件已经写入：作为提醒放在 notes 里，不算「没有处理的内容」
        assertEquals(1, report.get("notes").size());
        assertEquals("港口发生大火", report.get("notes").get(0).get("text").asText());
        assertTrue(report.get("notes").get(0).get("reason").asText().contains("原文没有写时间"));
        for (JsonNode u : report.get("unresolved")) assertFalse(u.get("reason").asText().contains("时间已留空"));
        assertEquals(1, logs.size());
    }

    @Test
    void organize_doesNotGuessUnknownOrAmbiguousNames_orAcceptUngroundedItems() throws Exception {
        characters.add(character(72, "苏晚"));
        aiOutput = "{\"events\":[{\"time\":\"潮历九年\",\"text\":\"雾港建城\",\"quote\":\"潮历三年，雾港建城\"},"
                + "{\"time\":\"\",\"text\":\"黎潮远航\",\"quote\":\"黎潮扬帆远航\"}],"
                + "\"entities\":[{\"category\":\"dragon\",\"name\":\"雾鹿\",\"quote\":\"雾鹿栖息在北境森林\"},"
                + "{\"category\":\"plant\",\"name\":\"月光菇\",\"quote\":\"月光菇长在灯塔下\"}],"
                + "\"characterRelations\":[{\"from\":\"黎潮\",\"to\":\"苏晚\",\"label\":\"挚友\",\"quote\":\"黎潮与苏晚互为挚友\"},"
                + "{\"from\":\"老陈\",\"to\":\"无名船长\",\"label\":\"旧识\",\"quote\":\"老陈是黎潮的师父\"}],"
                + "\"worldRelations\":[{\"from\":\"雾港纪元\",\"to\":\"不存在的世界\",\"label\":\"相邻\",\"quote\":\"雾港纪元与星桥诸岛隔着雾海相望\"}]}";
        JsonNode report = JSON.readTree((String) service.organize(USER, "world", 1L).get("report"));

        assertEquals(1, mergedEvents.size(), "找不到依据的事件不写入");
        assertEquals("", mergedEvents.get(0)[0], "不在原文里的时间不采用");
        assertTrue(records.isEmpty(), "类别无法识别或没有依据的实体不写入");
        assertTrue(relations.isEmpty(), "同名多个和找不到的角色都不建立关系");
        assertTrue(worldRelations.isEmpty());
        List<String> reasons = new ArrayList<String>();
        for (JsonNode u : report.get("unresolved")) reasons.add(u.get("reason").asText());
        assertTrue(reasons.stream().anyMatch(r -> r.contains("有多个名为「苏晚」的角色")));
        assertTrue(reasons.stream().anyMatch(r -> r.contains("找不到名为「无名船长」的角色")));
        assertTrue(reasons.stream().anyMatch(r -> r.contains("找不到名为「不存在的世界」的世界观")));
        assertTrue(reasons.stream().anyMatch(r -> r.contains("类别无法识别")));
        assertTrue(reasons.stream().anyMatch(r -> r.contains("原文中找不到依据")));
        assertEquals(6, reasons.size(), "六条都没有写入，逐条留在报告里");
        assertEquals(1, report.get("notes").size(), "时间不在原文里的事件写入了、时间留空，作为提醒");
        assertTrue(report.get("notes").get(0).get("reason").asText().contains("不在原文中"));
    }

    @Test
    void eventAlreadyOnTheTimelineWithoutTime_isNeitherUnresolvedNorNoted() throws Exception {
        eventsAlreadyOnTimeline = true;
        aiOutput = "{\"events\":[{\"time\":\"\",\"text\":\"港口发生大火\",\"quote\":\"后来港口发生过一场大火\"}]}";
        JsonNode report = JSON.readTree((String) service.organize(USER, "world", 1L).get("report"));

        assertEquals(0, report.get("eventsAdded").asInt());
        assertEquals(1, report.get("eventsExisting").asInt());
        assertEquals(0, report.get("unresolved").size(), "已有（未改动）的事件不算没有处理");
        assertEquals(0, report.get("notes").size(), "这次没有改动它，不再提醒时间留空");
    }

    @Test
    void savedReportsFromTheOldVersion_noLongerListSavedEventsAsUnresolved() throws Exception {
        String old = "{\"unresolved\":[{\"kind\":\"event\",\"text\":\"三位魔女守护世人\",\"reason\":\"原文没有写时间，时间已留空\"},"
                + "{\"kind\":\"relation\",\"text\":\"甲 → 乙\",\"reason\":\"找不到名为「乙」的角色，没有写入\"}],"
                + "\"eventsAdded\":0,\"eventsExisting\":1,\"recordsAdded\":0,\"recordsExisting\":1}";
        JsonNode fixed = JSON.readTree(SandboxOrganizeService.normalizeReport(old));
        assertEquals(1, fixed.get("unresolved").size(), "真正没写入的条目保留");
        assertEquals("relation", fixed.get("unresolved").get(0).get("kind").asText());
        assertEquals(0, fixed.get("notes").size(), "一个事件都没新增：时间留空的都是已有事件，不再列出");

        JsonNode withAdded = JSON.readTree(SandboxOrganizeService.normalizeReport(old.replace("\"eventsAdded\":0", "\"eventsAdded\":1")));
        assertEquals(1, withAdded.get("unresolved").size());
        assertEquals(1, withAdded.get("notes").size(), "有新增事件时移到提醒里，不算没有处理");

        String current = "{\"unresolved\":[],\"notes\":[],\"eventsAdded\":0}";
        assertEquals(current, SandboxOrganizeService.normalizeReport(current));
        assertEquals("not json", SandboxOrganizeService.normalizeReport("not json"));
    }

    @Test
    void organize_extractsOnlyGroundedEventLinks_betweenEventsThatWereWritten() throws Exception {
        world.setContent("潮历三年，雾港建城。后来港口发生过一场大火，这场大火导致引潮会成立。");
        aiOutput = "{\"events\":[{\"time\":\"潮历三年\",\"text\":\"雾港建城\",\"quote\":\"潮历三年，雾港建城\"},"
                + "{\"time\":\"\",\"text\":\"凭空出现的事件\",\"quote\":\"原文里没有这句\"},"
                + "{\"time\":\"\",\"text\":\"港口大火\",\"quote\":\"后来港口发生过一场大火\"},"
                + "{\"time\":\"\",\"text\":\"引潮会成立\",\"quote\":\"引潮会成立\"}],"
                + "\"eventLinks\":[{\"from\":2,\"to\":3,\"note\":\"导致\",\"quote\":\"这场大火导致引潮会成立\"},"
                + "{\"from\":0,\"to\":2,\"note\":\"先后\",\"quote\":\"建城之后才有大火\"},"
                + "{\"from\":1,\"to\":3,\"note\":\"无关\",\"quote\":\"引潮会成立\"},"
                + "{\"from\":3,\"to\":9,\"note\":\"越界\",\"quote\":\"引潮会成立\"}]}";
        JsonNode report = JSON.readTree((String) service.organize(USER, "world", 1L).get("report"));

        assertEquals(3, mergedEvents.size());
        assertEquals(1, mergedLinks.size(), "只有原文写明、两端事件都写入了的关联才建立");
        // 序号按写入的事件重新对应：第 2、3 条在写入的列表里是第 1、2 条
        assertEquals("港口大火", mergedEvents.get(Integer.parseInt(mergedLinks.get(0)[0]))[1]);
        assertEquals("引潮会成立", mergedEvents.get(Integer.parseInt(mergedLinks.get(0)[1]))[1]);
        assertEquals("导致", mergedLinks.get(0)[2]);
        assertEquals(1, report.get("eventLinksAdded").asInt());
        int skipped = 0;
        for (JsonNode u : report.get("unresolved")) if ("eventLink".equals(u.get("kind").asText())) skipped++;
        assertEquals(3, skipped, "没有依据、端点没写入、序号越界的关联逐条留在报告里");
    }

    @Test
    void organizeCharacter_readsTypedRelationships_andLinksThemByExactNameWithTheUsersOwnWords() throws Exception {
        SandboxCharacter lichao = characters.get(0);
        lichao.setContent("{\"name\":\"黎潮\",\"relationships\":[{\"name\":\"苏晚\",\"relation\":\"青梅竹马\"},"
                + "{\"name\":\"无名船长\",\"relation\":\"救命恩人\"},{\"name\":\"\",\"relation\":\"没写人物\"}]}");
        String source = service.sourceText(USER, "character", 69L);
        assertTrue(source.contains("人物关系：黎潮 → 苏晚：青梅竹马"), source);
        assertTrue(source.contains("人物关系：黎潮 → 无名船长：救命恩人"), source);

        // AI 把同一条关系反着又说了一遍：以用户手填的为准，不重复建立
        aiOutput = "{\"events\":[],\"entities\":[],\"characterRelations\":[{\"from\":\"苏晚\",\"to\":\"黎潮\",\"label\":\"朋友\",\"quote\":\"黎潮 → 苏晚：青梅竹马\"}],\"worldRelations\":[]}";
        JsonNode report = JSON.readTree((String) service.organize(USER, "character", 69L).get("report"));

        assertEquals(1, relations.size());
        assertEquals(69L, relations.get(0).getFromCharacterId());
        assertEquals(70L, relations.get(0).getToCharacterId());
        assertEquals("青梅竹马", relations.get(0).getLabel());
        assertEquals(SandboxRelation.ONE_WAY, relations.get(0).getDirection());
        assertEquals(1, report.get("relationsAdded").asInt());
        assertEquals(1, report.get("relationsExisting").asInt());
        boolean reported = false;
        for (JsonNode u : report.get("unresolved")) if (u.get("reason").asText().contains("找不到名为「无名船长」的角色")) reported = true;
        assertTrue(reported, "名字对不上的手填关系不建立，留在报告里");

        // 再整理一次：不重复建立，用户改过的关系名称不被覆盖
        relations.get(0).setLabel("恋人");
        JsonNode again = JSON.readTree((String) service.organize(USER, "character", 69L).get("report"));
        assertEquals(1, relations.size());
        assertEquals("恋人", relations.get(0).getLabel());
        assertEquals(0, again.get("relationsAdded").asInt());
    }

    @Test
    void worldRelationTheUserDeleted_isNotRebuiltByOrganize_andIsReported() throws Exception {
        aiOutput = "{\"events\":[],\"entities\":[],\"characterRelations\":[],"
                + "\"worldRelations\":[{\"from\":\"雾港纪元\",\"to\":\"星桥诸岛\",\"label\":\"隔海相望\",\"mutual\":false,\"quote\":\"雾港纪元与星桥诸岛隔着雾海相望\"}]}";
        service.organize(USER, "world", 1L);
        assertEquals(1, worldRelations.size());
        // 用户在多世界总览里删掉了这条关系；设定原文没有变
        worldRelations.clear();
        lenient().when(worldRelationService.isDismissed(anyLong(), anyLong(), anyLong())).thenReturn(true);

        JsonNode report = JSON.readTree((String) service.organize(USER, "world", 1L).get("report"));
        assertEquals(0, worldRelations.size());
        assertEquals(0, report.get("worldRelationsAdded").asInt());
        boolean reported = false;
        for (JsonNode u : report.get("unresolved")) if ("worldRelation".equals(u.get("kind").asText()) && u.get("reason").asText().contains("删除过")) reported = true;
        assertTrue(reported);
    }

    @Test
    void relationTheUserDeleted_isNotRebuiltByOrganize_andIsReported() throws Exception {
        aiOutput = "{\"events\":[],\"entities\":[],\"characterRelations\":[{\"from\":\"老陈\",\"to\":\"黎潮\",\"label\":\"师父\",\"quote\":\"老陈是黎潮的师父\"}],\"worldRelations\":[]}";
        service.organize(USER, "world", 1L);
        assertEquals(1, relations.size());
        // 用户在关系网里删掉了这条关系
        relations.clear();
        lenient().when(relationService.isDismissed(anyLong(), anyLong(), anyLong())).thenReturn(true);

        JsonNode report = JSON.readTree((String) service.organize(USER, "world", 1L).get("report"));
        assertEquals(0, relations.size());
        assertEquals(0, report.get("relationsAdded").asInt());
        boolean reported = false;
        for (JsonNode u : report.get("unresolved")) if (u.get("reason").asText().contains("删除过")) reported = true;
        assertTrue(reported);
    }

    @Test
    void repeatedOrganize_reusesExistingRecordsAndRelations_withoutOverwritingManualEdits() throws Exception {
        aiOutput = "{\"events\":[],\"entities\":[{\"category\":\"creature\",\"name\":\"雾鹿\",\"description\":\"AI 的描述\",\"quote\":\"雾鹿栖息在北境森林\"}],"
                + "\"characterRelations\":[{\"from\":\"老陈\",\"to\":\"黎潮\",\"label\":\"师父\",\"quote\":\"老陈是黎潮的师父\"}],\"worldRelations\":[]}";
        service.organize(USER, "world", 1L);
        // 用户手动改了记录描述和关系名称
        records.get(0).setDescription("我自己写的描述");
        relations.get(0).setLabel("恩师");
        JsonNode second = JSON.readTree((String) service.organize(USER, "world", 1L).get("report"));
        assertEquals(1, records.size());
        assertEquals("我自己写的描述", records.get(0).getDescription());
        assertEquals(1, relations.size());
        assertEquals("恩师", relations.get(0).getLabel());
        assertEquals(0, second.get("recordsAdded").asInt());
        assertEquals(1, second.get("recordsExisting").asInt());
        assertEquals(0, second.get("relationsAdded").asInt());
        assertEquals(1, second.get("relationsExisting").asInt());
        // 反方向已有关系也算已有
        SandboxRelation reverse = relations.get(0);
        long from = reverse.getFromCharacterId();
        reverse.setFromCharacterId(reverse.getToCharacterId());
        reverse.setToCharacterId(from);
        service.organize(USER, "world", 1L);
        assertEquals(1, relations.size());
    }

    @Test
    void invalidAiOutput_unavailableAi_andMissingKey_leaveEverythingUntouched() {
        for (String bad : new String[]{"抱歉，我无法处理", "[1,2,3]", "{}", "{\"events\":\"none\"}", "{\"events\":[1,2]}"}) {
            aiOutput = bad;
            assertThrows(BusinessException.class, () -> service.organize(USER, "world", 1L), bad);
        }
        lenient().when(ai.generateJson(anyString(), anyString(), anyInt())).thenThrow(new DeepSeekClient.DeepSeekException("down"));
        assertThrows(BusinessException.class, () -> service.organize(USER, "world", 1L));
        lenient().when(ai.isConfigured()).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.organize(USER, "world", 1L));

        assertTrue(mergedEvents.isEmpty());
        assertTrue(records.isEmpty());
        assertTrue(relations.isEmpty());
        assertTrue(worldRelations.isEmpty());
        assertTrue(logs.isEmpty());
        verify(recordService, never()).createRecord(anyLong(), any());
        verify(relationService, never()).createRelation(anyLong(), any());
    }

    @Test
    void textEditedWhileTheAiWasWorking_discardsTheLateResult() {
        aiOutput = "{\"events\":[{\"time\":\"潮历三年\",\"text\":\"雾港建城\",\"quote\":\"潮历三年，雾港建城\"}],\"entities\":[],\"characterRelations\":[],\"worldRelations\":[]}";
        lenient().when(ai.generateJson(anyString(), anyString(), anyInt())).thenAnswer(inv -> {
            world.setContent("用户在整理期间改写了设定。");
            return aiOutput;
        });
        assertThrows(BusinessException.class, () -> service.organize(USER, "world", 1L));
        assertTrue(mergedEvents.isEmpty());
        assertTrue(logs.isEmpty());
    }

    @Test
    void organize_rejectsForeignMissingAndEmptyTargets_withoutCallingTheAi() {
        world(3, "别人的世界", "内容").setUserId(8L);
        SandboxWorldview foreign = new SandboxWorldview();
        foreign.setId(3L); foreign.setUserId(8L); foreign.setName("别人的世界"); foreign.setContent("内容");
        lenient().when(worldviewMapper.selectById(3L)).thenReturn(foreign);
        assertThrows(BusinessException.class, () -> service.organize(USER, "world", 3L));
        assertThrows(BusinessException.class, () -> service.organize(USER, "world", 404L));
        assertThrows(BusinessException.class, () -> service.organize(USER, "planet", 1L));
        assertThrows(BusinessException.class, () -> service.organize(null, "world", 1L));
        world.setName(""); world.setDescription(""); world.setContent(" ");
        assertThrows(BusinessException.class, () -> service.organize(USER, "world", 1L));
        verify(ai, never()).generateJson(anyString(), anyString(), anyInt());
    }

    @Test
    void grounded_ignoresWhitespaceButRequiresRealOverlap() {
        assertTrue(SandboxOrganizeService.grounded("潮历三年，\n雾港 建城。", "潮历三年，雾港建城"));
        assertFalse(SandboxOrganizeService.grounded(TEXT, "潮历九年"));
        assertFalse(SandboxOrganizeService.grounded(TEXT, "  "));
        assertNull(SandboxOrganizeService.parseExtraction("not json"));
        assertNotNull(SandboxOrganizeService.parseExtraction("{\"events\":[]}"));
    }

    private SandboxWorldview world(long id, String name, String content) {
        SandboxWorldview w = new SandboxWorldview();
        w.setId(id); w.setUserId(USER); w.setName(name); w.setContent(content);
        return w;
    }

    private SandboxCharacter character(long id, String name) {
        SandboxCharacter c = new SandboxCharacter();
        c.setId(id); c.setUserId(USER); c.setWorldviewId(1L); c.setName(name);
        return c;
    }
}
