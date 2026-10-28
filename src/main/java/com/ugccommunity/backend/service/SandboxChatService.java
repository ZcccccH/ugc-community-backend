package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 箱庭聊天。
 *
 * 会话：一个主世界观（决定故事环境）、可选的一个补充世界观（最多两个）、从这两个世界观里选的角色、
 * 故事或角色扮演模式、用户填写并应用的模式提示词。没有应用提示词之前不生成任何回复，也不给出上下文。
 *
 * 平台 AI 和个人 API 用同一套会话配置和上下文：平台 AI 由这里直接调用；个人 API 由设备取走
 * 这里拼好的上下文，在设备上调用（密钥不经过服务器），再把回复存回来。
 */
@Service
public class SandboxChatService {
    private static final int MESSAGE_MAX = 8000;
    private static final int PROMPT_MAX = 4000;
    private static final int MEMORY_MAX = 1000;
    private static final int CHARACTERS_MAX = 30;
    /** 上下文各部分的字数上限：设定很多时按这些上限截断，保证不超过模型能接受的长度 */
    private static final int WORLD_TEXT_MAX = 3000;
    private static final int RECORDS_MAX = 30;
    private static final int RECORD_TEXT_MAX = 160;
    private static final int TIMELINE_LINES_MAX = 30;
    private static final int CHARACTER_TEXT_MAX = 1400;
    private static final int SYSTEM_PROMPT_MAX = 16000;
    private static final int HISTORY_CHARS_MAX = 12000;
    private static final int HISTORY_TURNS_MAX = 40;
    private static final Pattern SPEAKER = Pattern.compile("^\\s*【([^】]{1,40})】\\s*(.*)$");

    private final SandboxChatSessionMapper sessionMapper;
    private final SandboxChatMessageMapper messageMapper;
    private final SandboxChatMemoryMapper memoryMapper;
    private final SandboxWorldviewMapper worldviewMapper;
    private final SandboxCharacterMapper characterMapper;
    private final SandboxRecordService recordService;
    private final SandboxRelationService relationService;
    private final SandboxTimelineService timelineService;
    private final DeepSeekClient ai;

