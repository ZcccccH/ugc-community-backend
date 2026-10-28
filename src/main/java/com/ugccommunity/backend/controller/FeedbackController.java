package com.ugccommunity.backend.controller;

import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.common.utils.LoginContext;
import com.ugccommunity.backend.service.FeedbackService;
import com.ugccommunity.backend.vo.FeedbackVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {
    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public Result<List<FeedbackVO>> mine() {
        return Result.success(feedbackService.mine(LoginContext.getCurrentUserIdOrThrow()));
    }

    @PostMapping
    public Result<FeedbackVO> create(@RequestBody Map<String, String> body) {
        return Result.success(feedbackService.create(LoginContext.getCurrentUserIdOrThrow(), body == null ? null : body.get("content")));
    }
}
