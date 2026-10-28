package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ugccommunity.backend.entity.Message;
import com.ugccommunity.backend.mapper.MessageMapper;
import com.ugccommunity.backend.vo.MessageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class MessageService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final MessageMapper messageMapper;
    private final GrowthService growthService;

    public MessageService(MessageMapper messageMapper, GrowthService growthService) {
        this.messageMapper = messageMapper;
        this.growthService = growthService;
    }

    public List<MessageVO> list(Long userId) {
        List<Message> messages = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .orderByAsc(Message::getIsRead)
                .orderByDesc(Message::getCreatedAt));
        List<MessageVO> result = new ArrayList<MessageVO>();
        for (Message message : messages) {
            result.add(toVO(message));
        }
        return result;
    }

    public Integer unreadCount(Long userId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<Message>().eq(Message::getUserId, userId).eq(Message::getIsRead, 0)).intValue();
    }

    @Transactional(rollbackFor = Exception.class)
    public void readAll(Long userId) {
        messageMapper.update(null, new LambdaUpdateWrapper<Message>().eq(Message::getUserId, userId).set(Message::getIsRead, 1));
        if (growthService != null) {
            growthService.recordMessageVisit(userId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void readOne(Long userId, Long messageId) {
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getId, messageId)
                .set(Message::getIsRead, 1));
        if (growthService != null) {
            growthService.recordMessageVisit(userId);
        }
    }

    private MessageVO toVO(Message message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setType(message.getType());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setRead(message.getIsRead());
        vo.setCreatedAt(message.getCreatedAt() == null ? null : message.getCreatedAt().format(FORMATTER));
        return vo;
    }
}
