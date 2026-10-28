package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.PageResult;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.dto.request.PublishWorkRequest;
import com.ugccommunity.backend.entity.*;
import com.ugccommunity.backend.mapper.*;
import com.ugccommunity.backend.vo.WorkVO;
import com.ugccommunity.backend.vo.CommentVO;
import com.ugccommunity.backend.vo.PaidUnlockEarningsVO;
import com.ugccommunity.backend.vo.PaidUnlockWorkEarningVO;
import com.ugccommunity.backend.vo.TagVO;
import com.ugccommunity.backend.vo.UserVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WorkService {
    /**
     * 外部存储（七牛）的地址失效时换成本站 /uploads/ 地址（本地一定留着一份）。
     * setter 注入，现有构造参数不变；没注入（单元测试）时原样返回。
     */
    private com.ugccommunity.backend.common.upload.LocalUploadResolver localUploads;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLocalUploads(com.ugccommunity.backend.common.upload.LocalUploadResolver localUploads) { this.localUploads = localUploads; }
    private String local(String url) { return localUploads == null ? url : localUploads.preferLocal(url); }
    /** 逗号分隔的多个地址逐个换 */
    private String localList(String urls) {
        if (urls == null || localUploads == null || !urls.contains("http")) return urls;
        StringBuilder sb = new StringBuilder();
        for (String part : urls.split(",")) { if (sb.length() > 0) sb.append(','); sb.append(local(part.trim())); }
        return sb.toString();
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WorkMapper workMapper;
    private final UserMapper userMapper;
    private final UserLikeMapper userLikeMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final UserFollowMapper userFollowMapper;
    private final WorkTagMapper workTagMapper;
    private final TagMapper tagMapper;
    private final BanWordMapper banWordMapper;
    private final MessageMapper messageMapper;
    private final ReportMapper reportMapper;
    private final WorkCommentMapper workCommentMapper;
    private final UserViewHistoryMapper userViewHistoryMapper;
    private final UserTagBlockMapper userTagBlockMapper;
    private final UserBlockMapper userBlockMapper;
    private final WorkCollectionMapper workCollectionMapper;
    private final WorkCollectionItemMapper workCollectionItemMapper;
    private final UserPinMapper userPinMapper;
    private final UserWorkUnlockMapper userWorkUnlockMapper;
    private final UserService userService;
    private final TagService tagService;
    private final WorkCollectionService collectionService;
    private final GrowthService growthService;
    private final FavoriteCollectionService favoriteCollectionService;
    private final SettingService settingService;

    public WorkService(
            WorkMapper workMapper,
            UserMapper userMapper,
            UserLikeMapper userLikeMapper,
            UserFavoriteMapper userFavoriteMapper,
            UserFollowMapper userFollowMapper,
            WorkTagMapper workTagMapper,
            TagMapper tagMapper,
            BanWordMapper banWordMapper,
            MessageMapper messageMapper,
            ReportMapper reportMapper,
            WorkCommentMapper workCommentMapper,
            UserViewHistoryMapper userViewHistoryMapper,
            UserTagBlockMapper userTagBlockMapper,
            UserBlockMapper userBlockMapper,
            WorkCollectionMapper workCollectionMapper,
            WorkCollectionItemMapper workCollectionItemMapper,
            UserPinMapper userPinMapper,
            UserWorkUnlockMapper userWorkUnlockMapper,
            UserService userService,
            TagService tagService,
            WorkCollectionService collectionService,
            GrowthService growthService,
            FavoriteCollectionService favoriteCollectionService,
            SettingService settingService
    ) {
        this.workMapper = workMapper;
        this.userMapper = userMapper;
        this.userLikeMapper = userLikeMapper;
        this.userFavoriteMapper = userFavoriteMapper;
        this.userFollowMapper = userFollowMapper;
        this.workTagMapper = workTagMapper;
        this.tagMapper = tagMapper;
        this.banWordMapper = banWordMapper;
        this.messageMapper = messageMapper;
        this.reportMapper = reportMapper;
        this.workCommentMapper = workCommentMapper;
        this.userViewHistoryMapper = userViewHistoryMapper;
        this.userTagBlockMapper = userTagBlockMapper;
        this.userBlockMapper = userBlockMapper;
        this.workCollectionMapper = workCollectionMapper;
        this.workCollectionItemMapper = workCollectionItemMapper;
        this.userPinMapper = userPinMapper;
        this.userWorkUnlockMapper = userWorkUnlockMapper;
        this.userService = userService;
        this.tagService = tagService;
        this.collectionService = collectionService;
        this.growthService = growthService;
        this.favoriteCollectionService = favoriteCollectionService;
        this.settingService = settingService;
    }

    public PageResult<WorkVO> page(Long currentUserId, Integer pageNum, Integer pageSize, String type, String keyword, String tag, String sort) {
        Page<Work> page = new Page<Work>(safePageNum(pageNum), safePageSize(pageSize));
        LambdaQueryWrapper<Work> wrapper = new LambdaQueryWrapper<Work>()
                .eq(Work::getStatus, "published")
                .eq(Work::getIsDeleted, 0)
                .ne(Work::getType, "video");
        applyWorkSort(wrapper, sort);
        if (StringUtils.hasText(type) && !"all".equalsIgnoreCase(type)) {
            wrapper.eq(Work::getType, type);
        }
        applyBlockedTagFilter(wrapper, currentUserId);
        applyBlockedUserFilter(wrapper, currentUserId);
        if (StringUtils.hasText(keyword)) {
            String search = keyword.trim().replace("#", "");
            List<Tag> matchedTags = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getIsDeleted, 0)
                    .eq(Tag::getIsDisabled, 0)
                    .like(Tag::getName, search));
            if (currentUserId != null && !matchedTags.isEmpty()) {
                List<UserTagBlock> userTagBlocks = userTagBlockMapper.selectList(new LambdaQueryWrapper<UserTagBlock>()
                        .eq(UserTagBlock::getUserId, currentUserId));
                Set<Long> blockedTagIds = new HashSet<Long>();
                for (UserTagBlock b : userTagBlocks) {
                    if (b.getTagId() != null) blockedTagIds.add(b.getTagId());
                }
                matchedTags.removeIf(t -> blockedTagIds.contains(t.getId()));
            }
            List<Long> keywordWorkIds = new ArrayList<Long>();
            if (!matchedTags.isEmpty()) {
                List<Long> tagIds = new ArrayList<Long>();
                for (Tag matchedTag : matchedTags) {
                    tagIds.add(matchedTag.getId());
                }
                List<WorkTag> matchedWorkTags = workTagMapper.selectList(new LambdaQueryWrapper<WorkTag>().in(WorkTag::getTagId, tagIds));
                for (WorkTag workTag : matchedWorkTags) {
                    if (!keywordWorkIds.contains(workTag.getWorkId())) {
                        keywordWorkIds.add(workTag.getWorkId());
                    }
                }
            }
            wrapper.and(w -> {
                w.like(Work::getTitle, keyword).or().like(Work::getContent, keyword);
                if (!keywordWorkIds.isEmpty()) {
                    w.or().in(Work::getId, keywordWorkIds);
                }
            });
        }
        if (StringUtils.hasText(tag)) {
            Tag targetTag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getName, tag.replace("#", "").trim())
                    .eq(Tag::getIsDisabled, 0)
                    .last("limit 1"));
            if (targetTag == null) {
                return PageResult.of(0L, safePageNum(pageNum), safePageSize(pageSize), new ArrayList<WorkVO>());
            }
            List<WorkTag> workTags = workTagMapper.selectList(new LambdaQueryWrapper<WorkTag>().eq(WorkTag::getTagId, targetTag.getId()));
            if (workTags.isEmpty()) {
                return PageResult.of(0L, safePageNum(pageNum), safePageSize(pageSize), new ArrayList<WorkVO>());
            }
            List<Long> workIds = new ArrayList<Long>();
            for (WorkTag workTag : workTags) {
                workIds.add(workTag.getWorkId());
            }
            wrapper.in(Work::getId, workIds);
        }
        Page<Work> result = workMapper.selectPage(page, wrapper);
        List<WorkVO> records = toListVOs(result.getRecords(), currentUserId);
        return PageResult.of(result.getTotal(), safePageNum(pageNum), safePageSize(pageSize), records);
    }

    private void applyWorkSort(LambdaQueryWrapper<Work> wrapper, String sort) {
        if ("hot".equalsIgnoreCase(sort)) {
            wrapper
                    .orderByDesc(Work::getViewCount)
                    .orderByDesc(Work::getLikeCount)
                    .orderByDesc(Work::getFavCount)
                    .orderByDesc(Work::getCreatedAt);
            return;
        }
        wrapper.orderByDesc(Work::getCreatedAt);
    }

    /** 打开作品详情（GET /works/{id}）：唯一会增加浏览数、写入足迹的入口 */
    public WorkVO detail(Long currentUserId, Long id) {
        Work work = requireViewable(currentUserId, id);
        try {
            workMapper.update(null, new LambdaUpdateWrapper<Work>()
                    .eq(Work::getId, id)
                    .setSql("view_count = view_count + 1"));
        } catch (RuntimeException ignored) {
            // 浏览数是详情页的非核心写入，失败时不应阻塞作品展示。
        }
        try {
            recordViewHistory(currentUserId, id);
        } catch (RuntimeException ignored) {
            // 足迹记录存在唯一键冲突时，详情 GET 仍应正常返回。
        }
        return withCollections(toVO(work, currentUserId), currentUserId);
    }

    /**
     * 作品详情里的「所属合辑」。只在单篇作品的接口（详情、点赞/收藏等返回的 view）里查；
     * 列表、足迹、置顶用的 toVO / toListVOs 不查（每篇要多查几次表），collections 为空。
     */
    private WorkVO withCollections(WorkVO vo, Long currentUserId) {
        vo.setCollections(collectionService.collectionsForWork(vo.getId(), currentUserId));
        return vo;
    }

    /**
     * 点赞、收藏、评论、解锁等操作里取作品：同样的可见性校验，但不加浏览数、不写足迹。
     * 原来这些操作都调 detail()，在首页点个赞就会刷浏览数，还把没打开过的作品记进足迹。
     */
    // 包内可见：单元测试里用 spy 替换掉组装 VO 的查询
    WorkVO view(Long currentUserId, Long id) {
        return withCollections(toVO(requireViewable(currentUserId, id), currentUserId), currentUserId);
    }

    private Work requireViewable(Long currentUserId, Long id) {
        Work work = workMapper.selectOne(new LambdaQueryWrapper<Work>()
                .eq(Work::getId, id)
                .eq(Work::getIsDeleted, 0)
                .last("limit 1"));
        if (work == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "作品不存在");
        }
        if ("video".equalsIgnoreCase(work.getType())) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "作品不存在");
        }
        if (!"published".equals(work.getStatus()) && (currentUserId == null || !currentUserId.equals(work.getUserId()))) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "作品审核通过后才可查看");
        }
        if (isBlockedWorkForUser(currentUserId, work)) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "作品不存在");
        }
        return work;
    }
    public List<WorkVO> history(Long currentUserId) {
        List<UserViewHistory> rows = userViewHistoryMapper.selectList(new LambdaQueryWrapper<UserViewHistory>()
                .eq(UserViewHistory::getUserId, currentUserId)
                .orderByDesc(UserViewHistory::getViewedAt)
                .last("limit 100"));
        List<WorkVO> result = new ArrayList<WorkVO>();
        List<Long> blockedAuthorIds = blockedUserIds(currentUserId);
        List<Long> blockedWorkIds = blockedWorkIds(currentUserId);
        for (UserViewHistory row : rows) {
            Work work = workMapper.selectOne(new LambdaQueryWrapper<Work>()
                    .eq(Work::getId, row.getWorkId())
                    .eq(Work::getIsDeleted, 0)
                    .ne(Work::getStatus, "deleted")
                    .ne(Work::getType, "video")
                    .last("limit 1"));
            // 别人的作品被隐藏或重新进入审核后不在足迹里显示：点进去只会提示「审核通过后才可查看」
            boolean visible = "published".equals(work == null ? null : work.getStatus()) || work != null && currentUserId != null && currentUserId.equals(work.getUserId());
            if (work != null && visible && !blockedAuthorIds.contains(work.getUserId()) && !blockedWorkIds.contains(work.getId())) {
                result.add(toVO(work, currentUserId));
            }
        }
        return result;
    }

    public PageResult<WorkVO> userWorks(Long currentUserId, Long userId, String tab, String type, Integer pageNum, Integer pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        List<Long> ids = null;
        boolean ownSpace = currentUserId != null && currentUserId.equals(userId);
        boolean hiddenTab = "hidden".equals(tab);
        if (hiddenTab && !ownSpace) {
            return PageResult.of(0L, safePageNum, safePageSize, new ArrayList<WorkVO>());
        }
        if ("favorites".equals(tab)) {
            List<UserFavorite> favorites = userFavoriteMapper.selectList(new LambdaQueryWrapper<UserFavorite>().eq(UserFavorite::getUserId, userId));
            ids = new ArrayList<Long>();
            for (UserFavorite favorite : favorites) {
                ids.add(favorite.getWorkId());
            }
            if (ids.isEmpty()) {
                return PageResult.of(0L, safePageNum, safePageSize, new ArrayList<WorkVO>());
            }
        } else if ("likes".equals(tab)) {
            List<UserLike> likes = userLikeMapper.selectList(new LambdaQueryWrapper<UserLike>().eq(UserLike::getUserId, userId));
            ids = new ArrayList<Long>();
            for (UserLike like : likes) {
                ids.add(like.getWorkId());
            }
            if (ids.isEmpty()) {
                return PageResult.of(0L, safePageNum, safePageSize, new ArrayList<WorkVO>());
            }
        }
        List<Long> pinnedWorkIds = ids == null ? pinnedWorkIdsForUser(userId) : new ArrayList<Long>();
        LambdaQueryWrapper<Work> wrapper = new LambdaQueryWrapper<Work>()
                .eq(Work::getIsDeleted, 0)
                .ne(Work::getType, "video");
        if (hiddenTab) {
            wrapper.eq(Work::getStatus, "hidden");
        } else if (ids == null && ownSpace) {
            wrapper.ne(Work::getStatus, "deleted")
                    .ne(Work::getStatus, "hidden");
        } else {
            wrapper.eq(Work::getStatus, "published");
        }
        if (ids != null) {
            wrapper.in(Work::getId, ids);
        } else {
            wrapper.eq(Work::getUserId, userId);
        }
        if (StringUtils.hasText(type) && !"all".equalsIgnoreCase(type)) {
            wrapper.eq(Work::getType, type);
        }
        applyBlockedTagFilter(wrapper, currentUserId);
        applyBlockedUserFilter(wrapper, currentUserId);
        if (ids == null && !pinnedWorkIds.isEmpty()) {
            String joinedIds = joinLongIds(pinnedWorkIds);
            wrapper.last("order by case when id in (" + joinedIds + ") then 0 else 1 end, field(id, " + joinedIds + "), created_at desc");
        } else {
            wrapper.orderByDesc(Work::getCreatedAt);
        }
        Page<Work> page = workMapper.selectPage(new Page<Work>(safePageNum, safePageSize), wrapper);
        List<WorkVO> records = toListVOs(page.getRecords(), currentUserId);
        if (ids == null && !pinnedWorkIds.isEmpty()) {
            Set<Long> pinnedSet = new LinkedHashSet<Long>(pinnedWorkIds);
            for (WorkVO vo : records) {
                vo.setPinned(pinnedSet.contains(vo.getId()));
            }
        }
        return PageResult.of(page.getTotal(), safePageNum, safePageSize, records);
    }

    public List<WorkVO> followingFeed(Long currentUserId, Long authorId, Integer pageSize) {
        int safePageSize = Math.min(safePageSize(pageSize), 50);
        List<UserFollow> follows = userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getUserId, currentUserId));
        if (follows.isEmpty()) {
            return new ArrayList<WorkVO>();
        }
        List<Long> followedIds = new ArrayList<Long>();
        for (UserFollow follow : follows) {
            if (follow.getTargetUserId() != null && !followedIds.contains(follow.getTargetUserId())) {
                followedIds.add(follow.getTargetUserId());
            }
        }
        if (followedIds.isEmpty()) {
            return new ArrayList<WorkVO>();
        }
        if (authorId != null) {
            if (!followedIds.contains(authorId)) {
                return new ArrayList<WorkVO>();
            }
            followedIds.clear();
            followedIds.add(authorId);
        }
        LambdaQueryWrapper<Work> wrapper = new LambdaQueryWrapper<Work>()
                .eq(Work::getStatus, "published")
                .eq(Work::getIsDeleted, 0)
                .ne(Work::getType, "video")
                .in(Work::getUserId, followedIds)
                .orderByDesc(Work::getCreatedAt)
                .last("limit " + safePageSize);
        applyBlockedTagFilter(wrapper, currentUserId);
        applyBlockedUserFilter(wrapper, currentUserId);
        return toListVOs(workMapper.selectList(wrapper), currentUserId);
    }

    private List<Long> pinnedWorkIdsForUser(Long userId) {
        List<Long> result = new ArrayList<Long>();
        if (userId == null) {
            return result;
        }
        List<UserPin> pins = userPinMapper.selectList(new LambdaQueryWrapper<UserPin>()
                .eq(UserPin::getUserId, userId)
                .eq(UserPin::getTargetType, "work")
                .orderByAsc(UserPin::getSortOrder)
                .orderByDesc(UserPin::getCreatedAt));
        for (UserPin pin : pins) {
            if (pin.getTargetId() != null && isVisiblePinnedWork(pin.getTargetId())) {
                result.add(pin.getTargetId());
                if (result.size() >= 3) {
                    break;
                }
            }
        }
        return result;
    }

    private String joinLongIds(List<Long> ids) {
        StringBuilder builder = new StringBuilder();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }

    /** 旧接口（不分页）：规则同 collectionWorksPage，别人也能看公开合辑 */
    public List<WorkVO> collectionWorks(Long currentUserId, Long collectionId) {
        WorkCollection collection = collectionService.requireVisible(currentUserId, collectionId);
        boolean owner = currentUserId != null && currentUserId.equals(collection.getUserId());
        return toListVOs(visibleCollectionWorks(currentUserId, collectionId, owner), currentUserId);
    }

    /** 合辑里的作品，分页；作者看全部，别人只看已发布的（付费作品照常只给预览） */
    public PageResult<WorkVO> collectionWorksPage(Long currentUserId, Long collectionId, Integer pageNum, Integer pageSize) {
        WorkCollection collection = collectionService.requireVisible(currentUserId, collectionId);
        boolean owner = currentUserId != null && currentUserId.equals(collection.getUserId());
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        List<Work> works = visibleCollectionWorks(currentUserId, collectionId, owner);
        int from = Math.min((safePageNum - 1) * safePageSize, works.size());
        int to = Math.min(from + safePageSize, works.size());
        return PageResult.of((long) works.size(), safePageNum, safePageSize, toListVOs(works.subList(from, to), currentUserId));
    }

    /**
     * 合辑里当前用户看得到的作品，按发布时间倒序（作者和别人看到的顺序一样）。
     * 作者：未删除的都看得到（含审核中、隐藏）；别人：只有已发布的。屏蔽的作者、标签照常过滤。视频暂时下线。
     */
    public List<Work> visibleCollectionWorks(Long currentUserId, Long collectionId, boolean owner) {
        List<WorkCollectionItem> items = workCollectionItemMapper.selectList(new LambdaQueryWrapper<WorkCollectionItem>()
                .eq(WorkCollectionItem::getCollectionId, collectionId));
        List<Long> workIds = new ArrayList<Long>();
        for (WorkCollectionItem item : items) {
            if (item.getWorkId() != null && !workIds.contains(item.getWorkId())) {
                workIds.add(item.getWorkId());
            }
        }
        if (workIds.isEmpty()) {
            return new ArrayList<Work>();
        }
        LambdaQueryWrapper<Work> wrapper = new LambdaQueryWrapper<Work>()
                .eq(Work::getIsDeleted, 0)
                .ne(Work::getType, "video")
                .in(Work::getId, workIds)
                .orderByDesc(Work::getCreatedAt)
                .orderByDesc(Work::getId);
        if (owner) {
            wrapper.ne(Work::getStatus, "deleted");
        } else {
            wrapper.eq(Work::getStatus, "published");
        }
        applyBlockedTagFilter(wrapper, currentUserId);
        applyBlockedUserFilter(wrapper, currentUserId);
        List<Work> works = workMapper.selectList(wrapper);
        return works == null ? new ArrayList<Work>() : works;
    }

    /** 合辑卡片上的作者 */
    public UserVO briefUser(Long userId) {
        User user = userId == null ? null : userMapper.selectById(userId);
        return user == null ? null : toListUserVO(user);
    }

    /** 合辑封面：第一篇看得到的作品的封面，没有封面用第一张图 */
    public String coverOf(Work work) {
        if (work == null) return null;
        if (StringUtils.hasText(work.getCover())) return local(work.getCover());
        String media = work.getMediaUrl();
        if (StringUtils.hasText(media)) return local(media.split(",")[0].trim());
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkVO publish(Long userId, PublishWorkRequest request) {
        validateContent(request.getTitle(), request.getContent());
        if (request.getTags() == null || request.getTags().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "至少选择一个Tag");
        }
        if (request.getTags().size() > 3) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Tag最多只能选择3个");
        }
        List<String> mediaUrls = normalizeMediaUrls(request);
        boolean paidLocked = Boolean.TRUE.equals(request.getPaidLocked());
        String unlockCrystalId = normalizeUnlockCrystalId(request.getUnlockCrystalId());
        int unlockCrystalAmount = normalizeUnlockCrystalAmount(request.getUnlockCrystalAmount());
        if (paidLocked) {
            growthService.consumeCrystalEnergy(userId, unlockCrystalId, unlockCrystalAmount, crystalDisplayName(unlockCrystalId) + "不足，无法设置付费内容");
        }
        Work work = new Work();
        work.setUserId(userId);
        work.setType(request.getType());
        work.setTitle(request.getTitle());
        work.setContent(request.getContent());
        work.setMediaUrl(join(mediaUrls));
        work.setCover(resolveCover(request, mediaUrls));
        work.setLocation(request.getLocation());
        boolean isPrivate = request.isPrivateView();
        work.setStatus(isPrivate ? "hidden" : "pending");
        work.setEditCount(0);
        work.setAuditReason(null);
        work.setLikeCount(0);
        work.setFavCount(0);
        work.setViewCount(0);
        work.setIsDeleted(0);
        work.setAllowImageSave(Boolean.FALSE.equals(request.getAllowImageSave()) ? 1 : 0);
        work.setImageDisplayMode("long".equals(request.getImageDisplayMode()) ? "long" : "normal");
        work.setPaidLocked(paidLocked ? 1 : 0);
        work.setUnlockCrystalId(unlockCrystalId);
        work.setUnlockCrystalAmount(unlockCrystalAmount);
        workMapper.insert(work);
        List<Long> tagIds = tagService.ensureTags(userId, request.getTags());
        for (Long tagId : tagIds) {
            WorkTag workTag = new WorkTag();
            workTag.setWorkId(work.getId());
            workTag.setTagId(tagId);
            workTagMapper.insert(workTag);
            tagMapper.update(null, new LambdaUpdateWrapper<Tag>().eq(Tag::getId, tagId).setSql("usage_count = usage_count + 1"));
        }
        collectionService.attachWork(userId, work.getId(), request.getCollectionIds());
        growthService.recordPublish(userId);
        return view(userId, work.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkVO updateOwnWork(Long userId, Long workId, PublishWorkRequest request) {
        Work work = workMapper.selectById(workId);
        if (work == null || work.getIsDeleted() != null && work.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "作品不存在");
        }
        if (!userId.equals(work.getUserId())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "只能修改自己的作品");
        }
        if (!work.getType().equals(request.getType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能修改作品类型");
        }
        int edits = work.getEditCount() == null ? 0 : work.getEditCount();
        if (edits >= 3) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "作品最多只能修改3次");
        }
        validateContent(request.getTitle(), request.getContent());
        if (request.getTags() == null || request.getTags().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "至少选择一个Tag");
        }
        if (request.getTags().size() > 3) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Tag最多只能选择3个");
        }
        List<String> mediaUrls = normalizeMediaUrls(request);
        boolean wasLocked = isPaidLocked(work);
        boolean paidLocked = request.getPaidLocked() == null ? wasLocked : Boolean.TRUE.equals(request.getPaidLocked());
        String unlockCrystalId = StringUtils.hasText(request.getUnlockCrystalId()) ? normalizeUnlockCrystalId(request.getUnlockCrystalId()) : normalizeUnlockCrystalId(work.getUnlockCrystalId());
        int unlockCrystalAmount = request.getUnlockCrystalAmount() == null ? normalizeUnlockCrystalAmount(work.getUnlockCrystalAmount()) : normalizeUnlockCrystalAmount(request.getUnlockCrystalAmount());
        settlePaidCharge(userId, wasLocked, normalizeUnlockCrystalId(work.getUnlockCrystalId()), normalizeUnlockCrystalAmount(work.getUnlockCrystalAmount()),
                paidLocked, unlockCrystalId, unlockCrystalAmount);
        work.setTitle(request.getTitle());
        work.setContent(request.getContent());
        if (request.isPrivateView()) {
            work.setStatus("hidden");
        } else {
            work.setStatus("pending");
        }
        work.setAuditReason(null);
        work.setEditCount(edits + 1);
        work.setMediaUrl(join(mediaUrls));
        work.setCover(resolveCover(request, mediaUrls));
        work.setDuration(null);
        work.setAllowImageSave(Boolean.FALSE.equals(request.getAllowImageSave()) ? 1 : 0);
        work.setImageDisplayMode("long".equals(request.getImageDisplayMode()) ? "long" : "normal");
        work.setPaidLocked(paidLocked ? 1 : 0);
        work.setUnlockCrystalId(unlockCrystalId);
        work.setUnlockCrystalAmount(unlockCrystalAmount);
        workMapper.updateById(work);
        // updateById 会跳过 null：上一次的驳回原因、时长要显式清掉，否则重新提交后还显示旧的驳回原因
        workMapper.update(null, new LambdaUpdateWrapper<Work>().eq(Work::getId, workId)
                .set(Work::getAuditReason, null).set(Work::getDuration, null));
        workTagMapper.delete(new LambdaQueryWrapper<WorkTag>().eq(WorkTag::getWorkId, workId));
        List<Long> tagIds = tagService.ensureTags(userId, request.getTags());
        for (Long tagId : tagIds) {
            WorkTag workTag = new WorkTag();
            workTag.setWorkId(work.getId());
            workTag.setTagId(tagId);
            workTagMapper.insert(workTag);
        }
        collectionService.syncWorkCollections(userId, work.getId(), request.getCollectionIds());
        createMessage(userId, "audit", "作品已重新提交", "你的作品《" + safeTitle(work.getTitle()) + "》已重新进入审核，剩余修改次数：" + (3 - work.getEditCount()));
        return view(userId, work.getId());
    }

    public WorkVO updateOwnArticle(Long userId, Long workId, PublishWorkRequest request) {
        return updateOwnWork(userId, workId, request);
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkVO updatePaymentSettings(Long userId, Long workId, PublishWorkRequest request) {
        Work work = workMapper.selectById(workId);
        if (work == null || work.getIsDeleted() != null && work.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "作品不存在");
        }
        if (!userId.equals(work.getUserId())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "只能管理自己的作品");
        }
        boolean wasLocked = isPaidLocked(work);
        boolean paidLocked = request.getPaidLocked() == null ? wasLocked : Boolean.TRUE.equals(request.getPaidLocked());
        String unlockCrystalId = StringUtils.hasText(request.getUnlockCrystalId()) ? normalizeUnlockCrystalId(request.getUnlockCrystalId()) : normalizeUnlockCrystalId(work.getUnlockCrystalId());
        int unlockCrystalAmount = request.getUnlockCrystalAmount() == null ? normalizeUnlockCrystalAmount(work.getUnlockCrystalAmount()) : normalizeUnlockCrystalAmount(request.getUnlockCrystalAmount());
        settlePaidCharge(userId, wasLocked, normalizeUnlockCrystalId(work.getUnlockCrystalId()), normalizeUnlockCrystalAmount(work.getUnlockCrystalAmount()),
                paidLocked, unlockCrystalId, unlockCrystalAmount);
        work.setPaidLocked(paidLocked ? 1 : 0);
        work.setUnlockCrystalId(unlockCrystalId);
        work.setUnlockCrystalAmount(unlockCrystalAmount);
        workMapper.updateById(work);
        return view(userId, work.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkVO toggleLike(Long userId, Long workId) {
        return setLike(userId, workId, null);
    }

    /** wanted 为空时切换；否则设成指定状态，已经是该状态就不动。 */
    @Transactional(rollbackFor = Exception.class)
    public WorkVO setLike(Long userId, Long workId, Boolean wanted) {
        view(userId, workId);
        Long count = userLikeMapper.selectCount(new LambdaQueryWrapper<UserLike>()
                .eq(UserLike::getUserId, userId)
                .eq(UserLike::getWorkId, workId));
        boolean likedNow = count > 0;
        boolean target = wanted == null ? !likedNow : wanted;
        if (target == likedNow) {
            return view(userId, workId);
        }
        if (likedNow) {
            userLikeMapper.delete(new LambdaQueryWrapper<UserLike>().eq(UserLike::getUserId, userId).eq(UserLike::getWorkId, workId));
            workMapper.update(null, new LambdaUpdateWrapper<Work>().eq(Work::getId, workId).setSql("like_count = greatest(like_count - 1, 0)"));
        } else {
            insertLike(userId, workId);
        }
        return view(userId, workId);
    }

    /** 还没点赞才点赞（收藏时联动用）；已经点过赞不动，点赞数不会重复增加 */
    private void ensureLiked(Long userId, Long workId) {
        Long count = userLikeMapper.selectCount(new LambdaQueryWrapper<UserLike>()
                .eq(UserLike::getUserId, userId)
                .eq(UserLike::getWorkId, workId));
        if (count == null || count == 0) {
            insertLike(userId, workId);
        }
    }

    private void insertLike(Long userId, Long workId) {
        UserLike like = new UserLike();
        like.setUserId(userId);
        like.setWorkId(workId);
        userLikeMapper.insert(like);
        workMapper.update(null, new LambdaUpdateWrapper<Work>().eq(Work::getId, workId).setSql("like_count = like_count + 1"));
        Work work = workMapper.selectById(workId);
        if (work != null && !userId.equals(work.getUserId())) {
            growthService.rewardLikeEnergy(userId);
            createMessage(work.getUserId(), "like", "收到点赞", "你的作品《" + safeTitle(work.getTitle()) + "》收到了新的点赞");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkVO unlock(Long userId, Long workId) {
        Work work = workMapper.selectOne(new LambdaQueryWrapper<Work>()
                .eq(Work::getId, workId)
                .eq(Work::getIsDeleted, 0)
                .last("limit 1"));
        if (work == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "作品不存在");
        }
        if (!isPaidLocked(work) || userId.equals(work.getUserId()) || hasUnlocked(userId, workId)) {
            return view(userId, workId);
        }
        String crystalId = normalizeUnlockCrystalId(work.getUnlockCrystalId());
        int amount = normalizeUnlockCrystalAmount(work.getUnlockCrystalAmount());
        growthService.consumeCrystalEnergy(userId, crystalId, amount, "水晶不足，无法解锁该作品");
        UserWorkUnlock unlock = new UserWorkUnlock();
        unlock.setUserId(userId);
        unlock.setWorkId(workId);
        unlock.setCrystalId(crystalId);
        unlock.setCrystalAmount(amount);
        unlock.setCreatedAt(LocalDateTime.now());
        userWorkUnlockMapper.insert(unlock);
        growthService.grantCrystalCount(work.getUserId(), crystalId, amount);
        createMessage(
                work.getUserId(),
                "unlock",
                "付费内容被解锁",
                "你的作品《" + safeTitle(work.getTitle()) + "》被解锁，获得 " + amount + " 枚" + crystalDisplayName(crystalId)
        );
        return view(userId, workId);
    }

    public PaidUnlockEarningsVO paymentEarnings(Long authorId) {
        PaidUnlockEarningsVO summary = new PaidUnlockEarningsVO();
        summary.setTotalUnlocks(0);
        summary.setTotalCrystalAmount(0);
        List<Work> works = workMapper.selectList(new LambdaQueryWrapper<Work>()
                .eq(Work::getUserId, authorId)
                .eq(Work::getIsDeleted, 0));
        if (works.isEmpty()) {
            return summary;
        }
        Map<Long, Work> workMap = new HashMap<Long, Work>();
        List<Long> workIds = new ArrayList<Long>();
        for (Work work : works) {
            workMap.put(work.getId(), work);
            workIds.add(work.getId());
        }
        List<UserWorkUnlock> unlocks = userWorkUnlockMapper.selectList(new LambdaQueryWrapper<UserWorkUnlock>()
                .in(UserWorkUnlock::getWorkId, workIds)
                .orderByDesc(UserWorkUnlock::getCreatedAt));
        Map<Long, PaidUnlockWorkEarningVO> byWork = new HashMap<Long, PaidUnlockWorkEarningVO>();
        int totalUnlocks = 0;
        int totalAmount = 0;
        for (UserWorkUnlock unlock : unlocks) {
            Work work = workMap.get(unlock.getWorkId());
            if (work == null) {
                continue;
            }
            int amount = unlock.getCrystalAmount() == null ? 0 : Math.max(0, unlock.getCrystalAmount());
            totalUnlocks += 1;
            totalAmount += amount;
            PaidUnlockWorkEarningVO row = byWork.get(work.getId());
            if (row == null) {
                row = new PaidUnlockWorkEarningVO();
                row.setWorkId(work.getId());
                row.setTitle(safeTitle(work.getTitle()));
                row.setUnlockCount(0);
                row.setCrystalAmount(0);
                byWork.put(work.getId(), row);
            }
            row.setUnlockCount(row.getUnlockCount() + 1);
            row.setCrystalAmount(row.getCrystalAmount() + amount);
        }
        summary.setTotalUnlocks(totalUnlocks);
        summary.setTotalCrystalAmount(totalAmount);
        summary.setWorks(new ArrayList<PaidUnlockWorkEarningVO>(byWork.values()));
        return summary;
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkVO toggleFavorite(Long userId, Long workId) {
        return setFavorite(userId, workId, null);
    }

    /**
     * wanted 为空时切换；否则设成指定状态，已经是该状态就不动。
     * 新收藏的同时点赞（还没点过才点）；取消收藏不取消点赞。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkVO setFavorite(Long userId, Long workId, Boolean wanted) {
        view(userId, workId);
        Long count = userFavoriteMapper.selectCount(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getWorkId, workId));
        boolean favoritedNow = count > 0;
        boolean target = wanted == null ? !favoritedNow : wanted;
        if (target == favoritedNow) {
            return view(userId, workId);
        }
        if (favoritedNow) {
            userFavoriteMapper.delete(new LambdaQueryWrapper<UserFavorite>().eq(UserFavorite::getUserId, userId).eq(UserFavorite::getWorkId, workId));
            workMapper.update(null, new LambdaUpdateWrapper<Work>().eq(Work::getId, workId).setSql("fav_count = greatest(fav_count - 1, 0)"));
            favoriteCollectionService.clearWorkFromAllCollections(userId, workId);
        } else {
            UserFavorite favorite = new UserFavorite();
            favorite.setUserId(userId);
            favorite.setWorkId(workId);
            userFavoriteMapper.insert(favorite);
            workMapper.update(null, new LambdaUpdateWrapper<Work>().eq(Work::getId, workId).setSql("fav_count = fav_count + 1"));
            Work work = workMapper.selectById(workId);
            if (work != null && !userId.equals(work.getUserId())) {
                createMessage(work.getUserId(), "favorite", "作品被收藏", "你的作品《" + safeTitle(work.getTitle()) + "》被收藏了");
            }
            ensureLiked(userId, workId);
        }
        return view(userId, workId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void report(Long userId, Long workId, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "举报原因不能为空");
        }
        Work work = workMapper.selectOne(new LambdaQueryWrapper<Work>()
                .eq(Work::getId, workId)
                .eq(Work::getIsDeleted, 0)
                .last("limit 1"));
        if (work == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "作品不存在");
        }
        if (work.getUserId() != null && work.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "不能举报自己的作品");
        }
        Long pendingCount = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, userId)
                .eq(Report::getWorkId, workId)
                .eq(Report::getIsDeleted, 0)
                .eq(Report::getStatus, "pending"));
        if (pendingCount > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "你已举报过该作品，等待管理员处理");
        }
        Report report = new Report();
        report.setReporterId(userId);
        report.setWorkId(workId);
        report.setReason(reason.trim());
        report.setStatus("pending");
        report.setIsDeleted(0);
        report.setCreatedAt(LocalDateTime.now());
        reportMapper.insert(report);
    }

    @Transactional(rollbackFor = Exception.class)
    public void hideOwnWork(Long userId, Long workId) {
        Work work = workMapper.selectById(workId);
        if (work == null || work.getIsDeleted() != null && work.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "作品不存在");
        }
        if (!userId.equals(work.getUserId())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "只能隐藏自己的作品");
        }
        // 只有已发布的作品可以隐藏：恢复时会直接回到已发布，
        // 待审核或被驳回的作品隐藏再恢复就能绕过审核
        if (!"published".equals(work.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "只有已发布的作品可以隐藏");
        }
        workMapper.update(null, new LambdaUpdateWrapper<Work>()
                .eq(Work::getId, workId)
                .eq(Work::getStatus, "published")
                .set(Work::getStatus, "hidden"));
        userPinMapper.delete(new LambdaQueryWrapper<UserPin>()
                .eq(UserPin::getTargetType, "work")
                .eq(UserPin::getTargetId, workId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void unhideOwnWork(Long userId, Long workId) {
        Work work = workMapper.selectById(workId);
        if (work == null || work.getIsDeleted() != null && work.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "作品不存在");
        }
        if (!userId.equals(work.getUserId())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "只能恢复自己的作品");
        }
        if (!"hidden".equals(work.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "作品不是隐藏状态");
        }
        workMapper.update(null, new LambdaUpdateWrapper<Work>()
                .eq(Work::getId, workId)
                .eq(Work::getStatus, "hidden")
                .set(Work::getStatus, "published"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteOwnWork(Long userId, Long workId) {
        Work work = workMapper.selectById(workId);
        if (work == null || work.getIsDeleted() != null && work.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "作品不存在");
        }
        if (!userId.equals(work.getUserId())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "只能删除自己的作品");
        }
        // 作者自己删除付费作品：退回设置付费时扣的晶体
        if (isPaidLocked(work)) {
            growthService.grantCrystalCount(userId, normalizeUnlockCrystalId(work.getUnlockCrystalId()), normalizeUnlockCrystalAmount(work.getUnlockCrystalAmount()));
        }
        workMapper.update(null, new LambdaUpdateWrapper<Work>()
                .eq(Work::getId, workId)
                .set(Work::getIsDeleted, 1)
                .set(Work::getStatus, "deleted"));
        workCollectionItemMapper.delete(new LambdaQueryWrapper<WorkCollectionItem>()
                .eq(WorkCollectionItem::getWorkId, workId));
        favoriteCollectionService.clearDeletedWork(workId);
        userFavoriteMapper.delete(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getWorkId, workId));
        userPinMapper.delete(new LambdaQueryWrapper<UserPin>()
                .eq(UserPin::getTargetType, "work")
                .eq(UserPin::getTargetId, workId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean toggleFollow(Long userId, Long targetUserId) {
        return setFollow(userId, targetUserId, null);
    }

    /** wanted 为空时切换；否则设成指定状态，已经是该状态就不动。返回操作后的关注状态 */
    @Transactional(rollbackFor = Exception.class)
    public Boolean setFollow(Long userId, Long targetUserId, Boolean wanted) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "不能关注自己");
        }
        userService.getEntityById(targetUserId);
        // 拉黑会解除关注；之后任何一方都不能再关注对方（取消关注不受影响）
        boolean blockedEitherWay = userBlockMapper.selectCount(new LambdaQueryWrapper<UserBlock>()
                .and(w -> w.eq(UserBlock::getUserId, userId).eq(UserBlock::getBlockedUserId, targetUserId)
                        .or().eq(UserBlock::getUserId, targetUserId).eq(UserBlock::getBlockedUserId, userId))) > 0;
        Long count = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getUserId, userId)
                .eq(UserFollow::getTargetUserId, targetUserId));
        boolean followingNow = count > 0;
        boolean target = wanted == null ? !followingNow : wanted;
        if (target == followingNow) {
            return followingNow;
        }
        if (followingNow) {
            userFollowMapper.delete(new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getUserId, userId).eq(UserFollow::getTargetUserId, targetUserId));
            userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, userId).setSql("follow_count = greatest(follow_count - 1, 0)"));
            userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, targetUserId).setSql("fans_count = greatest(fans_count - 1, 0)"));
            return false;
        }
        if (blockedEitherWay) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "已拉黑或被对方拉黑，无法关注");
        }
        UserFollow follow = new UserFollow();
        follow.setUserId(userId);
        follow.setTargetUserId(targetUserId);
        userFollowMapper.insert(follow);
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, userId).setSql("follow_count = follow_count + 1"));
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, targetUserId).setSql("fans_count = fans_count + 1"));
        User user = userMapper.selectById(userId);
        createMessage(targetUserId, "follow", "新增关注", (user == null ? "有人" : user.getNickname()) + "关注了你");
        return true;
    }

    public WorkVO toVO(Work work, Long currentUserId) {
        WorkVO vo = new WorkVO();
        vo.setId(work.getId());
        vo.setType(work.getType());
        vo.setTitle(work.getTitle());
        boolean unlockedByMe = unlockedByMe(work, currentUserId);
        vo.setContent(unlockedByMe ? work.getContent() : lockPreviewText(work.getContent()));
        vo.setCover(local(work.getCover()));
        vo.setMediaUrl(localList(lockedMediaUrl(work, unlockedByMe)));
        vo.setDuration(work.getDuration());
        vo.setLocation(work.getLocation());
        vo.setStatus(work.getStatus());
        vo.setEditCount(work.getEditCount() == null ? 0 : work.getEditCount());
        vo.setAuditReason(work.getAuditReason());
        vo.setLikeCount(work.getLikeCount() == null ? 0 : work.getLikeCount());
        vo.setFavCount(work.getFavCount() == null ? 0 : work.getFavCount());
        vo.setViewCount(work.getViewCount() == null ? 0 : work.getViewCount());
        vo.setCreatedAt(work.getCreatedAt() == null ? null : work.getCreatedAt().format(FORMATTER));
        vo.setAuthor(userService.toVO(userMapper.selectById(work.getUserId()), currentUserId));
        vo.setTags(tagService.tagsByWorkId(work.getId(), currentUserId));
        vo.setLiked(currentUserId != null && userLikeMapper.selectCount(new LambdaQueryWrapper<UserLike>().eq(UserLike::getUserId, currentUserId).eq(UserLike::getWorkId, work.getId())) > 0);
        vo.setFavorited(currentUserId != null && userFavoriteMapper.selectCount(new LambdaQueryWrapper<UserFavorite>().eq(UserFavorite::getUserId, currentUserId).eq(UserFavorite::getWorkId, work.getId())) > 0);
        vo.setFollowedAuthor(currentUserId != null && userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getUserId, currentUserId).eq(UserFollow::getTargetUserId, work.getUserId())) > 0);
        vo.setPinned(isPinned(currentUserId, work.getId()));
        vo.setResonated(growthService.hasUserResonatedWork(currentUserId, work.getId()));
        vo.setResonatedCrystalId(growthService.userResonatedCrystalId(currentUserId, work.getId()));
        vo.setResonances(growthService.workResonancesForVO(work.getId()));
        vo.setAllowImageSave(work.getAllowImageSave() == null || work.getAllowImageSave() == 0);
        vo.setImageDisplayMode(StringUtils.hasText(work.getImageDisplayMode()) ? work.getImageDisplayMode() : "normal");
        vo.setPaidLocked(isPaidLocked(work));
        vo.setUnlockCrystalId(normalizeUnlockCrystalId(work.getUnlockCrystalId()));
        vo.setUnlockCrystalAmount(normalizeUnlockCrystalAmount(work.getUnlockCrystalAmount()));
        vo.setUnlockedByMe(unlockedByMe);
        vo.setLockPreviewText(lockPreviewText(work.getContent()));
        return vo;
    }

    public List<WorkVO> toListVOs(List<Work> works, Long currentUserId) {
        List<WorkVO> result = new ArrayList<WorkVO>();
        if (works == null || works.isEmpty()) {
            return result;
        }

        Set<Long> userIds = new LinkedHashSet<Long>();
        Set<Long> workIds = new LinkedHashSet<Long>();
        for (Work work : works) {
            if (work.getUserId() != null) {
                userIds.add(work.getUserId());
            }
            if (work.getId() != null) {
                workIds.add(work.getId());
            }
        }

        Map<Long, UserVO> authors = new HashMap<Long, UserVO>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .in(User::getId, userIds)
                    .eq(User::getDeleted, 0));
            for (User user : users) {
                authors.put(user.getId(), toListUserVO(user));
            }
        }

        Map<Long, List<TagVO>> tagsByWorkId = new HashMap<Long, List<TagVO>>();
        if (!workIds.isEmpty()) {
            List<WorkTag> workTags = workTagMapper.selectList(new LambdaQueryWrapper<WorkTag>().in(WorkTag::getWorkId, workIds));
            Set<Long> tagIds = new LinkedHashSet<Long>();
            for (WorkTag workTag : workTags) {
                if (workTag.getTagId() != null) {
                    tagIds.add(workTag.getTagId());
                }
            }
            Map<Long, Tag> tagsById = new HashMap<Long, Tag>();
            if (!tagIds.isEmpty()) {
                List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                        .in(Tag::getId, tagIds)
                        .eq(Tag::getIsDeleted, 0));
                for (Tag tag : tags) {
                    tagsById.put(tag.getId(), tag);
                }
            }
            for (WorkTag workTag : workTags) {
                Tag tag = tagsById.get(workTag.getTagId());
                if (tag == null) {
                    continue;
                }
                List<TagVO> workTagsList = tagsByWorkId.get(workTag.getWorkId());
                if (workTagsList == null) {
                    workTagsList = new ArrayList<TagVO>();
                    tagsByWorkId.put(workTag.getWorkId(), workTagsList);
                }
                workTagsList.add(toListTagVO(tag));
            }
        }

        Set<Long> likedWorkIds = new HashSet<Long>();
        if (currentUserId != null && !workIds.isEmpty()) {
            List<UserLike> likes = userLikeMapper.selectList(new LambdaQueryWrapper<UserLike>()
                    .eq(UserLike::getUserId, currentUserId)
                    .in(UserLike::getWorkId, workIds));
            for (UserLike like : likes) {
                likedWorkIds.add(like.getWorkId());
            }
        }

        // 列表里的收藏状态要是真实的：原来写死 false，首页菜单对已收藏作品显示「收藏」，点下去反而取消收藏
        Set<Long> favoritedWorkIds = new HashSet<Long>();
        if (currentUserId != null && !workIds.isEmpty()) {
            List<UserFavorite> favorites = userFavoriteMapper.selectList(new LambdaQueryWrapper<UserFavorite>()
                    .eq(UserFavorite::getUserId, currentUserId)
                    .in(UserFavorite::getWorkId, workIds));
            for (UserFavorite favorite : favorites) {
                favoritedWorkIds.add(favorite.getWorkId());
            }
        }

        for (Work work : works) {
            WorkVO vo = new WorkVO();
            vo.setId(work.getId());
            vo.setType(work.getType());
            vo.setTitle(work.getTitle());
            boolean unlockedByMe = unlockedByMe(work, currentUserId);
            vo.setContent(unlockedByMe ? work.getContent() : lockPreviewText(work.getContent()));
            vo.setCover(local(work.getCover()));
            vo.setMediaUrl(localList(lockedMediaUrl(work, unlockedByMe)));
            vo.setDuration(work.getDuration());
            vo.setLocation(work.getLocation());
            vo.setStatus(work.getStatus());
            vo.setEditCount(work.getEditCount() == null ? 0 : work.getEditCount());
            vo.setAuditReason(work.getAuditReason());
            vo.setLikeCount(work.getLikeCount() == null ? 0 : work.getLikeCount());
            vo.setFavCount(work.getFavCount() == null ? 0 : work.getFavCount());
            vo.setViewCount(work.getViewCount() == null ? 0 : work.getViewCount());
            vo.setCreatedAt(work.getCreatedAt() == null ? null : work.getCreatedAt().format(FORMATTER));
            vo.setAuthor(authors.get(work.getUserId()));
            vo.setTags(tagsByWorkId.getOrDefault(work.getId(), new ArrayList<TagVO>()));
            vo.setLiked(likedWorkIds.contains(work.getId()));
            vo.setFavorited(favoritedWorkIds.contains(work.getId()));
            vo.setFollowedAuthor(false);
            vo.setPinned(false);
            vo.setResonated(false);
            vo.setResonances(new ArrayList<>());
            vo.setAllowImageSave(work.getAllowImageSave() == null || work.getAllowImageSave() == 0);
            vo.setImageDisplayMode(StringUtils.hasText(work.getImageDisplayMode()) ? work.getImageDisplayMode() : "normal");
            vo.setPaidLocked(isPaidLocked(work));
            vo.setUnlockCrystalId(normalizeUnlockCrystalId(work.getUnlockCrystalId()));
            vo.setUnlockCrystalAmount(normalizeUnlockCrystalAmount(work.getUnlockCrystalAmount()));
            vo.setUnlockedByMe(unlockedByMe);
            vo.setLockPreviewText(lockPreviewText(work.getContent()));
            result.add(vo);
        }
        return result;
    }

    public List<WorkVO> toListVOs(List<Work> works) {
        return toListVOs(works, null);
    }

    private UserVO toListUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(local(user.getAvatar()));
        vo.setBackgroundImage(StringUtils.hasText(user.getBackgroundImage()) ? local(user.getBackgroundImage()) : "asset_bg");
        vo.setGender(user.getGender());
        vo.setGenderText(user.getGender() != null && user.getGender() == 1 ? "古兽" : "人类");
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setPrivacyAgreed(user.getPrivacyAgreed() == null ? 0 : user.getPrivacyAgreed());
        vo.setFollowCount(user.getFollowCount() == null ? 0 : user.getFollowCount());
        vo.setFansCount(user.getFansCount() == null ? 0 : user.getFansCount());
        vo.setLikeCount(user.getLikeCount() == null ? 0 : user.getLikeCount());
        vo.setPinned(false);
        return vo;
    }

    private TagVO toListTagVO(Tag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setUsageCount(tag.getUsageCount() == null ? 0 : tag.getUsageCount());
        vo.setDisabled(tag.getIsDisabled() == null ? 0 : tag.getIsDisabled());
        vo.setFavorited(false);
        vo.setPinned(false);
        return vo;
    }

    private boolean isPinned(Long userId, Long workId) {
        return userId != null && workId != null && userPinMapper.selectCount(new LambdaQueryWrapper<UserPin>()
                .eq(UserPin::getUserId, userId)
                .eq(UserPin::getTargetType, "work")
                .eq(UserPin::getTargetId, workId)) > 0;
    }

    private boolean isVisiblePinnedWork(Long workId) {
        Work work = workMapper.selectById(workId);
        return work != null
                && (work.getIsDeleted() == null || work.getIsDeleted() == 0)
                && !"deleted".equals(work.getStatus());
    }

    private boolean isPaidLocked(Work work) {
        return work != null && work.getPaidLocked() != null && work.getPaidLocked() != 0;
    }

    /**
     * 设置付费内容的扣费：作者为付费作品押上「一份定价」的晶体。
     * 开启时扣，关闭时退；上锁状态下改了价格或晶体类型，先退旧的再扣新的。
     * 原来只在「未上锁→上锁」时扣、关闭不退，关掉再打开就会被重复扣。
     */
    private void settlePaidCharge(Long userId, boolean wasLocked, String oldCrystalId, int oldAmount,
                                  boolean nowLocked, String newCrystalId, int newAmount) {
        boolean[] plan = paidChargePlan(wasLocked, oldCrystalId, oldAmount, nowLocked, newCrystalId, newAmount);
        if (plan[0]) {
            growthService.grantCrystalCount(userId, oldCrystalId, oldAmount);
        }
        if (plan[1]) {
            growthService.consumeCrystalEnergy(userId, newCrystalId, newAmount, crystalDisplayName(newCrystalId) + "不足，无法设置付费内容");
        }
    }

    /** 返回 {是否退回旧押金, 是否扣新押金} */
    static boolean[] paidChargePlan(boolean wasLocked, String oldCrystalId, int oldAmount,
                                    boolean nowLocked, String newCrystalId, int newAmount) {
        boolean sameCharge = oldCrystalId.equals(newCrystalId) && oldAmount == newAmount;
        return new boolean[]{wasLocked && (!nowLocked || !sameCharge), nowLocked && (!wasLocked || !sameCharge)};
    }

    private boolean unlockedByMe(Work work, Long currentUserId) {
        if (!isPaidLocked(work)) {
            return true;
        }
        if (currentUserId == null) {
            return false;
        }
        if (currentUserId.equals(work.getUserId())) {
            return true;
        }
        return hasUnlocked(currentUserId, work.getId());
    }

    private boolean hasUnlocked(Long userId, Long workId) {
        return userId != null && workId != null && userWorkUnlockMapper.selectCount(new LambdaQueryWrapper<UserWorkUnlock>()
                .eq(UserWorkUnlock::getUserId, userId)
                .eq(UserWorkUnlock::getWorkId, workId)) > 0;
    }

    private String normalizeUnlockCrystalId(String crystalId) {
        return StringUtils.hasText(crystalId) ? crystalId.trim() : "solid";
    }

    private int normalizeUnlockCrystalAmount(Integer amount) {
        return amount == null ? 1 : Math.max(1, amount);
    }

    private String crystalDisplayName(String crystalId) {
        String safeCrystalId = normalizeUnlockCrystalId(crystalId);
        if ("glow".equals(safeCrystalId)) return "辉光晶体";
        if ("flame".equals(safeCrystalId)) return "赤焰晶体";
        if ("void".equals(safeCrystalId)) return "幽紫晶体";
        if ("luminous".equals(safeCrystalId)) return "月白晶体";
        return "莹白晶体";
    }

    /**
     * 未解锁时下发的正文预览。最多 80 字，且不超过全文的三分之一：
     * 原来短于 80 字的付费文会原样下发全文，没付费也能读完。
     */
    static String lockPreviewText(String content) {
        // 先去掉正文格式标记再截取：直接截原文会留下半个 <u>、** 之类，预览里原样显示出来
        String raw = plainTextFromMarkup(content).trim();
        int limit = Math.min(80, raw.length() / 3);
        if (limit <= 0) {
            return "";
        }
        return raw.substring(0, limit) + "...";
    }

    private static final java.util.regex.Pattern MARKUP_TAG = java.util.regex.Pattern.compile(
            "</?u>|</?i>|<font size=\"\\d{1,3}\">|</font>|<color=#[0-9A-Fa-f]{6}>|</color>");

    /**
     * 文章正文的纯文字（与客户端 RichMarkup 的存储格式一致）：去掉 ** ~~ <u> <i> <font> <color> 标记、
     * 还原 \* \~ \< \\ 转义、去掉行首的「## 」标题前缀。
     */
    static String plainTextFromMarkup(String content) {
        if (content == null || content.isEmpty()) return "";
        StringBuilder out = new StringBuilder(content.length());
        java.util.regex.Matcher tag = MARKUP_TAG.matcher(content);
        int i = 0;
        while (i < content.length()) {
            char c = content.charAt(i);
            if (c == '\\' && i + 1 < content.length() && "*~<\\".indexOf(content.charAt(i + 1)) >= 0) {
                out.append(content.charAt(i + 1));
                i += 2;
            } else if (content.startsWith("**", i) || content.startsWith("~~", i)) {
                i += 2;
            } else if (c == '<' && tag.region(i, content.length()).lookingAt()) {
                i = tag.end();
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString().replaceAll("(?m)^## ", "");
    }

    /** 未解锁的付费图文只下发第一张图作为预览；其余类型原样返回 */
    static String lockedMediaUrl(Work work, boolean unlockedByMe) {
        String media = work.getMediaUrl();
        if (unlockedByMe || !"image".equals(work.getType()) || !StringUtils.hasText(media)) {
            return media;
        }
        String first = media.split(",")[0].trim();
        return first;
    }

    private void recordViewHistory(Long userId, Long workId) {
        if (userId == null || workId == null) {
            return;
        }
        UserViewHistory existing = userViewHistoryMapper.selectOne(new LambdaQueryWrapper<UserViewHistory>()
                .eq(UserViewHistory::getUserId, userId)
                .eq(UserViewHistory::getWorkId, workId)
                .last("limit 1"));
        if (existing == null) {
            UserViewHistory row = new UserViewHistory();
            row.setUserId(userId);
            row.setWorkId(workId);
            row.setViewedAt(LocalDateTime.now());
            try {
                userViewHistoryMapper.insert(row);
            } catch (RuntimeException ignored) {
                userViewHistoryMapper.update(null, new LambdaUpdateWrapper<UserViewHistory>()
                        .eq(UserViewHistory::getUserId, userId)
                        .eq(UserViewHistory::getWorkId, workId)
                        .set(UserViewHistory::getViewedAt, LocalDateTime.now()));
            }
        } else {
            userViewHistoryMapper.update(null, new LambdaUpdateWrapper<UserViewHistory>()
                    .eq(UserViewHistory::getUserId, userId)
                    .eq(UserViewHistory::getWorkId, workId)
                    .set(UserViewHistory::getViewedAt, LocalDateTime.now()));
        }
    }

    public List<CommentVO> comments(Long currentUserId, Long workId) {
        view(currentUserId, workId);
        List<WorkComment> comments = workCommentMapper.selectList(new LambdaQueryWrapper<WorkComment>()
                .eq(WorkComment::getWorkId, workId)
                .orderByDesc(WorkComment::getCreatedAt));
        List<CommentVO> result = new ArrayList<CommentVO>();
        // 拉黑的人发的评论不显示
        List<Long> blockedAuthors = blockedUserIds(currentUserId);
        for (WorkComment comment : comments) {
            if (comment.getUserId() != null && blockedAuthors.contains(comment.getUserId())) continue;
            result.add(toCommentVO(comment, currentUserId, workId));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public CommentVO addComment(Long userId, Long workId, String content) {
        view(userId, workId);
        String text = content == null ? "" : content.trim();
        if (!allowedComments().contains(text)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "评论只能选择固定词");
        }
        WorkComment comment = new WorkComment();
        comment.setWorkId(workId);
        comment.setUserId(userId);
        comment.setContent(text);
        comment.setCreatedAt(LocalDateTime.now());
        workCommentMapper.insert(comment);
        Work work = workMapper.selectById(workId);
        if (work != null && work.getUserId() != null && !work.getUserId().equals(userId)) {
            User user = userMapper.selectById(userId);
            createMessage(work.getUserId(), "comment", "收到评论", (user == null ? "有人" : user.getNickname()) + "评论了你的作品《" + safeTitle(work.getTitle()) + "》：" + text);
        }
        return toCommentVO(comment, userId, workId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long userId, Long workId, Long commentId) {
        Work work = workMapper.selectOne(new LambdaQueryWrapper<Work>()
                .eq(Work::getId, workId)
                .eq(Work::getIsDeleted, 0)
                .last("limit 1"));
        if (work == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "作品不存在");
        }
        WorkComment comment = workCommentMapper.selectOne(new LambdaQueryWrapper<WorkComment>()
                .eq(WorkComment::getId, commentId)
                .eq(WorkComment::getWorkId, workId)
                .last("limit 1"));
        if (comment == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "评论不存在");
        }
        boolean commentOwner = userId != null && userId.equals(comment.getUserId());
        boolean workOwner = userId != null && userId.equals(work.getUserId());
        if (!commentOwner && !workOwner) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "没有权限删除这条评论");
        }
        workCommentMapper.deleteById(commentId);
    }

    public List<String> allowedComments() {
        List<String> words = new ArrayList<String>();
        words.add("大大，超厉害");
        words.add("ε(´｡•᎑•`)っ♡");
        words.add("加油 •͈ᴗ⁃͈ ✧");
        words.add("期待续作！！！");
        words.add("呃……还行?");
        words.add("｢(ﾟﾍﾟ)");
        words.add("🔥火前留名！！！");
        words.add("♡╰(@☆▽☆@)╯♡");
        words.add("大佬，请收下我的膝盖！！！");
        words.add("🌹🌹🌹");
        return words;
    }

    private CommentVO toCommentVO(WorkComment comment, Long currentUserId, Long workId) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setContent(comment.getContent());
        vo.setCreatedAt(comment.getCreatedAt() == null ? null : comment.getCreatedAt().format(FORMATTER));
        User user = userMapper.selectById(comment.getUserId());
        if (user != null) {
            vo.setUser(userService.toVO(user));
        }
        Work work = workId == null ? null : workMapper.selectById(workId);
        vo.setCanDelete(currentUserId != null && (currentUserId.equals(comment.getUserId()) || work != null && currentUserId.equals(work.getUserId())));
        return vo;
    }

    private void validateContent(String title, String content) {
        List<BanWord> words = banWordMapper.selectList(null);
        String text = (title == null ? "" : title) + " " + (content == null ? "" : content);
        for (BanWord word : words) {
            if (StringUtils.hasText(word.getWord()) && text.contains(word.getWord())) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "内容包含违禁词：" + word.getWord());
            }
        }
    }

    private void createMessage(Long userId, String type, String title, String content) {
        Message message = new Message();
        message.setUserId(userId);
        message.setType(type);
        message.setTitle(title);
        message.setContent(content);
        message.setIsRead(0);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }

    private String safeTitle(String title) {
        return StringUtils.hasText(title) ? title : "未命名作品";
    }

    private void applyBlockedTagFilter(LambdaQueryWrapper<Work> wrapper, Long userId) {
        List<Long> blockedWorkIds = blockedWorkIds(userId);
        if (!blockedWorkIds.isEmpty()) {
            wrapper.notIn(Work::getId, blockedWorkIds);
        }
    }

    private void applyBlockedUserFilter(LambdaQueryWrapper<Work> wrapper, Long userId) {
        List<Long> blockedUserIds = blockedUserIds(userId);
        if (!blockedUserIds.isEmpty()) {
            wrapper.notIn(Work::getUserId, blockedUserIds);
        }
    }

    public boolean isBlockedWorkForUser(Long userId, Work work) {
        if (userId == null || work == null) {
            return false;
        }
        if (work.getId() != null && configuredNegativeWorkIds().contains(work.getId())) {
            return true;
        }
        if (work.getUserId() != null && userId.equals(work.getUserId())) {
            return false;
        }
        List<Long> blockedUserIds = blockedUserIds(userId);
        if (work.getUserId() != null && blockedUserIds.contains(work.getUserId())) {
            return true;
        }
        List<Long> blockedWorkIds = blockedWorkIds(userId);
        return work.getId() != null && blockedWorkIds.contains(work.getId());
    }

    /**
     * 当前用户看不到其内容的作者：自己拉黑的人，加上已删除的账号。
     * 已删除账号的作品和评论原来照常公开（作者名为空），这里统一过滤；
     * 列表、详情、足迹、评论、收藏夹都走这里。
     */
    private List<Long> blockedUserIds(Long userId) {
        List<Long> result = new ArrayList<Long>();
        if (userId == null) {
            return result;
        }
        for (User deleted : userMapper.selectList(new LambdaQueryWrapper<User>().select(User::getId).eq(User::getDeleted, 1))) {
            if (deleted.getId() != null && !deleted.getId().equals(userId)) result.add(deleted.getId());
        }
        List<UserBlock> blocks = userBlockMapper.selectList(new LambdaQueryWrapper<UserBlock>()
                .eq(UserBlock::getUserId, userId));
        for (UserBlock block : blocks) {
            if (block.getBlockedUserId() != null && !result.contains(block.getBlockedUserId())) {
                result.add(block.getBlockedUserId());
            }
        }
        List<UserBlock> blockedMe = userBlockMapper.selectList(new LambdaQueryWrapper<UserBlock>()
                .eq(UserBlock::getBlockedUserId, userId));
        for (UserBlock block : blockedMe) {
            if (block.getUserId() != null && !result.contains(block.getUserId())) {
                result.add(block.getUserId());
            }
        }
        return result;
    }

    private List<Long> blockedWorkIds(Long userId) {
        List<Long> result = new ArrayList<Long>();
        if (userId == null) {
            return result;
        }
        List<UserTagBlock> blocks = userTagBlockMapper.selectList(new LambdaQueryWrapper<UserTagBlock>()
                .eq(UserTagBlock::getUserId, userId));
        if (blocks.isEmpty()) {
            blocks = new ArrayList<UserTagBlock>();
        }
        List<Long> tagIds = new ArrayList<Long>();
        for (UserTagBlock block : blocks) {
            if (block.getTagId() != null && !tagIds.contains(block.getTagId())) {
                tagIds.add(block.getTagId());
            }
        }
        if (settingService.negativeTagFilterActive()) for (String name : settingService.negativeTagNames()) { Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name).last("limit 1")); if (tag != null && tag.getId() != null && !tagIds.contains(tag.getId())) tagIds.add(tag.getId()); }
        if (tagIds.isEmpty()) {
            return result;
        }
        List<WorkTag> workTags = workTagMapper.selectList(new LambdaQueryWrapper<WorkTag>().in(WorkTag::getTagId, tagIds));
        for (WorkTag workTag : workTags) {
            if (workTag.getWorkId() != null && !result.contains(workTag.getWorkId())) {
                result.add(workTag.getWorkId());
            }
        }
        return result;
    }

    private List<Long> configuredNegativeWorkIds() {
        List<Long> result = new ArrayList<Long>();
        if (!settingService.negativeTagFilterActive()) return result;
        List<Long> tagIds = new ArrayList<Long>();
        for (String name : settingService.negativeTagNames()) { Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name).last("limit 1")); if (tag != null && tag.getId() != null) tagIds.add(tag.getId()); }
        if (tagIds.isEmpty()) return result;
        for (WorkTag row : workTagMapper.selectList(new LambdaQueryWrapper<WorkTag>().in(WorkTag::getTagId, tagIds))) if (row.getWorkId()!=null&&!result.contains(row.getWorkId())) result.add(row.getWorkId());
        return result;
    }

    private List<String> normalizeMediaUrls(PublishWorkRequest request) {
        List<String> urls = new ArrayList<String>();
        if (request.getMediaUrls() != null) {
            for (String url : request.getMediaUrls()) {
                if (StringUtils.hasText(url)) {
                    urls.add(url.trim());
                }
            }
        }
        if (urls.isEmpty() && StringUtils.hasText(request.getMediaUrl())) {
            urls.add(request.getMediaUrl().trim());
        }
        String type = request.getType();
        if ("video".equalsIgnoreCase(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "视频功能已下线，请改用图片或文章发布");
        }
        if ("article".equals(type) || "status".equals(type)) {
            return new ArrayList<String>();
        }
        if ("image".equals(type)) {
            if (urls.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "图片作品至少上传1张图片");
            }
            if (urls.size() > 6) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "图片作品最多上传6张图片");
            }
            for (String url : urls) {
                if (!isImageUrl(url)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "图片作品只能上传图片格式文件");
                }
            }
            return urls;
        }
        if (!urls.isEmpty()) {
            return urls;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "该类型作品需要先上传文件");
    }

    private String join(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (String url : urls) {
            if (builder.length() > 0) {
                builder.append(",");
            }
            builder.append(url);
        }
        return builder.toString();
    }

    private String resolveCover(PublishWorkRequest request, List<String> mediaUrls) {
        if ("article".equals(request.getType()) || "status".equals(request.getType())) {
            return null;
        }
        if (StringUtils.hasText(request.getCover())) {
            return request.getCover();
        }
        if ("image".equals(request.getType()) && mediaUrls != null && !mediaUrls.isEmpty()) {
            return mediaUrls.get(0);
        }
        return defaultCover(request.getType());
    }

    private String defaultCover(String type) {
        if ("video".equals(type)) {
            return "asset_city";
        }
        return "asset_lake";
    }

    private boolean isImageUrl(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp");
    }

    private boolean isVideoUrl(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".m4v")
                || lower.endsWith(".webm") || lower.endsWith(".3gp") || lower.endsWith(".mkv");
    }

    private int safePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }
}
