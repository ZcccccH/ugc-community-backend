package com.ugccommunity.backend.controller;

import com.ugccommunity.backend.common.result.PageResult;
import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.common.utils.LoginContext;
import com.ugccommunity.backend.dto.request.UpdateProfileRequest;
import com.ugccommunity.backend.service.UserService;
import com.ugccommunity.backend.service.WorkService;
import com.ugccommunity.backend.vo.UserVO;
import com.ugccommunity.backend.vo.WorkVO;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final WorkService workService;
    private final com.ugccommunity.backend.service.WorkCollectionService collectionService;

    public UserController(UserService userService, WorkService workService, com.ugccommunity.backend.service.WorkCollectionService collectionService) {
        this.collectionService = collectionService;
        this.userService = userService;
        this.workService = workService;
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        Long userId = LoginContext.getCurrentUserIdOrThrow();
        return Result.success(userService.getById(userId, userId));
    }

    @GetMapping("/me-basic")
    public Result<UserVO> meBasic() {
        return Result.success(userService.getBasicById(LoginContext.getCurrentUserIdOrThrow()));
    }

    @PutMapping("/me")
    public Result<UserVO> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.success(userService.updateProfile(LoginContext.getCurrentUserIdOrThrow(), request));
    }

    @PostMapping("/privacy-agreement")
    public Result<UserVO> agreePrivacy() {
        return Result.success(userService.agreePrivacy(LoginContext.getCurrentUserIdOrThrow()));
    }

    @GetMapping("/search")
    public Result<List<UserVO>> searchUsers(@RequestParam String keyword) {
        return Result.success(userService.searchUsers(keyword, LoginContext.getCurrentUserIdOrThrow()));
    }

    /** An author's collections: the owner sees all; others only see public ones with visible works */
    @GetMapping("/{id}/collections")
    public Result<java.util.List<com.ugccommunity.backend.vo.WorkCollectionVO>> collections(@PathVariable Long id) {
        return Result.success(collectionService.userCollections(LoginContext.getCurrentUserIdOrThrow(), id));
    }

    @GetMapping("/{id}")
    public Result<UserVO> getById(@PathVariable Long id) {
        return Result.success(userService.getById(id, LoginContext.getCurrentUserIdOrThrow()));
    }

    @GetMapping("/{id}/space")
    public Result<PageResult<WorkVO>> space(
            @PathVariable Long id,
            @RequestParam(defaultValue = "works") String tab,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        return Result.success(workService.userWorks(LoginContext.getCurrentUserIdOrThrow(), id, tab, type, pageNum, pageSize));
    }

    @PostMapping("/{id}/follow")
    public Result<Boolean> follow(@PathVariable Long id, @RequestParam(required = false) Boolean followed) {
        // New clients send the desired state so a stale follow state in a list cannot invert the action; without the parameter it still toggles
        return Result.success(workService.setFollow(LoginContext.getCurrentUserIdOrThrow(), id, followed));
    }

    @PostMapping("/{id}/block")
    public Result<String> block(@PathVariable Long id) {
        userService.blockUser(LoginContext.getCurrentUserIdOrThrow(), id);
        return Result.success("已拉黑该用户");
    }

    @DeleteMapping("/{id}/block")
    public Result<String> unblock(@PathVariable Long id) {
        userService.unblockUser(LoginContext.getCurrentUserIdOrThrow(), id);
        return Result.success("已解除拉黑");
    }

    @GetMapping("/blocked")
    public Result<List<UserVO>> blocked() {
        Long currentUserId = LoginContext.getCurrentUserIdOrThrow();
        return Result.success(userService.blockedUsers(currentUserId));
    }

    @GetMapping("/{id}/following")
    public Result<List<UserVO>> following(@PathVariable Long id) {
        return Result.success(userService.following(id, LoginContext.getCurrentUserIdOrThrow()));
    }

    @GetMapping("/{id}/followers")
    public Result<List<UserVO>> followers(@PathVariable Long id) {
        return Result.success(userService.followers(id, LoginContext.getCurrentUserIdOrThrow()));
    }
}
