package com.ugccommunity.backend.controller;

import com.ugccommunity.backend.common.result.PageResult;
import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.common.utils.LoginContext;
import com.ugccommunity.backend.dto.request.PublishWorkRequest;
import com.ugccommunity.backend.service.RateLimitService;
import com.ugccommunity.backend.service.WorkService;
import com.ugccommunity.backend.vo.CommentVO;
import com.ugccommunity.backend.vo.PaidUnlockEarningsVO;
import com.ugccommunity.backend.vo.WorkVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/works")
public class WorkController {
    private final WorkService workService;
    private final RateLimitService rateLimitService;

    public WorkController(WorkService workService, RateLimitService rateLimitService) {
        this.workService = workService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public Result<PageResult<WorkVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String sort
    ) {
        return Result.success(workService.page(LoginContext.getCurrentUserIdOrThrow(), pageNum, pageSize, type, keyword, tag, sort));
    }

    @GetMapping("/following-feed")
    public Result<java.util.List<WorkVO>> followingFeed(
            @RequestParam(required = false) Long authorId,
            @RequestParam(defaultValue = "8") Integer pageSize
    ) {
        return Result.success(workService.followingFeed(LoginContext.getCurrentUserIdOrThrow(), authorId, pageSize));
    }

    @GetMapping("/payment-earnings")
    public Result<PaidUnlockEarningsVO> paymentEarnings() {
        return Result.success(workService.paymentEarnings(LoginContext.getCurrentUserIdOrThrow()));
    }

    @GetMapping("/{id}")
    public Result<WorkVO> detail(@PathVariable Long id) {
        return Result.success(workService.detail(LoginContext.getCurrentUserIdOrThrow(), id));
    }

    @GetMapping("/view-history")
    public Result<java.util.List<WorkVO>> history() {
        return Result.success(workService.history(LoginContext.getCurrentUserIdOrThrow()));
    }

    @PostMapping
    public Result<WorkVO> publish(@Valid @RequestBody PublishWorkRequest request) {
        Long userId = LoginContext.getCurrentUserIdOrThrow();
        rateLimitService.checkPublish(userId);
        return Result.success(workService.publish(userId, request));
    }

    @PutMapping("/{id}")
    public Result<WorkVO> updateOwnWork(@PathVariable Long id, @Valid @RequestBody PublishWorkRequest request) {
        return Result.success(workService.updateOwnWork(LoginContext.getCurrentUserIdOrThrow(), id, request));
    }

    @PatchMapping("/{id}/payment-settings")
    public Result<WorkVO> updatePaymentSettings(@PathVariable Long id, @RequestBody PublishWorkRequest request) {
        return Result.success(workService.updatePaymentSettings(LoginContext.getCurrentUserIdOrThrow(), id, request));
    }

    @GetMapping("/{id}/comments")
    public Result<java.util.List<CommentVO>> comments(@PathVariable Long id) {
        return Result.success(workService.comments(LoginContext.getCurrentUserIdOrThrow(), id));
    }

    @GetMapping("/comment-options")
    public Result<java.util.List<String>> commentOptions() {
        LoginContext.getCurrentUserIdOrThrow();
        return Result.success(workService.allowedComments());
    }

    @PostMapping("/{id}/comments")
    public Result<CommentVO> addComment(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        Long userId = LoginContext.getCurrentUserIdOrThrow();
        rateLimitService.checkComment(userId);
        return Result.success(workService.addComment(userId, id, body == null ? null : body.get("content")));
    }

    @DeleteMapping("/{workId}/comments/{commentId}")
    public Result<Void> deleteComment(@PathVariable Long workId, @PathVariable Long commentId) {
        workService.deleteComment(LoginContext.getCurrentUserIdOrThrow(), workId, commentId);
        return Result.success();
    }

    @PostMapping("/{id}/like")
    public Result<WorkVO> like(@PathVariable Long id, @RequestParam(required = false) Boolean liked) {
        Long userId = LoginContext.getCurrentUserIdOrThrow();
        rateLimitService.checkAction(userId, "like");
        // New clients send the desired state (liked=true/false) so a stale heart in a list cannot invert the like; legacy clients without the parameter still toggle
        return Result.success(workService.setLike(userId, id, liked));
    }

    @PostMapping("/{id}/favorite")
    public Result<WorkVO> favorite(@PathVariable Long id, @RequestParam(required = false) Boolean favorited) {
        Long userId = LoginContext.getCurrentUserIdOrThrow();
        rateLimitService.checkAction(userId, "favorite");
        // Same as likes: new clients send the desired state so a stale favorite state cannot invert the action; without the parameter it still toggles
        return Result.success(workService.setFavorite(userId, id, favorited));
    }

    @PostMapping("/{id}/unlock")
    public Result<WorkVO> unlock(@PathVariable Long id) {
        Long userId = LoginContext.getCurrentUserIdOrThrow();
        rateLimitService.checkAction(userId, "unlock");
        return Result.success(workService.unlock(userId, id));
    }

    @PostMapping("/{id}/report")
    public Result<Void> report(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        workService.report(LoginContext.getCurrentUserIdOrThrow(), id, body == null ? null : body.get("reason"));
        return Result.success();
    }

    @PostMapping("/{id}/hide")
    public Result<Void> hide(@PathVariable Long id) {
        workService.hideOwnWork(LoginContext.getCurrentUserIdOrThrow(), id);
        return Result.success();
    }

    @PostMapping("/{id}/unhide")
    public Result<Void> unhide(@PathVariable Long id) {
        workService.unhideOwnWork(LoginContext.getCurrentUserIdOrThrow(), id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workService.deleteOwnWork(LoginContext.getCurrentUserIdOrThrow(), id);
        return Result.success();
    }
}
