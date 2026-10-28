package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.entity.Tag;
import com.ugccommunity.backend.entity.UserPin;
import com.ugccommunity.backend.entity.UserTagBlock;
import com.ugccommunity.backend.entity.UserTagFavorite;
import com.ugccommunity.backend.entity.WorkTag;
import com.ugccommunity.backend.mapper.TagMapper;
import com.ugccommunity.backend.mapper.UserPinMapper;
import com.ugccommunity.backend.mapper.UserTagBlockMapper;
import com.ugccommunity.backend.mapper.UserTagFavoriteMapper;
import com.ugccommunity.backend.mapper.WorkTagMapper;
import com.ugccommunity.backend.vo.TagVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TagService {
    private final TagMapper tagMapper;
    private final WorkTagMapper workTagMapper;
    private final UserTagFavoriteMapper userTagFavoriteMapper;
    private final UserTagBlockMapper userTagBlockMapper;
    private final UserPinMapper userPinMapper;
    private final GrowthService growthService;
    private final SettingService settingService;

    public TagService(TagMapper tagMapper, WorkTagMapper workTagMapper, UserTagFavoriteMapper userTagFavoriteMapper, UserTagBlockMapper userTagBlockMapper, UserPinMapper userPinMapper, GrowthService growthService, SettingService settingService) {
        this.tagMapper = tagMapper;
        this.workTagMapper = workTagMapper;
        this.userTagFavoriteMapper = userTagFavoriteMapper;
        this.userTagBlockMapper = userTagBlockMapper;
        this.userPinMapper = userPinMapper;
        this.growthService = growthService;
        this.settingService = settingService;
    }

    public List<TagVO> hotTags() {
        return hotTags(null);
    }

    public List<TagVO> hotTags(Long currentUserId) {
        Set<Long> blockedIds = blockedTagIds(currentUserId);
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>()
                .eq(Tag::getIsDeleted, 0)
                .eq(Tag::getIsDisabled, 0);
        if (!blockedIds.isEmpty()) {
            wrapper.notIn(Tag::getId, blockedIds);
        }
        wrapper.orderByDesc(Tag::getUsageCount)
                .orderByAsc(Tag::getId)
                .last("limit 10");
        List<Tag> tags = tagMapper.selectList(wrapper);
        return toVOList(visibleTags(tags), currentUserId);
    }

    public List<TagVO> allTags() {
        return allTags(null);
    }

    public List<TagVO> allTags(Long currentUserId) {
        Set<Long> blockedIds = blockedTagIds(currentUserId);
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>()
                .eq(Tag::getIsDeleted, 0);
        if (!blockedIds.isEmpty()) {
            wrapper.notIn(Tag::getId, blockedIds);
        }
        wrapper.orderByDesc(Tag::getUsageCount)
                .orderByAsc(Tag::getId);
        List<Tag> tags = tagMapper.selectList(wrapper);
        return toVOList(visibleTags(tags), currentUserId);
    }

    public List<TagVO> favoriteTags(Long userId) {
        List<UserTagFavorite> favorites = userTagFavoriteMapper.selectList(new LambdaQueryWrapper<UserTagFavorite>()
                .eq(UserTagFavorite::getUserId, userId)
                .orderByDesc(UserTagFavorite::getCreatedAt));
        if (favorites.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> blockedIds = blockedTagIds(userId);
        List<Long> ids = new ArrayList<Long>();
        for (UserTagFavorite favorite : favorites) {
            if (!blockedIds.contains(favorite.getTagId())) {
                ids.add(favorite.getTagId());
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Tag> tags = tagMapper.selectBatchIds(ids);
        List<TagVO> result = new ArrayList<TagVO>();
        for (Tag tag : tags) {
            if ((tag.getIsDeleted() == null || tag.getIsDeleted() == 0) && !settingService.isNegativeTag(tag.getName()) && !blockedIds.contains(tag.getId())) {
                TagVO vo = toVO(tag, userId);
                vo.setFavorited(true);
                result.add(vo);
            }
        }
        return result;
    }

    public List<TagVO> mineTags(Long userId) {
        Set<Long> blockedIds = blockedTagIds(userId);
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>()
                .eq(Tag::getIsDeleted, 0)
                .eq(Tag::getCreatedBy, userId);
        if (!blockedIds.isEmpty()) {
            wrapper.notIn(Tag::getId, blockedIds);
        }
        wrapper.orderByDesc(Tag::getCreatedAt)
                .orderByAsc(Tag::getId);
        List<Tag> tags = tagMapper.selectList(wrapper);
        return toVOList(visibleTags(tags), userId);
    }

    private Set<Long> blockedTagIds(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        List<UserTagBlock> blocks = userTagBlockMapper.selectList(new LambdaQueryWrapper<UserTagBlock>()
                .eq(UserTagBlock::getUserId, userId));
        Set<Long> ids = new HashSet<Long>();
        for (UserTagBlock b : blocks) {
            if (b.getTagId() != null) {
                ids.add(b.getTagId());
            }
        }
        return ids;
    }

    public TagVO createUserTag(Long userId, String rawName) {
        String name = normalizeName(rawName);
        validateTagName(name);
        rejectNegativeTag(name);
        Tag existing = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getIsDeleted, 0).eq(Tag::getName, name).last("limit 1"));
        if (existing != null) {
            throw new BusinessException(ResultCode.DATA_EXIST, "Tag已存在");
        }
        Tag tag = new Tag();
        tag.setName(name);
        tag.setUsageCount(0);
        tag.setIsDisabled(0);
        tag.setIsDeleted(0);
        tag.setCreatedBy(userId);
        tagMapper.insert(tag);
        return toVO(tag, userId);
    }

    public Boolean toggleFavorite(Long userId, Long tagId) {
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null || tag.getIsDeleted() != null && tag.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "Tag不存在");
        }
        rejectNegativeTag(tag.getName());
        Long count = userTagFavoriteMapper.selectCount(new LambdaQueryWrapper<UserTagFavorite>()
                .eq(UserTagFavorite::getUserId, userId)
                .eq(UserTagFavorite::getTagId, tagId));
        if (count > 0) {
            userTagFavoriteMapper.delete(new LambdaQueryWrapper<UserTagFavorite>()
                    .eq(UserTagFavorite::getUserId, userId)
                    .eq(UserTagFavorite::getTagId, tagId));
            growthService.recordTagFavoriteCount(userId, favoriteCount(userId));
            return false;
        }
        // Favorite and block are mutually exclusive: favoriting a blocked tag means you no longer want it blocked (blocking also removes the favorite)
        userTagBlockMapper.delete(new LambdaQueryWrapper<UserTagBlock>()
                .eq(UserTagBlock::getUserId, userId)
                .eq(UserTagBlock::getTagId, tagId));
        UserTagFavorite favorite = new UserTagFavorite();
        favorite.setUserId(userId);
        favorite.setTagId(tagId);
        userTagFavoriteMapper.insert(favorite);
        growthService.recordTagFavoriteCount(userId, favoriteCount(userId));
        return true;
    }

    public List<TagVO> blockedTags(Long userId) {
        List<UserTagBlock> blocks = userTagBlockMapper.selectList(new LambdaQueryWrapper<UserTagBlock>()
                .eq(UserTagBlock::getUserId, userId)
                .orderByDesc(UserTagBlock::getCreatedAt));
        if (blocks.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<Long>();
        for (UserTagBlock block : blocks) {
            ids.add(block.getTagId());
        }
        List<Tag> tags = tagMapper.selectBatchIds(ids);
        List<TagVO> result = new ArrayList<TagVO>();
        for (Tag tag : tags) {
            if ((tag.getIsDeleted() == null || tag.getIsDeleted() == 0) && !settingService.isNegativeTag(tag.getName())) {
                result.add(toVO(tag, userId));
            }
        }
        return result;
    }

    public Boolean toggleBlock(Long userId, Long tagId) {
        return setBlock(userId, tagId, null);
    }

    /**
     * If "wanted" is null, toggle (legacy clients); otherwise set that state and do nothing if already in it. Returns whether the tag is blocked afterwards.
     * Previously it only toggled: with a stale client state (blocked elsewhere, another device, re-login) tapping "block" could unblock instead.
     */
    public Boolean setBlock(Long userId, Long tagId, Boolean wanted) {
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null || tag.getIsDeleted() != null && tag.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "Tag不存在");
        }
        Long count = userTagBlockMapper.selectCount(new LambdaQueryWrapper<UserTagBlock>()
                .eq(UserTagBlock::getUserId, userId)
                .eq(UserTagBlock::getTagId, tagId));
        boolean blockedNow = count > 0;
        boolean target = wanted == null ? !blockedNow : wanted;
        if (target == blockedNow) {
            return blockedNow;
        }
        if (blockedNow) {
            userTagBlockMapper.delete(new LambdaQueryWrapper<UserTagBlock>()
                    .eq(UserTagBlock::getUserId, userId)
                    .eq(UserTagBlock::getTagId, tagId));
            return false;
        }
        userTagFavoriteMapper.delete(new LambdaQueryWrapper<UserTagFavorite>()
                .eq(UserTagFavorite::getUserId, userId)
                .eq(UserTagFavorite::getTagId, tagId));
        UserTagBlock block = new UserTagBlock();
        block.setUserId(userId);
        block.setTagId(tagId);
        userTagBlockMapper.insert(block);
        return true;
    }

    private List<TagVO> toVOList(List<Tag> tags) {
        return toVOList(tags, null);
    }

    private List<TagVO> toVOList(List<Tag> tags, Long currentUserId) {
        List<TagVO> result = new ArrayList<TagVO>();
        for (Tag tag : tags) {
            result.add(toVO(tag, currentUserId));
        }
        return result;
    }

    public List<TagVO> tagsByWorkId(Long workId) {
        return tagsByWorkId(workId, null);
    }

    public List<TagVO> tagsByWorkId(Long workId, Long currentUserId) {
        List<WorkTag> workTags = workTagMapper.selectList(new LambdaQueryWrapper<WorkTag>().eq(WorkTag::getWorkId, workId));
        if (workTags.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> tagIds = new ArrayList<Long>();
        for (WorkTag workTag : workTags) {
            tagIds.add(workTag.getTagId());
        }
        List<Tag> tags = tagMapper.selectBatchIds(tagIds);
        List<TagVO> result = new ArrayList<TagVO>();
        for (Tag tag : tags) {
            if (tag.getIsDeleted() == null || tag.getIsDeleted() == 0) {
                result.add(toVO(tag, currentUserId));
            }
        }
        return result;
    }

    public List<Long> ensureTags(List<String> names) {
        return ensureTags(null, names);
    }

    public List<Long> ensureTags(Long userId, List<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<Long>();
        for (String raw : names) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String name = normalizeName(raw);
            validateTagName(name);
            rejectNegativeTag(name);
            Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getIsDeleted, 0)
                    .eq(Tag::getName, name)
                    .last("limit 1"));
            if (tag == null) {
                tag = new Tag();
                tag.setName(name);
                tag.setUsageCount(0);
                tag.setIsDisabled(0);
                tag.setIsDeleted(0);
                tag.setCreatedBy(userId);
                tagMapper.insert(tag);
            } else if (tag.getIsDisabled() != null && tag.getIsDisabled() != 0) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, tag.getIsDisabled() == 2 ? "tag正在审核中" : "tag已被禁用");
            }
            if (!ids.contains(tag.getId())) {
                ids.add(tag.getId());
            }
        }
        return ids;
    }

    public TagVO toVO(Tag tag) {
        return toVO(tag, null);
    }

    public TagVO toVO(Tag tag, Long currentUserId) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setUsageCount(tag.getUsageCount());
        vo.setDisabled(tag.getIsDisabled());
        // Return the real favorite state for the current user (it used to be hard-coded false, so clients could not show "favorited")
        vo.setFavorited(currentUserId != null && tag.getId() != null && userTagFavoriteMapper.selectCount(new LambdaQueryWrapper<UserTagFavorite>()
                .eq(UserTagFavorite::getUserId, currentUserId)
                .eq(UserTagFavorite::getTagId, tag.getId())) > 0);
        vo.setPinned(isPinned(currentUserId, tag.getId()));
        return vo;
    }

    private boolean isPinned(Long userId, Long tagId) {
        return userId != null && tagId != null && userPinMapper.selectCount(new LambdaQueryWrapper<UserPin>()
                .eq(UserPin::getUserId, userId)
                .eq(UserPin::getTargetType, "tag")
                .eq(UserPin::getTargetId, tagId)) > 0;
    }

    private int favoriteCount(Long userId) {
        return userTagFavoriteMapper.selectCount(new LambdaQueryWrapper<UserTagFavorite>()
                .eq(UserTagFavorite::getUserId, userId)).intValue();
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().replace("#", "");
    }

    private void validateTagName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Tag名称不能为空");
        }
        if (name.length() > 30) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Tag名称不能超过30个字符");
        }
        if (!name.matches("[\\u4e00-\\u9fa5A-Za-z0-9_]+")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Tag仅支持中文、英文、数字和下划线");
        }
    }

    private List<Tag> visibleTags(List<Tag> tags) {
        if (!settingService.negativeTagFilterActive()) return tags;
        List<Tag> visible = new ArrayList<Tag>();
        for (Tag tag : tags) if (tag != null && !settingService.isNegativeTag(tag.getName())) visible.add(tag);
        return visible;
    }

    private void rejectNegativeTag(String name) {
        if (settingService.isNegativeTag(name)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "当前未配置内容 API，该标签暂不可使用");
        }
    }
}
