package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.dto.request.ResetPasswordRequest;
import com.ugccommunity.backend.dto.request.UpdateProfileRequest;
import com.ugccommunity.backend.dto.request.UserRegisterRequest;
import com.ugccommunity.backend.entity.User;
import com.ugccommunity.backend.entity.UserBlock;
import com.ugccommunity.backend.entity.UserPin;
import com.ugccommunity.backend.mapper.UserBlockMapper;
import com.ugccommunity.backend.mapper.UserFavoriteMapper;
import com.ugccommunity.backend.mapper.UserFollowMapper;
import com.ugccommunity.backend.mapper.UserLikeMapper;
import com.ugccommunity.backend.mapper.UserMapper;
import com.ugccommunity.backend.mapper.UserPinMapper;
import com.ugccommunity.backend.mapper.WorkMapper;
import com.ugccommunity.backend.vo.UserVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class UserService {
    /**
     * Replace broken external storage (Qiniu) URLs with this site's /uploads/ URL (a local copy is always kept).
     * Setter injection keeps the existing constructor unchanged; if not injected (unit tests) values are returned unchanged.
     */
    private com.ugccommunity.backend.common.upload.LocalUploadResolver localUploads;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLocalUploads(com.ugccommunity.backend.common.upload.LocalUploadResolver localUploads) { this.localUploads = localUploads; }
    private String local(String url) { return localUploads == null ? url : localUploads.preferLocal(url); }
    /** Rewrite each URL in a comma-separated list */
    private String localList(String urls) {
        if (urls == null || localUploads == null || !urls.contains("http")) return urls;
        StringBuilder sb = new StringBuilder();
        for (String part : urls.split(",")) { if (sb.length() > 0) sb.append(','); sb.append(local(part.trim())); }
        return sb.toString();
    }

    private static final int MIN_ACCOUNT_LENGTH = 6;
    private static final int MAX_ACCOUNT_LENGTH = 16;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 18;

    private final UserMapper userMapper;
    private final WorkMapper workMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final UserFollowMapper userFollowMapper;
    private final UserLikeMapper userLikeMapper;
    private final UserPinMapper userPinMapper;
    private final UserBlockMapper userBlockMapper;
    private final PasswordService passwordService;

    public UserService(UserMapper userMapper, WorkMapper workMapper, UserFavoriteMapper userFavoriteMapper, UserFollowMapper userFollowMapper, UserLikeMapper userLikeMapper, UserPinMapper userPinMapper, UserBlockMapper userBlockMapper, PasswordService passwordService) {
        this.userMapper = userMapper;
        this.workMapper = workMapper;
        this.userFavoriteMapper = userFavoriteMapper;
        this.userFollowMapper = userFollowMapper;
        this.userLikeMapper = userLikeMapper;
        this.userPinMapper = userPinMapper;
        this.userBlockMapper = userBlockMapper;
        this.passwordService = passwordService;
    }

    public UserVO getById(Long id) {
        return getById(id, null);
    }

    public UserVO getById(Long id, Long currentUserId) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, id)
                .eq(User::getDeleted, 0));
        if (user == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "用户不存在");
        }
        return toVO(user, currentUserId);
    }

    public UserVO getBasicById(Long id) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, id)
                .eq(User::getDeleted, 0));
        if (user == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "用户不存在");
        }
        // me-basic is only returned to the user themself: include their phone and email so the edit-profile screen can show bound contacts
        UserVO vo = toBasicVO(user);
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        return vo;
    }

    public User getEntityById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null || user.getDeleted() != null && user.getDeleted() == 1) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "用户不存在");
        }
        return user;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markActive(Long userId) {
        if (userId == null) return;
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getDeleted, 0)
                .set(User::getLastActiveAt, LocalDateTime.now()));
    }

    public User getEntityByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getDeleted, 0)
                .last("limit 1"));
    }

    private User getAnyEntityByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("limit 1"));
    }

    public User getEntityByLoginAccount(String account) {
        if (!StringUtils.hasText(account)) {
            return null;
        }
        String value = account.trim();
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getDeleted, 0)
                .and(w -> w.eq(User::getUsername, value)
                        .or().eq(User::getPhone, value)
                        .or().eq(User::getEmail, value))
                .last("limit 1"));
    }

    public java.util.List<UserVO> searchUsers(String keyword, Long currentUserId) {
        String kw = trim(keyword);
        if (!StringUtils.hasText(kw)) {
            return java.util.Collections.emptyList();
        }
        java.util.Set<Long> blockedUserIds = new java.util.HashSet<Long>();
        if (currentUserId != null) {
            java.util.List<UserBlock> myBlocks = userBlockMapper.selectList(new LambdaQueryWrapper<UserBlock>()
                    .eq(UserBlock::getUserId, currentUserId));
            for (UserBlock b : myBlocks) {
                if (b.getBlockedUserId() != null) blockedUserIds.add(b.getBlockedUserId());
            }
            java.util.List<UserBlock> blockedMe = userBlockMapper.selectList(new LambdaQueryWrapper<UserBlock>()
                    .eq(UserBlock::getBlockedUserId, currentUserId));
            for (UserBlock b : blockedMe) {
                if (b.getUserId() != null) blockedUserIds.add(b.getUserId());
            }
        }
        java.util.List<User> exactUsers = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getDeleted, 0)
                        .eq(User::getStatus, 1)
                        .and(wrapper -> wrapper
                                .eq(User::getNickname, kw)
                                .or()
                                .eq(User::getUsername, kw))
                        .last("limit 20")
        );
        java.util.List<User> fuzzyUsers = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getDeleted, 0)
                        .eq(User::getStatus, 1)
                        .and(wrapper -> wrapper
                                .like(User::getNickname, kw)
                                .or()
                                .like(User::getUsername, kw))
                        .last("limit 20")
        );
        java.util.Map<Long, User> merged = new java.util.LinkedHashMap<Long, User>();
        for (User user : exactUsers) {
            if (user.getId() != null && !blockedUserIds.contains(user.getId())) {
                merged.put(user.getId(), user);
            }
        }
        for (User user : fuzzyUsers) {
            if (user.getId() != null && !blockedUserIds.contains(user.getId())) {
                merged.put(user.getId(), user);
            }
        }
        java.util.List<User> users = new java.util.ArrayList<User>(merged.values());
        users.sort((a, b) -> Integer.compare(userSearchRank(a, kw), userSearchRank(b, kw)));
        java.util.List<UserVO> result = new java.util.ArrayList<UserVO>();
        for (User user : users.subList(0, Math.min(20, users.size()))) {
            result.add(toVO(user, currentUserId));
        }
        return result;
    }

    private int userSearchRank(User user, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.toLowerCase(java.util.Locale.ROOT);
        String nickname = user.getNickname() == null ? "" : user.getNickname();
        String username = user.getUsername() == null ? "" : user.getUsername();
        String normalizedNickname = nickname.toLowerCase(java.util.Locale.ROOT);
        String normalizedUsername = username.toLowerCase(java.util.Locale.ROOT);
        if (normalizedNickname.equals(normalizedKeyword)) return 0;
        if (normalizedUsername.equals(normalizedKeyword)) return 1;
        if (normalizedNickname.contains(normalizedKeyword)) return 2;
        if (normalizedUsername.contains(normalizedKeyword)) return 3;
        return 4;
    }

    public void upgradePasswordIfNeeded(User user, String rawPassword) {
        if (user == null || !passwordService.needsUpgrade(user.getPassword())) {
            return;
        }
        user.setPassword(passwordService.encode(rawPassword));
        userMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO register(UserRegisterRequest request) {
        String username = trim(request.getUsername());
        String password = request.getPassword() == null ? "" : request.getPassword().trim();
        validateAccount(username);
        validatePassword(password);
        requireBoundContact(request.getPhone(), request.getEmail());
        if (getAnyEntityByUsername(username) != null) {
            throw new BusinessException(ResultCode.DATA_EXIST, "账号已存在");
        }
        ensureUniqueContact(request.getPhone(), request.getEmail(), null);
        if (request.getGender() == null || (request.getGender() != 0 && request.getGender() != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "性别只能选择人类或古兽");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordService.encode(password));
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username);
        user.setAvatar("asset_avatar");
        user.setBackgroundImage("asset_bg");
        user.setGender(request.getGender());
        user.setRole("user");
        user.setStatus(1);
        user.setPhone(emptyToNull(request.getPhone()));
        user.setEmail(emptyToNull(request.getEmail()));
        user.setBio("这个人还没有填写简介。");
        user.setFollowCount(0);
        user.setFansCount(0);
        user.setLikeCount(0);
        user.setPrivacyAgreed(0);
        user.setDeleted(0);
        user.setLastActiveAt(LocalDateTime.now());
        userMapper.insert(user);
        return getById(user.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPasswordByBoundContact(ResetPasswordRequest request) {
        String username = trim(request.getUsername());
        String phone = trim(request.getPhone());
        String email = trim(request.getEmail());
        String newPassword = request.getNewPassword() == null ? "" : request.getNewPassword().trim();
        validateAccount(username);
        validatePassword(newPassword);
        requireBoundContact(phone, email);
        User user = getEntityByUsername(username);
        if (user == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "账号不存在");
        }
        if (!phone.equals(user.getPhone()) || !email.equalsIgnoreCase(user.getEmail() == null ? "" : user.getEmail())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "绑定手机号或邮箱不匹配");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "账号状态异常，请联系管理员");
        }
        user.setPassword(passwordService.encode(newPassword));
        userMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getEntityById(userId);
        if (request.getNickname() != null) {
            if (!StringUtils.hasText(request.getNickname())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "昵称不能为空");
            }
            user.setNickname(request.getNickname().trim());
        }
        if (StringUtils.hasText(request.getAvatar())) {
            user.setAvatar(request.getAvatar());
        }
        if (StringUtils.hasText(request.getBackgroundImage())) {
            user.setBackgroundImage(request.getBackgroundImage());
        }
        // The bio may be cleared (previously an empty string was ignored and the old bio stayed despite a success message)
        if (request.getBio() != null) {
            user.setBio(request.getBio().trim());
        }
        if (request.getGender() != null) {
            if (request.getGender() != 0 && request.getGender() != 1) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "性别只能选择人类或古兽");
            }
            user.setGender(request.getGender());
        }
        if (request.getPhone() != null) {
            ensureUniqueContact(request.getPhone(), null, userId);
            user.setPhone(emptyToNull(request.getPhone()));
        }
        if (request.getEmail() != null) {
            ensureUniqueContact(null, request.getEmail(), userId);
            user.setEmail(emptyToNull(request.getEmail()));
        }
        if (StringUtils.hasText(request.getBirthday())) {
            user.setBirthday(LocalDate.parse(request.getBirthday()));
        }
        userMapper.updateById(user);
        // Returned to the user themself: include phone and email so the edit screen does not show them empty after the client refreshes the current user
        return getById(userId, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO agreePrivacy(Long userId) {
        User user = getEntityById(userId);
        user.setPrivacyAgreed(1);
        userMapper.updateById(user);
        return getById(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void blockUser(Long currentUserId, Long targetUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或token已过期");
        }
        if (targetUserId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户不存在");
        }
        if (currentUserId.equals(targetUserId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能拉黑自己");
        }
        User target = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, targetUserId)
                .eq(User::getDeleted, 0)
                .last("limit 1"));
        if (target == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "用户不存在");
        }
        Long existing = userBlockMapper.selectCount(new LambdaQueryWrapper<UserBlock>()
                .eq(UserBlock::getUserId, currentUserId)
                .eq(UserBlock::getBlockedUserId, targetUserId));
        if (existing != null && existing > 0) {
            return;
        }
        UserBlock block = new UserBlock();
        block.setUserId(currentUserId);
        block.setBlockedUserId(targetUserId);
        block.setCreatedAt(java.time.LocalDateTime.now());
        userBlockMapper.insert(block);
        // Blocking removes follows in both directions: I stop following them and they stop following me
        unfollowPair(currentUserId, targetUserId);
        unfollowPair(targetUserId, currentUserId);
    }

    /** Remove the follower -> target follow and update both users' following / follower counts */
    private void unfollowPair(Long followerId, Long targetId) {
        int removed = userFollowMapper.delete(new LambdaQueryWrapper<com.ugccommunity.backend.entity.UserFollow>()
                .eq(com.ugccommunity.backend.entity.UserFollow::getUserId, followerId)
                .eq(com.ugccommunity.backend.entity.UserFollow::getTargetUserId, targetId));
        if (removed <= 0) return;
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                .eq(User::getId, followerId).setSql("follow_count = greatest(follow_count - 1, 0)"));
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                .eq(User::getId, targetId).setSql("fans_count = greatest(fans_count - 1, 0)"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void unblockUser(Long currentUserId, Long targetUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或token已过期");
        }
        if (targetUserId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户不存在");
        }
        userBlockMapper.delete(new LambdaQueryWrapper<UserBlock>()
                .eq(UserBlock::getUserId, currentUserId)
                .eq(UserBlock::getBlockedUserId, targetUserId));
    }

    public java.util.List<UserVO> blockedUsers(Long currentUserId) {
        java.util.List<UserBlock> blocks = userBlockMapper.selectList(new LambdaQueryWrapper<UserBlock>()
                .eq(UserBlock::getUserId, currentUserId));
        java.util.List<UserVO> result = new java.util.ArrayList<UserVO>();
        for (UserBlock block : blocks) {
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getId, block.getBlockedUserId())
                    .eq(User::getDeleted, 0)
                    .last("limit 1"));
            if (user != null) {
                result.add(toVO(user, currentUserId));
            }
        }
        return result;
    }

    public UserVO toVO(User user) {
        return toVO(user, null);
    }

    public UserVO toBasicVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(local(user.getAvatar()));
        vo.setBackgroundImage(StringUtils.hasText(user.getBackgroundImage()) ? local(user.getBackgroundImage()) : "asset_bg");
        vo.setGender(user.getGender());
        vo.setGenderText(user.getGender() != null && user.getGender() == 1 ? "古兽" : "人类");
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setBio(user.getBio());
        vo.setPrivacyAgreed(user.getPrivacyAgreed() == null ? 0 : user.getPrivacyAgreed());
        vo.setFollowCount(user.getFollowCount() == null ? 0 : user.getFollowCount());
        vo.setFansCount(user.getFansCount() == null ? 0 : user.getFansCount());
        vo.setLikeCount(user.getLikeCount() == null ? 0 : user.getLikeCount());
        vo.setPinned(false);
        return vo;
    }

    public UserVO toVO(User user, Long currentUserId) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(local(user.getAvatar()));
        vo.setBackgroundImage(StringUtils.hasText(user.getBackgroundImage()) ? local(user.getBackgroundImage()) : "asset_bg");
        vo.setGender(user.getGender());
        vo.setGenderText(user.getGender() != null && user.getGender() == 1 ? "古兽" : "人类");
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        boolean self = currentUserId != null && currentUserId.equals(user.getId());
        vo.setPhone(self ? user.getPhone() : null);
        vo.setEmail(self ? user.getEmail() : null);
        vo.setBirthday(user.getBirthday() == null ? null : user.getBirthday().toString());
        vo.setBio(user.getBio());
        vo.setPrivacyAgreed(user.getPrivacyAgreed() == null ? 0 : user.getPrivacyAgreed());
        vo.setFollowCount(userFollowMapper.selectCount(new LambdaQueryWrapper<com.ugccommunity.backend.entity.UserFollow>()
                .eq(com.ugccommunity.backend.entity.UserFollow::getUserId, user.getId())).intValue());
        vo.setFansCount(userFollowMapper.selectCount(new LambdaQueryWrapper<com.ugccommunity.backend.entity.UserFollow>()
                .eq(com.ugccommunity.backend.entity.UserFollow::getTargetUserId, user.getId())).intValue());
        java.util.List<com.ugccommunity.backend.entity.Work> userWorks = workMapper.selectList(new LambdaQueryWrapper<com.ugccommunity.backend.entity.Work>()
                .eq(com.ugccommunity.backend.entity.Work::getUserId, user.getId())
                .eq(com.ugccommunity.backend.entity.Work::getStatus, "published")
                .eq(com.ugccommunity.backend.entity.Work::getIsDeleted, 0));
        java.util.List<Long> workIds = new java.util.ArrayList<Long>();
        for (com.ugccommunity.backend.entity.Work work : userWorks) {
            workIds.add(work.getId());
        }
        vo.setLikeCount(workIds.isEmpty() ? 0 : userLikeMapper.selectCount(
                new LambdaQueryWrapper<com.ugccommunity.backend.entity.UserLike>()
                        .in(com.ugccommunity.backend.entity.UserLike::getWorkId, workIds)
        ).intValue());
        vo.setWorkCount(workMapper.selectCount(new LambdaQueryWrapper<com.ugccommunity.backend.entity.Work>()
                .eq(com.ugccommunity.backend.entity.Work::getUserId, user.getId())
                .eq(com.ugccommunity.backend.entity.Work::getStatus, "published")
                .eq(com.ugccommunity.backend.entity.Work::getIsDeleted, 0)).intValue());
        vo.setFavoriteCount(userFavoriteMapper.selectCount(new LambdaQueryWrapper<com.ugccommunity.backend.entity.UserFavorite>()
                .eq(com.ugccommunity.backend.entity.UserFavorite::getUserId, user.getId())).intValue());
        vo.setPinned(isPinned(currentUserId, user.getId()));
        return vo;
    }

    public java.util.List<UserVO> following(Long userId) {
        return following(userId, userId);
    }

    public java.util.List<UserVO> following(Long userId, Long currentUserId) {
        java.util.List<com.ugccommunity.backend.entity.UserFollow> follows = userFollowMapper.selectList(
                new LambdaQueryWrapper<com.ugccommunity.backend.entity.UserFollow>()
                        .eq(com.ugccommunity.backend.entity.UserFollow::getUserId, userId)
                        // Most recently followed first; previously unsorted (by followee id), so unfollow + refollow kept the old position
                        .orderByDesc(com.ugccommunity.backend.entity.UserFollow::getCreatedAt)
                        .orderByDesc(com.ugccommunity.backend.entity.UserFollow::getTargetUserId)
        );
        java.util.List<UserVO> result = new java.util.ArrayList<UserVO>();
        for (com.ugccommunity.backend.entity.UserFollow follow : follows) {
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getId, follow.getTargetUserId())
                    .eq(User::getDeleted, 0)
                    .last("limit 1"));
            if (user != null) {
                result.add(toVO(user, currentUserId));
            }
        }
        return pinnedFirst(result);
    }

    /** Users pinned by the current viewer come first; the rest keep their order (stable sort) */
    static java.util.List<UserVO> pinnedFirst(java.util.List<UserVO> users) {
        java.util.List<UserVO> sorted = new java.util.ArrayList<UserVO>(users);
        sorted.sort((a, b) -> Boolean.compare(Boolean.TRUE.equals(b.getPinned()), Boolean.TRUE.equals(a.getPinned())));
        return sorted;
    }

    public java.util.List<UserVO> followers(Long userId) {
        return followers(userId, userId);
    }

    public java.util.List<UserVO> followers(Long userId, Long currentUserId) {
        java.util.List<com.ugccommunity.backend.entity.UserFollow> follows = userFollowMapper.selectList(
                new LambdaQueryWrapper<com.ugccommunity.backend.entity.UserFollow>()
                        .eq(com.ugccommunity.backend.entity.UserFollow::getTargetUserId, userId)
                        .orderByDesc(com.ugccommunity.backend.entity.UserFollow::getCreatedAt)
                        .orderByDesc(com.ugccommunity.backend.entity.UserFollow::getUserId)
        );
        java.util.List<UserVO> result = new java.util.ArrayList<UserVO>();
        for (com.ugccommunity.backend.entity.UserFollow follow : follows) {
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getId, follow.getUserId())
                    .eq(User::getDeleted, 0)
                    .last("limit 1"));
            if (user != null) {
                result.add(toVO(user, currentUserId));
            }
        }
        return result;
    }

    private Integer value(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isPinned(Long userId, Long targetUserId) {
        return userId != null && targetUserId != null && userPinMapper.selectCount(new LambdaQueryWrapper<UserPin>()
                .eq(UserPin::getUserId, userId)
                .eq(UserPin::getTargetType, "user")
                .eq(UserPin::getTargetId, targetUserId)) > 0;
    }

    private void validateAccount(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "账号不能为空");
        }
        if (username.length() < MIN_ACCOUNT_LENGTH || username.length() > MAX_ACCOUNT_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "账号长度需为6到16位");
        }
        if (!username.matches("[A-Za-z0-9_]+")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "账号仅支持英文、数字和下划线");
        }
    }

    private void validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "密码不能为空");
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "密码长度需为8到18位");
        }
    }

    private void requireBoundContact(String phone, String email) {
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(email)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "注册必须绑定手机号和邮箱");
        }
        if (!phone.trim().matches("^1\\d{10}$")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "手机号格式不正确");
        }
        if (!email.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "邮箱格式不正确");
        }
    }

    private void ensureUniqueContact(String phone, String email, Long excludeUserId) {
        if (StringUtils.hasText(phone)) {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, phone.trim());
            if (excludeUserId != null) wrapper.ne(User::getId, excludeUserId);
            if (userMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(ResultCode.DATA_EXIST, "手机号已绑定其他账号");
            }
        }
        if (StringUtils.hasText(email)) {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, email.trim());
            if (excludeUserId != null) wrapper.ne(User::getId, excludeUserId);
            if (userMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(ResultCode.DATA_EXIST, "邮箱已绑定其他账号");
            }
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
