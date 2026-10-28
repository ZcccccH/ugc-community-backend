package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.PageResult;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.entity.FavoriteCollection;
import com.ugccommunity.backend.entity.FavoriteCollectionItem;
import com.ugccommunity.backend.entity.UserFavorite;
import com.ugccommunity.backend.entity.Work;
import com.ugccommunity.backend.mapper.FavoriteCollectionItemMapper;
import com.ugccommunity.backend.mapper.FavoriteCollectionMapper;
import com.ugccommunity.backend.mapper.UserFavoriteMapper;
import com.ugccommunity.backend.mapper.WorkMapper;
import com.ugccommunity.backend.vo.FavoriteCollectionVO;
import com.ugccommunity.backend.vo.WorkVO;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class FavoriteCollectionService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FavoriteCollectionMapper collectionMapper;
    private final FavoriteCollectionItemMapper itemMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final WorkMapper workMapper;
    private final WorkService workService;

    public FavoriteCollectionService(
            FavoriteCollectionMapper collectionMapper,
            FavoriteCollectionItemMapper itemMapper,
            UserFavoriteMapper userFavoriteMapper,
            WorkMapper workMapper,
            @Lazy WorkService workService
    ) {
        this.collectionMapper = collectionMapper;
        this.itemMapper = itemMapper;
        this.userFavoriteMapper = userFavoriteMapper;
        this.workMapper = workMapper;
        this.workService = workService;
    }

    public List<FavoriteCollectionVO> mine(Long userId) {
        List<FavoriteCollection> rows = collectionMapper.selectList(new LambdaQueryWrapper<FavoriteCollection>()
                .eq(FavoriteCollection::getUserId, userId)
                .eq(FavoriteCollection::getIsDeleted, 0)
                .orderByDesc(FavoriteCollection::getCreatedAt)
                .orderByAsc(FavoriteCollection::getId));
        List<FavoriteCollectionVO> result = new ArrayList<FavoriteCollectionVO>();
        for (FavoriteCollection row : rows) {
            result.add(toVO(row));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public FavoriteCollectionVO create(Long userId, String rawName) {
        String name = checkName(userId, rawName, null);
        FavoriteCollection collection = new FavoriteCollection();
        collection.setUserId(userId);
        collection.setName(name);
        collection.setIsDeleted(0);
        collection.setCreatedAt(LocalDateTime.now());
        collectionMapper.insert(collection);
        return toVO(collection);
    }

    /** Only your own folders can be renamed; same name rules as creation, no duplicate of your other folders, unchanged name returns immediately */
    @Transactional(rollbackFor = Exception.class)
    public FavoriteCollectionVO rename(Long userId, Long collectionId, String rawName) {
        FavoriteCollection collection = requireOwnCollection(userId, collectionId);
        String name = checkName(userId, rawName, collectionId);
        if (!name.equals(collection.getName())) {
            collectionMapper.update(null, new LambdaUpdateWrapper<FavoriteCollection>()
                    .eq(FavoriteCollection::getId, collectionId)
                    .eq(FavoriteCollection::getUserId, userId)
                    .eq(FavoriteCollection::getIsDeleted, 0)
                    .set(FavoriteCollection::getName, name));
            collection.setName(name);
        }
        return toVO(collection);
    }

    /** After trimming: not empty, at most 30 characters, not a duplicate of your other folders (excludeId is the folder being renamed) */
    private String checkName(Long userId, String rawName, Long excludeId) {
        String name = rawName == null ? "" : rawName.trim();
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "收藏夹名称不能为空");
        }
        if (name.length() > 30) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "收藏夹名称不能超过30个字符");
        }
        LambdaQueryWrapper<FavoriteCollection> sameName = new LambdaQueryWrapper<FavoriteCollection>()
                .eq(FavoriteCollection::getUserId, userId)
                .eq(FavoriteCollection::getIsDeleted, 0)
                .eq(FavoriteCollection::getName, name);
        if (excludeId != null) {
            sameName.ne(FavoriteCollection::getId, excludeId);
        }
        if (collectionMapper.selectCount(sameName) > 0) {
            throw new BusinessException(ResultCode.DATA_EXIST, "收藏夹名称已存在");
        }
        return name;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteOwn(Long userId, Long collectionId) {
        requireOwnCollection(userId, collectionId);
        collectionMapper.update(null, new LambdaUpdateWrapper<FavoriteCollection>()
                .eq(FavoriteCollection::getId, collectionId)
                .eq(FavoriteCollection::getUserId, userId)
                .set(FavoriteCollection::getIsDeleted, 1));
        itemMapper.delete(new LambdaQueryWrapper<FavoriteCollectionItem>()
                .eq(FavoriteCollectionItem::getCollectionId, collectionId)
                .eq(FavoriteCollectionItem::getUserId, userId));
    }

    public PageResult<WorkVO> works(Long userId, Long collectionId, Integer pageNum, Integer pageSize) {
        requireOwnCollection(userId, collectionId);
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        List<FavoriteCollectionItem> items = itemMapper.selectList(new LambdaQueryWrapper<FavoriteCollectionItem>()
                .eq(FavoriteCollectionItem::getCollectionId, collectionId)
                .eq(FavoriteCollectionItem::getUserId, userId)
                .orderByDesc(FavoriteCollectionItem::getCreatedAt));
        List<Work> works = new ArrayList<Work>();
        for (FavoriteCollectionItem item : items) {
            Work work = workMapper.selectOne(new LambdaQueryWrapper<Work>()
                    .eq(Work::getId, item.getWorkId())
                    .eq(Work::getIsDeleted, 0)
                    .eq(Work::getStatus, "published")
                    .last("limit 1"));
            if (work != null && !workService.isBlockedWorkForUser(userId, work)) {
                works.add(work);
            }
        }
        int total = works.size();
        int fromIndex = (safePageNum - 1) * safePageSize;
        if (fromIndex >= total) {
            return PageResult.of((long) total, safePageNum, safePageSize, new ArrayList<WorkVO>());
        }
        int toIndex = Math.min(fromIndex + safePageSize, total);
        return PageResult.of((long) total, safePageNum, safePageSize, workService.toListVOs(works.subList(fromIndex, toIndex), userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void addWork(Long userId, Long collectionId, Long workId) {
        requireOwnCollection(userId, collectionId);
        requirePublishedWork(workId);
        ensureFavorite(userId, workId);
        Long exists = itemMapper.selectCount(new LambdaQueryWrapper<FavoriteCollectionItem>()
                .eq(FavoriteCollectionItem::getCollectionId, collectionId)
                .eq(FavoriteCollectionItem::getUserId, userId)
                .eq(FavoriteCollectionItem::getWorkId, workId));
        if (exists > 0) {
            return;
        }
        FavoriteCollectionItem item = new FavoriteCollectionItem();
        item.setCollectionId(collectionId);
        item.setUserId(userId);
        item.setWorkId(workId);
        item.setCreatedAt(LocalDateTime.now());
        itemMapper.insert(item);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeWork(Long userId, Long collectionId, Long workId) {
        requireOwnCollection(userId, collectionId);
        itemMapper.delete(new LambdaQueryWrapper<FavoriteCollectionItem>()
                .eq(FavoriteCollectionItem::getCollectionId, collectionId)
                .eq(FavoriteCollectionItem::getUserId, userId)
                .eq(FavoriteCollectionItem::getWorkId, workId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncWorkCollections(Long userId, Long workId, List<Long> collectionIds) {
        requirePublishedWork(workId);
        ensureFavorite(userId, workId);
        itemMapper.delete(new LambdaQueryWrapper<FavoriteCollectionItem>()
                .eq(FavoriteCollectionItem::getUserId, userId)
                .eq(FavoriteCollectionItem::getWorkId, workId));
        if (collectionIds == null) {
            return;
        }
        for (Long collectionId : collectionIds) {
            if (collectionId != null) {
                addWork(userId, collectionId, workId);
            }
        }
    }

    public List<Long> workCollectionIds(Long userId, Long workId) {
        List<FavoriteCollectionItem> items = itemMapper.selectList(new LambdaQueryWrapper<FavoriteCollectionItem>()
                .eq(FavoriteCollectionItem::getUserId, userId)
                .eq(FavoriteCollectionItem::getWorkId, workId));
        List<Long> result = new ArrayList<Long>();
        for (FavoriteCollectionItem item : items) {
            FavoriteCollection collection = collectionMapper.selectOne(new LambdaQueryWrapper<FavoriteCollection>()
                    .eq(FavoriteCollection::getId, item.getCollectionId())
                    .eq(FavoriteCollection::getUserId, userId)
                    .eq(FavoriteCollection::getIsDeleted, 0)
                    .last("limit 1"));
            if (collection != null && !result.contains(item.getCollectionId())) {
                result.add(item.getCollectionId());
            }
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void clearWorkFromAllCollections(Long userId, Long workId) {
        itemMapper.delete(new LambdaQueryWrapper<FavoriteCollectionItem>()
                .eq(FavoriteCollectionItem::getUserId, userId)
                .eq(FavoriteCollectionItem::getWorkId, workId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void clearDeletedWork(Long workId) {
        itemMapper.delete(new LambdaQueryWrapper<FavoriteCollectionItem>()
                .eq(FavoriteCollectionItem::getWorkId, workId));
    }

    private FavoriteCollection requireOwnCollection(Long userId, Long collectionId) {
        FavoriteCollection collection = collectionMapper.selectOne(new LambdaQueryWrapper<FavoriteCollection>()
                .eq(FavoriteCollection::getId, collectionId)
                .eq(FavoriteCollection::getUserId, userId)
                .eq(FavoriteCollection::getIsDeleted, 0)
                .last("limit 1"));
        if (collection == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "收藏夹不存在");
        }
        return collection;
    }

    private Work requirePublishedWork(Long workId) {
        Work work = workMapper.selectOne(new LambdaQueryWrapper<Work>()
                .eq(Work::getId, workId)
                .eq(Work::getIsDeleted, 0)
                .eq(Work::getStatus, "published")
                .last("limit 1"));
        if (work == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "作品不存在");
        }
        return work;
    }

    /** Favorite the work before adding it to a folder; goes through WorkService.setFavorite, so a new favorite also likes it and sends a notification */
    private void ensureFavorite(Long userId, Long workId) {
        Long count = userFavoriteMapper.selectCount(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getWorkId, workId));
        if (count > 0) {
            return;
        }
        workService.setFavorite(userId, workId, true);
    }

    private FavoriteCollectionVO toVO(FavoriteCollection collection) {
        FavoriteCollectionVO vo = new FavoriteCollectionVO();
        vo.setId(collection.getId());
        vo.setName(collection.getName());
        vo.setCreatedAt(collection.getCreatedAt() == null ? null : collection.getCreatedAt().format(FORMATTER));
        vo.setWorkCount(itemMapper.selectCount(new LambdaQueryWrapper<FavoriteCollectionItem>()
                .eq(FavoriteCollectionItem::getCollectionId, collection.getId())
                .eq(FavoriteCollectionItem::getUserId, collection.getUserId())).intValue());
        return vo;
    }

    private int safePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
    }
}
