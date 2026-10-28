package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.entity.UserCatState;
import com.ugccommunity.backend.entity.UserCrystalState;
import com.ugccommunity.backend.mapper.MessageMapper;
import com.ugccommunity.backend.mapper.UserAchievementMapper;
import com.ugccommunity.backend.mapper.UserCatStateMapper;
import com.ugccommunity.backend.mapper.UserCrystalStateMapper;
import com.ugccommunity.backend.mapper.UserTagFavoriteMapper;
import com.ugccommunity.backend.mapper.UserWorkResonanceMapper;
import com.ugccommunity.backend.mapper.WorkCrystalResonanceMapper;
import com.ugccommunity.backend.mapper.WorkMapper;
import com.ugccommunity.backend.vo.CatStateVO;
import com.ugccommunity.backend.vo.CrystalStateVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrowthServiceCrystalPolicyTest {
    private UserAchievementMapper userAchievementMapper;
    private UserCrystalStateMapper userCrystalStateMapper;
    private WorkCrystalResonanceMapper workCrystalResonanceMapper;
    private UserCatStateMapper userCatStateMapper;
    private UserTagFavoriteMapper userTagFavoriteMapper;
    private UserWorkResonanceMapper userWorkResonanceMapper;
    private WorkMapper workMapper;
    private MessageMapper messageMapper;
    private GrowthService growthService;

    @BeforeEach
    void setUp() {
        initTableInfo(UserCatState.class);
        initTableInfo(UserCrystalState.class);
        userAchievementMapper = mock(UserAchievementMapper.class);
        userCrystalStateMapper = mock(UserCrystalStateMapper.class);
        workCrystalResonanceMapper = mock(WorkCrystalResonanceMapper.class);
        userCatStateMapper = mock(UserCatStateMapper.class);
        userTagFavoriteMapper = mock(UserTagFavoriteMapper.class);
        userWorkResonanceMapper = mock(UserWorkResonanceMapper.class);
        workMapper = mock(WorkMapper.class);
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
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
    }

    @Test
    void newUserCrystalsDefaultWithSolidActiveAndNoEnergy() {
        List<UserCrystalState> inserted = new ArrayList<UserCrystalState>();
        when(userCrystalStateMapper.selectCount(any())).thenReturn(0L);
        when(userCrystalStateMapper.insert(any())).thenAnswer(invocation -> {
            inserted.add(invocation.getArgument(0));
            return 1;
        });
        when(userCrystalStateMapper.selectList(any())).thenAnswer(invocation -> inserted);

        List<CrystalStateVO> crystals = growthService.crystals(1001L);
        Map<String, CrystalStateVO> byId = crystals.stream()
                .collect(Collectors.toMap(CrystalStateVO::getCrystalId, item -> item));

        assertEquals(0, byId.get("solid").getEnergy());
        assertEquals(0, byId.get("solid").getCrystalCount());
        assertEquals(0, byId.get("solid").getDailyGainCount());
        assertEquals(10, byId.get("solid").getDailyLimit());
        assertEquals(false, byId.get("solid").getDailyRedeemed());
        assertTrue(byId.get("solid").getActive());
        assertEquals(0, byId.get("glow").getEnergy());
        assertEquals(0, byId.get("flame").getEnergy());
        assertEquals(0, byId.get("void").getEnergy());
        assertEquals(0, byId.get("luminous").getEnergy());
    }

    @Test
    void feedCatRejectsNonOwnedCrystalBeforeCatUpdate() {
        when(userCrystalStateMapper.selectCount(any())).thenReturn(1L);
        when(userCrystalStateMapper.selectOne(any())).thenReturn(crystal("flame", 0, 0, 0));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> growthService.feedCat(1002L, "mianmian", "flame"));

        assertEquals(ResultCode.NO_PERMISSION.getCode(), exception.getCode());
        assertEquals("只能喂食已拥有的晶体", exception.getBusinessMessage());
        verify(userCatStateMapper, never()).selectCount(any());
        verify(userCatStateMapper, never()).update(any(), any());
    }

    @Test
    void feedCatAllowsSolidWhenCrystalCountPositive() {
        when(userCrystalStateMapper.selectCount(any())).thenReturn(1L);
        when(userCrystalStateMapper.update(any(), any())).thenReturn(1);
        when(userCrystalStateMapper.selectOne(any())).thenReturn(crystal("solid", 0, 1, 1));
        when(userCatStateMapper.selectCount(any())).thenReturn(1L);
        when(userCatStateMapper.selectOne(any())).thenReturn(cat("mianmian", "solid", 0));
        when(userCatStateMapper.selectList(any())).thenReturn(list(cat("mianmian", "solid", 1)));

        List<CatStateVO> cats = growthService.feedCat(1003L, "mianmian", "solid");

        assertEquals("mianmian", cats.get(0).getCatId());
        assertEquals("solid", cats.get(0).getColorCrystalId());
    }

    @Test
    void feedCatAllowsNonSolidOnlyAfterCrystalCountGranted() {
        when(userCrystalStateMapper.selectCount(any())).thenReturn(1L);
        when(userCrystalStateMapper.update(any(), any())).thenReturn(1);
        when(userCrystalStateMapper.selectOne(any())).thenReturn(crystal("flame", 0, 0, 1));
        when(userCatStateMapper.selectCount(any())).thenReturn(1L);
        when(userCatStateMapper.selectOne(any())).thenReturn(cat("mianmian", "solid", 0));
        when(userCatStateMapper.selectList(any())).thenReturn(list(cat("mianmian", "flame", 1)));

        List<CatStateVO> cats = growthService.feedCat(1004L, "mianmian", "flame");

        assertEquals("flame", cats.get(0).getColorCrystalId());
    }

    @Test
    void feedCatRejectsLuminousCrystal() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> growthService.feedCat(1005L, "mianmian", "luminous"));

        assertEquals(ResultCode.NO_PERMISSION.getCode(), exception.getCode());
        assertEquals("猫咪互动只能消耗普通晶体", exception.getBusinessMessage());
    }

    @Test
    void luminousCrystalRejectsDirectEnergyGain() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> growthService.addCrystalEnergy(1006L, "luminous", 10));

        assertEquals(ResultCode.BUSINESS_ERROR.getCode(), exception.getCode());
        assertEquals("莹白晶体需要由其他四种晶体各 3 枚合成", exception.getBusinessMessage());
    }

    @Test
    void synthesizeLuminousRequiresThreeOfEachBaseCrystal() {
        when(userCrystalStateMapper.selectCount(any())).thenReturn(1L);
        List<UserCrystalState> states = new ArrayList<UserCrystalState>();
        states.add(crystal("solid", 0, 1, 3));
        states.add(crystal("glow", 0, 0, 3));
        states.add(crystal("flame", 0, 0, 3));
        states.add(crystal("void", 0, 0, 2));
        states.add(crystal("luminous", 0, 0, 0));
        when(userCrystalStateMapper.selectList(any())).thenReturn(states);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> growthService.synthesizeLuminousCrystal(1007L));

        assertEquals(ResultCode.BUSINESS_ERROR.getCode(), exception.getCode());
        assertEquals("合成莹白晶体需要其他四种晶体各 3 枚", exception.getBusinessMessage());
    }

    @Test
    void redeemBaseCrystalRequiresFullSharedEnergy() {
        when(userCrystalStateMapper.selectCount(any())).thenReturn(1L);
        List<UserCrystalState> states = new ArrayList<UserCrystalState>();
        states.add(crystal("solid", 90, 1, 0));
        states.add(crystal("glow", 90, 0, 0));
        states.add(crystal("flame", 90, 0, 0));
        states.add(crystal("void", 90, 0, 0));
        states.add(crystal("luminous", 0, 0, 0));
        when(userCrystalStateMapper.selectList(any())).thenReturn(states);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> growthService.redeemBaseCrystal(1008L, "glow"));

        assertEquals(ResultCode.BUSINESS_ERROR.getCode(), exception.getCode());
        assertEquals("元素粒子未满 100，暂时无法兑换晶体", exception.getBusinessMessage());
    }

    @Test
    void redeemBaseCrystalOnlyOncePerDay() {
        when(userCrystalStateMapper.selectCount(any())).thenReturn(1L);
        List<UserCrystalState> states = new ArrayList<UserCrystalState>();
        UserCrystalState solid = crystal("solid", 100, 1, 0);
        solid.setDailyRedeemDate(LocalDate.now());
        states.add(solid);
        states.add(crystal("glow", 100, 0, 0));
        states.add(crystal("flame", 100, 0, 0));
        states.add(crystal("void", 100, 0, 0));
        states.add(crystal("luminous", 0, 0, 0));
        when(userCrystalStateMapper.selectList(any())).thenReturn(states);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> growthService.redeemBaseCrystal(1009L, "void"));

        assertEquals(ResultCode.BUSINESS_ERROR.getCode(), exception.getCode());
        assertEquals("今日已兑换过晶体，请明天再来", exception.getBusinessMessage());
    }

    private UserCrystalState crystal(String crystalId, int energy, int active, int crystalCount) {
        UserCrystalState state = new UserCrystalState();
        state.setUserId(1L);
        state.setCrystalId(crystalId);
        state.setEnergy(energy);
        state.setCrystalCount(crystalCount);
        state.setActive(active);
        return state;
    }

    private UserCatState cat(String catId, String crystalId, int feedCount) {
        UserCatState state = new UserCatState();
        state.setUserId(1L);
        state.setCatId(catId);
        state.setSatiety(0.40);
        state.setColorCrystalId(crystalId);
        state.setFeedCount(feedCount);
        return state;
    }

    private List<UserCatState> list(UserCatState state) {
        List<UserCatState> rows = new ArrayList<UserCatState>();
        rows.add(state);
        return rows;
    }

    @Test
    void consumeFailsWhenAtomicDeductionMatchesNoRow() {
        // 并发时另一个请求先扣掉了余额：读到的余额够，但带条件的扣减更新 0 行
        when(userCrystalStateMapper.selectCount(any())).thenReturn(1L);
        when(userCrystalStateMapper.selectOne(any())).thenReturn(crystal("solid", 0, 1, 1));
        when(userCrystalStateMapper.update(any(), any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> growthService.consumeCrystalEnergy(1010L, "solid", 1, "水晶不足，无法解锁该作品"));

        assertEquals(ResultCode.BUSINESS_ERROR.getCode(), exception.getCode());
        assertEquals("水晶不足，无法解锁该作品", exception.getBusinessMessage());
    }

    @Test
    void synthesizeRejectsWhenConcurrentRequestAlreadySpentCrystals() {
        when(userCrystalStateMapper.selectCount(any())).thenReturn(1L);
        List<UserCrystalState> states = new ArrayList<UserCrystalState>();
        states.add(crystal("solid", 0, 1, 3));
        states.add(crystal("glow", 0, 0, 3));
        states.add(crystal("flame", 0, 0, 3));
        states.add(crystal("void", 0, 0, 3));
        states.add(crystal("luminous", 0, 0, 0));
        when(userCrystalStateMapper.selectList(any())).thenReturn(states);
        when(userCrystalStateMapper.update(any(), any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> growthService.synthesizeLuminousCrystal(1011L));
    }

    @Test
    void redeemRejectsWhenTodaysClaimMatchesNoRow() {
        when(userCrystalStateMapper.selectCount(any())).thenReturn(1L);
        List<UserCrystalState> states = new ArrayList<UserCrystalState>();
        states.add(crystal("solid", 100, 1, 0));
        states.add(crystal("glow", 100, 0, 0));
        states.add(crystal("flame", 100, 0, 0));
        states.add(crystal("void", 100, 0, 0));
        states.add(crystal("luminous", 0, 0, 0));
        when(userCrystalStateMapper.selectList(any())).thenReturn(states);
        when(userCrystalStateMapper.update(any(), any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> growthService.redeemBaseCrystal(1012L, "glow"));
        assertEquals("今日已兑换过晶体，请明天再来", exception.getBusinessMessage());
    }

    @Test
    void clientCanOnlyReportNightOwlAchievement() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> growthService.reportClientAchievement(1013L, "rising_star"));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
    }
}
