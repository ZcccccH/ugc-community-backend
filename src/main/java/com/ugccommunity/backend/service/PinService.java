package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.entity.Tag;
import com.ugccommunity.backend.entity.User;
import com.ugccommunity.backend.entity.UserPin;
import com.ugccommunity.backend.entity.Work;
import com.ugccommunity.backend.mapper.TagMapper;
import com.ugccommunity.backend.mapper.UserMapper;
import com.ugccommunity.backend.mapper.UserPinMapper;
import com.ugccommunity.backend.mapper.WorkMapper;
import com.ugccommunity.backend.vo.PinVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class PinService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_WORK_PIN_COUNT = 3;

    private final UserPinMapper userPinMapper;
    private final TagMapper tagMapper;
    private final UserMapper userMapper;
    private final WorkMapper workMapper;
    private final TagService tagService;
    private final UserService userService;
    private final WorkService workService;

    public PinService(
            UserPinMapper userPinMapper,
            TagMapper tagMapper,
            UserMapper userMapper,
            WorkMapper workMapper,
            TagService tagService,
            UserService userService,
            WorkService workService
    ) {
        this.userPinMapper = userPinMapper;
        this.tagMapper = tagMapper;
        this.userMapper = userMapper;
        this.workMapper = workMapper;
        this.tagService = tagService;
        this.userService = userService;
        this.workService = workService;
    }

    public List<PinVO> list(Long userId, String rawType) {
        String type = normalizeType(rawType);
        List<UserPin> pins = userPinMapper.selectList(new LambdaQueryWrapper<UserPin>()
                .eq(UserPin::getUserId, userId)
                .eq(UserPin::getTargetType, type)
                .orderByAsc(UserPin::getSortOrder)
                .orderByDesc(UserPin::getCreatedAt));
        List<PinVO> result = new ArrayList<PinVO>();
        for (UserPin pin : pins) {
            PinVO vo = toVO(pin, userId);
            if (vo != null) {
                result.add(vo);
            }
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public PinVO pin(Long userId, String rawType, Long targetId) {
        String type = normalizeType(rawType);
        validateTarget(userId, type, targetId);
        UserPin existing = userPinMapper.selectOne(new LambdaQueryWrapper<UserPin>()
                .eq(UserPin::getUserId, userId)
                .eq(UserPin::getTargetType, type)
                .eq(UserPin::getTargetId, targetId)
                .last("limit 1"));
        if (existing != null) {
            return toVO(existing, userId);
        }
        if ("work".equals(type)) {
            long pinnedWorkCount = validPinnedWorkCount(userId);
            if (pinnedWorkCount >= MAX_WORK_PIN_COUNT) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "每个账号最多只能置顶 3 个作品");
            }
        }
        UserPin pin = new UserPin();
        pin.setUserId(userId);
        pin.setTargetType(type);
        pin.setTargetId(targetId);
        pin.setSortOrder(0);
        pin.setCreatedAt(LocalDateTime.now());
        userPinMapper.insert(pin);
        return toVO(pin, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unpin(Long userId, String rawType, Long targetId) {
        String type = normalizeType(rawType);
        userPinMapper.delete(new LambdaQueryWrapper<UserPin>()
                .eq(UserPin::getUserId, userId)
                .eq(UserPin::getTargetType, type)
                .eq(UserPin::getTargetId, targetId));
    }

    public boolean isPinned(Long userId, String rawType, Long targetId) {
        if (userId == null || targetId == null) {
            return false;
        }
        String type = normalizeType(rawType);
        return userPinMapper.selectCount(new LambdaQueryWrapper<UserPin>()
                .eq(UserPin::getUserId, userId)
                .eq(UserPin::getTargetType, type)
                .eq(UserPin::getTargetId, targetId)) > 0;
    }

    private PinVO toVO(UserPin pin, Long currentUserId) {
        PinVO vo = new PinVO();
        vo.setTargetType(pin.getTargetType());
        vo.setTargetId(pin.getTargetId());
        vo.setSortOrder(pin.getSortOrder() == null ? 0 : pin.getSortOrder());
        vo.setCreatedAt(pin.getCreatedAt() == null ? null : pin.getCreatedAt().format(FORMATTER));
        if ("tag".equals(pin.getTargetType())) {
            Tag tag = tagMapper.selectById(pin.getTargetId());
            if (tag == null || tag.getIsDeleted() != null && tag.getIsDeleted() == 1) return null;
            vo.setTag(tagService.toVO(tag, currentUserId));
        } else if ("user".equals(pin.getTargetType())) {
            User user = userMapper.selectById(pin.getTargetId());
            if (user == null || user.getDeleted() != null && user.getDeleted() == 1) return null;
            vo.setUser(userService.toVO(user, currentUserId));
        } else if ("work".equals(pin.getTargetType())) {
            Work work = workMapper.selectById(pin.getTargetId());
            if (!isValidPinnedWork(work)) return null;
            vo.setWork(workService.toVO(work, currentUserId));
        }
        return vo;
    }

    private long validPinnedWorkCount(Long userId) {
        List<UserPin> pins = userPinMapper.selectList(new LambdaQueryWrapper<UserPin>()
                .eq(UserPin::getUserId, userId)
                .eq(UserPin::getTargetType, "work"));
        long count = 0;
        for (UserPin pin : pins) {
            Work work = pin.getTargetId() == null ? null : workMapper.selectById(pin.getTargetId());
            if (isValidPinnedWork(work)) {
                count++;
            }
        }
        return count;
    }

    private boolean isValidPinnedWork(Work work) {
        return work != null
                && (work.getIsDeleted() == null || work.getIsDeleted() == 0)
                && !"deleted".equals(work.getStatus());
    }

    private void validateTarget(Long userId, String type, Long targetId) {
        if (targetId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "固定目标不能为空");
        }
        if ("tag".equals(type)) {
            Tag tag = tagMapper.selectById(targetId);
            if (tag == null || tag.getIsDeleted() != null && tag.getIsDeleted() == 1) {
                throw new BusinessException(ResultCode.DATA_NOT_EXIST, "Tag不存在");
            }
        } else if ("user".equals(type)) {
            User user = userMapper.selectById(targetId);
            if (user == null || user.getDeleted() != null && user.getDeleted() == 1) {
                throw new BusinessException(ResultCode.DATA_NOT_EXIST, "用户不存在");
            }
        } else if ("work".equals(type)) {
            Work work = workMapper.selectById(targetId);
            if (!isValidPinnedWork(work)) {
                throw new BusinessException(ResultCode.DATA_NOT_EXIST, "作品不存在");
            }
            if (work.getUserId() == null || !work.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "只能置顶自己的作品");
            }
        }
    }

    private String normalizeType(String rawType) {
        String type = rawType == null ? "" : rawType.trim().toLowerCase();
        if (!"tag".equals(type) && !"user".equals(type) && !"work".equals(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "固定类型只能是tag/user/work");
        }
        return type;
    }
}
