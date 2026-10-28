package com.ugccommunity.backend.service;

import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.dto.request.SandboxChatRequest;
import com.ugccommunity.backend.entity.SandboxCharacter;
import com.ugccommunity.backend.entity.SandboxChatMemory;
import com.ugccommunity.backend.entity.SandboxChatMessage;
import com.ugccommunity.backend.entity.SandboxChatSession;
import com.ugccommunity.backend.entity.SandboxRecord;
import com.ugccommunity.backend.entity.SandboxRelation;
import com.ugccommunity.backend.entity.SandboxWorldview;
import com.ugccommunity.backend.mapper.SandboxCharacterMapper;
import com.ugccommunity.backend.mapper.SandboxChatMemoryMapper;
import com.ugccommunity.backend.mapper.SandboxChatMessageMapper;
import com.ugccommunity.backend.mapper.SandboxChatSessionMapper;
import com.ugccommunity.backend.mapper.SandboxWorldviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SandboxChatServiceTest {
    private SandboxRelation relationRow;
    private static final long USER = 7L;
    private static final long OTHER_USER = 8L;

    @Mock private SandboxChatSessionMapper sessionMapper;
    @Mock private SandboxChatMessageMapper messageMapper;
    @Mock private SandboxChatMemoryMapper memoryMapper;
    @Mock private SandboxWorldviewMapper worldviewMapper;
    @Mock private SandboxCharacterMapper characterMapper;
    @Mock private SandboxRecordService recordService;
    @Mock private SandboxRelationService relationService;
    @Mock private SandboxTimelineService timelineService;
    @Mock private DeepSeekClient ai;
    private SandboxChatService service;

    private final Map<Long, SandboxChatSession> sessions = new LinkedHashMap<Long, SandboxChatSession>();
    private final Map<Long, SandboxChatMessage> messages = new LinkedHashMap<Long, SandboxChatMessage>();
    private final Map<Long, SandboxChatMemory> memories = new LinkedHashMap<Long, SandboxChatMemory>();
    private final Map<Long, SandboxWorldview> worlds = new LinkedHashMap<Long, SandboxWorldview>();
    private final Map<Long, SandboxCharacter> characters = new LinkedHashMap<Long, SandboxCharacter>();
    private long seq = 100;
    private String aiReply = "【旁白】雾从港口升起。\n【黎潮】潮信到了。";
    private final List<String> systemPrompts = new ArrayList<String>();
    private final List<List<SandboxChatMessage>> histories = new ArrayList<List<SandboxChatMessage>>();

    @BeforeEach
    void setUp() {
        service = new SandboxChatService(sessionMapper, messageMapper, memoryMapper, worldviewMapper, characterMapper, recordService, relationService, timelineService, ai);
        lenient().when(sessionMapper.selectById(anyLong())).thenAnswer(inv -> sessions.get((Long) inv.getArgument(0)));
        lenient().when(sessionMapper.insert(any(SandboxChatSession.class))).thenAnswer(inv -> { SandboxChatSession s = inv.getArgument(0); s.setId(++seq); sessions.put(s.getId(), s); return 1; });
        lenient().when(sessionMapper.updateById(any(SandboxChatSession.class))).thenReturn(1);
        lenient().when(messageMapper.selectById(anyLong())).thenAnswer(inv -> messages.get((Long) inv.getArgument(0)));
        lenient().when(messageMapper.selectList(any())).thenAnswer(inv -> new ArrayList<SandboxChatMessage>(messages.values()));
        lenient().when(messageMapper.insert(any(SandboxChatMessage.class))).thenAnswer(inv -> { SandboxChatMessage m = inv.getArgument(0); m.setId(++seq); messages.put(m.getId(), m); return 1; });
        lenient().when(messageMapper.updateById(any(SandboxChatMessage.class))).thenReturn(1);
        lenient().when(messageMapper.deleteById(anyLong())).thenAnswer(inv -> messages.remove((Long) inv.getArgument(0)) == null ? 0 : 1);
        lenient().when(memoryMapper.selectById(anyLong())).thenAnswer(inv -> memories.get((Long) inv.getArgument(0)));
        lenient().when(memoryMapper.selectList(any())).thenAnswer(inv -> new ArrayList<SandboxChatMemory>(memories.values()));
        lenient().when(memoryMapper.insert(any(SandboxChatMemory.class))).thenAnswer(inv -> { SandboxChatMemory m = inv.getArgument(0); m.setId(++seq); memories.put(m.getId(), m); return 1; });
        lenient().when(memoryMapper.deleteById(anyLong())).thenAnswer(inv -> memories.remove((Long) inv.getArgument(0)) == null ? 0 : 1);
        lenient().when(worldviewMapper.selectById(anyLong())).thenAnswer(inv -> worlds.get((Long) inv.getArgument(0)));
        lenient().when(characterMapper.selectById(anyLong())).thenAnswer(inv -> characters.get((Long) inv.getArgument(0)));
        lenient().when(ai.isConfigured()).thenReturn(true);
        lenient().when(ai.generateChat(anyString(), anyList(), anyInt(), anyDouble(), any())).thenAnswer(inv -> {
            systemPrompts.add(inv.getArgument(0));
            histories.add(new ArrayList<SandboxChatMessage>((List<SandboxChatMessage>) inv.getArgument(1)));
            return aiReply;
        });
        SandboxRecord record = new SandboxRecord(); record.setName("雾鹿"); record.setDescription("栖息在雾港北岸");
        lenient().when(recordService.listRecords(eq(USER), eq(1L), any())).thenReturn(Collections.singletonList(record));
        SandboxRelation relation = new SandboxRelation();
        relationRow = relation;
        relation.setFromCharacterId(71L); relation.setToCharacterId(69L); relation.setLabel("师父"); relation.setReverseLabel("徒弟");
        relation.setDirection(SandboxRelation.TWO_WAY); relation.setNote("教他辨认潮信");
        lenient().when(relationService.listRelations(USER)).thenReturn(Collections.singletonList(relation));
        lenient().when(timelineService.lines(eq(USER), eq("world:1"))).thenReturn(Collections.singletonList("潮历三年：雾港建城"));
        lenient().when(timelineService.lines(eq(USER), eq("char:69"))).thenReturn(Collections.singletonList("潮历七年：成为引潮人"));
        world(1, USER, "雾港纪元", "海雾终年不散。");
        world(2, USER, "星桥诸岛", "群岛由星桥相连。");
        world(3, USER, "第三世界", "");
        world(9, OTHER_USER, "别人的世界", "");
        character(69, USER, 1, "黎潮", "{\"profession\":\"引潮人\"}");
        character(71, USER, 1, "老陈", null);
        character(72, USER, 2, "栖星", null);
        character(73, USER, 3, "第三世界的人", null);
        character(99, OTHER_USER, 9, "外人", null);
    }

    // ---------- 创建与两个世界观的限制 ----------

    @Test
    void create_savesPrimarySecondaryCharactersAndMode_andNeedsThePromptBeforeItCanRun() {
        SandboxChatSession s = service.createSession(USER, config(1L, 2L, Arrays.asList(69L, 72L, 69L), "story", null));
        assertEquals(1L, s.getPrimaryWorldviewId());
        assertEquals(2L, s.getSecondaryWorldviewId());
        assertEquals("69,72", s.getCharacterIds(), "重复选择的角色只记一次");
        assertEquals("story", s.getMode());
        assertEquals("server", s.getApiSource());
        assertEquals(0, s.getPromptApplied());
        assertEquals(1, service.createSession(USER, config(1L, null, Arrays.asList(69L), "roleplay", "我扮演旅人")).getPromptApplied());
    }

    @Test
    void create_rejectsCharactersFromAThirdWorld_soAChatNeverSpansMoreThanTwoWorlds() {
        assertThrows(BusinessException.class, () -> service.createSession(USER, config(1L, 2L, Arrays.asList(69L, 72L, 73L), "story", null)));
        assertThrows(BusinessException.class, () -> service.createSession(USER, config(1L, null, Arrays.asList(69L, 72L), "story", null)), "没选补充世界观时不能带它的角色");
        assertThrows(BusinessException.class, () -> service.createSession(USER, config(1L, 1L, Arrays.asList(69L), "story", null)), "补充世界观不能和主世界观相同");
        assertThrows(BusinessException.class, () -> service.createSession(USER, config(9L, null, Arrays.asList(69L), "story", null)));
        assertThrows(BusinessException.class, () -> service.createSession(USER, config(1L, 9L, Arrays.asList(69L), "story", null)));
        assertThrows(BusinessException.class, () -> service.createSession(USER, config(1L, null, Arrays.asList(99L), "story", null)));
        assertThrows(BusinessException.class, () -> service.createSession(USER, config(1L, null, new ArrayList<Long>(), "story", null)));
        assertThrows(BusinessException.class, () -> service.createSession(USER, config(1L, null, Arrays.asList(69L), "freeform", null)));
        assertTrue(sessions.isEmpty());
    }

    // ---------- 提示词门控 ----------

    @Test
    void generationAndContextAreBlocked_untilAPromptIsApplied() {
        SandboxChatSession s = service.createSession(USER, config(1L, null, Arrays.asList(69L), "story", null));
        assertThrows(BusinessException.class, () -> service.send(USER, s.getId(), "开始吧", true));
        assertThrows(BusinessException.class, () -> service.send(USER, s.getId(), "开始吧", false));
        assertThrows(BusinessException.class, () -> service.context(USER, s.getId(), null));
        assertThrows(BusinessException.class, () -> service.saveReply(USER, s.getId(), "设备端回复", null));
        assertThrows(BusinessException.class, () -> service.applyPrompt(USER, s.getId(), "   "));
        assertTrue(messages.isEmpty(), "被拒绝时连用户消息也不保存");
        verify(ai, never()).generateChat(anyString(), anyList(), anyInt(), anyDouble(), any());

        service.applyPrompt(USER, s.getId(), "雾港禁止使用星桥的飞行术");
        assertEquals(3, service.send(USER, s.getId(), "开始吧", true).size());
    }

    @Test
    void legacySessionsStayReadable_butCannotGenerateUntilConfigured() {
        SandboxChatSession legacy = service.createSession(USER, new SandboxChatRequest());
        SandboxChatMessage old = new SandboxChatMessage();
        old.setId(50L); old.setSessionId(legacy.getId()); old.setUserId(USER); old.setRole("user"); old.setContent("很久以前的消息"); old.setMockReply(0);
        messages.put(50L, old);
        assertNull(legacy.getMode());
        assertEquals(1, service.listMessages(USER, legacy.getId()).size());
        assertThrows(BusinessException.class, () -> service.send(USER, legacy.getId(), "继续", true));
        assertThrows(BusinessException.class, () -> service.applyPrompt(USER, legacy.getId(), "直接应用提示词"), "没选世界观和模式之前不能应用提示词");
        service.configure(USER, legacy.getId(), config(1L, null, Arrays.asList(69L), "roleplay", "我是旅人"));
        List<SandboxChatMessage> out = service.send(USER, legacy.getId(), "继续", true);
        assertEquals("user", out.get(0).getRole());
        assertTrue(out.get(0).position() > old.position(), "新消息排在旧消息之后");
    }

    // ---------- 上下文 ----------

    @Test
    void context_carriesWorldsRecordsTimelinesCharactersRelationsPromptAndMode() {
        SandboxChatSession s = service.createSession(USER, config(1L, 2L, Arrays.asList(69L, 71L, 72L), "story", "雾港禁止飞行术；冲突时以雾港为准"));
        service.send(USER, s.getId(), "写黎潮第一次出海", true);
        String prompt = systemPrompts.get(0);
        for (String expected : new String[]{"续写一个故事", "【主世界观】雾港纪元", "【补充世界观】星桥诸岛", "海雾终年不散", "雾鹿：栖息在雾港北岸",
                "潮历三年：雾港建城", "【角色】黎潮（来自雾港纪元）", "职业：引潮人", "潮历七年：成为引潮人", "【角色】栖星（来自星桥诸岛）",
                "关系：对老陈——徒弟", "关系：对黎潮——师父（教他辨认潮信）", "雾港禁止飞行术；冲突时以雾港为准", "保留其原世界观的设定"}) {
            assertTrue(prompt.contains(expected), "上下文缺少：" + expected);
        }
        assertFalse(prompt.contains("角色扮演"));
        SandboxChatSession r = service.createSession(USER, config(1L, null, Arrays.asList(69L), "roleplay", "我是旅人"));
        service.send(USER, r.getId(), "你好", true);
        assertTrue(systemPrompts.get(1).contains("角色扮演"));
        assertTrue(systemPrompts.get(1).contains("不要替用户说话"));
        assertFalse(systemPrompts.get(1).contains("【补充世界观】"));
    }

    @Test
    void personalApi_getsTheSameContextFromTheServer_andStoresTheDeviceReply() {
        SandboxChatRequest c = config(1L, null, Arrays.asList(69L), "story", "雾港禁止飞行术");
        c.setApiSource("personal");
        SandboxChatSession s = service.createSession(USER, c);
        List<SandboxChatMessage> sent = service.send(USER, s.getId(), "写一段开场", false);
        assertEquals(1, sent.size(), "个人 API：服务器只保存用户消息，不调用平台 AI");
        verify(ai, never()).generateChat(anyString(), anyList(), anyInt(), anyDouble(), any());
        Map<String, Object> context = service.context(USER, s.getId(), null);
        assertTrue(((String) context.get("systemPrompt")).contains("雾港禁止飞行术"));
        assertTrue(((String) context.get("systemPrompt")).contains("【角色】黎潮"));
        List<Map<String, String>> history = (List<Map<String, String>>) context.get("messages");
        assertEquals(1, history.size());
        assertEquals("写一段开场", history.get(0).get("content"));
        List<SandboxChatMessage> reply = service.saveReply(USER, s.getId(), "【旁白】雾很浓。\n【黎潮】该走了。", null);
        assertEquals(2, reply.size());
        assertEquals(3, service.listMessages(USER, s.getId()).size());
    }

    // ---------- 说话者 ----------

    @Test
    void replies_areSplitByNarrationAndTheActualSpeakingCharacter() {
        SandboxChatSession s = ready("story", 69L, 71L);
        aiReply = "【旁白】雾从港口升起。\n灯塔亮了。\n【黎潮】潮信到了。\n【老陈】再等一刻。\n【路人甲】让一让！";
        List<SandboxChatMessage> out = service.send(USER, s.getId(), "继续", true);
        assertEquals(Arrays.asList("user", "narration", "character", "character", "character"), out.stream().map(SandboxChatMessage::getKind).collect(Collectors.toList()));
        assertEquals("雾从港口升起。\n灯塔亮了。", out.get(1).getContent());
        assertEquals(69L, out.get(2).getSpeakerCharacterId());
        assertEquals("黎潮", out.get(2).getSpeakerName());
        assertEquals(71L, out.get(3).getSpeakerCharacterId());
        assertNull(out.get(4).getSpeakerCharacterId(), "没选中的名字保留名字，但不冒认成某个角色");
        assertEquals("路人甲", out.get(4).getSpeakerName());
        assertEquals(out.get(1).getReplyGroup(), out.get(4).getReplyGroup());
        assertEquals(out.get(1).getSortKey(), out.get(4).getSortKey());
    }

    @Test
    void systemPrompt_labelsTraitsAsPersonalityWithoutRepeatingIt() {
        characters.get(69L).setPersonality("沉稳");
        characters.get(69L).setContent("{\"traits\":\"沉稳\",\"habits\":\"看潮\"}");
        characters.get(71L).setPersonality("嘴硬");
        characters.get(71L).setContent("{\"traits\":\"心软\"}");
        SandboxChatSession s = ready("story", 69L, 71L);
        service.send(USER, s.getId(), "开始", true);
        String prompt = systemPrompts.get(0);
        assertFalse(prompt.contains("特点"), prompt);
        assertEquals(1, prompt.split("性格：沉稳", -1).length - 1, prompt);
        assertTrue(prompt.contains("习惯：看潮"), prompt);
        assertTrue(prompt.contains("性格：嘴硬"), prompt);
        assertTrue(prompt.contains("性格：心软"), prompt);
    }

    @Test
    void systemPrompt_carriesEachCharactersViewOfTheOther() {
        SandboxChatSession s = ready("story", 69L, 71L);
        relationRow.setFromView("总想着出海的毛头小子");
        relationRow.setToView("嘴硬心软的老头");
        service.send(USER, s.getId(), "开始", true);
        String prompt = systemPrompts.get(0);
        assertTrue(prompt.contains("认知：老陈眼中的黎潮——总想着出海的毛头小子"), prompt);
        assertTrue(prompt.contains("认知：黎潮眼中的老陈——嘴硬心软的老头"), prompt);
    }
    @Test
    void narrationOff_dropsNarrationFromReplyPromptAndHistory() {
        SandboxChatSession s = ready("story", 69L, 71L);
        aiReply = "【旁白】海雾漫上来。\n【黎潮】先回灯塔。\n【老陈】好。";
        ChatGenOptions off = new ChatGenOptions(false, 1.2, 0.9, 900, 0);
        List<SandboxChatMessage> out = service.send(USER, s.getId(), "开始", true, off);
        assertEquals(3, out.size(), "用户消息 + 两句角色台词，旁白被丢掉");
        assertEquals("黎潮", out.get(1).getSpeakerName());
        assertFalse(systemPrompts.get(0).contains("【旁白】……"));
        assertTrue(systemPrompts.get(0).contains("不要写旁白"));
        verify(ai).generateChat(anyString(), anyList(), eq(900), eq(1.2), eq(0.9));
        // 先用默认设置产生一条旁白，关闭旁白后历史里不再带它
        aiReply = "【旁白】天亮了。\n【黎潮】早。";
        service.send(USER, s.getId(), "继续", true);
        service.send(USER, s.getId(), "再继续", true, off);
        String lastHistory = histories.get(histories.size() - 1).toString();
        for (SandboxChatMessage m : histories.get(histories.size() - 1)) assertFalse(m.getContent().contains("【旁白】"), lastHistory);
    }
    @Test
    void contextLimit_keepsOnlyTheMostRecentTurns() {
        SandboxChatSession s = ready("roleplay", 69L);
        aiReply = "【黎潮】嗯。";
        for (int i = 0; i < 4; i++) service.send(USER, s.getId(), "第" + i + "句", true);
        service.send(USER, s.getId(), "最后一句", true, new ChatGenOptions(true, 0.8, 1.0, 1500, 3));
        assertEquals(3, histories.get(histories.size() - 1).size());
    }
    @Test
    void deleteMessage_removesOnlyThatMessage_andRejectsOthersMessages() {
        SandboxChatSession s = ready("roleplay", 69L);
        aiReply = "【黎潮】嗯。";
        List<SandboxChatMessage> out = service.send(USER, s.getId(), "你好", true);
        service.deleteMessage(USER, out.get(0).getId());
        assertEquals(1, service.listMessages(USER, s.getId()).size());
        assertThrows(BusinessException.class, () -> service.deleteMessage(OTHER_USER, out.get(1).getId()));
        assertEquals(1, service.listMessages(USER, s.getId()).size());
    }
    @Test
    void untaggedReply_isNarrationInStory_andTheOnlyCharacterInRoleplay() {
        assertNull(SandboxChatService.parseReply("没有任何标记的一段话", null).get(0)[0]);
        assertEquals("黎潮", SandboxChatService.parseReply("没有任何标记的一段话", "黎潮").get(0)[0]);
        SandboxChatSession s = ready("roleplay", 69L);
        aiReply = "我看见潮水退了。";
        List<SandboxChatMessage> out = service.send(USER, s.getId(), "你看见了什么", true);
        assertEquals("character", out.get(1).getKind());
        assertEquals(69L, out.get(1).getSpeakerCharacterId());
    }

    // ---------- 重新生成与编辑 ----------

    @Test
    void regenerate_replacesOnlyTheSelectedReply_inPlace_usingOnlyEarlierHistory() {
        SandboxChatSession s = ready("story", 69L);
        aiReply = "【旁白】第一段回复。";
        List<SandboxChatMessage> first = service.send(USER, s.getId(), "第一句", true);
        aiReply = "【旁白】第二段回复。";
        List<SandboxChatMessage> second = service.send(USER, s.getId(), "第二句", true);
        aiReply = "【旁白】重新生成的第一段。\n【黎潮】换个说法。";
        histories.clear();
        List<SandboxChatMessage> regenerated = service.regenerate(USER, s.getId(), first.get(1).getId());

        assertEquals(1, histories.get(0).size(), "只带被替换回复之前的历史");
        assertEquals("第一句", histories.get(0).get(0).getContent());
        assertEquals(2, regenerated.size());
        List<String> texts = service.listMessages(USER, s.getId()).stream().map(SandboxChatMessage::getContent).collect(Collectors.toList());
        assertEquals(Arrays.asList("第一句", "重新生成的第一段。", "换个说法。", "第二句", "第二段回复。"), texts);
        assertFalse(messages.containsKey(first.get(1).getId()));
        assertEquals("第二段回复。", messages.get(second.get(1).getId()).getContent(), "后面的消息不动");
        assertThrows(BusinessException.class, () -> service.regenerate(USER, s.getId(), first.get(0).getId()), "用户消息不能重新生成");
    }

    @Test
    void editingAnEarlierMessage_changesOnlyThatMessage_andLaterGenerationUsesTheEditedHistory() {
        SandboxChatSession s = ready("story", 69L);
        List<SandboxChatMessage> first = service.send(USER, s.getId(), "黎潮出海了", true);
        List<SandboxChatMessage> second = service.send(USER, s.getId(), "然后呢", true);
        SandboxChatMessage edited = service.editMessage(USER, first.get(0).getId(), "黎潮留在了港口");
        service.editMessage(USER, first.get(2).getId(), "潮信还没到。");
        assertEquals(1, edited.getEdited());
        assertEquals("然后呢", messages.get(second.get(0).getId()).getContent(), "后面保存的消息没有被悄悄改写");
        histories.clear();
        service.send(USER, s.getId(), "第三句", true);
        List<SandboxChatMessage> history = histories.get(0);
        assertEquals("黎潮留在了港口", history.get(0).getContent());
        assertTrue(history.get(1).getContent().contains("【黎潮】潮信还没到。"));
        assertTrue(history.get(1).getContent().contains("【旁白】雾从港口升起。"), "同一次回复合并成一条并带说话者");
        assertThrows(BusinessException.class, () -> service.editMessage(USER, first.get(0).getId(), "  "));
        assertThrows(BusinessException.class, () -> service.editMessage(OTHER_USER, first.get(0).getId(), "越权"));
    }

    // ---------- 临时记忆 ----------

    @Test
    void temporaryMemory_appliesOnlyAfterItsPosition_onlyInItsSession_andNeverTouchesTheCharacter() {
        SandboxChatSession s = ready("story", 69L);
        SandboxChatSession otherChat = ready("story", 69L);
        List<SandboxChatMessage> first = service.send(USER, s.getId(), "第一句", true);
        String sheetBefore = characters.get(69L).getContent();
        SandboxChatMemory memory = service.addMemory(USER, s.getId(), 69L, "黎潮刚得知老陈病重", first.get(1).getId());
        assertEquals(first.get(1).position(), memory.getAfterSortKey());

        systemPrompts.clear();
        // memoryMapper 的查询条件不解析，这里只留当前会话的记忆来模拟按会话过滤
        service.send(USER, s.getId(), "第二句", true);
        assertTrue(systemPrompts.get(0).contains("【临时记忆（只在这段对话里有效）】"));
        assertTrue(systemPrompts.get(0).contains("黎潮：黎潮刚得知老陈病重"));

        systemPrompts.clear();
        service.regenerate(USER, s.getId(), first.get(1).getId());
        assertFalse(systemPrompts.get(0).contains("老陈病重"), "重新生成记忆位置之前的回复时不带这条记忆");

        assertEquals(sheetBefore, characters.get(69L).getContent(), "不写入角色的永久设定");
        verify(characterMapper, never()).updateById(any(SandboxCharacter.class));
        assertEquals(s.getId(), memory.getSessionId());
        assertThrows(BusinessException.class, () -> service.addMemory(USER, otherChat.getId(), 71L, "不在这段聊天里的角色", null));
        assertThrows(BusinessException.class, () -> service.addMemory(USER, s.getId(), 69L, " ", null));
        assertThrows(BusinessException.class, () -> service.deleteMemory(OTHER_USER, memory.getId()));
        service.deleteMemory(USER, memory.getId());
        assertTrue(memories.isEmpty());
    }

    // ---------- 失败与越权 ----------

    @Test
    void aiFailure_keepsTheHistoryIntact_andOtherUsersAreRejected() {
        SandboxChatSession s = ready("story", 69L);
        List<SandboxChatMessage> first = service.send(USER, s.getId(), "第一句", true);
        lenient().when(ai.generateChat(anyString(), anyList(), anyInt(), anyDouble(), any())).thenThrow(new DeepSeekClient.DeepSeekException("down"));
        assertThrows(BusinessException.class, () -> service.regenerate(USER, s.getId(), first.get(1).getId()));
        assertTrue(messages.containsKey(first.get(1).getId()), "重新生成失败时原回复还在");
        lenient().when(ai.isConfigured()).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.regenerate(USER, s.getId(), first.get(1).getId()));
        assertThrows(BusinessException.class, () -> service.listMessages(OTHER_USER, s.getId()));
        assertThrows(BusinessException.class, () -> service.send(OTHER_USER, s.getId(), "越权", true));
        assertThrows(BusinessException.class, () -> service.context(OTHER_USER, s.getId(), null));
        assertThrows(BusinessException.class, () -> service.configure(OTHER_USER, s.getId(), config(1L, null, Arrays.asList(69L), "story", null)));
        assertThrows(BusinessException.class, () -> service.send(null, s.getId(), "未登录", true));
    }

    private SandboxChatSession ready(String mode, Long... characterIds) {
        return service.createSession(USER, config(1L, null, Arrays.asList(characterIds), mode, "测试用的会话设定"));
    }

    private static SandboxChatRequest config(Long primary, Long secondary, List<Long> characterIds, String mode, String prompt) {
        SandboxChatRequest r = new SandboxChatRequest();
        r.setPrimaryWorldviewId(primary);
        r.setSecondaryWorldviewId(secondary);
        r.setCharacterIds(characterIds);
        r.setMode(mode);
        r.setPrompt(prompt);
        return r;
    }

    private void world(long id, long userId, String name, String content) {
        SandboxWorldview w = new SandboxWorldview();
        w.setId(id); w.setUserId(userId); w.setName(name); w.setContent(content);
        worlds.put(id, w);
    }

    private void character(long id, long userId, long worldviewId, String name, String content) {
        SandboxCharacter c = new SandboxCharacter();
        c.setId(id); c.setUserId(userId); c.setWorldviewId(worldviewId); c.setName(name); c.setContent(content);
        characters.put(id, c);
    }
}
