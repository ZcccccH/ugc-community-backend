package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.entity.UserFeedback;
import com.ugccommunity.backend.mapper.UserFeedbackMapper;
import com.ugccommunity.backend.vo.FeedbackVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class FeedbackService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final UserFeedbackMapper feedbackMapper;

    public FeedbackService(UserFeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    public List<FeedbackVO> mine(Long userId) {
        List<UserFeedback> rows = feedbackMapper.selectList(new LambdaQueryWrapper<UserFeedback>()
                .eq(UserFeedback::getUserId, userId)
                .eq(UserFeedback::getIsDeleted, 0)
                .orderByDesc(UserFeedback::getCreatedAt));
        List<FeedbackVO> result = new ArrayList<FeedbackVO>();
        for (UserFeedback row : rows) {
            result.add(toVO(row));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public FeedbackVO create(Long userId, String content) {
        String text = content == null ? "" : content.trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "反馈内容不能为空");
        }
        if (text.length() > 500) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "反馈内容不能超过500字");
        }
        // Identical content from the same user within 1 minute is treated as a duplicate (double taps, weak-network retries); return the existing record
        UserFeedback recent = feedbackMapper.selectOne(new LambdaQueryWrapper<UserFeedback>()
                .eq(UserFeedback::getUserId, userId)
                .eq(UserFeedback::getContent, text)
                .ge(UserFeedback::getCreatedAt, LocalDateTime.now().minusMinutes(1))
                .orderByDesc(UserFeedback::getId)
                .last("limit 1"));
        if (recent != null) {
            return toVO(recent);
        }
        UserFeedback feedback = new UserFeedback();
        feedback.setUserId(userId);
        feedback.setContent(text);
        feedback.setStatus("pending");
        feedback.setIsDeleted(0);
        feedback.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(feedback);
        return toVO(feedback);
    }

    private FeedbackVO toVO(UserFeedback feedback) {
        FeedbackVO vo = new FeedbackVO();
        vo.setId(feedback.getId());
        vo.setContent(feedback.getContent());
        vo.setStatus(feedback.getStatus());
        vo.setReply(feedback.getReply());
        vo.setCreatedAt(feedback.getCreatedAt() == null ? null : feedback.getCreatedAt().format(FORMATTER));
        vo.setHandledAt(feedback.getHandledAt() == null ? null : feedback.getHandledAt().format(FORMATTER));
        return vo;
    }
}
