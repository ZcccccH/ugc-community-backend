package com.ugccommunity.backend.controller;

import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.common.utils.LoginContext;
import com.ugccommunity.backend.service.WorkCollectionService;
import com.ugccommunity.backend.service.WorkService;
import com.ugccommunity.backend.vo.WorkCollectionVO;
import com.ugccommunity.backend.vo.WorkVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.ugccommunity.backend.common.result.PageResult;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/collections")
public class WorkCollectionController {
    private final WorkCollectionService collectionService;
    private final WorkService workService;

    public WorkCollectionController(WorkCollectionService collectionService, WorkService workService) {
        this.collectionService = collectionService;
        this.workService = workService;
    }

    @GetMapping
    public Result<List<WorkCollectionVO>> mine() {
        return Result.success(collectionService.mine(LoginContext.getCurrentUserIdOrThrow()));
    }

    @PostMapping
    public Result<WorkCollectionVO> create(@RequestBody java.util.Map<String, String> body) {
        return Result.success(collectionService.create(LoginContext.getCurrentUserIdOrThrow(), body == null ? null : body.get("name")));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        collectionService.deleteOwn(LoginContext.getCurrentUserIdOrThrow(), id);
        return Result.success();
    }

    @PostMapping("/{id}/works/{workId}")
    public Result<Void> addWork(@PathVariable Long id, @PathVariable Long workId) {
        collectionService.addWork(LoginContext.getCurrentUserIdOrThrow(), id, workId);
        return Result.success();
    }

    @DeleteMapping("/{id}/works/{workId}")
    public Result<Void> removeWork(@PathVariable Long id, @PathVariable Long workId) {
        collectionService.removeWork(LoginContext.getCurrentUserIdOrThrow(), id, workId);
        return Result.success();
    }

    /** Legacy clients: without pageNum, return the whole list */
    @GetMapping("/{id}/works")
    public Result<List<WorkVO>> works(@PathVariable Long id) {
        Long userId = LoginContext.getCurrentUserIdOrThrow();
        return Result.success(workService.collectionWorks(userId, id));
    }

    @GetMapping(value = "/{id}/works", params = "pageNum")
    public Result<PageResult<WorkVO>> worksPage(@PathVariable Long id,
                                                @RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(workService.collectionWorksPage(LoginContext.getCurrentUserIdOrThrow(), id, pageNum, pageSize));
    }

    /** Collections I have favorited (whole collections by other users, not my favorite folders) */
    @GetMapping("/favorites")
    public Result<PageResult<WorkCollectionVO>> favorites(@RequestParam(defaultValue = "1") Integer pageNum,
                                                          @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(collectionService.favorites(LoginContext.getCurrentUserIdOrThrow(), pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public Result<WorkCollectionVO> detail(@PathVariable Long id) {
        return Result.success(collectionService.detail(LoginContext.getCurrentUserIdOrThrow(), id));
    }

    @PutMapping("/{id}")
    public Result<WorkCollectionVO> update(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        return Result.success(collectionService.update(LoginContext.getCurrentUserIdOrThrow(), id,
                body == null ? null : body.get("name"), body == null ? null : body.get("visibility")));
    }

    @PostMapping("/{id}/favorite")
    public Result<WorkCollectionVO> favorite(@PathVariable Long id, @RequestParam(required = false) Boolean favorited) {
        return Result.success(collectionService.setFavorite(LoginContext.getCurrentUserIdOrThrow(), id, favorited));
    }
}
