package com.ugccommunity.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ugccommunity.backend.vo.UserVO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 关注列表：置顶用户排最前，其余保持「最近关注在前」的原顺序 */
class UserFollowOrderTest {
    private static UserVO user(long id, Boolean pinned) {
        UserVO vo = new UserVO();
        vo.setId(id);
        vo.setPinned(pinned);
        return vo;
    }

    @Test
    void pinnedUsersMoveToFrontAndOthersKeepOrder() {
        List<UserVO> input = Arrays.asList(user(5, false), user(4, true), user(3, null), user(2, true), user(1, false));
        List<Long> ids = new ArrayList<Long>();
        for (UserVO vo : UserService.pinnedFirst(input)) ids.add(vo.getId());
        assertEquals(Arrays.asList(4L, 2L, 5L, 3L, 1L), ids);
    }
}
