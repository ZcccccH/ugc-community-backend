package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.entity.FavoriteCollection;
import com.ugccommunity.backend.entity.FavoriteCollectionItem;
import com.ugccommunity.backend.mapper.FavoriteCollectionItemMapper;
import com.ugccommunity.backend.mapper.FavoriteCollectionMapper;
import com.ugccommunity.backend.mapper.UserFavoriteMapper;
import com.ugccommunity.backend.mapper.WorkMapper;
import com.ugccommunity.backend.vo.FavoriteCollectionVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FavoriteCollectionRenameTest {
    private static final long USER = 5L;
    private FavoriteCollectionMapper collectionMapper;
    private FavoriteCollectionService service;
    private FavoriteCollection own;

    @BeforeEach
    void setUp() {
        for (Class<?> c : new Class<?>[]{FavoriteCollection.class, FavoriteCollectionItem.class}) {
            if (TableInfoHelper.getTableInfo(c) == null) {
                TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), c);
            }
        }
        collectionMapper = mock(FavoriteCollectionMapper.class);
        FavoriteCollectionItemMapper itemMapper = mock(FavoriteCollectionItemMapper.class);
        when(itemMapper.selectCount(any())).thenReturn(3L);
        service = new FavoriteCollectionService(collectionMapper, itemMapper, mock(UserFavoriteMapper.class), mock(WorkMapper.class), null);
        own = new FavoriteCollection();
        own.setId(9L);
        own.setUserId(USER);
        own.setName("旧名字");
        own.setIsDeleted(0);
        when(collectionMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    void ownerCanRenameAndNameIsTrimmed() {
        when(collectionMapper.selectOne(any())).thenReturn(own);
        FavoriteCollectionVO vo = service.rename(USER, 9L, "  新名字 ");
        assertEquals("新名字", vo.getName());
        assertEquals(3, vo.getWorkCount());
        verify(collectionMapper, times(1)).update(isNull(), any());
    }

    @Test
    void duplicateCheckExcludesTheFolderItself() {
        when(collectionMapper.selectOne(any())).thenReturn(own);
        service.rename(USER, 9L, "新名字");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<FavoriteCollection>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(collectionMapper).selectCount(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("<>"), sql);
        assertTrue(sql.contains("user_id"), sql);
    }

    @Test
    void renamingToTheSameNameIsANoop() {
        when(collectionMapper.selectOne(any())).thenReturn(own);
        assertEquals("旧名字", service.rename(USER, 9L, "旧名字").getName());
        verify(collectionMapper, never()).update(isNull(), any());
    }

    @Test
    void rejectsBlankTooLongDuplicateAndForeignFolders() {
        when(collectionMapper.selectOne(any())).thenReturn(own);
        assertThrows(BusinessException.class, () -> service.rename(USER, 9L, "   "));
        assertThrows(BusinessException.class, () -> service.rename(USER, 9L, null));
        assertThrows(BusinessException.class, () -> service.rename(USER, 9L, "一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十一"));
        when(collectionMapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.rename(USER, 9L, "别的夹子"));
        // 不是自己的收藏夹：按用户查不到
        when(collectionMapper.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.rename(USER, 9L, "新名字"));
        verify(collectionMapper, never()).update(isNull(), any());
    }

    @Test
    void createStillRejectsDuplicates() {
        when(collectionMapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.create(USER, "夹子"));
    }
}
