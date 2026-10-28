package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.entity.Work;
import com.ugccommunity.backend.entity.WorkCollection;
import com.ugccommunity.backend.entity.WorkCollectionFavorite;
import com.ugccommunity.backend.entity.WorkCollectionItem;
import com.ugccommunity.backend.mapper.WorkCollectionFavoriteMapper;
import com.ugccommunity.backend.mapper.WorkCollectionItemMapper;
import com.ugccommunity.backend.mapper.WorkCollectionMapper;
import com.ugccommunity.backend.mapper.WorkMapper;
import com.ugccommunity.backend.vo.WorkCollectionVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 收藏别人的合辑：可见性、不能收藏的情况、重复收藏/取消不乱计数、失效的合辑 */
class WorkCollectionFavoriteTest {
    private static final long AUTHOR = 1L;
    private static final long READER = 2L;
    private static final long PUBLIC_ID = 10L;
    private static final long PRIVATE_ID = 11L;
    private static final long DELETED_ID = 12L;

    private final Map<Long, WorkCollection> collections = new HashMap<Long, WorkCollection>();
    /** "user:collection" */
    private final Set<String> favorites = new HashSet<String>();
    private final List<String> favoriteOrder = new ArrayList<String>();
    private WorkCollectionService service;
    private WorkService workService;

    /** 条件里的 Long 参数，按出现顺序（MPGENVAL1, 2, …）；参数要在生成 SQL 片段后才有 */
    private static List<Long> longParams(Object wrapper) {
        AbstractWrapper<?, ?, ?> w = (AbstractWrapper<?, ?, ?>) wrapper;
        w.getSqlSegment();
        Map<String, Object> pairs = new java.util.TreeMap<String, Object>(java.util.Comparator.comparingInt((String k) -> Integer.parseInt(k.replaceAll("\\D", ""))));
        pairs.putAll(w.getParamNameValuePairs());
        List<Long> out = new ArrayList<Long>();
        for (Object v : pairs.values()) if (v instanceof Long) out.add((Long) v);
        return out;
    }

