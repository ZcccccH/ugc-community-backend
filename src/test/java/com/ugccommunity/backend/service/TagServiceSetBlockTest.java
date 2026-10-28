package com.ugccommunity.backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ugccommunity.backend.entity.Tag;
import com.ugccommunity.backend.entity.UserTagBlock;
import com.ugccommunity.backend.mapper.TagMapper;
import com.ugccommunity.backend.mapper.UserPinMapper;
import com.ugccommunity.backend.mapper.UserTagBlockMapper;
import com.ugccommunity.backend.mapper.UserTagFavoriteMapper;
import com.ugccommunity.backend.mapper.WorkTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 屏蔽 tag 按指定状态设置：已屏蔽时再点「屏蔽」不能变成取消屏蔽 */
class TagServiceSetBlockTest {
    private TagMapper tagMapper;
    private UserTagBlockMapper blockMapper;
    private UserTagFavoriteMapper favoriteMapper;
    private TagService service;

    @BeforeEach
    void setUp() {
        tagMapper = mock(TagMapper.class);
        blockMapper = mock(UserTagBlockMapper.class);
        favoriteMapper = mock(UserTagFavoriteMapper.class);
        service = new TagService(tagMapper, mock(WorkTagMapper.class), favoriteMapper, blockMapper,
                mock(UserPinMapper.class), mock(GrowthService.class), mock(SettingService.class));
        Tag tag = new Tag();
        tag.setId(7L);
        tag.setIsDeleted(0);
        when(tagMapper.selectById(7L)).thenReturn(tag);
    }

    @Test
    void blockingAnAlreadyBlockedTagKeepsItBlocked() {
        when(blockMapper.selectCount(any())).thenReturn(1L);
        assertTrue(service.setBlock(1L, 7L, true));
        verify(blockMapper, never()).delete(any());
        verify(blockMapper, never()).insert(any(UserTagBlock.class));
    }

    @Test
    void unblockingAnUnblockedTagIsNoop() {
        when(blockMapper.selectCount(any())).thenReturn(0L);
        assertFalse(service.setBlock(1L, 7L, false));
        verify(blockMapper, never()).insert(any(UserTagBlock.class));
    }

    @Test
    void blockingInsertsAndRemovesFavorite() {
        when(blockMapper.selectCount(any())).thenReturn(0L);
        assertTrue(service.setBlock(1L, 7L, true));
        verify(favoriteMapper).delete(any());
        verify(blockMapper).insert(any(UserTagBlock.class));
    }

    @Test
    void nullKeepsLegacyToggle() {
        when(blockMapper.selectCount(any())).thenReturn(1L);
        assertFalse(service.setBlock(1L, 7L, null));
        verify(blockMapper).delete(any());
    }
}
