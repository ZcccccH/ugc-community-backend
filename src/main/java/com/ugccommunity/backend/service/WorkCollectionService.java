package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.entity.Work;
import com.ugccommunity.backend.entity.WorkCollection;
import com.ugccommunity.backend.entity.WorkCollectionItem;
import com.ugccommunity.backend.mapper.WorkCollectionItemMapper;
import com.ugccommunity.backend.mapper.WorkCollectionMapper;
import com.ugccommunity.backend.mapper.WorkMapper;
import com.ugccommunity.backend.vo.WorkCollectionVO;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.ugccommunity.backend.common.result.PageResult;
import com.ugccommunity.backend.entity.WorkCollectionFavorite;
import com.ugccommunity.backend.mapper.WorkCollectionFavoriteMapper;
import com.ugccommunity.backend.vo.WorkCollectionBriefVO;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class WorkCollectionService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final WorkCollectionMapper collectionMapper;
    private final WorkCollectionItemMapper itemMapper;
    private final WorkMapper workMapper;
    private final WorkCollectionFavoriteMapper favoriteMapper;
    private final WorkService workService;

    public WorkCollectionService(WorkCollectionMapper collectionMapper, WorkCollectionItemMapper itemMapper, WorkMapper workMapper,
                                 WorkCollectionFavoriteMapper favoriteMapper, @Lazy WorkService workService) {
        this.collectionMapper = collectionMapper;
        this.itemMapper = itemMapper;
        this.workMapper = workMapper;
        this.favoriteMapper = favoriteMapper;
        this.workService = workService;
    }

    public List<WorkCollectionVO> mine(Long userId) {
        List<WorkCollection> rows = collectionMapper.selectList(new LambdaQueryWrapper<WorkCollection>()
                .eq(WorkCollection::getUserId, userId)
                .eq(WorkCollection::getIsDeleted, 0)
                .orderByDesc(WorkCollection::getCreatedAt)
                .orderByAsc(WorkCollection::getId));
        List<WorkCollectionVO> result = new ArrayList<WorkCollectionVO>();
        for (WorkCollection row : rows) {
            result.add(toVO(row));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkCollectionVO create(Long userId, String rawName) {
        String name = checkName(userId, rawName, null);
        WorkCollection collection = new WorkCollection();
        collection.setUserId(userId);
        collection.setName(name);
        collection.setIsDeleted(0);
        collection.setVisibility(WorkCollection.PUBLIC);
        collection.setFavCount(0);
        collection.setCreatedAt(LocalDateTime.now());
        collection.setUpdatedAt(collection.getCreatedAt());
        collectionMapper.insert(collection);
        return toVO(collection);
    }

    @Transactional(rollbackFor = Exception.class)
    public void attachWork(Long userId, Long workId, List<Long> collectionIds) {
        if (collectionIds == null || collectionIds.isEmpty()) return;
        boolean anyPrivate = false;
        for (Long collectionId : collectionIds) {
            if (collectionId == null) continue;
            WorkCollection collection = collectionMapper.selectOne(new LambdaQueryWrapper<WorkCollection>()
                    .eq(WorkCollection::getId, collectionId)
                    .eq(WorkCollection::getUserId, userId)
                    .eq(WorkCollection::getIsDeleted, 0)
                    .last("limit 1"));
            if (collection == null) {
                throw new BusinessException(ResultCode.NO_PERMISSION, "只能加入自己的合辑");
            }
            if (!collection.isPublic()) {
                anyPrivate = true;
            }
            Long exists = itemMapper.selectCount(new LambdaQueryWrapper<WorkCollectionItem>()
                    .eq(WorkCollectionItem::getCollectionId, collectionId)
                    .eq(WorkCollectionItem::getWorkId, workId));
            if (exists > 0) continue;
            WorkCollectionItem item = new WorkCollectionItem();
            item.setCollectionId(collectionId);
            item.setWorkId(workId);
            item.setCreatedAt(LocalDateTime.now());
            itemMapper.insert(item);
            touch(collectionId);
        }
        if (anyPrivate) {
            workMapper.update(null, new LambdaUpdateWrapper<Work>()
                    .eq(Work::getId, workId)
                    .eq(Work::getUserId, userId)
                    .ne(Work::getStatus, "deleted")
                    .set(Work::getStatus, "hidden"));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void addWork(Long userId, Long collectionId, Long workId) {
        WorkCollection collection = collectionMapper.selectOne(new LambdaQueryWrapper<WorkCollection>()
                .eq(WorkCollection::getId, collectionId)
                .eq(WorkCollection::getUserId, userId)
                .eq(WorkCollection::getIsDeleted, 0)
                .last("limit 1"));
        if (collection == null) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "只能加入自己的合辑");
        }
        Work work = workMapper.selectById(workId);
        if (work == null || (work.getIsDeleted() != null && work.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "作品不存在");
        }
        if (!userId.equals(work.getUserId())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "只能将自己的作品加入合辑");
        }
        Long exists = itemMapper.selectCount(new LambdaQueryWrapper<WorkCollectionItem>()
                .eq(WorkCollectionItem::getCollectionId, collectionId)
                .eq(WorkCollectionItem::getWorkId, workId));
        if (exists > 0) return;

        WorkCollectionItem item = new WorkCollectionItem();
        item.setCollectionId(collectionId);
        item.setWorkId(workId);
        item.setCreatedAt(LocalDateTime.now());
        itemMapper.insert(item);
        touch(collectionId);

        if (!collection.isPublic()) {
            workMapper.update(null, new LambdaUpdateWrapper<Work>()
                    .eq(Work::getId, workId)
                    .eq(Work::getUserId, userId)
                    .ne(Work::getStatus, "deleted")
                    .set(Work::getStatus, "hidden"));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeWork(Long userId, Long collectionId, Long workId) {
        WorkCollection collection = collectionMapper.selectOne(new LambdaQueryWrapper<WorkCollection>()
                .eq(WorkCollection::getId, collectionId)
                .eq(WorkCollection::getUserId, userId)
                .eq(WorkCollection::getIsDeleted, 0)
                .last("limit 1"));
        if (collection == null) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "只能管理自己的合辑");
        }
        int deleted = itemMapper.delete(new LambdaQueryWrapper<WorkCollectionItem>()
                .eq(WorkCollectionItem::getCollectionId, collectionId)
                .eq(WorkCollectionItem::getWorkId, workId));
        if (deleted > 0) {
            touch(collectionId);
        }
    }

    /** 修改作品时同步合辑归属：移出未勾选的已有合辑，加入新勾选的合辑 */
    @Transactional(rollbackFor = Exception.class)
    public void syncWorkCollections(Long userId, Long workId, List<Long> collectionIds) {
        if (collectionIds == null) return;
        java.util.Set<Long> targetCollectionIds = new java.util.HashSet<Long>();
        for (Long id : collectionIds) {
            if (id != null && id > 0) {
                targetCollectionIds.add(id);
            }
        }

        boolean anyPrivate = false;
        for (Long collectionId : targetCollectionIds) {
            WorkCollection collection = collectionMapper.selectOne(new LambdaQueryWrapper<WorkCollection>()
                    .eq(WorkCollection::getId, collectionId)
                    .eq(WorkCollection::getUserId, userId)
                    .eq(WorkCollection::getIsDeleted, 0)
                    .last("limit 1"));
            if (collection == null) {
                throw new BusinessException(ResultCode.NO_PERMISSION, "只能加入自己的合辑");
            }
            if (!collection.isPublic()) {
                anyPrivate = true;
            }
        }

        List<WorkCollection> myCollections = collectionMapper.selectList(new LambdaQueryWrapper<WorkCollection>()
                .eq(WorkCollection::getUserId, userId)
                .eq(WorkCollection::getIsDeleted, 0));
        List<Long> myCollectionIds = new ArrayList<Long>();
        for (WorkCollection c : myCollections) {
            myCollectionIds.add(c.getId());
        }

        if (!myCollectionIds.isEmpty()) {
            List<WorkCollectionItem> existingItems = itemMapper.selectList(new LambdaQueryWrapper<WorkCollectionItem>()
                    .eq(WorkCollectionItem::getWorkId, workId)
                    .in(WorkCollectionItem::getCollectionId, myCollectionIds));

            java.util.Set<Long> existingCollectionIds = new java.util.HashSet<Long>();
            for (WorkCollectionItem item : existingItems) {
                existingCollectionIds.add(item.getCollectionId());
                if (!targetCollectionIds.contains(item.getCollectionId())) {
                    itemMapper.delete(new LambdaQueryWrapper<WorkCollectionItem>()
                            .eq(WorkCollectionItem::getCollectionId, item.getCollectionId())
                            .eq(WorkCollectionItem::getWorkId, workId));
                    touch(item.getCollectionId());
                }
            }

            for (Long targetId : targetCollectionIds) {
                if (!existingCollectionIds.contains(targetId)) {
                    WorkCollectionItem item = new WorkCollectionItem();
                    item.setCollectionId(targetId);
                    item.setWorkId(workId);
                    item.setCreatedAt(LocalDateTime.now());
                    itemMapper.insert(item);
                    touch(targetId);
                }
            }
        }

        if (anyPrivate) {
            workMapper.update(null, new LambdaUpdateWrapper<Work>()
                    .eq(Work::getId, workId)
                    .eq(Work::getUserId, userId)
                    .ne(Work::getStatus, "deleted")
                    .set(Work::getStatus, "hidden"));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteOwn(Long userId, Long collectionId) {
        WorkCollection collection = collectionMapper.selectOne(new LambdaQueryWrapper<WorkCollection>()
                .eq(WorkCollection::getId, collectionId)
                .eq(WorkCollection::getUserId, userId)
                .eq(WorkCollection::getIsDeleted, 0)
                .last("limit 1"));
        if (collection == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "合辑不存在");
        }
        collectionMapper.update(null, new LambdaUpdateWrapper<WorkCollection>()
                .eq(WorkCollection::getId, collectionId)
                .eq(WorkCollection::getUserId, userId)
                .set(WorkCollection::getIsDeleted, 1));
        itemMapper.delete(new LambdaQueryWrapper<WorkCollectionItem>()
                .eq(WorkCollectionItem::getCollectionId, collectionId));
    }

    private WorkCollectionVO toVO(WorkCollection collection) {
        WorkCollectionVO vo = new WorkCollectionVO();
        vo.setId(collection.getId());
        vo.setName(collection.getName());
        vo.setCreatedAt(collection.getCreatedAt() == null ? null : collection.getCreatedAt().format(FORMATTER));
        vo.setVisibility(collection.isPublic() ? WorkCollection.PUBLIC : WorkCollection.PRIVATE);
        vo.setFavCount(collection.getFavCount() == null ? 0 : collection.getFavCount());
        vo.setIsOwner(true);
        vo.setUpdatedAt(format(collection.getUpdatedAt()));
        List<WorkCollectionItem> items = itemMapper.selectList(new LambdaQueryWrapper<WorkCollectionItem>()
                .eq(WorkCollectionItem::getCollectionId, collection.getId()));
        List<Long> workIds = new ArrayList<Long>();
        for (WorkCollectionItem item : items) {
            if (item.getWorkId() != null && !workIds.contains(item.getWorkId())) {
                workIds.add(item.getWorkId());
            }
        }
        if (workIds.isEmpty()) {
            vo.setWorkCount(0);
        } else {
            vo.setWorkCount(workMapper.selectCount(new LambdaQueryWrapper<Work>()
                    .in(Work::getId, workIds)
                    .eq(Work::getIsDeleted, 0)
                    .ne(Work::getStatus, "deleted")).intValue());
        }
        return vo;
    }

    // ------------------------------------------------------------------ 收藏别人的合辑

    private static String format(LocalDateTime t) {
        return t == null ? null : t.format(FORMATTER);
    }

    private static boolean deleted(WorkCollection c) {
        return c == null || (c.getIsDeleted() != null && c.getIsDeleted() != 0);
    }

    private static boolean isOwner(Long viewerId, WorkCollection c) {
        return viewerId != null && c != null && viewerId.equals(c.getUserId());
    }

    /** 名称规则（新建、改名共用）：去掉首尾空格后不能为空、不超过 30 个字、不和自己其他合辑重名 */
    private String checkName(Long userId, String rawName, Long excludeId) {
        String name = rawName == null ? "" : rawName.trim();
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "合辑名称不能为空");
        }
        if (name.length() > 30) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "合辑名称不能超过30个字符");
        }
        LambdaQueryWrapper<WorkCollection> same = new LambdaQueryWrapper<WorkCollection>()
                .eq(WorkCollection::getUserId, userId)
                .eq(WorkCollection::getIsDeleted, 0)
                .eq(WorkCollection::getName, name);
        if (excludeId != null) {
            same.ne(WorkCollection::getId, excludeId);
        }
        if (collectionMapper.selectCount(same) > 0) {
            throw new BusinessException(ResultCode.DATA_EXIST, "合辑名称已存在");
        }
        return name;
    }

    /** 合辑里加入或移出作品后记一下时间 */
    void touch(Long collectionId) {
        collectionMapper.update(null, new LambdaUpdateWrapper<WorkCollection>()
                .eq(WorkCollection::getId, collectionId)
                .set(WorkCollection::getUpdatedAt, LocalDateTime.now()));
    }

    /** 作者自己随时可见；别人只能看未删除的公开合辑，否则当作不存在 */
    public WorkCollection requireVisible(Long viewerId, Long collectionId) {
        WorkCollection c = collectionId == null ? null : collectionMapper.selectById(collectionId);
        if (deleted(c) || (!isOwner(viewerId, c) && !c.isPublic())) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "合辑不存在");
        }
        return c;
    }

    private boolean favorited(Long viewerId, Long collectionId) {
        return viewerId != null && favoriteMapper.selectCount(new LambdaQueryWrapper<WorkCollectionFavorite>()
                .eq(WorkCollectionFavorite::getUserId, viewerId)
                .eq(WorkCollectionFavorite::getCollectionId, collectionId)) > 0;
    }

    /** 给某个人看的合辑信息：作品数和封面只算他看得到的作品 */
    private WorkCollectionVO viewVO(WorkCollection c, Long viewerId) {
        boolean owner = isOwner(viewerId, c);
        List<Work> visible = workService.visibleCollectionWorks(viewerId, c.getId(), owner);
        WorkCollectionVO vo = new WorkCollectionVO();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setCreatedAt(format(c.getCreatedAt()));
        vo.setUpdatedAt(format(c.getUpdatedAt()));
        vo.setWorkCount(visible.size());
        vo.setAuthor(workService.briefUser(c.getUserId()));
        vo.setFavCount(c.getFavCount() == null ? 0 : c.getFavCount());
        vo.setFavorited(favorited(viewerId, c.getId()));
        vo.setIsOwner(owner);
        vo.setVisibility(c.isPublic() ? WorkCollection.PUBLIC : WorkCollection.PRIVATE);
        vo.setCover(visible.isEmpty() ? null : workService.coverOf(visible.get(0)));
        vo.setInvalid(false);
        return vo;
    }

    public WorkCollectionVO detail(Long viewerId, Long collectionId) {
        return viewVO(requireVisible(viewerId, collectionId), viewerId);
    }

    /** 某个作者的合辑：自己看全部；别人只看公开、且至少有一篇看得到的作品的合辑 */
    public List<WorkCollectionVO> userCollections(Long viewerId, Long userId) {
        List<WorkCollection> rows = collectionMapper.selectList(new LambdaQueryWrapper<WorkCollection>()
                .eq(WorkCollection::getUserId, userId)
                .eq(WorkCollection::getIsDeleted, 0)
                .orderByDesc(WorkCollection::getCreatedAt)
                .orderByAsc(WorkCollection::getId));
        List<WorkCollectionVO> result = new ArrayList<WorkCollectionVO>();
        for (WorkCollection row : rows) {
            boolean owner = isOwner(viewerId, row);
            if (!owner && !row.isPublic()) continue;
            WorkCollectionVO vo = viewVO(row, viewerId);
            if (!owner && vo.getWorkCount() == 0) continue;
            result.add(vo);
        }
        return result;
    }

    /**
     * 设成收藏 / 不收藏（重复请求不会重复计数，收藏数不会小于 0）。
     * 不能收藏自己的、私密的、已删除的合辑；取消收藏总是可以（包括已失效的）。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkCollectionVO setFavorite(Long viewerId, Long collectionId, Boolean favorited) {
        boolean want = favorited == null || favorited;
        WorkCollection c = collectionId == null ? null : collectionMapper.selectById(collectionId);
        if (want) {
            if (deleted(c)) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "合辑不存在或已被作者删除");
            if (isOwner(viewerId, c)) throw new BusinessException(ResultCode.BUSINESS_ERROR, "不能收藏自己的合辑");
            if (!c.isPublic()) throw new BusinessException(ResultCode.BUSINESS_ERROR, "作者已把这个合辑设为私密，暂时不能收藏");
            if (!favorited(viewerId, collectionId)) {
                WorkCollectionFavorite row = new WorkCollectionFavorite();
                row.setUserId(viewerId);
                row.setCollectionId(collectionId);
                row.setCreatedAt(LocalDateTime.now());
                try {
                    favoriteMapper.insert(row);
                    collectionMapper.update(null, new LambdaUpdateWrapper<WorkCollection>()
                            .eq(WorkCollection::getId, collectionId)
                            .setSql("fav_count = fav_count + 1"));
                } catch (DuplicateKeyException ignored) {
                    // 并发的重复收藏：已经收藏过了
                }
            }
        } else {
            int removed = favoriteMapper.delete(new LambdaQueryWrapper<WorkCollectionFavorite>()
                    .eq(WorkCollectionFavorite::getUserId, viewerId)
                    .eq(WorkCollectionFavorite::getCollectionId, collectionId));
            if (removed > 0 && c != null) {
                collectionMapper.update(null, new LambdaUpdateWrapper<WorkCollection>()
                        .eq(WorkCollection::getId, collectionId)
                        .setSql("fav_count = greatest(fav_count - 1, 0)"));
            }
        }
        WorkCollection fresh = collectionId == null ? null : collectionMapper.selectById(collectionId);
        if (deleted(fresh) || (!isOwner(viewerId, fresh) && !fresh.isPublic())) {
            return invalidVO(collectionId, fresh, favorited(viewerId, collectionId));
        }
        return viewVO(fresh, viewerId);
    }

    private WorkCollectionVO invalidVO(Long id, WorkCollection c, boolean favorited) {
        WorkCollectionVO vo = new WorkCollectionVO();
        vo.setId(id);
        vo.setName(c == null ? "" : c.getName());
        vo.setWorkCount(0);
        vo.setAuthor(c == null ? null : workService.briefUser(c.getUserId()));
        vo.setFavCount(c == null || c.getFavCount() == null ? 0 : c.getFavCount());
        vo.setFavorited(favorited);
        vo.setIsOwner(false);
        vo.setVisibility(c == null || c.isPublic() ? WorkCollection.PUBLIC : WorkCollection.PRIVATE);
        vo.setInvalid(true);
        return vo;
    }

    /** 我收藏的合辑，最近收藏的在前；已删除或改成私密的照样返回（invalid = true），方便取消收藏 */
    public PageResult<WorkCollectionVO> favorites(Long viewerId, Integer pageNum, Integer pageSize) {
        int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        List<WorkCollectionFavorite> rows = favoriteMapper.selectList(new LambdaQueryWrapper<WorkCollectionFavorite>()
                .eq(WorkCollectionFavorite::getUserId, viewerId)
                .orderByDesc(WorkCollectionFavorite::getCreatedAt)
                .orderByDesc(WorkCollectionFavorite::getCollectionId));
        List<WorkCollectionVO> records = new ArrayList<WorkCollectionVO>();
        int from = (page - 1) * size;
        for (int i = from; i < Math.min(from + size, rows.size()); i++) {
            Long id = rows.get(i).getCollectionId();
            WorkCollection c = collectionMapper.selectById(id);
            boolean invalid = deleted(c) || (!isOwner(viewerId, c) && !c.isPublic());
            records.add(invalid ? invalidVO(id, c, true) : viewVO(c, viewerId));
        }
        return PageResult.of((long) rows.size(), page, size, records);
    }

    /** 作者修改名称或公开 / 私密；名称规则同新建 */
    @Transactional(rollbackFor = Exception.class)
    public WorkCollectionVO update(Long userId, Long collectionId, String rawName, String visibility) {
        WorkCollection c = collectionMapper.selectById(collectionId);
        if (deleted(c) || !isOwner(userId, c)) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "合辑不存在");
        }
        LambdaUpdateWrapper<WorkCollection> update = new LambdaUpdateWrapper<WorkCollection>()
                .eq(WorkCollection::getId, collectionId)
                .eq(WorkCollection::getUserId, userId);
        boolean changed = false;
        if (rawName != null) {
            String name = checkName(userId, rawName, collectionId);
            update.set(WorkCollection::getName, name);
            changed = true;
        }
        if (visibility != null) {
            String v = visibility.trim().toLowerCase();
            if (!WorkCollection.PUBLIC.equals(v) && !WorkCollection.PRIVATE.equals(v)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "可见范围只能是公开或私密");
            }
            update.set(WorkCollection::getVisibility, v);
            changed = true;
            List<WorkCollectionItem> items = itemMapper.selectList(new LambdaQueryWrapper<WorkCollectionItem>()
                    .eq(WorkCollectionItem::getCollectionId, collectionId));
            List<Long> workIds = new ArrayList<Long>();
            for (WorkCollectionItem item : items) {
                if (item.getWorkId() != null && !workIds.contains(item.getWorkId())) {
                    workIds.add(item.getWorkId());
                }
            }
            if (!workIds.isEmpty()) {
                if (WorkCollection.PRIVATE.equals(v)) {
                    workMapper.update(null, new LambdaUpdateWrapper<Work>()
                            .in(Work::getId, workIds)
                            .eq(Work::getUserId, userId)
                            .ne(Work::getStatus, "deleted")
                            .set(Work::getStatus, "hidden"));
                } else if (WorkCollection.PUBLIC.equals(v)) {
                    workMapper.update(null, new LambdaUpdateWrapper<Work>()
                            .in(Work::getId, workIds)
                            .eq(Work::getUserId, userId)
                            .eq(Work::getStatus, "hidden")
                            .isNull(Work::getAuditReason)
                            .set(Work::getStatus, "published"));
                }
            }
        }
        if (changed) collectionMapper.update(null, update);
        return detail(userId, collectionId);
    }

    /** 作品详情里的「所属合辑」：别人只看到公开的，作者自己连私密的也看到；作品数按当前用户能看到的算 */
    public List<WorkCollectionBriefVO> collectionsForWork(Long workId, Long viewerId) {
        List<WorkCollectionItem> items = itemMapper.selectList(new LambdaQueryWrapper<WorkCollectionItem>()
                .eq(WorkCollectionItem::getWorkId, workId));
        List<WorkCollectionBriefVO> result = new ArrayList<WorkCollectionBriefVO>();
        for (WorkCollectionItem item : items) {
            WorkCollection c = collectionMapper.selectById(item.getCollectionId());
            if (deleted(c)) continue;
            boolean owner = isOwner(viewerId, c);
            if (!owner && !c.isPublic()) continue;
            result.add(new WorkCollectionBriefVO(c.getId(), c.getName(), workService.visibleCollectionWorks(viewerId, c.getId(), owner).size()));
        }
        return result;
    }
}
