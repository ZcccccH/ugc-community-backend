package com.ugccommunity.backend.controller;

import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.common.utils.LoginContext;
import com.ugccommunity.backend.service.PinService;
import com.ugccommunity.backend.vo.PinVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pins")
public class PinController {
    private final PinService pinService;

    public PinController(PinService pinService) {
        this.pinService = pinService;
    }

    @GetMapping
    public Result<List<PinVO>> list(@RequestParam String type) {
        return Result.success(pinService.list(LoginContext.getCurrentUserIdOrThrow(), type));
    }

    @PostMapping("/{type}/{id}")
    public Result<PinVO> pin(@PathVariable String type, @PathVariable Long id) {
        return Result.success(pinService.pin(LoginContext.getCurrentUserIdOrThrow(), type, id));
    }

    @DeleteMapping("/{type}/{id}")
    public Result<Void> unpin(@PathVariable String type, @PathVariable Long id) {
        pinService.unpin(LoginContext.getCurrentUserIdOrThrow(), type, id);
        return Result.success();
    }
}
