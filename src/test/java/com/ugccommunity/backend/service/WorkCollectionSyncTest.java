package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ugccommunity.backend.entity.Work;
import com.ugccommunity.backend.entity.WorkCollection;
import com.ugccommunity.backend.entity.WorkCollectionFavorite;
import com.ugccommunity.backend.entity.WorkCollectionItem;
import com.ugccommunity.backend.mapper.WorkCollectionFavoriteMapper;
import com.ugccommunity.backend.mapper.WorkCollectionItemMapper;
import com.ugccommunity.backend.mapper.WorkCollectionMapper;
import com.ugccommunity.backend.mapper.WorkMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkCollectionSyncTest {
    private static final long USER_ID = 100L;
    private static final long WORK_ID = 1L;
    private static final long COLL_A = 10L; // 旧合辑
    private static final long COLL_B = 20L; // 新合辑
    private static final long COLL_PRIVATE = 30L; // 私密合辑

    private final Map<Long, WorkCollection> collections = new HashMap<>();
    private final List<WorkCollectionItem> items = new ArrayList<>();
    private WorkCollectionService service;
    private WorkMapper workMapper;

    @BeforeEach
    void setUp() {
        for (Class<?> c : new Class<?>[]{WorkCollection.class, WorkCollectionFavorite.class, WorkCollectionItem.class, Work.class}) {
            if (TableInfoHelper.getTableInfo(c) == null) {
                TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), c);
            }
        }
        collections.clear();
        items.clear();

        // 初始化 COLL_A 和 COLL_B
        WorkCollection ca = new WorkCollection();
        ca.setId(COLL_A);
        ca.setName("世绘人缘");
        ca.setUserId(USER_ID);
        ca.setVisibility(WorkCollection.PUBLIC);
        ca.setIsDeleted(0);
        collections.put(COLL_A, ca);

        WorkCollection cb = new WorkCollection();
        cb.setId(COLL_B);
        cb.setName("省拟");
        cb.setUserId(USER_ID);
        cb.setVisibility(WorkCollection.PUBLIC);
        cb.setIsDeleted(0);
        collections.put(COLL_B, cb);

        WorkCollection cp = new WorkCollection();
        cp.setId(COLL_PRIVATE);
        cp.setName("私密合辑");
        cp.setUserId(USER_ID);
        cp.setVisibility(WorkCollection.PRIVATE);
        cp.setIsDeleted(0);
        collections.put(COLL_PRIVATE, cp);

        // 初始状态：work 在 COLL_A 中
        WorkCollectionItem initialItem = new WorkCollectionItem();
        initialItem.setCollectionId(COLL_A);
        initialItem.setWorkId(WORK_ID);
        items.add(initialItem);

        WorkCollectionMapper collectionMapper = mock(WorkCollectionMapper.class);
        WorkCollectionItemMapper itemMapper = mock(WorkCollectionItemMapper.class);
        workMapper = mock(WorkMapper.class);
        WorkCollectionFavoriteMapper favoriteMapper = mock(WorkCollectionFavoriteMapper.class);

        when(collectionMapper.selectOne(any())).thenAnswer(inv -> {
            // 根据 query wrapper 中的 ID 查询
            for (WorkCollection c : collections.values()) {
                // 简单匹配
                return c;
            }
            return null;
        });
        // 针对具体的 selectOne 查找
        doAnswer(inv -> collections.get(COLL_A)).when(collectionMapper).selectOne(argThat(w -> w != null && w.getSqlSegment().contains("id = 10")));
        doAnswer(inv -> collections.get(COLL_B)).when(collectionMapper).selectOne(argThat(w -> w != null && w.getSqlSegment().contains("id = 20")));
        doAnswer(inv -> collections.get(COLL_PRIVATE)).when(collectionMapper).selectOne(argThat(w -> w != null && w.getSqlSegment().contains("id = 30")));

        when(collectionMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(collections.values()));

        when(itemMapper.selectList(any())).thenAnswer(inv -> {
            List<WorkCollectionItem> matched = new ArrayList<>();
            for (WorkCollectionItem it : items) {
                if (it.getWorkId().equals(WORK_ID)) {
                    matched.add(it);
                }
            }
            return matched;
        });

        when(itemMapper.delete(any())).thenAnswer(inv -> {
            // 简单根据 ID 移除
            items.removeIf(it -> it.getWorkId().equals(WORK_ID) && !it.getCollectionId().equals(COLL_B));
            return 1;
        });

        when(itemMapper.insert(any())).thenAnswer(inv -> {
            WorkCollectionItem it = inv.getArgument(0);
            items.add(it);
            return 1;
        });

        service = new WorkCollectionService(collectionMapper, itemMapper, workMapper, favoriteMapper, mock(WorkService.class));
    }

    @Test
    void syncWorkCollections_movesWorkFromAToB() {
        // 验证初始状态在 COLL_A
        assertEquals(1, items.size());
        assertEquals(COLL_A, items.get(0).getCollectionId());

        // 执行同步：将作品移入 COLL_B（省拟）
        service.syncWorkCollections(USER_ID, WORK_ID, Collections.singletonList(COLL_B));

        // 验证作品已不在 COLL_A，而在 COLL_B
        boolean inA = items.stream().anyMatch(it -> it.getCollectionId().equals(COLL_A));
        boolean inB = items.stream().anyMatch(it -> it.getCollectionId().equals(COLL_B));
        assertFalse(inA, "Work should no longer be in COLL_A");
        assertTrue(inB, "Work should now be in COLL_B");
    }

    @Test
    void syncWorkCollections_nullDoesNotChange() {
        // null 入参不修改已有归属
        service.syncWorkCollections(USER_ID, WORK_ID, null);
        assertEquals(1, items.size());
        assertEquals(COLL_A, items.get(0).getCollectionId());
    }

    @Test
    void addWork_success() {
        Work work = new Work();
        work.setId(WORK_ID);
        work.setUserId(USER_ID);
        work.setIsDeleted(0);
        when(workMapper.selectById(WORK_ID)).thenReturn(work);

        service.addWork(USER_ID, COLL_B, WORK_ID);
        assertTrue(items.stream().anyMatch(it -> it.getCollectionId().equals(COLL_B) && it.getWorkId().equals(WORK_ID)));
    }

    @Test
    void removeWork_success() {
        service.removeWork(USER_ID, COLL_A, WORK_ID);
        assertFalse(items.stream().anyMatch(it -> it.getCollectionId().equals(COLL_A) && it.getWorkId().equals(WORK_ID)));
    }
}
