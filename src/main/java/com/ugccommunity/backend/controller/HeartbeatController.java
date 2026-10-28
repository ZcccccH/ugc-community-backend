package com.ugccommunity.backend.controller;

import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.common.utils.LoginContext;
import com.ugccommunity.backend.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/heartbeat")
public class HeartbeatController {
    private final UserService userService;

    public HeartbeatController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<Map<String, Object>> heartbeat() {
        Long userId = LoginContext.getCurrentUserIdOrThrow();
        userService.getEntityById(userId);
        userService.markActive(userId);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("serverTime", System.currentTimeMillis());
        data.put("userId", userId);
        data.put("status", "ok");
        return Result.success(data);
    }
}
