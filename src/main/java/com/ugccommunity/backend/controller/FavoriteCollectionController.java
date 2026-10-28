package com.ugccommunity.backend.controller;

import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.common.result.PageResult;
import com.ugccommunity.backend.common.utils.LoginContext;
import com.ugccommunity.backend.service.FavoriteCollectionService;
import com.ugccommunity.backend.vo.FavoriteCollectionVO;
import com.ugccommunity.backend.vo.WorkVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorite-collections")
public class FavoriteCollectionController {
    private final FavoriteCollectionService favoriteCollectionService;

    public FavoriteCollectionController(FavoriteCollectionService favoriteCollectionService) {
        this.favoriteCollectionService = favoriteCollectionService;
    }

    @GetMapping
    public Result<List<FavoriteCollectionVO>> mine() {
        return Result.success(favoriteCollectionService.mine(LoginContext.getCurrentUserIdOrThrow()));
    }

    @PostMapping
    public Result<FavoriteCollectionVO> create(@RequestBody Map<String, String> body) {
        return Result.success(favoriteCollectionService.create(LoginContext.getCurrentUserIdOrThrow(), body == null ? null : body.get("name")));
    }

    @PutMapping("/{id}")
    public Result<FavoriteCollectionVO> rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.success(favoriteCollectionService.rename(LoginContext.getCurrentUserIdOrThrow(), id, body == null ? null : body.get("name")));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        favoriteCollectionService.deleteOwn(LoginContext.getCurrentUserIdOrThrow(), id);
        return Result.success();
    }

    @GetMapping("/{id}/works")
    public Result<PageResult<WorkVO>> works(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") Integer pageNum,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") Integer pageSize
    ) {
        return Result.success(favoriteCollectionService.works(LoginContext.getCurrentUserIdOrThrow(), id, pageNum, pageSize));
    }

    @PostMapping("/{id}/works/{workId}")
    public Result<Void> addWork(@PathVariable Long id, @PathVariable Long workId) {
        favoriteCollectionService.addWork(LoginContext.getCurrentUserIdOrThrow(), id, workId);
        return Result.success();
    }

    @DeleteMapping("/{id}/works/{workId}")
    public Result<Void> removeWork(@PathVariable Long id, @PathVariable Long workId) {
        favoriteCollectionService.removeWork(LoginContext.getCurrentUserIdOrThrow(), id, workId);
        return Result.success();
    }

    @GetMapping("/works/{workId}/collections")
    public Result<List<Long>> workCollectionIds(@PathVariable Long workId) {
        return Result.success(favoriteCollectionService.workCollectionIds(LoginContext.getCurrentUserIdOrThrow(), workId));
    }

    @PostMapping("/works/{workId}/collections")
    public Result<Void> syncWorkCollections(@PathVariable Long workId, @RequestBody Map<String, List<Long>> body) {
        favoriteCollectionService.syncWorkCollections(LoginContext.getCurrentUserIdOrThrow(), workId, body == null ? null : body.get("collectionIds"));
        return Result.success();
    }
}
