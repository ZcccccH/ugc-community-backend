package com.ugccommunity.backend.controller;

import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.common.utils.LoginContext;
import com.ugccommunity.backend.dto.request.AchievementProgressRequest;
import com.ugccommunity.backend.dto.request.CatFeedRequest;
import com.ugccommunity.backend.dto.request.CrystalActionRequest;
import com.ugccommunity.backend.service.GrowthService;
import com.ugccommunity.backend.vo.AchievementVO;
import com.ugccommunity.backend.vo.CatStateVO;
import com.ugccommunity.backend.vo.CrystalStateVO;
import com.ugccommunity.backend.vo.WorkCrystalResonanceVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/growth")
public class GrowthController {
    private final GrowthService growthService;

    public GrowthController(GrowthService growthService) {
        this.growthService = growthService;
    }

    @GetMapping("/achievements")
    public Result<List<AchievementVO>> achievements() {
        return Result.success(growthService.achievements(LoginContext.getCurrentUserIdOrThrow()));
    }

    @PostMapping("/achievements/{id}/progress")
    public Result<List<AchievementVO>> progress(@PathVariable String id, @RequestBody AchievementProgressRequest request) {
        // Only accept achievements the client can detect itself (late-night visit), with a fixed amount of 1; all other progress is computed server-side from real data
        return Result.success(growthService.reportClientAchievement(LoginContext.getCurrentUserIdOrThrow(), id));
    }

    @GetMapping("/crystals")
    public Result<List<CrystalStateVO>> crystals() {
        return Result.success(growthService.crystals(LoginContext.getCurrentUserIdOrThrow()));
    }

    // The former POST /crystals/energy let clients report arbitrary energy and bypass the daily cap; it has been removed.
    // Element particles are only earned through like rewards (rewardLikeEnergy, with a daily cap); clients do not call this endpoint.

    @PostMapping("/crystals/active")
    public Result<List<CrystalStateVO>> activateCrystal(@RequestBody CrystalActionRequest request) {
        return Result.success(growthService.activateCrystal(LoginContext.getCurrentUserIdOrThrow(), request == null ? null : request.getCrystalId()));
    }

    @PostMapping("/crystals/redeem")
    public Result<List<CrystalStateVO>> redeemCrystal(@RequestBody CrystalActionRequest request) {
        return Result.success(growthService.redeemBaseCrystal(LoginContext.getCurrentUserIdOrThrow(), request == null ? null : request.getCrystalId()));
    }

    @PostMapping("/crystals/luminous/synthesize")
    public Result<List<CrystalStateVO>> synthesizeLuminousCrystal() {
        return Result.success(growthService.synthesizeLuminousCrystal(LoginContext.getCurrentUserIdOrThrow()));
    }

    @GetMapping("/cats")
    public Result<List<CatStateVO>> cats() {
        return Result.success(growthService.cats(LoginContext.getCurrentUserIdOrThrow()));
    }

    @PostMapping("/cats/{catId}/feed")
    public Result<List<CatStateVO>> feedCat(@PathVariable String catId, @RequestBody CatFeedRequest request) {
        return Result.success(growthService.feedCat(LoginContext.getCurrentUserIdOrThrow(), catId, request == null ? null : request.getCrystalId()));
    }

    @GetMapping("/works/{workId}/resonances")
    public Result<List<WorkCrystalResonanceVO>> workResonances(@PathVariable Long workId) {
        LoginContext.getCurrentUserIdOrThrow();
        return Result.success(growthService.workResonances(workId));
    }

    @PostMapping("/works/{workId}/resonances")
    public Result<List<WorkCrystalResonanceVO>> resonateWork(@PathVariable Long workId, @RequestBody CrystalActionRequest request) {
        return Result.success(growthService.resonateWork(LoginContext.getCurrentUserIdOrThrow(), workId, request == null ? null : request.getCrystalId()));
    }
}
