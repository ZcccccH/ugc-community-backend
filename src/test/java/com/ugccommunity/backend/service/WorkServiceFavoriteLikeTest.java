package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ugccommunity.backend.entity.UserFavorite;
import com.ugccommunity.backend.entity.UserLike;
import com.ugccommunity.backend.entity.Work;
import com.ugccommunity.backend.mapper.*;
import com.ugccommunity.backend.vo.WorkVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/** 收藏联动点赞：新收藏时没点赞就点赞，已点赞不重复加数，取消收藏不取消点赞 */
class WorkServiceFavoriteLikeTest {
    private static final long USER = 5L;
    private static final long AUTHOR = 6L;
    private static final long WORK = 100L;

    private boolean liked;
    private boolean favorited;
    private int likeCount;
    private int favCount;
    private WorkService service;
    private FavoriteCollectionService favoriteCollectionService;
    private FavoriteCollectionMapper collectionMapper;
    private FavoriteCollectionItemMapper itemMapper;

    @BeforeEach
    void setUp() {
        for (Class<?> c : new Class<?>[]{UserLike.class, UserFavorite.class, Work.class,
                com.ugccommunity.backend.entity.FavoriteCollection.class, com.ugccommunity.backend.entity.FavoriteCollectionItem.class}) {
            if (TableInfoHelper.getTableInfo(c) == null) {
                TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), c);
            }
        }
        WorkMapper workMapper = mock(WorkMapper.class);
        UserLikeMapper likeMapper = mock(UserLikeMapper.class);
        UserFavoriteMapper favoriteMapper = mock(UserFavoriteMapper.class);
        collectionMapper = mock(FavoriteCollectionMapper.class);
        itemMapper = mock(FavoriteCollectionItemMapper.class);
        favoriteCollectionService = new FavoriteCollectionService(collectionMapper, itemMapper, favoriteMapper, workMapper, null);

        when(likeMapper.selectCount(any())).thenAnswer(inv -> liked ? 1L : 0L);
        when(likeMapper.insert(any(UserLike.class))).thenAnswer(inv -> { liked = true; return 1; });
        when(likeMapper.delete(any())).thenAnswer(inv -> { liked = false; return 1; });
        when(favoriteMapper.selectCount(any())).thenAnswer(inv -> favorited ? 1L : 0L);
        when(favoriteMapper.insert(any(UserFavorite.class))).thenAnswer(inv -> { favorited = true; return 1; });
        when(favoriteMapper.delete(any())).thenAnswer(inv -> { favorited = false; return 1; });
        when(workMapper.update(isNull(), any())).thenAnswer(inv -> {
            String sql = ((LambdaUpdateWrapper<?>) inv.getArgument(1)).getSqlSet();
            if (sql.contains("like_count = like_count + 1")) likeCount++;
            else if (sql.contains("like_count = greatest")) likeCount = Math.max(likeCount - 1, 0);
            else if (sql.contains("fav_count = fav_count + 1")) favCount++;
            else if (sql.contains("fav_count = greatest")) favCount = Math.max(favCount - 1, 0);
            return 1;
        });
        Work work = new Work();
        work.setId(WORK);
        work.setUserId(AUTHOR);
        work.setTitle("雾港");
        work.setStatus("published");
        work.setIsDeleted(0);
        when(workMapper.selectById(anyLong())).thenReturn(work);
        when(workMapper.selectOne(any())).thenReturn(work);

        WorkService real = new WorkService(workMapper, mock(UserMapper.class), likeMapper, favoriteMapper,
                mock(UserFollowMapper.class), mock(WorkTagMapper.class), mock(TagMapper.class), mock(BanWordMapper.class),
                mock(MessageMapper.class), mock(ReportMapper.class), mock(WorkCommentMapper.class), mock(UserViewHistoryMapper.class),
                mock(UserTagBlockMapper.class), mock(UserBlockMapper.class), mock(WorkCollectionMapper.class),
                mock(WorkCollectionItemMapper.class), mock(UserPinMapper.class), mock(UserWorkUnlockMapper.class),
                mock(UserService.class), mock(TagService.class), mock(WorkCollectionService.class), mock(GrowthService.class),
                favoriteCollectionService, mock(SettingService.class));
        service = spy(real);
        doAnswer(inv -> {
            WorkVO vo = new WorkVO();
            vo.setId(WORK);
            vo.setLiked(liked);
            vo.setLikeCount(likeCount);
            vo.setFavorited(favorited);
            vo.setFavCount(favCount);
            return vo;
        }).when(service).view(anyLong(), anyLong());
        // FavoriteCollectionService 依赖 WorkService（生产环境由 @Lazy 注入）
        favoriteCollectionService = new FavoriteCollectionService(collectionMapper, itemMapper, favoriteMapper, workMapper, service);
    }

    @Test
    void favoritingAnUnlikedWorkAlsoLikesItOnce() {
        WorkVO vo = service.setFavorite(USER, WORK, true);
        assertTrue(vo.getFavorited());
        assertTrue(vo.getLiked());
        assertEquals(1, vo.getLikeCount());
        assertEquals(1, vo.getFavCount());
        // 再次请求收藏（例如列表状态过期）不会重复加数
        vo = service.setFavorite(USER, WORK, true);
        assertEquals(1, vo.getLikeCount());
        assertEquals(1, vo.getFavCount());
    }

    @Test
    void favoritingAnAlreadyLikedWorkDoesNotDoubleCountLikes() {
        service.setLike(USER, WORK, true);
        assertEquals(1, likeCount);
        WorkVO vo = service.setFavorite(USER, WORK, true);
        assertTrue(vo.getLiked());
        assertEquals(1, vo.getLikeCount());
    }

    @Test
    void unfavoritingKeepsTheLike() {
        service.setFavorite(USER, WORK, true);
        WorkVO vo = service.setFavorite(USER, WORK, false);
        assertFalse(vo.getFavorited());
        assertTrue(vo.getLiked());
        assertEquals(1, vo.getLikeCount());
        assertEquals(0, vo.getFavCount());
    }

    @Test
    void addingToAFolderFavoritesAndLikes() {
        com.ugccommunity.backend.entity.FavoriteCollection folder = new com.ugccommunity.backend.entity.FavoriteCollection();
        folder.setId(9L);
        folder.setUserId(USER);
        folder.setName("夹子");
        folder.setIsDeleted(0);
        when(collectionMapper.selectOne(any())).thenReturn(folder);
        when(itemMapper.selectCount(any())).thenReturn(0L);
        favoriteCollectionService.addWork(USER, 9L, WORK);
        assertTrue(favorited);
        assertTrue(liked);
        assertEquals(1, likeCount);
        assertEquals(1, favCount);
    }
}
