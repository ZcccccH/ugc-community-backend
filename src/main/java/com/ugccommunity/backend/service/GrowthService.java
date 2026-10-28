package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.entity.Message;
import com.ugccommunity.backend.entity.UserAchievement;
import com.ugccommunity.backend.entity.UserCatState;
import com.ugccommunity.backend.entity.UserCrystalState;
import com.ugccommunity.backend.entity.UserWorkResonance;
import com.ugccommunity.backend.entity.Work;
import com.ugccommunity.backend.entity.WorkCrystalResonance;
import com.ugccommunity.backend.mapper.MessageMapper;
import com.ugccommunity.backend.mapper.UserAchievementMapper;
import com.ugccommunity.backend.mapper.UserCatStateMapper;
import com.ugccommunity.backend.mapper.UserCrystalStateMapper;
import com.ugccommunity.backend.mapper.UserTagFavoriteMapper;
import com.ugccommunity.backend.mapper.UserWorkResonanceMapper;
import com.ugccommunity.backend.mapper.WorkCrystalResonanceMapper;
import com.ugccommunity.backend.mapper.WorkMapper;
import com.ugccommunity.backend.vo.AchievementVO;
import com.ugccommunity.backend.vo.CatStateVO;
import com.ugccommunity.backend.vo.CrystalStateVO;
import com.ugccommunity.backend.vo.WorkCrystalResonanceVO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GrowthService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LUMINOUS_CRYSTAL_ID = "luminous";
    private static final List<String> BASE_CRYSTAL_IDS = Arrays.asList("solid", "glow", "flame", "void");
    private static final List<String> CRYSTAL_IDS = Arrays.asList("solid", "glow", "flame", "void", LUMINOUS_CRYSTAL_ID);
    private static final List<String> CAT_IDS = Arrays.asList("mianmian", "buding", "xueqiu");
    private static final int CRYSTAL_ENERGY_TARGET = 100;
    private static final int DAILY_LIKE_REWARD_LIMIT = 10;
    private static final int LIKE_REWARD_ENERGY = 10;
    private static final int LUMINOUS_SYNTHESIS_COST = 3;

    private final UserAchievementMapper userAchievementMapper;
    private final UserCrystalStateMapper userCrystalStateMapper;
    private final WorkCrystalResonanceMapper workCrystalResonanceMapper;
    private final UserCatStateMapper userCatStateMapper;
    private final UserTagFavoriteMapper userTagFavoriteMapper;
    private final UserWorkResonanceMapper userWorkResonanceMapper;
    private final WorkMapper workMapper;
    private final MessageMapper messageMapper;

    public GrowthService(
            UserAchievementMapper userAchievementMapper,
            UserCrystalStateMapper userCrystalStateMapper,
            WorkCrystalResonanceMapper workCrystalResonanceMapper,
            UserCatStateMapper userCatStateMapper,
            UserTagFavoriteMapper userTagFavoriteMapper,
            UserWorkResonanceMapper userWorkResonanceMapper,
            WorkMapper workMapper,
            MessageMapper messageMapper
    ) {
        this.userAchievementMapper = userAchievementMapper;
        this.userCrystalStateMapper = userCrystalStateMapper;
        this.workCrystalResonanceMapper = workCrystalResonanceMapper;
        this.userCatStateMapper = userCatStateMapper;
        this.userTagFavoriteMapper = userTagFavoriteMapper;
        this.userWorkResonanceMapper = userWorkResonanceMapper;
        this.workMapper = workMapper;
        this.messageMapper = messageMapper;
    }

    public List<AchievementVO> achievements(Long userId) {
        syncDerivedAchievements(userId);
        List<AchievementVO> result = new ArrayList<AchievementVO>();
        for (AchievementDefinition definition : definitions()) {
            UserAchievement state = getAchievement(userId, definition.id);
            result.add(toAchievementVO(definition, state));
        }
        return result;
    }

    public List<CrystalStateVO> crystals(Long userId) {
        ensureCrystalStates(userId);
        List<UserCrystalState> rows = userCrystalStateMapper.selectList(new LambdaQueryWrapper<UserCrystalState>()
                .eq(UserCrystalState::getUserId, userId));
        Map<String, UserCrystalState> byId = new LinkedHashMap<String, UserCrystalState>();
        for (UserCrystalState row : rows) {
            byId.put(row.getCrystalId(), row);
        }
        List<CrystalStateVO> result = new ArrayList<CrystalStateVO>();
        UserCrystalState sharedBaseState = sharedBaseState(byId);
        for (String crystalId : CRYSTAL_IDS) {
            result.add(toCrystalVO(crystalId, byId.get(crystalId), sharedBaseState));
        }
        return result;
    }

    public List<CatStateVO> cats(Long userId) {
        ensureCatStates(userId);
        List<UserCatState> rows = userCatStateMapper.selectList(new LambdaQueryWrapper<UserCatState>()
                .eq(UserCatState::getUserId, userId));
        Map<String, UserCatState> byId = new LinkedHashMap<String, UserCatState>();
        for (UserCatState row : rows) {
            byId.put(row.getCatId(), row);
        }
        List<CatStateVO> result = new ArrayList<CatStateVO>();
        for (String catId : CAT_IDS) {
            result.add(toCatVO(byId.get(catId)));
        }
        return result;
    }

    public List<WorkCrystalResonanceVO> workResonances(Long workId) {
        ensureWorkExists(workId);
        List<WorkCrystalResonance> rows = workCrystalResonanceMapper.selectList(new LambdaQueryWrapper<WorkCrystalResonance>()
                .eq(WorkCrystalResonance::getWorkId, workId));
        Map<String, WorkCrystalResonance> byId = new LinkedHashMap<String, WorkCrystalResonance>();
        for (WorkCrystalResonance row : rows) {
            byId.put(row.getCrystalId(), row);
        }
        List<WorkCrystalResonanceVO> result = new ArrayList<WorkCrystalResonanceVO>();
        for (String crystalId : CRYSTAL_IDS) {
            result.add(toWorkResonanceVO(crystalId, byId.get(crystalId)));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<WorkCrystalResonanceVO> resonateWork(Long userId, Long workId, String crystalId) {
        String safeCrystalId = normalizeCrystalId(crystalId);
        ensureWorkExists(workId);
        if (hasUserResonatedWork(userId, workId)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "每个作品只能共鸣一次");
        }
        UserWorkResonance userWorkResonance = new UserWorkResonance();
        userWorkResonance.setUserId(userId);
        userWorkResonance.setWorkId(workId);
        userWorkResonance.setCrystalId(safeCrystalId);
        userWorkResonance.setCreatedAt(LocalDateTime.now());
        try {
            userWorkResonanceMapper.insert(userWorkResonance);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "每个作品只能共鸣一次");
        }
        WorkCrystalResonance existing = workCrystalResonanceMapper.selectOne(new LambdaQueryWrapper<WorkCrystalResonance>()
                .eq(WorkCrystalResonance::getWorkId, workId)
                .eq(WorkCrystalResonance::getCrystalId, safeCrystalId)
                .last("limit 1"));
        if (existing == null) {
            WorkCrystalResonance row = new WorkCrystalResonance();
            row.setWorkId(workId);
            row.setCrystalId(safeCrystalId);
            row.setCount(1);
            row.setUpdatedAt(LocalDateTime.now());
            workCrystalResonanceMapper.insert(row);
        } else {
            workCrystalResonanceMapper.update(null, new LambdaUpdateWrapper<WorkCrystalResonance>()
                    .eq(WorkCrystalResonance::getWorkId, workId)
                    .eq(WorkCrystalResonance::getCrystalId, safeCrystalId)
                    .setSql("count = count + 1")
                    .set(WorkCrystalResonance::getUpdatedAt, LocalDateTime.now()));
        }
        return workResonances(workId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<CrystalStateVO> addCrystalEnergy(Long userId, String crystalId, Integer amount) {
        String safeCrystalId = normalizeCrystalId(crystalId);
        ensureEnergyCrystal(safeCrystalId);
        int delta = amount == null ? LIKE_REWARD_ENERGY : Math.max(1, amount);
        ensureCrystalStates(userId);
        UserCrystalState state = sharedBaseState(crystalStatesById(userId));
        int nextEnergy = nextSharedEnergy(state, delta);
        updateBaseCrystalEnergy(userId, nextEnergy);
        updateAchievementProgress(userId, "crystal_contract", crystalContractProgress(userId));
        return crystals(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void consumeCrystalEnergy(Long userId, String crystalId, Integer amount, String message) {
        String safeCrystalId = normalizeCrystalId(crystalId);
        int cost = amount == null ? 1 : Math.max(1, amount);
        ensureCrystalStates(userId);
        UserCrystalState state = userCrystalStateMapper.selectOne(new LambdaQueryWrapper<UserCrystalState>()
                .eq(UserCrystalState::getUserId, userId)
                .eq(UserCrystalState::getCrystalId, safeCrystalId)
                .last("limit 1"));
        int current = state == null || state.getCrystalCount() == null ? 0 : state.getCrystalCount();
        if (current < cost) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, StringUtils.hasText(message) ? message : "水晶不足");
        }
        // 原子扣减：在库里按 crystal_count >= cost 条件减，不写回读到的值。
        // 写回绝对值会在并发时超额消费，也会冲掉同一时刻到账的解锁收益。
        int updated = userCrystalStateMapper.update(null, new LambdaUpdateWrapper<UserCrystalState>()
                .eq(UserCrystalState::getUserId, userId)
                .eq(UserCrystalState::getCrystalId, safeCrystalId)
                .ge(UserCrystalState::getCrystalCount, cost)
                .setSql("crystal_count = crystal_count - " + cost)
                .set(UserCrystalState::getUpdatedAt, LocalDateTime.now()));
        if (updated <= 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, StringUtils.hasText(message) ? message : "水晶不足");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void grantCrystalEnergy(Long userId, String crystalId, Integer amount) {
        String safeCrystalId = normalizeCrystalId(crystalId);
        ensureEnergyCrystal(safeCrystalId);
        int gain = amount == null ? 1 : Math.max(1, amount);
        ensureCrystalStates(userId);
        UserCrystalState state = sharedBaseState(crystalStatesById(userId));
        updateBaseCrystalEnergy(userId, nextSharedEnergy(state, gain));
        updateAchievementProgress(userId, "crystal_contract", crystalContractProgress(userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void grantCrystalCount(Long userId, String crystalId, Integer amount) {
        String safeCrystalId = normalizeCrystalId(crystalId);
        int gain = amount == null ? 1 : Math.max(1, amount);
        ensureCrystalStates(userId);
        userCrystalStateMapper.update(null, new LambdaUpdateWrapper<UserCrystalState>()
                .eq(UserCrystalState::getUserId, userId)
                .eq(UserCrystalState::getCrystalId, safeCrystalId)
                .setSql("crystal_count = crystal_count + " + gain)
                .set(UserCrystalState::getUpdatedAt, LocalDateTime.now()));
    }

    @Transactional(rollbackFor = Exception.class)
    public List<CrystalStateVO> synthesizeLuminousCrystal(Long userId) {
        ensureCrystalStates(userId);
        Map<String, UserCrystalState> byId = crystalStatesById(userId);
        for (String crystalId : BASE_CRYSTAL_IDS) {
            UserCrystalState state = byId.get(crystalId);
            int count = state == null || state.getCrystalCount() == null ? 0 : state.getCrystalCount();
            if (count < LUMINOUS_SYNTHESIS_COST) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "合成莹白晶体需要其他四种晶体各 3 枚");
            }
        }
        for (String crystalId : BASE_CRYSTAL_IDS) {
            // 带余额条件扣减：连点时第二次在这里失败并整体回滚，余额不会变负
            int updated = userCrystalStateMapper.update(null, new LambdaUpdateWrapper<UserCrystalState>()
                    .eq(UserCrystalState::getUserId, userId)
                    .eq(UserCrystalState::getCrystalId, crystalId)
                    .ge(UserCrystalState::getCrystalCount, LUMINOUS_SYNTHESIS_COST)
                    .setSql("crystal_count = crystal_count - " + LUMINOUS_SYNTHESIS_COST)
                    .set(UserCrystalState::getUpdatedAt, LocalDateTime.now()));
            if (updated <= 0) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "合成莹白晶体需要其他四种晶体各 3 枚");
            }
        }
        userCrystalStateMapper.update(null, new LambdaUpdateWrapper<UserCrystalState>()
                .eq(UserCrystalState::getUserId, userId)
                .eq(UserCrystalState::getCrystalId, LUMINOUS_CRYSTAL_ID)
                .setSql("crystal_count = crystal_count + 1")
                .set(UserCrystalState::getUpdatedAt, LocalDateTime.now()));
        updateAchievementProgress(userId, "crystal_contract", crystalContractProgress(userId));
        return crystals(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<CrystalStateVO> redeemBaseCrystal(Long userId, String crystalId) {
        String safeCrystalId = normalizeCrystalId(crystalId);
        ensureEnergyCrystal(safeCrystalId);
        ensureCrystalStates(userId);
        Map<String, UserCrystalState> byId = crystalStatesById(userId);
        UserCrystalState sharedState = sharedBaseState(byId);
        if (sharedState == null || sharedBaseEnergy(sharedState) < CRYSTAL_ENERGY_TARGET) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "元素粒子未满 100，暂时无法兑换晶体");
        }
        if (baseCrystalRedeemedToday(byId)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "今日已兑换过晶体，请明天再来");
        }
        LocalDate today = LocalDate.now();
        // 先占用今天的兑换次数：只更新今天还没兑换过的行。并发的第二个请求会等第一个提交，
        // 之后条件不再成立、更新 0 行，在这里被拒绝，不会兑换两次。
        int claimed = userCrystalStateMapper.update(null, new LambdaUpdateWrapper<UserCrystalState>()
                .eq(UserCrystalState::getUserId, userId)
                .in(UserCrystalState::getCrystalId, BASE_CRYSTAL_IDS)
                .and(w -> w.isNull(UserCrystalState::getDailyRedeemDate).or().ne(UserCrystalState::getDailyRedeemDate, today))
                .set(UserCrystalState::getEnergy, 0)
                .set(UserCrystalState::getDailyRedeemDate, today)
                .set(UserCrystalState::getUpdatedAt, LocalDateTime.now()));
        if (claimed < BASE_CRYSTAL_IDS.size()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "今日已兑换过晶体，请明天再来");
        }
        userCrystalStateMapper.update(null, new LambdaUpdateWrapper<UserCrystalState>()
                .eq(UserCrystalState::getUserId, userId)
                .eq(UserCrystalState::getCrystalId, safeCrystalId)
                .setSql("crystal_count = crystal_count + 1")
                .set(UserCrystalState::getUpdatedAt, LocalDateTime.now()));
        updateAchievementProgress(userId, "crystal_contract", CRYSTAL_ENERGY_TARGET);
        return crystals(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean rewardLikeEnergy(Long userId) {
        ensureCrystalStates(userId);
        UserCrystalState state = activeCrystalState(userId);
        if (state == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        int currentDaily = today.equals(state.getDailyGainDate()) && state.getDailyGainCount() != null ? state.getDailyGainCount() : 0;
        if (currentDaily >= DAILY_LIKE_REWARD_LIMIT) {
            if (!today.equals(state.getDailyGainDate())) {
                userCrystalStateMapper.update(null, new LambdaUpdateWrapper<UserCrystalState>()
                        .eq(UserCrystalState::getUserId, userId)
                        .eq(UserCrystalState::getCrystalId, state.getCrystalId())
                        .set(UserCrystalState::getDailyGainCount, 0)
                        .set(UserCrystalState::getDailyGainDate, today)
                        .set(UserCrystalState::getUpdatedAt, LocalDateTime.now()));
            }
            return false;
        }
        int nextEnergy = nextSharedEnergy(state, LIKE_REWARD_ENERGY);
        for (String crystalId : BASE_CRYSTAL_IDS) {
            userCrystalStateMapper.update(null, new LambdaUpdateWrapper<UserCrystalState>()
                    .eq(UserCrystalState::getUserId, userId)
                    .eq(UserCrystalState::getCrystalId, crystalId)
                    .set(UserCrystalState::getEnergy, nextEnergy)
                    .set(UserCrystalState::getDailyGainCount, currentDaily + 1)
                    .set(UserCrystalState::getDailyGainDate, today)
                    .set(UserCrystalState::getUpdatedAt, LocalDateTime.now()));
        }
        updateAchievementProgress(userId, "crystal_contract", crystalContractProgress(userId));
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<CrystalStateVO> activateCrystal(Long userId, String crystalId) {
        String safeCrystalId = normalizeCrystalId(crystalId);
        ensureEnergyCrystal(safeCrystalId);
        ensureCrystalStates(userId);
        userCrystalStateMapper.update(null, new LambdaUpdateWrapper<UserCrystalState>()
                .eq(UserCrystalState::getUserId, userId)
                .set(UserCrystalState::getActive, 0));
        userCrystalStateMapper.update(null, new LambdaUpdateWrapper<UserCrystalState>()
                .eq(UserCrystalState::getUserId, userId)
                .eq(UserCrystalState::getCrystalId, safeCrystalId)
                .set(UserCrystalState::getActive, 1)
                .set(UserCrystalState::getUpdatedAt, LocalDateTime.now()));
        return crystals(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<CatStateVO> feedCat(Long userId, String catId, String crystalId) {
        String safeCatId = normalizeCatId(catId);
        String safeCrystalId = normalizeCrystalId(crystalId);
        ensureBaseFeedCrystal(safeCrystalId);
        ensureCrystalStates(userId);
        UserCrystalState crystalState = userCrystalStateMapper.selectOne(new LambdaQueryWrapper<UserCrystalState>()
                .eq(UserCrystalState::getUserId, userId)
                .eq(UserCrystalState::getCrystalId, safeCrystalId)
                .last("limit 1"));
        if (!canFeedCrystal(crystalState)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "只能喂食已拥有的晶体");
        }
        consumeCrystalEnergy(userId, safeCrystalId, 1, "晶石不足，无法互动");
        ensureCatStates(userId);
        UserCatState state = userCatStateMapper.selectOne(new LambdaQueryWrapper<UserCatState>()
                .eq(UserCatState::getUserId, userId)
                .eq(UserCatState::getCatId, safeCatId)
                .last("limit 1"));
        double satiety = Math.min(1.0, (state == null || state.getSatiety() == null ? defaultSatiety(safeCatId) : state.getSatiety()) + 0.25);
        int feedCount = (state == null || state.getFeedCount() == null ? 0 : state.getFeedCount()) + 1;
        if (state == null) {
            state = new UserCatState();
            state.setUserId(userId);
            state.setCatId(safeCatId);
            state.setSatiety(satiety);
            state.setColorCrystalId(safeCrystalId);
            state.setFeedCount(feedCount);
            state.setUpdatedAt(LocalDateTime.now());
            userCatStateMapper.insert(state);
        } else {
            userCatStateMapper.update(null, new LambdaUpdateWrapper<UserCatState>()
                    .eq(UserCatState::getUserId, userId)
                    .eq(UserCatState::getCatId, safeCatId)
                    .set(UserCatState::getSatiety, satiety)
                    .set(UserCatState::getColorCrystalId, safeCrystalId)
                    .set(UserCatState::getFeedCount, feedCount)
                    .set(UserCatState::getUpdatedAt, LocalDateTime.now()));
        }
        // 「猫咪挚友」成就暂时下线，喂猫不再计入成就进度
        // updateAchievementProgress(userId, "cat_friend", totalCatFeedCount(userId));
        return cats(userId);
    }

    /** 客户端可以自己上报的成就只有深夜访问一项，每次只算 1；其余成就由服务端按真实数据计算 */
    private static final List<String> CLIENT_REPORTABLE_ACHIEVEMENTS = Arrays.asList("night_owl");

    @Transactional(rollbackFor = Exception.class)
    public List<AchievementVO> reportClientAchievement(Long userId, String achievementId) {
        if (!CLIENT_REPORTABLE_ACHIEVEMENTS.contains(achievementId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该成就由系统自动统计");
        }
        return addAchievementProgress(userId, achievementId, 1);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<AchievementVO> addAchievementProgress(Long userId, String achievementId, Integer amount) {
        AchievementDefinition definition = definitionById(achievementId);
        if (definition == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "成就不存在");
        }
        UserAchievement current = getAchievement(userId, definition.id);
        int progress = current == null || current.getProgress() == null ? 0 : current.getProgress();
        updateAchievementProgress(userId, definition.id, progress + (amount == null ? 1 : Math.max(1, amount)));
        return achievements(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordPublish(Long userId) {
        updateAchievementProgress(userId, "rising_star", publishedWorkCount(userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordMessageVisit(Long userId) {
        addAchievementProgress(userId, "listener", 1);
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordNightVisit(Long userId) {
        addAchievementProgress(userId, "night_owl", 1);
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordTagFavoriteCount(Long userId, int favoriteCount) {
        updateAchievementProgress(userId, "scholar", favoriteCount);
    }

    public List<WorkCrystalResonanceVO> workResonancesForVO(Long workId) {
        return workResonances(workId);
    }

    public boolean hasUserResonatedWork(Long userId, Long workId) {
        if (userId == null || workId == null) {
            return false;
        }
        return userWorkResonanceMapper.selectCount(new LambdaQueryWrapper<UserWorkResonance>()
                .eq(UserWorkResonance::getUserId, userId)
                .eq(UserWorkResonance::getWorkId, workId)) > 0;
    }

    public String userResonatedCrystalId(Long userId, Long workId) {
        if (userId == null || workId == null) {
            return null;
        }
        UserWorkResonance row = userWorkResonanceMapper.selectOne(new LambdaQueryWrapper<UserWorkResonance>()
                .eq(UserWorkResonance::getUserId, userId)
                .eq(UserWorkResonance::getWorkId, workId)
                .last("limit 1"));
        return row == null ? null : row.getCrystalId();
    }

    private void updateAchievementProgress(Long userId, String achievementId, int progress) {
        AchievementDefinition definition = definitionById(achievementId);
        if (definition == null) {
            return;
        }
        int safeProgress = Math.max(0, progress);
        UserAchievement existing = getAchievement(userId, achievementId);
        boolean alreadyUnlocked = existing != null && existing.getUnlocked() != null && existing.getUnlocked() == 1;
        boolean unlocked = alreadyUnlocked || safeProgress >= definition.target;
        if (existing == null) {
            UserAchievement row = new UserAchievement();
            row.setUserId(userId);
            row.setAchievementId(achievementId);
            row.setProgress(Math.min(safeProgress, definition.target));
            row.setUnlocked(unlocked ? 1 : 0);
            row.setUnlockedAt(unlocked ? LocalDateTime.now() : null);
            row.setUpdatedAt(LocalDateTime.now());
            userAchievementMapper.insert(row);
            return;
        }
        LocalDateTime unlockedAt = existing.getUnlockedAt();
        if (unlocked && (existing.getUnlocked() == null || existing.getUnlocked() == 0)) {
            unlockedAt = LocalDateTime.now();
        }
        userAchievementMapper.update(null, new LambdaUpdateWrapper<UserAchievement>()
                .eq(UserAchievement::getUserId, userId)
                .eq(UserAchievement::getAchievementId, achievementId)
                .set(UserAchievement::getProgress, unlocked ? definition.target : Math.min(safeProgress, definition.target))
                .set(UserAchievement::getUnlocked, unlocked ? 1 : 0)
                .set(UserAchievement::getUnlockedAt, unlockedAt)
                .set(UserAchievement::getUpdatedAt, LocalDateTime.now()));
    }

    private UserAchievement getAchievement(Long userId, String achievementId) {
        return userAchievementMapper.selectOne(new LambdaQueryWrapper<UserAchievement>()
                .eq(UserAchievement::getUserId, userId)
                .eq(UserAchievement::getAchievementId, achievementId)
                .last("limit 1"));
    }

    private void ensureCrystalStates(Long userId) {
        for (String crystalId : CRYSTAL_IDS) {
            Long count = userCrystalStateMapper.selectCount(new LambdaQueryWrapper<UserCrystalState>()
                    .eq(UserCrystalState::getUserId, userId)
                    .eq(UserCrystalState::getCrystalId, crystalId));
            if (count == 0) {
                UserCrystalState row = new UserCrystalState();
                row.setUserId(userId);
                row.setCrystalId(crystalId);
                row.setEnergy(initialCrystalEnergy(crystalId));
                row.setCrystalCount(0);
                row.setDailyGainCount(0);
                row.setDailyRedeemDate(null);
                row.setActive(initialCrystalActive(crystalId));
                row.setUpdatedAt(LocalDateTime.now());
                userCrystalStateMapper.insert(row);
            }
        }
    }

    private void ensureCatStates(Long userId) {
        for (String catId : CAT_IDS) {
            Long count = userCatStateMapper.selectCount(new LambdaQueryWrapper<UserCatState>()
                    .eq(UserCatState::getUserId, userId)
                    .eq(UserCatState::getCatId, catId));
            if (count == 0) {
                UserCatState row = new UserCatState();
                row.setUserId(userId);
                row.setCatId(catId);
                row.setSatiety(defaultSatiety(catId));
                row.setColorCrystalId("solid");
                row.setFeedCount(0);
                row.setUpdatedAt(LocalDateTime.now());
                userCatStateMapper.insert(row);
            }
        }
    }

    private void ensureWorkExists(Long workId) {
        Work work = workMapper.selectById(workId);
        if (work == null || work.getIsDeleted() != null && work.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "作品不存在");
        }
    }

    private int maxCrystalEnergy(Long userId) {
        int max = 0;
        List<UserCrystalState> rows = userCrystalStateMapper.selectList(new LambdaQueryWrapper<UserCrystalState>()
                .eq(UserCrystalState::getUserId, userId));
        for (UserCrystalState row : rows) {
            max = Math.max(max, row.getEnergy() == null ? 0 : row.getEnergy());
        }
        return max;
    }

    private int crystalContractProgress(Long userId) {
        List<UserCrystalState> rows = userCrystalStateMapper.selectList(new LambdaQueryWrapper<UserCrystalState>()
                .eq(UserCrystalState::getUserId, userId));
        int maxEnergy = 0;
        for (UserCrystalState row : rows) {
            if (row.getCrystalCount() != null && row.getCrystalCount() > 0) {
                return CRYSTAL_ENERGY_TARGET;
            }
            maxEnergy = Math.max(maxEnergy, row.getEnergy() == null ? 0 : row.getEnergy());
        }
        return maxEnergy;
    }

    private int totalCatFeedCount(Long userId) {
        int total = 0;
        List<UserCatState> rows = userCatStateMapper.selectList(new LambdaQueryWrapper<UserCatState>()
                .eq(UserCatState::getUserId, userId));
        for (UserCatState row : rows) {
            total += row.getFeedCount() == null ? 0 : row.getFeedCount();
        }
        return total;
    }

    private int publishedWorkCount(Long userId) {
        return workMapper.selectCount(new LambdaQueryWrapper<Work>()
                .eq(Work::getUserId, userId)
                .eq(Work::getIsDeleted, 0)
                // 「成功发布」按审核通过算：待审核、被驳回的作品不算（原来提交审核就解锁成就）
                .eq(Work::getStatus, "published")).intValue();
    }

    private int tagFavoriteCount(Long userId) {
        return userTagFavoriteMapper.selectCount(new LambdaQueryWrapper<com.ugccommunity.backend.entity.UserTagFavorite>()
                .eq(com.ugccommunity.backend.entity.UserTagFavorite::getUserId, userId)).intValue();
    }

    private Map<String, UserCrystalState> crystalStatesById(Long userId) {
        List<UserCrystalState> rows = userCrystalStateMapper.selectList(new LambdaQueryWrapper<UserCrystalState>()
                .eq(UserCrystalState::getUserId, userId));
        Map<String, UserCrystalState> byId = new LinkedHashMap<String, UserCrystalState>();
        for (UserCrystalState row : rows) {
            byId.put(row.getCrystalId(), row);
        }
        return byId;
    }

    private UserCrystalState sharedBaseState(Map<String, UserCrystalState> byId) {
        UserCrystalState best = byId.get("solid");
        for (String crystalId : BASE_CRYSTAL_IDS) {
            UserCrystalState row = byId.get(crystalId);
            if (row == null) {
                continue;
            }
            if (best == null || sharedBaseEnergy(row) > sharedBaseEnergy(best)) {
                best = row;
            }
        }
        return best;
    }

    private int sharedBaseEnergy(UserCrystalState state) {
        return state == null || state.getEnergy() == null ? 0 : Math.max(0, Math.min(CRYSTAL_ENERGY_TARGET, state.getEnergy()));
    }

    private int nextSharedEnergy(UserCrystalState state, int gain) {
        return Math.min(CRYSTAL_ENERGY_TARGET, sharedBaseEnergy(state) + Math.max(0, gain));
    }

    private void updateBaseCrystalEnergy(Long userId, int energy) {
        int safeEnergy = Math.max(0, Math.min(CRYSTAL_ENERGY_TARGET, energy));
        for (String crystalId : BASE_CRYSTAL_IDS) {
            userCrystalStateMapper.update(null, new LambdaUpdateWrapper<UserCrystalState>()
                    .eq(UserCrystalState::getUserId, userId)
                    .eq(UserCrystalState::getCrystalId, crystalId)
                    .set(UserCrystalState::getEnergy, safeEnergy)
                    .set(UserCrystalState::getUpdatedAt, LocalDateTime.now()));
        }
    }

    private boolean baseCrystalRedeemedToday(Map<String, UserCrystalState> byId) {
        LocalDate today = LocalDate.now();
        for (String crystalId : BASE_CRYSTAL_IDS) {
            UserCrystalState row = byId.get(crystalId);
            if (row != null && today.equals(row.getDailyRedeemDate())) {
                return true;
            }
        }
        return false;
    }

    private void syncDerivedAchievements(Long userId) {
        updateAchievementProgress(userId, "rising_star", publishedWorkCount(userId));
        updateAchievementProgress(userId, "scholar", tagFavoriteCount(userId));
        updateAchievementProgress(userId, "crystal_contract", crystalContractProgress(userId));
        if (hasReadMessage(userId)) {
            updateAchievementProgress(userId, "listener", 1);
        }
        // 「猫咪挚友」成就暂时下线
        // updateAchievementProgress(userId, "cat_friend", totalCatFeedCount(userId));
    }

    private boolean hasReadMessage(Long userId) {
        if (userId == null || messageMapper == null) {
            return false;
        }
        return messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 1)) > 0;
    }

    private AchievementVO toAchievementVO(AchievementDefinition definition, UserAchievement state) {
        AchievementVO vo = new AchievementVO();
        int progress = state == null || state.getProgress() == null ? 0 : state.getProgress();
        boolean unlocked = state != null && state.getUnlocked() != null && state.getUnlocked() == 1;
        vo.setId(definition.id);
        vo.setTitle(definition.title);
        vo.setDescription(definition.description);
        vo.setIcon(definition.icon);
        vo.setTarget(definition.target);
        vo.setProgress(unlocked ? definition.target : Math.min(progress, definition.target));
        vo.setUnlocked(unlocked);
        vo.setUnlockedAt(state == null || state.getUnlockedAt() == null ? null : state.getUnlockedAt().format(FORMATTER));
        return vo;
    }

    int initialCrystalEnergy(String crystalId) {
        return 0;
    }

    int initialCrystalActive(String crystalId) {
        return "solid".equals(crystalId) ? 1 : 0;
    }

    boolean canFeedCrystal(UserCrystalState state) {
        return state != null && state.getCrystalCount() != null && state.getCrystalCount() > 0;
    }

    private CrystalStateVO toCrystalVO(String crystalId, UserCrystalState state, UserCrystalState sharedBaseState) {
        CrystalStateVO vo = new CrystalStateVO();
        vo.setCrystalId(state == null ? crystalId : state.getCrystalId());
        vo.setEnergy(LUMINOUS_CRYSTAL_ID.equals(crystalId) ? 0 : sharedBaseEnergy(sharedBaseState));
        vo.setActive(!LUMINOUS_CRYSTAL_ID.equals(crystalId) && state != null && state.getActive() != null && state.getActive() == 1);
        vo.setCrystalCount(state == null || state.getCrystalCount() == null ? 0 : state.getCrystalCount());
        vo.setDailyGainCount(LUMINOUS_CRYSTAL_ID.equals(crystalId) ? 0 : currentDailyGainCount(sharedBaseState));
        vo.setDailyLimit(LUMINOUS_CRYSTAL_ID.equals(crystalId) ? 0 : DAILY_LIKE_REWARD_LIMIT);
        vo.setDailyRedeemed(!LUMINOUS_CRYSTAL_ID.equals(crystalId) && sharedBaseState != null && LocalDate.now().equals(sharedBaseState.getDailyRedeemDate()));
        return vo;
    }

    private int currentDailyGainCount(UserCrystalState state) {
        if (state == null || state.getDailyGainCount() == null || !LocalDate.now().equals(state.getDailyGainDate())) {
            return 0;
        }
        return Math.min(state.getDailyGainCount(), DAILY_LIKE_REWARD_LIMIT);
    }

    private UserCrystalState activeCrystalState(Long userId) {
        return sharedBaseState(crystalStatesById(userId));
    }

    private WorkCrystalResonanceVO toWorkResonanceVO(String crystalId, WorkCrystalResonance state) {
        WorkCrystalResonanceVO vo = new WorkCrystalResonanceVO();
        vo.setCrystalId(crystalId);
        vo.setCount(state == null || state.getCount() == null ? 0 : state.getCount());
        return vo;
    }

    private CatStateVO toCatVO(UserCatState state) {
        CatStateVO vo = new CatStateVO();
        String catId = state == null ? "mianmian" : state.getCatId();
        vo.setCatId(catId);
        vo.setSatiety(state == null || state.getSatiety() == null ? defaultSatiety(catId) : state.getSatiety());
        vo.setColorCrystalId(state == null || !StringUtils.hasText(state.getColorCrystalId()) ? "solid" : state.getColorCrystalId());
        vo.setFeedCount(state == null || state.getFeedCount() == null ? 0 : state.getFeedCount());
        return vo;
    }

    private String normalizeCrystalId(String crystalId) {
        String value = StringUtils.hasText(crystalId) ? crystalId.trim() : "solid";
        if ("healing".equals(value) || "Blue".equals(value)) value = LUMINOUS_CRYSTAL_ID;
        if ("joy".equals(value)) value = "glow";
        if ("passion".equals(value) || "Red".equals(value)) value = "flame";
        if ("dream".equals(value) || "Violet".equals(value)) value = "void";
        if ("Green".equals(value)) value = "solid";
        if (!CRYSTAL_IDS.contains(value)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "晶体不存在");
        }
        return value;
    }

    private void ensureEnergyCrystal(String crystalId) {
        if (LUMINOUS_CRYSTAL_ID.equals(crystalId)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "莹白晶体需要由其他四种晶体各 3 枚合成");
        }
    }

    private void ensureBaseFeedCrystal(String crystalId) {
        if (!BASE_CRYSTAL_IDS.contains(crystalId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "猫咪互动只能消耗普通晶体");
        }
    }

    private String normalizeCatId(String catId) {
        String value = StringUtils.hasText(catId) ? catId.trim() : "";
        if ("棉棉".equals(value)) value = "mianmian";
        if ("布丁".equals(value)) value = "buding";
        if ("雪球".equals(value)) value = "xueqiu";
        if (!CAT_IDS.contains(value)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "猫不存在");
        }
        return value;
    }

    private double defaultSatiety(String catId) {
        if ("buding".equals(catId)) return 0.60;
        if ("xueqiu".equals(catId)) return 0.25;
        return 0.40;
    }

    private AchievementDefinition definitionById(String id) {
        for (AchievementDefinition definition : definitions()) {
            if (definition.id.equals(id)) {
                return definition;
            }
        }
        return null;
    }

    private List<AchievementDefinition> definitions() {
        List<AchievementDefinition> list = new ArrayList<AchievementDefinition>();
        list.add(new AchievementDefinition("rising_star", "初露锋芒", "在 UGC Community 成功发布第一篇原创文章", "✍️", 1));
        // 「猫咪挚友」成就暂时下线，成就中心不再展示；user_achievement 里的历史数据保留，恢复时取消注释即可
        // list.add(new AchievementDefinition("cat_friend", "猫咪挚友", "在棉猫馆里给可爱的小猫咪喂食一次晶体", "🐱", 1));
        list.add(new AchievementDefinition("crystal_contract", "晶体契约", "与任一情绪晶体达到100%圆满共鸣", "🔮", 100));
        list.add(new AchievementDefinition("night_owl", "夜猫修行", "在深夜 23:00 - 05:00 期间在应用中创作或阅读", "月", 1));
        list.add(new AchievementDefinition("listener", "聆听万物", "首次读取消息，关注世界与他人的共鸣", "📣", 1));
        list.add(new AchievementDefinition("scholar", "棉流学者", "在标签管理中心收藏超过 3 个感兴趣的主题", "书", 3));
        return list;
    }

    private static class AchievementDefinition {
        private final String id;
        private final String title;
        private final String description;
        private final String icon;
        private final Integer target;

        private AchievementDefinition(String id, String title, String description, String icon, Integer target) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.icon = icon;
            this.target = target;
        }
    }

    private static class CrystalGain {
        private final int energy;
        private final int crystalCount;

        private CrystalGain(int energy, int crystalCount) {
            this.energy = energy;
            this.crystalCount = crystalCount;
        }
    }
}