    @BeforeEach
    void setUp() {
        for (Class<?> c : new Class<?>[]{WorkCollection.class, WorkCollectionFavorite.class, WorkCollectionItem.class, Work.class,
                com.ugccommunity.backend.entity.User.class, com.ugccommunity.backend.entity.UserBlock.class, com.ugccommunity.backend.entity.UserTagBlock.class,
                com.ugccommunity.backend.entity.WorkTag.class, com.ugccommunity.backend.entity.Tag.class}) {
            if (TableInfoHelper.getTableInfo(c) == null) {
                TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), c);
            }
        }
        collection(PUBLIC_ID, "公开合辑", "public", 0);
        collection(PRIVATE_ID, "私密合辑", "private", 0);
        collection(DELETED_ID, "已删合辑", "public", 1);

        WorkCollectionMapper collectionMapper = mock(WorkCollectionMapper.class);
        WorkCollectionFavoriteMapper favoriteMapper = mock(WorkCollectionFavoriteMapper.class);
        when(collectionMapper.selectById(any())).thenAnswer(inv -> collections.get((Long) inv.getArgument(0)));
        when(collectionMapper.update(isNull(), any())).thenAnswer(inv -> {
            LambdaUpdateWrapper<?> w = inv.getArgument(1);
            Long id = longParams(w).get(0);
            WorkCollection c = collections.get(id);
            String sql = w.getSqlSet();
            if (sql == null || c == null) return 1;
            if (sql.contains("fav_count = fav_count + 1")) c.setFavCount(c.getFavCount() + 1);
            if (sql.contains("greatest(fav_count - 1, 0)")) c.setFavCount(Math.max(c.getFavCount() - 1, 0));
            return 1;
        });
        when(favoriteMapper.selectCount(any())).thenAnswer(inv -> {
            List<Long> p = longParams(inv.getArgument(0));
            return favorites.contains(p.get(0) + ":" + p.get(1)) ? 1L : 0L;
        });
        when(favoriteMapper.insert(any(WorkCollectionFavorite.class))).thenAnswer(inv -> {
            WorkCollectionFavorite f = inv.getArgument(0);
            String key = f.getUserId() + ":" + f.getCollectionId();
            favorites.add(key);
            favoriteOrder.add(0, key);
            return 1;
        });
        when(favoriteMapper.delete(any())).thenAnswer(inv -> {
            List<Long> p = longParams(inv.getArgument(0));
            String key = p.get(0) + ":" + p.get(1);
            favoriteOrder.remove(key);
            return favorites.remove(key) ? 1 : 0;
        });
        when(favoriteMapper.selectList(any())).thenAnswer(inv -> {
            List<WorkCollectionFavorite> rows = new ArrayList<WorkCollectionFavorite>();
            for (String key : favoriteOrder) {
                String[] parts = key.split(":");
                WorkCollectionFavorite f = new WorkCollectionFavorite();
                f.setUserId(Long.valueOf(parts[0]));
                f.setCollectionId(Long.valueOf(parts[1]));
                rows.add(f);
            }
            return rows;
        });

        workService = mock(WorkService.class);
        Work published = new Work();
        published.setId(100L);
        when(workService.visibleCollectionWorks(any(), anyLong(), anyBoolean())).thenReturn(Arrays.asList(published));
        service = new WorkCollectionService(collectionMapper, mock(WorkCollectionItemMapper.class), mock(WorkMapper.class), favoriteMapper, workService);
    }

    private void collection(long id, String name, String visibility, int deleted) {
        WorkCollection c = new WorkCollection();
        c.setId(id);
        c.setUserId(AUTHOR);
        c.setName(name);
        c.setVisibility(visibility);
        c.setIsDeleted(deleted);
        c.setFavCount(0);
        collections.put(id, c);
    }

    @Test
    void othersCannotSeePrivateOrDeletedCollections() {
        assertThrows(BusinessException.class, () -> service.detail(READER, PRIVATE_ID));
        assertThrows(BusinessException.class, () -> service.detail(READER, DELETED_ID));
        assertThrows(BusinessException.class, () -> service.detail(AUTHOR, DELETED_ID));
        // 作者自己看得到私密合辑
        WorkCollectionVO own = service.detail(AUTHOR, PRIVATE_ID);
        assertTrue(own.getIsOwner());
        assertEquals("private", own.getVisibility());
        WorkCollectionVO pub = service.detail(READER, PUBLIC_ID);
        assertFalse(pub.getIsOwner());
        assertEquals(1, pub.getWorkCount());
    }

    @Test
    void cannotFavoriteOwnPrivateOrDeleted() {
        assertThrows(BusinessException.class, () -> service.setFavorite(AUTHOR, PUBLIC_ID, true));
        assertThrows(BusinessException.class, () -> service.setFavorite(READER, PRIVATE_ID, true));
        assertThrows(BusinessException.class, () -> service.setFavorite(READER, DELETED_ID, true));
        assertTrue(favorites.isEmpty());
        assertEquals(0, collections.get(PUBLIC_ID).getFavCount());
    }

    @Test
    void repeatedFavoriteAndUnfavoriteNeverMiscount() {
        WorkCollectionVO vo = service.setFavorite(READER, PUBLIC_ID, true);
        assertTrue(vo.getFavorited());
        assertEquals(1, vo.getFavCount());
        service.setFavorite(READER, PUBLIC_ID, true);
        assertEquals(1, collections.get(PUBLIC_ID).getFavCount());
        vo = service.setFavorite(READER, PUBLIC_ID, false);
        assertFalse(vo.getFavorited());
        assertEquals(0, vo.getFavCount());
        service.setFavorite(READER, PUBLIC_ID, false);
        assertEquals(0, collections.get(PUBLIC_ID).getFavCount());
    }

    @Test
    void deletedOrPrivatizedFavoritesAreReturnedAsInvalidAndCanBeRemoved() {
        service.setFavorite(READER, PUBLIC_ID, true);
        WorkCollectionVO valid = service.favorites(READER, 1, 20).getRecords().get(0);
        assertFalse(valid.getInvalid());
        // 作者删除合辑：收藏记录保留，显示为失效
        collections.get(PUBLIC_ID).setIsDeleted(1);
        WorkCollectionVO invalid = service.favorites(READER, 1, 20).getRecords().get(0);
        assertTrue(invalid.getInvalid());
        assertEquals("公开合辑", invalid.getName());
        // 失效的也能取消收藏
        WorkCollectionVO after = service.setFavorite(READER, PUBLIC_ID, false);
        assertTrue(after.getInvalid());
        assertFalse(after.getFavorited());
        assertTrue(service.favorites(READER, 1, 20).getRecords().isEmpty());
    }

    @Test
    void switchingToPrivateMakesFavoriteInvalidForOthers() {
        service.setFavorite(READER, PUBLIC_ID, true);
        collections.get(PUBLIC_ID).setVisibility("private");
        assertTrue(service.favorites(READER, 1, 20).getRecords().get(0).getInvalid());
    }

    @Test
    void othersOnlySeePublishedWorks() {
        WorkMapper workMapper = mock(WorkMapper.class);
        WorkCollectionItemMapper itemMapper = mock(WorkCollectionItemMapper.class);
        WorkCollectionItem item = new WorkCollectionItem();
        item.setCollectionId(PUBLIC_ID);
        item.setWorkId(100L);
        when(itemMapper.selectList(any())).thenReturn(Arrays.asList(item));
        List<String> statusFilters = new ArrayList<String>();
        when(workMapper.selectList(any())).thenAnswer(inv -> {
            AbstractWrapper<?, ?, ?> w = inv.getArgument(0);
            statusFilters.add(w.getSqlSegment() + " " + w.getParamNameValuePairs().values());
            return new ArrayList<Work>();
        });
        WorkService real = WorkServiceTestFactory.create(workMapper, itemMapper);
        real.visibleCollectionWorks(READER, PUBLIC_ID, false);
        real.visibleCollectionWorks(AUTHOR, PUBLIC_ID, true);
        assertTrue(statusFilters.get(0).contains("status =") && statusFilters.get(0).contains("published"), statusFilters.get(0));
        assertTrue(statusFilters.get(1).contains("status <>") && statusFilters.get(1).contains("deleted"), statusFilters.get(1));
    }

    @Test
    void hidingCollectionHidesContainedWorksAndPublicRestoresThem() {
        WorkCollectionItemMapper itemMapper = mock(WorkCollectionItemMapper.class);
        WorkMapper workMapper = mock(WorkMapper.class);
        WorkCollectionMapper collectionMapper = mock(WorkCollectionMapper.class);
        when(collectionMapper.selectById(PUBLIC_ID)).thenReturn(collections.get(PUBLIC_ID));

        WorkCollectionItem item = new WorkCollectionItem();
        item.setCollectionId(PUBLIC_ID);
        item.setWorkId(200L);
        when(itemMapper.selectList(any())).thenReturn(Arrays.asList(item));

        List<String> updates = new ArrayList<String>();
        when(workMapper.update(isNull(), any())).thenAnswer(inv -> {
            AbstractWrapper<?, ?, ?> w = inv.getArgument(1);
            w.getSqlSegment();
            updates.add(w.getSqlSet() + " | " + w.getParamNameValuePairs().values());
            return 1;
        });

        WorkCollectionService testService = new WorkCollectionService(collectionMapper, itemMapper, workMapper, mock(WorkCollectionFavoriteMapper.class), workService);
        testService.update(AUTHOR, PUBLIC_ID, null, "private");
        assertEquals(1, updates.size());
        assertTrue(updates.get(0).contains("status") && updates.get(0).contains("hidden"), updates.get(0));

        testService.update(AUTHOR, PUBLIC_ID, null, "public");
        assertEquals(2, updates.size());
        assertTrue(updates.get(1).contains("status") && updates.get(1).contains("published"), updates.get(1));
    }
}
