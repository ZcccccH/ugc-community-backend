package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ugccommunity.backend.entity.Message;
import com.ugccommunity.backend.entity.UserAchievement;
import com.ugccommunity.backend.entity.UserCatState;
import com.ugccommunity.backend.entity.UserCrystalState;
import com.ugccommunity.backend.mapper.*;
import com.ugccommunity.backend.vo.AchievementVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessageAchievementTest {
    private MessageMapper messageMapper;
    private UserAchievementMapper userAchievementMapper;
    private GrowthService growthService;
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        for (Class<?> c : new Class<?>[]{UserAchievement.class, Message.class, UserCatState.class, UserCrystalState.class}) {
            if (TableInfoHelper.getTableInfo(c) == null) {
                TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), c);
            }
        }

        userAchievementMapper = mock(UserAchievementMapper.class);
        UserCrystalStateMapper userCrystalStateMapper = mock(UserCrystalStateMapper.class);
        WorkCrystalResonanceMapper workCrystalResonanceMapper = mock(WorkCrystalResonanceMapper.class);
        UserCatStateMapper userCatStateMapper = mock(UserCatStateMapper.class);
        UserTagFavoriteMapper userTagFavoriteMapper = mock(UserTagFavoriteMapper.class);
        UserWorkResonanceMapper userWorkResonanceMapper = mock(UserWorkResonanceMapper.class);
        WorkMapper workMapper = mock(WorkMapper.class);
        messageMapper = mock(MessageMapper.class);

        growthService = new GrowthService(
                userAchievementMapper,
                userCrystalStateMapper,
                workCrystalResonanceMapper,
                userCatStateMapper,
                userTagFavoriteMapper,
                userWorkResonanceMapper,
                workMapper,
                messageMapper
        );

        messageService = new MessageService(messageMapper, growthService);
    }

    @Test
    void readOneTriggersAchievementRecord() {
        GrowthService mockGrowth = mock(GrowthService.class);
        MessageService service = new MessageService(messageMapper, mockGrowth);

        service.readOne(100L, 200L);

        verify(messageMapper, times(1)).update(isNull(), any());
        verify(mockGrowth, times(1)).recordMessageVisit(100L);
    }

    @Test
    void readAllTriggersAchievementRecord() {
        GrowthService mockGrowth = mock(GrowthService.class);
        MessageService service = new MessageService(messageMapper, mockGrowth);

        service.readAll(100L);

        verify(messageMapper, times(1)).update(isNull(), any());
        verify(mockGrowth, times(1)).recordMessageVisit(100L);
    }

    @Test
    void syncDerivedAchievementsUnlocksListenerWhenUserHasReadMessages() {
        when(messageMapper.selectCount(any())).thenReturn(1L);

        List<UserAchievement> saved = new ArrayList<UserAchievement>();
        when(userAchievementMapper.selectOne(any())).thenReturn(null);
        when(userAchievementMapper.insert(any(UserAchievement.class))).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return 1;
        });

        List<AchievementVO> list = growthService.achievements(100L);

        AchievementVO listenerVO = list.stream().filter(a -> "listener".equals(a.getId())).findFirst().orElse(null);
        assertTrue(listenerVO != null);
        assertEquals("聆听万物", listenerVO.getTitle());
        assertEquals("首次读取消息，关注世界与他人的共鸣", listenerVO.getDescription());
        assertEquals(1, listenerVO.getTarget());
        verify(userAchievementMapper, atLeastOnce()).insert(any(UserAchievement.class));
    }

    @Test
    void syncDerivedAchievementsDoesNotUnlockListenerWhenNoReadMessages() {
        when(messageMapper.selectCount(any())).thenReturn(0L);
        when(userAchievementMapper.selectOne(any())).thenReturn(null);

        List<AchievementVO> list = growthService.achievements(100L);

        AchievementVO listenerVO = list.stream().filter(a -> "listener".equals(a.getId())).findFirst().orElse(null);
        assertTrue(listenerVO != null);
        assertEquals(0, listenerVO.getProgress());
        assertEquals(false, listenerVO.getUnlocked());
    }
}
