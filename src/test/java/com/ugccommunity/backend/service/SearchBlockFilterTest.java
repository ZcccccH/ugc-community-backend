package com.ugccommunity.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ugccommunity.backend.entity.Tag;
import com.ugccommunity.backend.entity.User;
import com.ugccommunity.backend.entity.UserBlock;
import com.ugccommunity.backend.entity.UserTagBlock;
import com.ugccommunity.backend.mapper.TagMapper;
import com.ugccommunity.backend.mapper.UserBlockMapper;
import com.ugccommunity.backend.mapper.UserFavoriteMapper;
import com.ugccommunity.backend.mapper.UserFollowMapper;
import com.ugccommunity.backend.mapper.UserLikeMapper;
import com.ugccommunity.backend.mapper.UserMapper;
import com.ugccommunity.backend.mapper.UserPinMapper;
import com.ugccommunity.backend.mapper.UserTagBlockMapper;
import com.ugccommunity.backend.mapper.UserTagFavoriteMapper;
import com.ugccommunity.backend.mapper.WorkMapper;
import com.ugccommunity.backend.mapper.WorkTagMapper;
import com.ugccommunity.backend.vo.TagVO;
import com.ugccommunity.backend.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class SearchBlockFilterTest {

    private TagMapper tagMapper;
    private UserTagBlockMapper userTagBlockMapper;
    private UserTagFavoriteMapper userTagFavoriteMapper;
    private TagService tagService;

    private UserMapper userMapper;
    private UserBlockMapper userBlockMapper;
    private UserService userService;

    @BeforeEach
    void setUp() {
        tagMapper = mock(TagMapper.class);
        userTagBlockMapper = mock(UserTagBlockMapper.class);
        userTagFavoriteMapper = mock(UserTagFavoriteMapper.class);
        tagService = new TagService(
                tagMapper,
                mock(WorkTagMapper.class),
                userTagFavoriteMapper,
                userTagBlockMapper,
                mock(UserPinMapper.class),
                mock(GrowthService.class),
                mock(SettingService.class)
        );

        userMapper = mock(UserMapper.class);
        userBlockMapper = mock(UserBlockMapper.class);
        userService = new UserService(
                userMapper,
                mock(WorkMapper.class),
                mock(UserFavoriteMapper.class),
                mock(UserFollowMapper.class),
                mock(UserLikeMapper.class),
                mock(UserPinMapper.class),
                userBlockMapper,
                mock(PasswordService.class)
        );
    }

    @Test
    void allTagsExcludesBlockedTagsForCurrentUser() {
        Long userId = 100L;
        UserTagBlock block = new UserTagBlock();
        block.setUserId(userId);
        block.setTagId(1L);

        when(userTagBlockMapper.selectList(any())).thenReturn(Collections.singletonList(block));

        Tag normalTag = new Tag();
        normalTag.setId(2L);
        normalTag.setName("procreate");
        normalTag.setIsDeleted(0);
        normalTag.setIsDisabled(0);
        normalTag.setUsageCount(10);

        when(tagMapper.selectList(any())).thenReturn(Collections.singletonList(normalTag));

        List<TagVO> result = tagService.allTags(userId);
        assertEquals(1, result.size());
        assertEquals("procreate", result.get(0).getName());
    }

    @Test
    void searchUsersExcludesBlockedUsersBothWays() {
        Long currentUserId = 10L;

        // User 20 is blocked by current user
        UserBlock block1 = new UserBlock();
        block1.setUserId(currentUserId);
        block1.setBlockedUserId(20L);

        // User 30 has blocked current user
        UserBlock block2 = new UserBlock();
        block2.setUserId(30L);
        block2.setBlockedUserId(currentUserId);

        when(userBlockMapper.selectList(any()))
                .thenReturn(Collections.singletonList(block1), Collections.singletonList(block2));

        User user20 = new User();
        user20.setId(20L);
        user20.setNickname("BlockedByMe");
        user20.setUsername("user20");
        user20.setStatus(1);
        user20.setDeleted(0);

        User user30 = new User();
        user30.setId(30L);
        user30.setNickname("BlockedMe");
        user30.setUsername("user30");
        user30.setStatus(1);
        user30.setDeleted(0);

        User user40 = new User();
        user40.setId(40L);
        user40.setNickname("NormalFriend");
        user40.setUsername("user40");
        user40.setStatus(1);
        user40.setDeleted(0);

        when(userMapper.selectList(any())).thenReturn(Arrays.asList(user20, user30, user40));

        List<UserVO> results = userService.searchUsers("user", currentUserId);
        assertEquals(1, results.size());
        assertEquals(40L, results.get(0).getId());
        assertEquals("NormalFriend", results.get(0).getNickname());
    }
}