    public SandboxChatService(SandboxChatSessionMapper sessionMapper, SandboxChatMessageMapper messageMapper, SandboxChatMemoryMapper memoryMapper,
                              SandboxWorldviewMapper worldviewMapper, SandboxCharacterMapper characterMapper, SandboxRecordService recordService,
                              SandboxRelationService relationService, SandboxTimelineService timelineService, DeepSeekClient ai) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.memoryMapper = memoryMapper;
        this.worldviewMapper = worldviewMapper;
        this.characterMapper = characterMapper;
        this.recordService = recordService;
        this.relationService = relationService;
        this.timelineService = timelineService;
        this.ai = ai;
    }

    // ------------------------------------------------------------------ 会话

    public List<SandboxChatSession> listSessions(Long userId) {
        validUser(userId);
        List<SandboxChatSession> list = sessionMapper.selectList(new LambdaQueryWrapper<SandboxChatSession>()
                .eq(SandboxChatSession::getUserId, userId).orderByDesc(SandboxChatSession::getUpdatedAt));
        return list == null ? new ArrayList<SandboxChatSession>() : list;
    }

    /**
     * 新建会话。带了 primaryWorldviewId 就按新流程校验并保存配置（带 prompt 时一并应用）；
     * 什么配置都不带（旧版客户端的请求）得到一个未配置的会话，配置好之前不能生成。
     */
    @Transactional
    public SandboxChatSession createSession(Long userId, SandboxChatRequest r) {
        validUser(userId);
        SandboxChatSession x = new SandboxChatSession();
        x.setUserId(userId);
        String title = r == null || r.getTitle() == null ? "" : r.getTitle().trim();
        x.setTitle(title.isEmpty() ? "箱庭聊天" : (title.length() > 120 ? title.substring(0, 120) : title));
        x.setStatus("active");
        x.setPromptApplied(0);
        x.setCreatedAt(LocalDateTime.now());
        x.setUpdatedAt(LocalDateTime.now());
        if (r != null && r.getPrimaryWorldviewId() != null) applyConfig(userId, x, r);
        sessionMapper.insert(x);
        return x;
    }

    /** 修改会话配置；旧会话也通过它补上世界观、角色和模式 */
    @Transactional
    public SandboxChatSession configure(Long userId, Long sessionId, SandboxChatRequest r) {
        SandboxChatSession x = ownedSession(userId, sessionId);
        if (r == null || r.getPrimaryWorldviewId() == null) throw new BusinessException(ResultCode.PARAM_ERROR, "请选择主世界观");
        applyConfig(userId, x, r);
        if (r.getTitle() != null && !r.getTitle().trim().isEmpty()) x.setTitle(r.getTitle().trim().length() > 120 ? r.getTitle().trim().substring(0, 120) : r.getTitle().trim());
        x.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(x);
        return x;
    }

    /** 填写并应用模式提示词。应用之后才能开始对话 */
    @Transactional
    public SandboxChatSession applyPrompt(Long userId, Long sessionId, String prompt) {
        SandboxChatSession x = ownedSession(userId, sessionId);
        requireConfigured(x);
        x.setModePrompt(validPrompt(prompt));
        x.setPromptApplied(1);
        x.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(x);
        return x;
    }

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        SandboxChatSession x = ownedSession(userId, sessionId);
        messageMapper.delete(new LambdaQueryWrapper<SandboxChatMessage>().eq(SandboxChatMessage::getSessionId, x.getId()).eq(SandboxChatMessage::getUserId, userId));
        memoryMapper.delete(new LambdaQueryWrapper<SandboxChatMemory>().eq(SandboxChatMemory::getSessionId, x.getId()).eq(SandboxChatMemory::getUserId, userId));
        sessionMapper.deleteById(x.getId());
    }

    private void applyConfig(Long userId, SandboxChatSession x, SandboxChatRequest r) {
        SandboxWorldview primary = ownedWorldview(userId, r.getPrimaryWorldviewId());
        SandboxWorldview secondary = null;
        if (r.getSecondaryWorldviewId() != null) {
            if (r.getSecondaryWorldviewId().equals(primary.getId())) throw new BusinessException(ResultCode.PARAM_ERROR, "补充世界观不能和主世界观相同");
            secondary = ownedWorldview(userId, r.getSecondaryWorldviewId());
        }
        List<Long> ids = r.getCharacterIds() == null ? new ArrayList<Long>() : r.getCharacterIds();
        if (ids.isEmpty()) throw new BusinessException(ResultCode.PARAM_ERROR, "请至少选择一个角色");
        if (ids.size() > CHARACTERS_MAX) throw new BusinessException(ResultCode.PARAM_ERROR, "选择的角色太多了");
        List<String> kept = new ArrayList<String>();
        for (Long id : ids) {
            if (id == null || kept.contains(String.valueOf(id))) continue;
            SandboxCharacter c = characterMapper.selectById(id);
            if (c == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "角色不存在");
            if (!userId.equals(c.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
            // 一段聊天最多涉及两个世界观：角色必须来自主世界观或补充世界观
            if (!inWorld(c, primary.getId()) && (secondary == null || !inWorld(c, secondary.getId()))) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "角色「" + c.getName() + "」不属于所选的世界观；一段聊天最多涉及两个世界观");
            }
            kept.add(String.valueOf(id));
        }
        String mode = r.getMode() == null ? "" : r.getMode().trim();
        if (!SandboxChatSession.MODE_STORY.equals(mode) && !SandboxChatSession.MODE_ROLEPLAY.equals(mode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择故事模式或角色扮演模式");
        }
        String source = r.getApiSource() == null || r.getApiSource().trim().isEmpty() ? SandboxChatSession.SOURCE_SERVER : r.getApiSource().trim();
        if (!SandboxChatSession.SOURCE_SERVER.equals(source) && !SandboxChatSession.SOURCE_PERSONAL.equals(source)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "AI 来源无效");
        }
        x.setPrimaryWorldviewId(primary.getId());
        x.setSecondaryWorldviewId(secondary == null ? null : secondary.getId());
        x.setCharacterIds(String.join(",", kept));
        x.setMode(mode);
        x.setApiSource(source);
        if (r.getPrompt() != null && !r.getPrompt().trim().isEmpty()) {
            x.setModePrompt(validPrompt(r.getPrompt()));
            x.setPromptApplied(1);
        } else if (x.getPromptApplied() == null) {
            x.setPromptApplied(0);
        }
        // 去掉了补充世界观时，secondary 需要显式置空（updateById 会跳过 null）
        if (secondary == null && x.getId() != null) {
            sessionMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SandboxChatSession>()
                    .eq(SandboxChatSession::getId, x.getId()).eq(SandboxChatSession::getUserId, userId)
                    .set(SandboxChatSession::getSecondaryWorldviewId, null));
        }
    }

    // ------------------------------------------------------------------ 消息

    public List<SandboxChatMessage> listMessages(Long userId, Long sessionId) {
        ownedSession(userId, sessionId);
        return ordered(userId, sessionId);
    }

    /**
     * 发一条用户消息。generate 为 true（平台 AI）时生成并保存回复，返回 [用户消息, 回复各行…]；
     * 为 false（个人 API）时只保存用户消息，回复由设备生成后通过 saveReply 存回来。
     * 会话没有配置或没有应用提示词时拒绝，什么都不保存。
     */
    @Transactional
    public List<SandboxChatMessage> send(Long userId, Long sessionId, String content, boolean generate) {
        return send(userId, sessionId, content, generate, ChatGenOptions.DEFAULT);
    }

    @Transactional
    public List<SandboxChatMessage> send(Long userId, Long sessionId, String content, boolean generate, ChatGenOptions opts) {
        SandboxChatSession session = ownedSession(userId, sessionId);
        requireReady(session);
        String text = validMessage(content);
        List<SandboxChatMessage> history = ordered(userId, sessionId);
        long position = nextPosition(history);
        SandboxChatMessage user = row(session, "user", SandboxChatMessage.KIND_USER, null, null, text, position, null);
        messageMapper.insert(user);
        List<SandboxChatMessage> out = new ArrayList<SandboxChatMessage>();
        out.add(user);
        if (generate) {
            history.add(user);
            String reply = generateReply(userId, session, history, position + 1000, opts);
            out.addAll(insertReply(userId, session, reply, position + 1000, opts));
        }
        touch(session);
        return out;
    }

    /**
     * 个人 API 用：返回拼好的系统提示词和对话历史。beforeMessageId 不为空时只给到那条消息之前（重新生成它时用）。
     * 没有应用提示词时拒绝。
     */
    public Map<String, Object> context(Long userId, Long sessionId, Long beforeMessageId) {
        return context(userId, sessionId, beforeMessageId, ChatGenOptions.DEFAULT);
    }

    public Map<String, Object> context(Long userId, Long sessionId, Long beforeMessageId, ChatGenOptions opts) {
        SandboxChatSession session = ownedSession(userId, sessionId);
        requireReady(session);
        List<SandboxChatMessage> all = ordered(userId, sessionId);
        long target = beforeMessageId == null ? nextPosition(all) : positionOf(all, beforeMessageId);
        List<Map<String, String>> history = new ArrayList<Map<String, String>>();
        for (SandboxChatMessage m : historyBefore(all, target, opts.narration(), opts.contextLimit())) {
            Map<String, String> item = new LinkedHashMap<String, String>();
            item.put("role", m.getRole());
            item.put("content", m.getContent());
            history.add(item);
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("systemPrompt", systemPrompt(userId, session, target, opts));
        out.put("messages", history);
        return out;
    }

    /** 保存设备端（个人 API）生成的回复。replaceMessageId 不为空时替换那条回复所在的整次回复，位置不变 */
    @Transactional
    public List<SandboxChatMessage> saveReply(Long userId, Long sessionId, String content, Long replaceMessageId) {
        return saveReply(userId, sessionId, content, replaceMessageId, ChatGenOptions.DEFAULT);
    }

    @Transactional
    public List<SandboxChatMessage> saveReply(Long userId, Long sessionId, String content, Long replaceMessageId, ChatGenOptions opts) {
        SandboxChatSession session = ownedSession(userId, sessionId);
        requireReady(session);
        String text = validMessage(content);
        List<SandboxChatMessage> all = ordered(userId, sessionId);
        long position;
        if (replaceMessageId != null) {
            SandboxChatMessage target = assistantMessage(all, replaceMessageId);
            position = target.position();
            deleteGroup(all, target);
        } else {
            position = nextPosition(all);
        }
        List<SandboxChatMessage> out = insertReply(userId, session, text, position, opts);
        touch(session);
        return out;
    }

    /** 用平台 AI 重新生成选中的那次回复：只依据它之前的历史，替换它本身，后面的消息不动 */
    @Transactional
    public List<SandboxChatMessage> regenerate(Long userId, Long sessionId, Long messageId) {
        return regenerate(userId, sessionId, messageId, ChatGenOptions.DEFAULT);
    }

    @Transactional
    public List<SandboxChatMessage> regenerate(Long userId, Long sessionId, Long messageId, ChatGenOptions opts) {
        SandboxChatSession session = ownedSession(userId, sessionId);
        requireReady(session);
        List<SandboxChatMessage> all = ordered(userId, sessionId);
        SandboxChatMessage target = assistantMessage(all, messageId);
        long position = target.position();
        String reply = generateReply(userId, session, historyRows(all, position), position, opts);
        deleteGroup(all, target);
        List<SandboxChatMessage> out = insertReply(userId, session, reply, position, opts);
        touch(session);
        return out;
    }

    /** 修改一条用户消息或角色／旁白消息的文字。只改这一条；之后再生成时用改过的历史 */
    @Transactional
    public SandboxChatMessage editMessage(Long userId, Long messageId, String content) {
        validUser(userId);
        SandboxChatMessage m = messageId == null ? null : messageMapper.selectById(messageId);
        if (m == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "消息不存在");
        if (!userId.equals(m.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        m.setContent(validMessage(content));
        m.setEdited(1);
        messageMapper.updateById(m);
        return m;
    }

    /** 删除单条消息；同一次回复的其它行不动。临时记忆按位置生效，不受影响 */
    @Transactional
    public void deleteMessage(Long userId, Long messageId) {
        validUser(userId);
        SandboxChatMessage m = messageId == null ? null : messageMapper.selectById(messageId);
        if (m == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "消息不存在");
        if (!userId.equals(m.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        messageMapper.deleteById(m.getId());
    }

    // ------------------------------------------------------------------ 临时记忆

    public List<SandboxChatMemory> listMemories(Long userId, Long sessionId) {
        ownedSession(userId, sessionId);
        List<SandboxChatMemory> list = memoryMapper.selectList(new LambdaQueryWrapper<SandboxChatMemory>()
                .eq(SandboxChatMemory::getUserId, userId).eq(SandboxChatMemory::getSessionId, sessionId).orderByAsc(SandboxChatMemory::getId));
        return list == null ? new ArrayList<SandboxChatMemory>() : list;
    }

    /** 在某条消息的位置给某个角色加临时记忆；afterMessageId 为空表示从对话开头就生效 */
    @Transactional
    public SandboxChatMemory addMemory(Long userId, Long sessionId, Long characterId, String content, Long afterMessageId) {
        SandboxChatSession session = ownedSession(userId, sessionId);
        if (characterId == null || !selectedCharacterIds(session).contains(characterId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择这段聊天里的角色");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) throw new BusinessException(ResultCode.PARAM_ERROR, "记忆内容不能为空");
        if (text.length() > MEMORY_MAX) throw new BusinessException(ResultCode.PARAM_ERROR, "记忆内容不能超过 " + MEMORY_MAX + " 个字");
        SandboxChatMemory x = new SandboxChatMemory();
        x.setUserId(userId);
        x.setSessionId(session.getId());
        x.setCharacterId(characterId);
        x.setContent(text);
        x.setAfterSortKey(afterMessageId == null ? 0L : positionOf(ordered(userId, sessionId), afterMessageId));
        x.setCreatedAt(LocalDateTime.now());
        memoryMapper.insert(x);
        return x;
    }

    @Transactional
    public void deleteMemory(Long userId, Long memoryId) {
        validUser(userId);
        SandboxChatMemory x = memoryId == null ? null : memoryMapper.selectById(memoryId);
        if (x == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST);
        if (!userId.equals(x.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        memoryMapper.deleteById(x.getId());
    }

    // ------------------------------------------------------------------ 生成

    private String generateReply(Long userId, SandboxChatSession session, List<SandboxChatMessage> rowsBefore, long position, ChatGenOptions opts) {
        if (ai == null || !ai.isConfigured()) throw new BusinessException(ResultCode.BUSINESS_ERROR, "平台 AI 尚未配置，暂时不能生成回复");
        try {
            return ai.generateChat(systemPrompt(userId, session, position, opts), historyBefore(rowsBefore, position, opts.narration(), opts.contextLimit()),
                    opts.maxTokens(), opts.temperature(), opts.topP());
        } catch (DeepSeekClient.DeepSeekException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "AI 服务暂时不可用，请稍后重试");
        }
    }

    /** 把一次回复按【说话者】拆行保存：旁白一行，每个角色一行，同一次回复共用 replyGroup 和 sortKey */
    private List<SandboxChatMessage> insertReply(Long userId, SandboxChatSession session, String reply, long position, ChatGenOptions opts) {
        List<SandboxCharacter> selected = selectedCharacters(userId, session);
        List<SandboxChatMessage> out = new ArrayList<SandboxChatMessage>();
        Long group = null;
        List<String[]> segments = parseReply(reply, SandboxChatSession.MODE_ROLEPLAY.equals(session.getMode()) && selected.size() == 1 ? selected.get(0).getName() : null);
        if (!opts.narration()) segments = withoutNarration(segments);
        for (String[] seg : segments) {
            SandboxCharacter speaker = null;
            if (seg[0] != null) for (SandboxCharacter c : selected) if (seg[0].equals(c.getName() == null ? "" : c.getName().trim())) { speaker = c; break; }
            SandboxChatMessage m = row(session, "assistant", seg[0] == null ? SandboxChatMessage.KIND_NARRATION : SandboxChatMessage.KIND_CHARACTER,
                    speaker == null ? null : speaker.getId(), seg[0], seg[1], position, group);
            messageMapper.insert(m);
            if (group == null) {
                group = m.getId();
                m.setReplyGroup(group);
                messageMapper.updateById(m);
            }
            out.add(m);
        }
        return out;
    }

    /**
     * 回复拆成 [说话者, 文字]：以「【名字】」开头的一行开始一段，说话者为「旁白」或没有标记的是旁白（说话者为 null）。
     * 整段都没有标记时：defaultSpeaker 不为空（角色扮演且只选了一个角色）算作那个角色说的，否则算旁白。
     */
    static List<String[]> parseReply(String reply, String defaultSpeaker) {
        List<String[]> out = new ArrayList<String[]>();
        String speaker = null;
        StringBuilder body = new StringBuilder();
        boolean tagged = false;
        for (String line : (reply == null ? "" : reply).split("\\r?\\n")) {
            Matcher m = SPEAKER.matcher(line);
            if (m.matches()) {
                flush(out, speaker, body);
                tagged = true;
                String name = m.group(1).trim();
                speaker = "旁白".equals(name) || "叙述".equals(name) ? null : name;
                body.append(m.group(2));
            } else {
                if (body.length() > 0) body.append('\n');
                body.append(line);
            }
        }
        flush(out, speaker, body);
        if (!tagged && defaultSpeaker != null) for (String[] seg : out) seg[0] = defaultSpeaker;
        return out;
    }

    /** 关闭旁白时丢掉旁白段；整段回复都是旁白时保留原样，免得回复变成空的 */
    static List<String[]> withoutNarration(List<String[]> segments) {
        List<String[]> kept = new ArrayList<String[]>();
        for (String[] seg : segments) if (seg[0] != null) kept.add(seg);
        return kept.isEmpty() ? segments : kept;
    }

    private static void flush(List<String[]> out, String speaker, StringBuilder body) {
        String text = body.toString().trim();
        body.setLength(0);
        if (!text.isEmpty()) out.add(new String[]{speaker, text});
    }

    /** position 之前的消息行 */
    private static List<SandboxChatMessage> historyRows(List<SandboxChatMessage> all, long position) {
        List<SandboxChatMessage> out = new ArrayList<SandboxChatMessage>();
        for (SandboxChatMessage m : all) if (m.position() < position) out.add(m);
        return out;
    }

    /**
     * 给模型的对话历史：position 之前的消息，同一次回复的各行合并成一条 assistant 消息并带上说话者标记；
     * 旧版的模拟回复不算历史。超出长度时丢掉最早的。
     */
    static List<SandboxChatMessage> historyBefore(List<SandboxChatMessage> all, long position) {
        return historyBefore(all, position, true, 0);
    }

    /** narration = false 时历史里不带旁白行；contextLimit &gt; 0 时只保留最近这么多条（用户消息和回复各算一条） */
    static List<SandboxChatMessage> historyBefore(List<SandboxChatMessage> all, long position, boolean narration, int contextLimit) {
        List<SandboxChatMessage> turns = new ArrayList<SandboxChatMessage>();
        SandboxChatMessage open = null;
        long openKey = Long.MIN_VALUE;
        for (SandboxChatMessage m : historyRows(all, position)) {
            if (Integer.valueOf(1).equals(m.getMockReply())) continue;
            if ("user".equals(m.getRole())) {
                open = null;
                turns.add(copy("user", m.getContent()));
            } else if ("assistant".equals(m.getRole())) {
                if (!narration && SandboxChatMessage.KIND_NARRATION.equals(m.getKind())) continue;
                String line = (m.getKind() == null ? "" : "【" + (SandboxChatMessage.KIND_NARRATION.equals(m.getKind()) || m.getSpeakerName() == null ? "旁白" : m.getSpeakerName()) + "】") + m.getContent();
                long key = m.getReplyGroup() != null ? m.getReplyGroup() : -m.getId();
                if (open != null && key == openKey) open.setContent(open.getContent() + "\n" + line);
                else { open = copy("assistant", line); openKey = key; turns.add(open); }
            }
        }
        int from = Math.max(0, turns.size() - (contextLimit > 0 ? Math.min(contextLimit, HISTORY_TURNS_MAX) : HISTORY_TURNS_MAX));
        int chars = 0;
        int start = turns.size();
        for (int i = turns.size() - 1; i >= from; i--) {
            chars += turns.get(i).getContent().length();
            if (chars > HISTORY_CHARS_MAX && i < turns.size() - 1) break;
            start = i;
        }
        return new ArrayList<SandboxChatMessage>(turns.subList(start, turns.size()));
    }

    private static SandboxChatMessage copy(String role, String content) {
        SandboxChatMessage m = new SandboxChatMessage();
        m.setRole(role);
        m.setContent(content == null ? "" : content);
        m.setMockReply(0);
        return m;
    }

    /**
     * 系统提示词：模式要求、输出格式、主世界观和补充世界观的设定（正文、设定记录、世界时间线）、
     * 每个角色的资料、关系和时间线、用户应用的模式提示词、在 position 之前生效的临时记忆。
     */
    String systemPrompt(Long userId, SandboxChatSession session, long position) {
        return systemPrompt(userId, session, position, ChatGenOptions.DEFAULT);
    }

    String systemPrompt(Long userId, SandboxChatSession session, long position, ChatGenOptions opts) {
        List<SandboxCharacter> selected = selectedCharacters(userId, session);
        Map<Long, SandboxWorldview> worlds = new LinkedHashMap<Long, SandboxWorldview>();
        for (Long id : new Long[]{session.getPrimaryWorldviewId(), session.getSecondaryWorldviewId()}) {
            if (id == null) continue;
            SandboxWorldview w = worldviewMapper.selectById(id);
            if (w != null && userId.equals(w.getUserId())) worlds.put(id, w);
        }
        StringBuilder sb = new StringBuilder();
        boolean roleplay = SandboxChatSession.MODE_ROLEPLAY.equals(session.getMode());
        if (roleplay) {
            sb.append("你在进行角色扮演。请以下面所选角色的身份回应；用户以自己或其在会话设定里指定的身份发言。不要替用户说话或决定用户的行动。\n");
        } else {
            sb.append(opts.narration()
                    ? "你在续写一个故事。请按用户要求的情节继续，用旁白叙述并让所选角色对话。\n"
                    : "你在续写一个故事。请按用户要求的情节继续，只让所选角色开口对话和行动，不要写旁白。\n");
        }
        sb.append(opts.narration()
                ? "输出格式：每一段单独成行并以说话者开头——旁白写作「【旁白】……」，角色说的话写作「【角色名】……」，角色名必须是下面所选角色之一。\n"
                : "输出格式：每一段单独成行并以说话者开头——角色说的话写作「【角色名】……」，角色名必须是下面所选角色之一。不要输出「【旁白】」。\n");
        sb.append("只依据下面提供的设定；设定里没有的内容不要当作既定事实。\n\n");

        boolean first = true;
        for (SandboxWorldview w : worlds.values()) {
            sb.append(first ? "【主世界观】" : "【补充世界观】").append(w.getName()).append('\n');
            if (first && worlds.size() > 1) sb.append("故事发生在主世界观的环境里；来自补充世界观的角色保留其原世界观的设定。\n");
            appendLine(sb, "简介", cut(w.getDescription(), 600));
            appendLine(sb, "设定", cut(w.getContent(), WORLD_TEXT_MAX));
            List<SandboxRecord> records = recordService.listRecords(userId, w.getId(), null);
            int n = 0;
            for (SandboxRecord r : records == null ? new ArrayList<SandboxRecord>() : records) {
                if (n++ >= RECORDS_MAX) break;
                sb.append("· ").append(r.getName()).append("：").append(cut(r.getDescription(), RECORD_TEXT_MAX)).append('\n');
            }
            appendTimeline(sb, "世界时间线", timelineService.lines(userId, SandboxTimelineService.worldSlot(w.getId())));
            sb.append('\n');
            first = false;
        }

        Map<Long, String> names = new HashMap<Long, String>();
        for (SandboxCharacter c : selected) names.put(c.getId(), c.getName());
        List<SandboxRelation> relations = relationService.listRelations(userId);
        for (SandboxCharacter c : selected) {
            StringBuilder cb = new StringBuilder();
            SandboxWorldview home = worlds.get(c.getWorldviewId());
            cb.append("【角色】").append(c.getName());
            if (home != null) cb.append("（来自").append(home.getName()).append("）");
            cb.append('\n');
            appendLine(cb, "简介", c.getDescription());
            appendLine(cb, "性格", c.getPersonality());
            appendLine(cb, "背景", c.getBackground());
            appendSheet(cb, c.getContent(), c.getPersonality());
            for (SandboxRelation r : relations == null ? new ArrayList<SandboxRelation>() : relations) {
                boolean from = c.getId().equals(r.getFromCharacterId());
                boolean to = c.getId().equals(r.getToCharacterId());
                if (!from && !to) continue;
                String other = names.get(from ? r.getToCharacterId() : r.getFromCharacterId());
                if (other == null) continue;
                String label = from ? r.getLabel() : (SandboxRelation.TWO_WAY.equals(r.getDirection()) ? r.getReverseLabel() : null);
                String view = from ? r.getFromView() : r.getToView();
                boolean hasView = view != null && !view.trim().isEmpty();
                if (label == null || label.trim().isEmpty()) {
                    if (!from && !hasView) continue;
                    label = from ? "有关系" : "";
                }
                if (label.trim().isEmpty()) cb.append("关系：对").append(other);
                else cb.append("关系：对").append(other).append("——").append(label.trim());
                if (r.getNote() != null && !r.getNote().trim().isEmpty()) cb.append("（").append(cut(r.getNote(), 120)).append("）");
                cb.append('\n');
                // 这个角色眼中的对方：它自己的看法，不一定是事实
                if (hasView) cb.append("认知：").append(c.getName()).append("眼中的").append(other).append("——").append(cut(view.trim(), 200)).append('\n');
            }
            appendTimeline(cb, "经历", timelineService.lines(userId, SandboxTimelineService.characterSlot(c.getId())));
            sb.append(cut(cb.toString(), CHARACTER_TEXT_MAX)).append("\n\n");
        }

        // 用户写的会话设定放在后面并保证不被截断：世界规则冲突时以它为准
        StringBuilder tail = new StringBuilder();
        tail.append("【会话设定（用户填写；不同世界观的规则有冲突时以此为准）】\n").append(session.getModePrompt() == null ? "" : session.getModePrompt().trim()).append('\n');
        List<SandboxChatMemory> memories = memoryMapper.selectList(new LambdaQueryWrapper<SandboxChatMemory>()
                .eq(SandboxChatMemory::getUserId, userId).eq(SandboxChatMemory::getSessionId, session.getId()).orderByAsc(SandboxChatMemory::getId));
        boolean header = false;
        for (SandboxChatMemory m : memories == null ? new ArrayList<SandboxChatMemory>() : memories) {
            // 只对这个位置之后的生成有效
            if (m.getAfterSortKey() != null && m.getAfterSortKey() >= position) continue;
            String name = names.get(m.getCharacterId());
            if (name == null) continue;
            if (!header) { tail.append("【临时记忆（只在这段对话里有效）】\n"); header = true; }
            tail.append(name).append("：").append(m.getContent()).append('\n');
        }
        String head = sb.toString();
        int room = Math.max(2000, SYSTEM_PROMPT_MAX - tail.length());
        return (head.length() > room ? head.substring(0, room) + "\n（设定过长，后面的部分已省略）\n\n" : head) + tail;
    }

    private static final String[][] SHEET_FIELDS = {
            {"gender", "性别"}, {"era", "时代"}, {"birthday", "生日"}, {"profession", "职业"}, {"residence", "住地"}, {"hometown", "故乡"},
            {"appearanceDetail", "外貌"}, {"physicalCondition", "身体状态"}, {"scars", "伤疤"}, {"beliefs", "信念"}, {"traits", "性格"},
            {"habits", "习惯"}, {"meaningfulPlace", "意义非凡之地"}, {"treasure", "宝贵之物"}, {"livingStandard", "生活水平"}, {"spendingHabits", "消费习惯"}
    };
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /** 角色卡里填写过的各栏文字；角色卡的「性格」保存时同步写进 personality，和上面已写的性格相同就不重复 */
    private static void appendSheet(StringBuilder sb, String sheetContent, String personality) {
        if (sheetContent == null || sheetContent.trim().isEmpty()) return;
        try {
            com.fasterxml.jackson.databind.JsonNode sheet = JSON.readTree(sheetContent);
            if (sheet == null || !sheet.isObject()) return;
            for (String[] field : SHEET_FIELDS) {
                com.fasterxml.jackson.databind.JsonNode v = sheet.get(field[0]);
                if (v == null || !v.isTextual()) continue;
                if ("traits".equals(field[0]) && personality != null && v.asText().trim().equals(personality.trim())) continue;
                appendLine(sb, field[1], cut(v.asText(), 200));
            }
        } catch (Exception ignored) {
            // 角色卡不是 JSON（很旧的数据）：只用简介、性格、背景
        }
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        if (value != null && !value.trim().isEmpty()) sb.append(label).append("：").append(value.trim()).append('\n');
    }

    private static void appendTimeline(StringBuilder sb, String label, List<String> lines) {
        if (lines == null || lines.isEmpty()) return;
        sb.append(label).append("：\n");
        int n = 0;
        for (String line : lines) {
            if (n++ >= TIMELINE_LINES_MAX) { sb.append("  …（其余 ").append(lines.size() - TIMELINE_LINES_MAX).append(" 条省略）\n"); break; }
            sb.append("  ").append(cut(line, 200)).append('\n');
        }
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    // ------------------------------------------------------------------ 工具

    private void requireConfigured(SandboxChatSession x) {
        if (x.getMode() == null || x.getPrimaryWorldviewId() == null || x.getCharacterIds() == null || x.getCharacterIds().trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请先为这段聊天选择世界观、角色和模式");
        }
    }

    private void requireReady(SandboxChatSession x) {
        requireConfigured(x);
        if (!Integer.valueOf(1).equals(x.getPromptApplied()) || x.getModePrompt() == null || x.getModePrompt().trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请先填写并应用模式提示词");
        }
    }

    private static String validPrompt(String prompt) {
        String p = prompt == null ? "" : prompt.trim();
        if (p.isEmpty()) throw new BusinessException(ResultCode.PARAM_ERROR, "模式提示词不能为空");
        if (p.length() > PROMPT_MAX) throw new BusinessException(ResultCode.PARAM_ERROR, "模式提示词不能超过 " + PROMPT_MAX + " 个字");
        return p;
    }

    private static String validMessage(String content) {
        String t = content == null ? "" : content.trim();
        if (t.isEmpty()) throw new BusinessException(ResultCode.PARAM_ERROR, "消息不能为空");
        if (t.length() > MESSAGE_MAX) throw new BusinessException(ResultCode.PARAM_ERROR, "消息不能超过 " + MESSAGE_MAX + " 个字");
        return t;
    }

    private SandboxChatMessage row(SandboxChatSession session, String role, String kind, Long speakerId, String speakerName, String content, long position, Long group) {
        SandboxChatMessage m = new SandboxChatMessage();
        m.setSessionId(session.getId());
        m.setUserId(session.getUserId());
        m.setRole(role);
        m.setKind(kind);
        m.setSpeakerCharacterId(speakerId);
        m.setSpeakerName(speakerName);
        m.setContent(content);
        m.setMockReply(0);
        m.setSortKey(position);
        m.setReplyGroup(group);
        m.setEdited(0);
        m.setCreatedAt(LocalDateTime.now());
        return m;
    }

    private List<SandboxChatMessage> ordered(Long userId, Long sessionId) {
        List<SandboxChatMessage> list = messageMapper.selectList(new LambdaQueryWrapper<SandboxChatMessage>()
                .eq(SandboxChatMessage::getSessionId, sessionId).eq(SandboxChatMessage::getUserId, userId));
        List<SandboxChatMessage> out = list == null ? new ArrayList<SandboxChatMessage>() : new ArrayList<SandboxChatMessage>(list);
        Collections.sort(out, new Comparator<SandboxChatMessage>() {
            @Override public int compare(SandboxChatMessage a, SandboxChatMessage b) {
                int byPosition = Long.compare(a.position(), b.position());
                return byPosition != 0 ? byPosition : Long.compare(a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId());
            }
        });
        return out;
    }

    private static long nextPosition(List<SandboxChatMessage> ordered) {
        return ordered.isEmpty() ? 1000L : ordered.get(ordered.size() - 1).position() + 1000L;
    }

    private static long positionOf(List<SandboxChatMessage> ordered, Long messageId) {
        for (SandboxChatMessage m : ordered) if (m.getId() != null && m.getId().equals(messageId)) return m.position();
        throw new BusinessException(ResultCode.DATA_NOT_EXIST, "消息不存在");
    }

    private static SandboxChatMessage assistantMessage(List<SandboxChatMessage> ordered, Long messageId) {
        for (SandboxChatMessage m : ordered) {
            if (m.getId() != null && m.getId().equals(messageId)) {
                if (!"assistant".equals(m.getRole())) throw new BusinessException(ResultCode.PARAM_ERROR, "只能重新生成 AI 的回复");
                return m;
            }
        }
        throw new BusinessException(ResultCode.DATA_NOT_EXIST, "消息不存在");
    }

    /** 删除 target 所在的那一次回复的全部行（旧数据没有分组，只删它自己） */
    private void deleteGroup(List<SandboxChatMessage> ordered, SandboxChatMessage target) {
        for (SandboxChatMessage m : ordered) {
            boolean same = target.getReplyGroup() != null ? target.getReplyGroup().equals(m.getReplyGroup()) : m.getId().equals(target.getId());
            if (same) messageMapper.deleteById(m.getId());
        }
    }

    private void touch(SandboxChatSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    private static List<Long> selectedCharacterIds(SandboxChatSession session) {
        List<Long> out = new ArrayList<Long>();
        if (session.getCharacterIds() == null) return out;
        for (String part : session.getCharacterIds().split(",")) {
            try { out.add(Long.valueOf(part.trim())); } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    private List<SandboxCharacter> selectedCharacters(Long userId, SandboxChatSession session) {
        List<SandboxCharacter> out = new ArrayList<SandboxCharacter>();
        for (Long id : selectedCharacterIds(session)) {
            SandboxCharacter c = characterMapper.selectById(id);
            if (c != null && userId.equals(c.getUserId())) out.add(c);
        }
        return out;
    }

    private static boolean inWorld(SandboxCharacter c, Long worldviewId) {
        if (worldviewId.equals(c.getWorldviewId())) return true;
        if (c.getWorldviewIds() == null) return false;
        for (String part : c.getWorldviewIds().split(",")) if (part.trim().equals(String.valueOf(worldviewId))) return true;
        return false;
    }

    private SandboxChatSession ownedSession(Long userId, Long id) {
        validUser(userId);
        SandboxChatSession x = id == null ? null : sessionMapper.selectById(id);
        if (x == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "会话不存在");
        if (!userId.equals(x.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        return x;
    }

    private SandboxWorldview ownedWorldview(Long userId, Long id) {
        SandboxWorldview w = worldviewMapper.selectById(id);
        if (w == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "世界观不存在");
        if (!userId.equals(w.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        return w;
    }

    private static void validUser(Long userId) {
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
    }
}
