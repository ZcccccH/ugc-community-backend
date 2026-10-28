package com.ugccommunity.backend.service;

import com.ugccommunity.backend.mapper.*;

import static org.mockito.Mockito.mock;

/** 用 Mock 依赖构造真实的 WorkService（只给需要的 mapper 传实例，其余全是空 Mock） */
final class WorkServiceTestFactory {
    private WorkServiceTestFactory() {}

    static WorkService create(WorkMapper workMapper, WorkCollectionItemMapper itemMapper) {
        return new WorkService(workMapper, mock(UserMapper.class), mock(UserLikeMapper.class), mock(UserFavoriteMapper.class),
                mock(UserFollowMapper.class), mock(WorkTagMapper.class), mock(TagMapper.class), mock(BanWordMapper.class),
                mock(MessageMapper.class), mock(ReportMapper.class), mock(WorkCommentMapper.class), mock(UserViewHistoryMapper.class),
                mock(UserTagBlockMapper.class), mock(UserBlockMapper.class), mock(WorkCollectionMapper.class),
                itemMapper, mock(UserPinMapper.class), mock(UserWorkUnlockMapper.class),
                mock(UserService.class), mock(TagService.class), mock(WorkCollectionService.class), mock(GrowthService.class),
                mock(FavoriteCollectionService.class), mock(SettingService.class));
    }
}
