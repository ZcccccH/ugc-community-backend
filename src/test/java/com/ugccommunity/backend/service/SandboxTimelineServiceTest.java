package com.ugccommunity.backend.service;

import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.dto.request.SandboxTimelineRequest;
import com.ugccommunity.backend.entity.SandboxCharacter;
import com.ugccommunity.backend.entity.SandboxTimeline;
import com.ugccommunity.backend.entity.SandboxWorldview;
import com.ugccommunity.backend.mapper.SandboxCharacterMapper;
import com.ugccommunity.backend.mapper.SandboxTimelineMapper;
import com.ugccommunity.backend.mapper.SandboxWorldviewMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SandboxTimelineServiceTest {
    private static final long USER = 7L;
    private static final long OTHER_USER = 8L;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SHEET = "{\"name\":\"黎潮\",\"profession\":\"引潮人\","
            + "\"experiences\":[{\"time\":\"潮历三年·春\",\"content\":\"被老陈收留\"},{\"time\":\"\",\"content\":\"成为引潮人\"},{\"time\":\"\",\"content\":\"\"}],"
            + "\"extraTimelineTitle\":\"梦境\",\"extraTimeline\":[{\"time\":\"某夜\",\"content\":\"梦见雾海\"}]}";

    @Mock private SandboxTimelineMapper timelineMapper;
    @Mock private SandboxWorldviewMapper worldviewMapper;
    @Mock private SandboxCharacterMapper characterMapper;
    private SandboxTimelineService service;

    private final Map<Long, SandboxTimeline> timelines = new LinkedHashMap<Long, SandboxTimeline>();
    private final Map<Long, SandboxCharacter> characters = new LinkedHashMap<Long, SandboxCharacter>();
    private final Map<Long, SandboxWorldview> worlds = new LinkedHashMap<Long, SandboxWorldview>();
    private long seq = 100;
    /** bySlot 的查询条件不解析：测试在调用前指定要找的 slot */
    private String slotFilter = "";
    private boolean concurrentEdit;
    /** 记录 mirror 写回角色卡的内容 */
    private String mirroredContent;

    @BeforeEach
    void setUp() {
        service = new SandboxTimelineService(timelineMapper, worldviewMapper, characterMapper);
        lenient().when(worldviewMapper.selectById(anyLong())).thenAnswer(inv -> worlds.get((Long) inv.getArgument(0)));
        lenient().when(characterMapper.selectById(anyLong())).thenAnswer(inv -> characters.get((Long) inv.getArgument(0)));
        lenient().when(characterMapper.update(isNull(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?> w = inv.getArgument(1);
            // set 片段形如 content=#{ew.paramNameValuePairs.MPGENVAL3}：直接取参数表里最长的字符串就是新的角色卡内容
            String longest = "";
            for (Object v : w.getParamNameValuePairs().values()) if (v instanceof String && ((String) v).length() > longest.length()) longest = (String) v;
            mirroredContent = longest;
            return 1;
        });
        lenient().when(timelineMapper.selectById(anyLong())).thenAnswer(inv -> timelines.get((Long) inv.getArgument(0)));
        lenient().when(timelineMapper.selectList(any())).thenAnswer(inv -> {
            List<SandboxTimeline> out = new ArrayList<SandboxTimeline>();
            for (SandboxTimeline t : timelines.values()) if (t.getUserId() == USER && t.getSlot().equals(slotFilter)) out.add(t);
            return out;
        });
        lenient().when(timelineMapper.insert(any(SandboxTimeline.class))).thenAnswer(inv -> {
            SandboxTimeline t = inv.getArgument(0);
            t.setId(++seq);
            timelines.put(t.getId(), t);
            return 1;
        });
        lenient().when(timelineMapper.update(any(SandboxTimeline.class), any())).thenAnswer(inv -> {
            if (concurrentEdit) return 0;
            SandboxTimeline t = inv.getArgument(0);
            timelines.put(t.getId(), t);
            return 1;
        });
        SandboxWorldview w = new SandboxWorldview(); w.setId(1L); w.setUserId(USER); w.setName("雾港纪元"); worlds.put(1L, w);
        SandboxWorldview foreignWorld = new SandboxWorldview(); foreignWorld.setId(9L); foreignWorld.setUserId(OTHER_USER); worlds.put(9L, foreignWorld);
        character(69, USER, SHEET);
        character(70, USER, null);
        character(99, OTHER_USER, SHEET);
    }

    @Test
    void worldTimeline_isCreatedEmptyOnce() throws Exception {
        slotFilter = SandboxTimelineService.worldSlot(1L);
        SandboxTimeline a = service.worldTimeline(USER, 1L);
        SandboxTimeline b = service.worldTimeline(USER, 1L);
        assertEquals(a.getId(), b.getId());
        assertEquals(1, timelines.size());
        JsonNode doc = JSON.readTree(a.getData());
        assertEquals(0, doc.get("nodes").size());
        assertEquals(0, doc.get("events").size());
        assertThrows(BusinessException.class, () -> service.worldTimeline(USER, 9L));
        assertThrows(BusinessException.class, () -> service.worldTimeline(USER, 404L));
    }

    @Test
    void characterTimeline_importsSheetExperiencesAsWritten_withoutInventingAnything() throws Exception {
        slotFilter = SandboxTimelineService.characterSlot(69L);
        SandboxTimeline t = service.characterTimeline(USER, 69L);
        JsonNode doc = JSON.readTree(t.getData());
        assertEquals(2, doc.get("nodes").size(), "空白的一条不导入");
        assertEquals("潮历三年·春", doc.get("nodes").get(0).get("label").asText());
        assertEquals("", doc.get("nodes").get(1).get("label").asText(), "没写时间的保持为空");
        assertEquals("被老陈收留", doc.get("events").get(0).get("text").asText());
        assertEquals(doc.get("nodes").get(1).get("id").asText(), doc.get("events").get(1).get("nodeId").asText());
        assertEquals(0, doc.get("links").size(), "不凭空建立连接");
        assertEquals(1, doc.get("shadows").size());
        assertEquals("某夜", doc.get("shadows").get(0).get("title").asText());
        assertEquals("梦见雾海", doc.get("shadows").get(0).get("text").asText());
        assertEquals("", doc.get("shadows").get(0).get("targetId").asText());
        // 再读一次还是同一条，不重复导入
        assertEquals(t.getId(), service.characterTimeline(USER, 69L).getId());
        assertEquals(1, timelines.size());
    }

    @Test
    void characterTimeline_forACharacterWithoutSheetIsEmpty_andForeignCharactersAreRejected() throws Exception {
        slotFilter = SandboxTimelineService.characterSlot(70L);
        assertEquals(0, JSON.readTree(service.characterTimeline(USER, 70L).getData()).get("nodes").size());
        assertThrows(BusinessException.class, () -> service.characterTimeline(USER, 99L));
        assertThrows(BusinessException.class, () -> service.characterTimeline(USER, 404L));
        assertThrows(BusinessException.class, () -> service.characterTimeline(null, 69L));
    }

    @Test
    void savingACharacterTimeline_writesEventsBackToTheSheet_keepingOtherSheetFields() throws Exception {
        slotFilter = SandboxTimelineService.characterSlot(69L);
        SandboxTimeline t = service.characterTimeline(USER, 69L);
        String edited = "{\"nodes\":[{\"id\":\"n1\",\"label\":\"潮历三年·春\"},{\"id\":\"n2\",\"label\":\"潮历七年\"},{\"id\":\"n3\",\"label\":\"潮历九年\"}],"
                + "\"events\":[{\"id\":\"e1\",\"nodeId\":\"n1\",\"title\":\"\",\"text\":\"被老陈收留\"},{\"id\":\"e2\",\"nodeId\":\"n2\",\"title\":\"出师\",\"text\":\"成为引潮人\"},{\"id\":\"e3\",\"nodeId\":\"n1\",\"text\":\"第一次看见灯塔\"}],"
                + "\"links\":[{\"id\":\"l1\",\"from\":\"e1\",\"to\":\"e2\",\"note\":\"因果\"}],"
                + "\"shadows\":[{\"id\":\"s1\",\"targetId\":\"e1\",\"text\":\"挂在事件上的注记\"},{\"id\":\"s2\",\"targetId\":\"\",\"title\":\"某夜\",\"text\":\"<large>梦见<u>雾海</u></large>\"}],\"settings\":{}}";
        SandboxTimeline saved = service.update(USER, t.getId(), request(edited, 1));
        assertEquals(2, saved.getVersion());
        JsonNode sheet = JSON.readTree(mirroredContent);
        assertEquals("引潮人", sheet.get("profession").asText());
        assertEquals("梦境", sheet.get("extraTimelineTitle").asText());
        JsonNode exp = sheet.get("experiences");
        assertEquals(4, exp.size());
        assertEquals("潮历三年·春", exp.get(0).get("time").asText());
        assertEquals("被老陈收留", exp.get(0).get("content").asText());
        assertEquals("第一次看见灯塔", exp.get(1).get("content").asText(), "同一个锚点下的事件排在一起");
        assertEquals("出师：成为引潮人", exp.get(2).get("content").asText());
        assertEquals("潮历九年", exp.get(3).get("time").asText(), "没有事件的锚点保留时间");
        assertEquals("", exp.get(3).get("content").asText());
        assertEquals(1, sheet.get("extraTimeline").size(), "只有不挂在事件上的文字算补充时间线");
        assertEquals("梦见雾海", sheet.get("extraTimeline").get(0).get("content").asText(), "格式标记只留在时间线上，角色卡里是纯文字");
        assertTrue(saved.getData().contains("<large>梦见<u>雾海</u></large>"), "时间线自己保存的文字带着格式标记");
    }

    @Test
    void plainShadowText_removesOnlyTheFormatTags() {
        assertEquals("影子文字 ce", SandboxTimelineService.plainShadowText("<large>影子<b>文字</b> <u>ce</u></large>"));
        assertEquals("1 < 2 <x>", SandboxTimelineService.plainShadowText("1 < 2 <x>"));
        assertEquals("", SandboxTimelineService.plainShadowText(null));
    }

    @Test
    void reapplyToSheet_restoresTimelineContentAfterAStaleSheetSave() throws Exception {
        slotFilter = SandboxTimelineService.characterSlot(69L);
        SandboxTimeline t = service.characterTimeline(USER, 69L);
        service.update(USER, t.getId(), request("{\"nodes\":[{\"id\":\"n1\",\"label\":\"新历元年\"}],\"events\":[{\"id\":\"e1\",\"nodeId\":\"n1\",\"text\":\"时间线上改过的经历\"}],\"links\":[],\"shadows\":[],\"settings\":{}}", 1));
        // 旧版客户端把整份旧角色卡又存了回来
        characters.get(69L).setContent(SHEET);
        mirroredContent = null;
        service.reapplyToSheet(USER, 69L);
        JsonNode exp = JSON.readTree(mirroredContent).get("experiences");
        assertEquals(1, exp.size());
        assertEquals("时间线上改过的经历", exp.get(0).get("content").asText());
        // 没有时间线的角色不受影响
        slotFilter = SandboxTimelineService.characterSlot(70L);
        mirroredContent = null;
        service.reapplyToSheet(USER, 70L);
        org.junit.jupiter.api.Assertions.assertNull(mirroredContent);
    }

    @Test
    void olderFormatSave_keepsBranchesStylesAndThreeItemCrossings_butNewerSavesCanRemoveThem() throws Exception {
        slotFilter = SandboxTimelineService.worldSlot(1L);
        SandboxTimeline t = service.worldTimeline(USER, 1L);
        String advanced = "{\"format\":2,\"nodes\":[{\"id\":\"n1\",\"label\":\"建城\",\"fill\":\"#112233\"},{\"id\":\"n2\",\"label\":\"迁都\"}],"
                + "\"events\":[{\"id\":\"e1\",\"nodeId\":\"n1\",\"text\":\"母\",\"ink\":\"#445566\"},{\"id\":\"e2\",\"nodeId\":\"n1\",\"text\":\"子\",\"parentId\":\"e1\"},"
                + "{\"id\":\"e3\",\"nodeId\":\"n1\",\"text\":\"孙\",\"parentId\":\"e2\",\"border\":\"#778899\"},{\"id\":\"e4\",\"nodeId\":\"n1\",\"text\":\"另一个子\",\"parentId\":\"e1\"}],"
                + "\"links\":[{\"id\":\"l1\",\"from\":\"e1\",\"to\":\"e3\",\"note\":\"a\",\"third\":\"n2\",\"note2\":\"b\",\"note3\":\"c\",\"color\":\"#3F9BC4\"},"
                + "{\"id\":\"l2\",\"from\":\"e2\",\"to\":\"e4\",\"note\":\"d\",\"third\":\"e3\",\"note2\":\"e\",\"note3\":\"f\"}],\"shadows\":[],\"settings\":{}}";
        service.update(USER, t.getId(), request(advanced, 1));
        assertEquals(advanced, timelines.get(t.getId()).getData(), "新版保存原样存下");

        // 旧版客户端：不认识这些字段，改了一处文字、把 e4 挪到 n2、删掉 e3 之后整份保存
        String legacy = "{\"nodes\":[{\"id\":\"n1\",\"label\":\"建城改\"},{\"id\":\"n2\",\"label\":\"迁都\"}],"
                + "\"events\":[{\"id\":\"e1\",\"nodeId\":\"n1\",\"text\":\"母\"},{\"id\":\"e2\",\"nodeId\":\"n1\",\"text\":\"子改\"},{\"id\":\"e4\",\"nodeId\":\"n2\",\"text\":\"另一个子\"}],"
                + "\"links\":[{\"id\":\"l1\",\"from\":\"e1\",\"to\":\"e2\",\"note\":\"a\"},{\"id\":\"l2\",\"from\":\"e2\",\"to\":\"e4\",\"note\":\"d\"}],\"shadows\":[],\"settings\":{}}";
        JsonNode kept = JSON.readTree(service.update(USER, t.getId(), request(legacy, 2)).getData());
        assertEquals("建城改", kept.get("nodes").get(0).get("label").asText(), "旧版客户端改的内容照常生效");
        assertEquals("#112233", kept.get("nodes").get(0).get("fill").asText());
        assertEquals("#445566", kept.get("events").get(0).get("ink").asText());
        assertEquals("e1", kept.get("events").get(1).get("parentId").asText());
        assertEquals("子改", kept.get("events").get(1).get("text").asText());
        assertEquals(3, kept.get("events").size(), "旧版客户端删掉的事件不会回来");
        assertFalse(kept.get("events").get(2).has("parentId"), "挪到别的节骨点之后不再接回原来的母事件");
        assertFalse(kept.get("links").get(0).has("third"), "两端被改过的跨越不接回第三个节点");
        assertEquals("#3F9BC4", kept.get("links").get(0).get("color").asText());
        assertFalse(kept.get("links").get(1).has("third"), "第三个节点已经删掉");

        // 1.0.38：没有 format 但自己写这些字段，按它保存的为准
        String v138 = "{\"nodes\":[{\"id\":\"n1\",\"label\":\"建城改\"},{\"id\":\"n2\",\"label\":\"迁都\"}],"
                + "\"events\":[{\"id\":\"e1\",\"nodeId\":\"n1\",\"text\":\"母\"},{\"id\":\"e2\",\"nodeId\":\"n1\",\"text\":\"子改\",\"parentId\":\"e1\"}],\"links\":[],\"shadows\":[],\"settings\":{}}";
        service.update(USER, t.getId(), request(v138, 3));
        assertEquals(v138, timelines.get(t.getId()).getData());

        // 新版客户端把最后一个母事件关系也去掉：带 format，不补回
        String cleared = "{\"format\":2,\"nodes\":[{\"id\":\"n1\",\"label\":\"建城改\"}],\"events\":[{\"id\":\"e1\",\"nodeId\":\"n1\",\"text\":\"母\"},{\"id\":\"e2\",\"nodeId\":\"n1\",\"text\":\"子改\"}],\"links\":[],\"shadows\":[],\"settings\":{}}";
        service.update(USER, t.getId(), request(cleared, 4));
        assertEquals(cleared, timelines.get(t.getId()).getData());
    }

    @Test
    void update_rejectsStaleVersionConcurrentEditBrokenDataAndOtherUsers() {
        slotFilter = SandboxTimelineService.worldSlot(1L);
        SandboxTimeline t = service.worldTimeline(USER, 1L);
        String doc = "{\"nodes\":[{\"id\":\"n1\",\"label\":\"建城\"}],\"events\":[],\"links\":[],\"shadows\":[],\"settings\":{}}";
        service.update(USER, t.getId(), request(doc, 1));
        assertThrows(BusinessException.class, () -> service.update(USER, t.getId(), request("{\"nodes\":[]}", 1)));
        assertEquals(doc, timelines.get(t.getId()).getData());
        assertThrows(BusinessException.class, () -> service.update(USER, t.getId(), request("not json", 2)));
        assertThrows(BusinessException.class, () -> service.update(USER, t.getId(), request("[1,2]", 2)));
        assertThrows(BusinessException.class, () -> service.update(USER, t.getId(), request(null, 2)));
        assertThrows(BusinessException.class, () -> service.update(OTHER_USER, t.getId(), request(doc, 2)));
        assertThrows(BusinessException.class, () -> service.update(USER, 999L, request(doc, 2)));
        concurrentEdit = true;
        assertThrows(BusinessException.class, () -> service.update(USER, t.getId(), request(doc, 2)));
        assertEquals(doc, timelines.get(t.getId()).getData());
    }

    @Test
    void mergeOrganized_addsOnlyNewEvents_reusesAnchors_andNeverTouchesManualEdits() throws Exception {
        slotFilter = SandboxTimelineService.worldSlot(1L);
        SandboxTimeline t = service.worldTimeline(USER, 1L);
        List<String[]> extracted = new ArrayList<String[]>();
        extracted.add(new String[]{"潮历三年", "雾港建城", "潮历三年，雾港建城"});
        extracted.add(new String[]{"潮历三年", "引潮会成立", "同年引潮会成立"});
        extracted.add(new String[]{"", "港口大火", "后来港口发生过一场大火"});
        int[] first = service.mergeOrganized(USER, t, "world:1", extracted);
        assertEquals(3, first[0]);
        JsonNode doc = JSON.readTree(t.getData());
        assertEquals(2, doc.get("nodes").size(), "同一个时间写法共用一个锚点");
        assertEquals("", doc.get("nodes").get(1).get("label").asText(), "没写时间的事件挂在空时间的锚点上");
        assertEquals("world:1", doc.get("events").get(0).get("source").asText());
        assertEquals("潮历三年，雾港建城", doc.get("events").get(0).get("quote").asText());
        assertEquals(0, doc.get("links").size());

        // 用户手动改了第一条的文字，删了第三条（客户端把它记进 dismissed）
        com.fasterxml.jackson.databind.node.ObjectNode edited = (com.fasterxml.jackson.databind.node.ObjectNode) doc;
        ((com.fasterxml.jackson.databind.node.ObjectNode) edited.get("events").get(0)).put("text", "雾港在潮历三年建城（我改过）");
        String removedId = edited.get("events").get(2).get("id").asText();
        ((com.fasterxml.jackson.databind.node.ArrayNode) edited.get("events")).remove(2);
        edited.putArray("dismissed").add(removedId);
        t.setData(edited.toString());

        int[] second = service.mergeOrganized(USER, t, "world:1", extracted);
        assertEquals(0, second[0], "重复整理不新增");
        assertEquals(3, second[1]);
        JsonNode after = JSON.readTree(t.getData());
        assertEquals(2, after.get("events").size());
        assertEquals("雾港在潮历三年建城（我改过）", after.get("events").get(0).get("text").asText());

        t.setData("broken");
        assertThrows(BusinessException.class, () -> service.mergeOrganized(USER, t, "world:1", extracted));
    }

    @Test
    void mergeOrganized_addsEventLinksOnce_andRespectsManualLinksEditsAndDeletions() throws Exception {
        slotFilter = SandboxTimelineService.worldSlot(1L);
        SandboxTimeline t = service.worldTimeline(USER, 1L);
        List<String[]> events = new ArrayList<String[]>();
        events.add(new String[]{"潮历三年", "雾港建城", "潮历三年，雾港建城"});
        events.add(new String[]{"", "港口大火", "后来港口发生过一场大火"});
        events.add(new String[]{"", "引潮会成立", "引潮会成立"});
        List<String[]> links = new ArrayList<String[]>();
        links.add(new String[]{"1", "2", "导致"});
        links.add(new String[]{"2", "1", "重复的反方向"});
        links.add(new String[]{"0", "7", "序号越界"});
        links.add(new String[]{"0", "0", "自己连自己"});
        int[] first = service.mergeOrganized(USER, t, "world:1", events, links);
        assertEquals(3, first[0]);
        assertEquals(1, first[2]);
        assertEquals(1, first[3], "同一对事件只有一条连接");
        JsonNode doc = JSON.readTree(t.getData());
        assertEquals(1, doc.get("links").size());
        JsonNode link = doc.get("links").get(0);
        assertTrue(link.get("id").asText().startsWith("ol-"));
        assertEquals(doc.get("events").get(1).get("id").asText(), link.get("from").asText());
        assertEquals(doc.get("events").get(2).get("id").asText(), link.get("to").asText());
        assertEquals("导致", link.get("note").asText());

        // 事件都已经在时间线上、只多出一条关联时也要写入
        links.add(new String[]{"0", "1", "之后"});
        int[] second = service.mergeOrganized(USER, t, "world:1", events, links);
        assertEquals(0, second[0]);
        assertEquals(1, second[2]);
        com.fasterxml.jackson.databind.node.ObjectNode edited = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(t.getData());
        assertEquals(2, edited.get("links").size());

        // 用户改了第一条的说明，删了第二条（客户端把它记进 dismissed）
        ((com.fasterxml.jackson.databind.node.ObjectNode) edited.get("links").get(0)).put("note", "我改过的说明");
        String removed = edited.get("links").get(1).get("id").asText();
        ((com.fasterxml.jackson.databind.node.ArrayNode) edited.get("links")).remove(1);
        edited.putArray("dismissed").add(removed);
        t.setData(edited.toString());
        int version = t.getVersion();
        int[] third = service.mergeOrganized(USER, t, "world:1", events, links);
        assertEquals(0, third[2], "改过的不覆盖，删掉的不回来");
        assertEquals(version, t.getVersion(), "没有新增就不保存");
        JsonNode after = JSON.readTree(t.getData());
        assertEquals(1, after.get("links").size());
        assertEquals("我改过的说明", after.get("links").get(0).get("note").asText());

        // 端点事件被用户删掉了：不留下指向空处的连接
        com.fasterxml.jackson.databind.node.ObjectNode cut = (com.fasterxml.jackson.databind.node.ObjectNode) after;
        String goneEvent = cut.get("events").get(0).get("id").asText();
        ((com.fasterxml.jackson.databind.node.ArrayNode) cut.get("events")).remove(0);
        ((com.fasterxml.jackson.databind.node.ArrayNode) cut.get("dismissed")).removeAll().add(goneEvent);
        t.setData(cut.toString());
        int[] fourth = service.mergeOrganized(USER, t, "world:1", events, links);
        assertEquals(0, fourth[0]);
        assertEquals(0, fourth[2]);
    }

    @Test
    void lines_listEventsInOrderForChatContext() {
        slotFilter = SandboxTimelineService.characterSlot(69L);
        service.characterTimeline(USER, 69L);
        List<String> lines = service.lines(USER, SandboxTimelineService.characterSlot(69L));
        assertEquals(2, lines.size());
        assertEquals("潮历三年·春：被老陈收留", lines.get(0));
        assertEquals("成为引潮人", lines.get(1));
        slotFilter = "char:12345";
        assertTrue(service.lines(USER, "char:12345").isEmpty());
    }

    private void character(long id, long userId, String content) {
        SandboxCharacter c = new SandboxCharacter();
        c.setId(id);
        c.setUserId(userId);
        c.setWorldviewId(1L);
        c.setName("角色" + id);
        c.setContent(content);
        characters.put(id, c);
    }

    @Test
    void mergeOrganized_recognisesTheSameFactWhenTheModelRewordsIt() throws Exception {
        slotFilter = SandboxTimelineService.worldSlot(1L);
        SandboxTimeline t = service.worldTimeline(USER, 1L);
        List<String[]> first = new ArrayList<String[]>();
        first.add(new String[]{"Year 3", "Fog Harbor was founded", "In Year 3 Fog Harbor was founded."});
        first.add(new String[]{"Year 7", "a great fire burned the docks", "In Year 7 a great fire burned the docks"});
        first.add(new String[]{"Year 9", "the Tide Guild being formed", "which led to the Tide Guild being formed in Year 9"});
        List<String[]> firstLinks = new ArrayList<String[]>();
        firstLinks.add(new String[]{"1", "2", "led to"});
        assertEquals(3, service.mergeOrganized(USER, t, "world:1", first, firstLinks)[0]);

        // 第二次整理：标点、大小写、说法、引用的原文长短、事件顺序都变了
        List<String[]> second = new ArrayList<String[]>();
        second.add(new String[]{"Year 9", "The Tide Guild was formed.", "the Tide Guild being formed in Year 9"});
        second.add(new String[]{"Year 3", "Fog Harbor was founded.", "Fog Harbor was founded"});
        second.add(new String[]{"Year 7", "A great fire burned the docks.", "In Year 7 a great fire burned the docks, which led to the Tide Guild"});
        List<String[]> secondLinks = new ArrayList<String[]>();
        secondLinks.add(new String[]{"2", "0", "caused"});
        int version = t.getVersion();
        int[] result = service.mergeOrganized(USER, t, "world:1", second, secondLinks);
        assertEquals(0, result[0], "同一件事换了说法不重复添加");
        assertEquals(3, result[1]);
        assertEquals(0, result[2], "关联接在原来的两个事件上，不再多出一条");
        assertEquals(1, result[3]);
        assertEquals(version, t.getVersion());
        JsonNode doc = JSON.readTree(t.getData());
        assertEquals(3, doc.get("events").size());
        assertEquals(1, doc.get("links").size());
        assertEquals("Fog Harbor was founded", doc.get("events").get(0).get("text").asText(), "已有的文字不改写");
    }

    @Test
    void mergeOrganized_secondFactFromTheSameSentenceIsStillAdded_once() throws Exception {
        slotFilter = SandboxTimelineService.worldSlot(1L);
        SandboxTimeline t = service.worldTimeline(USER, 1L);
        String sentence = "潮历七年港口大火，港务官死于火中";
        List<String[]> first = new ArrayList<String[]>();
        first.add(new String[]{"潮历七年", "港口发生大火", sentence});
        assertEquals(1, service.mergeOrganized(USER, t, "world:1", first)[0]);

        List<String[]> second = new ArrayList<String[]>();
        second.add(new String[]{"潮历七年", "港务官死于火中", sentence});
        second.add(new String[]{"潮历七年", "港口大火", "潮历七年港口大火"});
        int[] result = service.mergeOrganized(USER, t, "world:1", second);
        assertEquals(1, result[0], "同一句原文里的另一件事照常添加");
        assertEquals(1, result[1]);
        assertEquals(0, service.mergeOrganized(USER, t, "world:1", second)[0]);
        assertEquals(2, JSON.readTree(t.getData()).get("events").size());
    }

    @Test
    void deletedOrEditedOrganizedEvents_surviveARewordedReorganize() throws Exception {
        slotFilter = SandboxTimelineService.worldSlot(1L);
        SandboxTimeline t = service.worldTimeline(USER, 1L);
        List<String[]> first = new ArrayList<String[]>();
        first.add(new String[]{"Year 3", "Fog Harbor was founded", "In Year 3 Fog Harbor was founded."});
        first.add(new String[]{"Year 7", "a great fire burned the docks", "In Year 7 a great fire burned the docks"});
        service.mergeOrganized(USER, t, "world:1", first);

        // 客户端保存：第一条改了文字和时间，第二条删掉；客户端不认识 dismissedFacts，发来的文档里没有它
        com.fasterxml.jackson.databind.node.ObjectNode edited = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(t.getData());
        ((com.fasterxml.jackson.databind.node.ObjectNode) edited.get("events").get(0)).put("text", "我自己写的建城经过");
        ((com.fasterxml.jackson.databind.node.ObjectNode) edited.get("nodes").get(0)).put("label", "Year 5");
        String removedId = edited.get("events").get(1).get("id").asText();
        ((com.fasterxml.jackson.databind.node.ArrayNode) edited.get("events")).remove(1);
        edited.putArray("dismissed").add(removedId);
        SandboxTimeline saved = service.update(USER, t.getId(), request(edited.toString(), t.getVersion()));
        JsonNode stored = JSON.readTree(saved.getData());
        assertEquals(removedId, stored.get("dismissedFacts").get(0).get("id").asText());
        assertEquals("In Year 7 a great fire burned the docks", stored.get("dismissedFacts").get(0).get("quote").asText());

        // 再保存一次（文档里仍然没有 dismissedFacts）：记录不丢
        saved = service.update(USER, t.getId(), request(edited.toString(), saved.getVersion()));
        assertEquals(1, JSON.readTree(saved.getData()).get("dismissedFacts").size());

        List<String[]> reworded = new ArrayList<String[]>();
        reworded.add(new String[]{"Year 3", "Fog Harbor was founded.", "Fog Harbor was founded"});
        reworded.add(new String[]{"Year 7", "A great fire burned the docks.", "a great fire burned the docks"});
        int[] result = service.mergeOrganized(USER, saved, "world:1", reworded);
        assertEquals(0, result[0], "改过的不重复添加，删掉的不回来");
        JsonNode after = JSON.readTree(saved.getData());
        assertEquals(1, after.get("events").size());
        assertEquals("我自己写的建城经过", after.get("events").get(0).get("text").asText());
        assertEquals("Year 5", after.get("nodes").get(0).get("label").asText());
    }

    @Test
    void shadowBlocks_areNotWrittenIntoTheCharacterSheetsExtraTimeline() throws Exception {
        slotFilter = SandboxTimelineService.characterSlot(70L);
        SandboxTimeline t = service.characterTimeline(USER, 70L);
        String data = "{\"nodes\":[],\"events\":[],\"links\":[],\"settings\":{},\"shadows\":["
                + "{\"id\":\"s1\",\"targetId\":\"\",\"title\":\"某夜\",\"text\":\"梦见雾海\"},"
                + "{\"id\":\"s2\",\"targetId\":\"\",\"title\":\"\",\"text\":\"大潮年\",\"block\":true,\"x\":-120,\"y\":300,\"w\":96,\"h\":240}]}";
        service.update(USER, t.getId(), request(data, t.getVersion()));
        JsonNode extra = JSON.readTree(mirroredContent).get("extraTimeline");
        assertEquals(1, extra.size(), "影子块是画布上的色块，不是补充时间线的文字");
        assertEquals("梦见雾海", extra.get(0).get("content").asText());
    }

    private static SandboxTimelineRequest request(String data, Integer version) {
        SandboxTimelineRequest r = new SandboxTimelineRequest();
        r.setData(data);
        r.setVersion(version);
        return r;
    }
}
