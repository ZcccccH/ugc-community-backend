package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ugccommunity.backend.entity.FavoriteCollectionItem;
import com.ugccommunity.backend.entity.User;
import com.ugccommunity.backend.entity.UserFavorite;
import com.ugccommunity.backend.entity.UserFollow;
import com.ugccommunity.backend.entity.UserLike;
import com.ugccommunity.backend.entity.UserPin;
import com.ugccommunity.backend.entity.Work;
import com.ugccommunity.backend.entity.WorkCollectionItem;
import com.ugccommunity.backend.mapper.FavoriteCollectionItemMapper;
import com.ugccommunity.backend.mapper.UserFavoriteMapper;
import com.ugccommunity.backend.mapper.UserFollowMapper;
import com.ugccommunity.backend.mapper.UserLikeMapper;
import com.ugccommunity.backend.mapper.UserMapper;
import com.ugccommunity.backend.mapper.UserPinMapper;
import com.ugccommunity.backend.mapper.WorkCollectionItemMapper;
import com.ugccommunity.backend.mapper.WorkMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * When a user is deleted, also clean up what they left behind. Previously deleting a user only marked the account as deleted,
 * while their works stayed public (with an empty author name) and their follows and likes remained.
 *
 * <ul>
 *   <li>Works: soft-deleted (recoverable) and removed from collections, favorite folders and favorites</li>
 *   <li>Follows: removed in both directions; likes and favorites: removed those made by the user</li>
 *   <li>Pins of this user by others: removed</li>
 *   <li>Comments are kept: public APIs already filter out comments of deleted users (see WorkService.blockedUserIds), so they come back if the user is restored</li>
 * </ul>
 * Finally, affected like, favorite, following and follower counts are recomputed from actual records.
 */
@Service
public class DeletedUserCleanupService {
    private final UserMapper userMapper;
    private final WorkMapper workMapper;
    private final UserFollowMapper followMapper;
    private final UserLikeMapper likeMapper;
    private final UserFavoriteMapper favoriteMapper;
    private final FavoriteCollectionItemMapper favoriteItemMapper;
    private final WorkCollectionItemMapper collectionItemMapper;
    private final UserPinMapper pinMapper;

    public DeletedUserCleanupService(UserMapper userMapper, WorkMapper workMapper, UserFollowMapper followMapper,
                                     UserLikeMapper likeMapper, UserFavoriteMapper favoriteMapper,
                                     FavoriteCollectionItemMapper favoriteItemMapper, WorkCollectionItemMapper collectionItemMapper,
                                     UserPinMapper pinMapper) {
        this.userMapper = userMapper;
        this.workMapper = workMapper;
        this.followMapper = followMapper;
        this.likeMapper = likeMapper;
        this.favoriteMapper = favoriteMapper;
        this.favoriteItemMapper = favoriteItemMapper;
        this.collectionItemMapper = collectionItemMapper;
        this.pinMapper = pinMapper;
    }

    /** Clean up content left by these (already deleted) users */
    @Transactional(rollbackFor = Exception.class)
    public void cleanup(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        Set<Long> touchedWorks = new LinkedHashSet<Long>();
        Set<Long> touchedUsers = new LinkedHashSet<Long>(userIds);

        // Works
        List<Long> workIds = new ArrayList<Long>();
        for (Work w : workMapper.selectList(new LambdaQueryWrapper<Work>().in(Work::getUserId, userIds).eq(Work::getIsDeleted, 0))) {
            workIds.add(w.getId());
        }
        if (!workIds.isEmpty()) {
            workMapper.update(null, new LambdaUpdateWrapper<Work>().in(Work::getId, workIds)
                    .set(Work::getIsDeleted, 1).set(Work::getStatus, "deleted"));
            collectionItemMapper.delete(new LambdaQueryWrapper<WorkCollectionItem>().in(WorkCollectionItem::getWorkId, workIds));
            favoriteItemMapper.delete(new LambdaQueryWrapper<FavoriteCollectionItem>().in(FavoriteCollectionItem::getWorkId, workIds));
            favoriteMapper.delete(new LambdaQueryWrapper<UserFavorite>().in(UserFavorite::getWorkId, workIds));
            pinMapper.delete(new LambdaQueryWrapper<UserPin>().eq(UserPin::getTargetType, "work").in(UserPin::getTargetId, workIds));
        }

        // Follows (both directions)
        for (UserFollow f : followMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .in(UserFollow::getUserId, userIds).or().in(UserFollow::getTargetUserId, userIds))) {
            touchedUsers.add(f.getUserId());
            touchedUsers.add(f.getTargetUserId());
        }
        followMapper.delete(new LambdaQueryWrapper<UserFollow>().in(UserFollow::getUserId, userIds).or().in(UserFollow::getTargetUserId, userIds));

        // Likes and favorites made by the user
        for (UserLike l : likeMapper.selectList(new LambdaQueryWrapper<UserLike>().in(UserLike::getUserId, userIds))) touchedWorks.add(l.getWorkId());
        likeMapper.delete(new LambdaQueryWrapper<UserLike>().in(UserLike::getUserId, userIds));
        for (UserFavorite f : favoriteMapper.selectList(new LambdaQueryWrapper<UserFavorite>().in(UserFavorite::getUserId, userIds))) touchedWorks.add(f.getWorkId());
        favoriteMapper.delete(new LambdaQueryWrapper<UserFavorite>().in(UserFavorite::getUserId, userIds));
        favoriteItemMapper.delete(new LambdaQueryWrapper<FavoriteCollectionItem>().in(FavoriteCollectionItem::getUserId, userIds));

        // Pins of this user by others
        pinMapper.delete(new LambdaQueryWrapper<UserPin>().eq(UserPin::getTargetType, "user").in(UserPin::getTargetId, userIds));

        // Recompute counters from actual records
        touchedWorks.removeIf(id -> id == null);
        if (!touchedWorks.isEmpty()) {
            workMapper.update(null, new LambdaUpdateWrapper<Work>().in(Work::getId, touchedWorks)
                    .setSql("like_count = (SELECT COUNT(*) FROM user_like ul WHERE ul.work_id = work.id)")
                    .setSql("fav_count = (SELECT COUNT(*) FROM user_favorite uf WHERE uf.work_id = work.id)"));
        }
        touchedUsers.removeIf(id -> id == null);
        if (!touchedUsers.isEmpty()) {
            userMapper.update(null, new LambdaUpdateWrapper<User>().in(User::getId, touchedUsers)
                    .setSql("follow_count = (SELECT COUNT(*) FROM user_follow f WHERE f.user_id = user.id)")
                    .setSql("fans_count = (SELECT COUNT(*) FROM user_follow f WHERE f.target_user_id = user.id)"));
        }
    }
}
