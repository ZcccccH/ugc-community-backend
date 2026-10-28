package com.ugccommunity.backend.controller;

import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.common.utils.LoginContext;
import com.ugccommunity.backend.service.TagService;
import com.ugccommunity.backend.vo.TagVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {
    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("/hot")
    public Result<List<TagVO>> hotTags() {
        return Result.success(tagService.hotTags(LoginContext.getCurrentUserIdOrThrow()));
    }

    @GetMapping
    public Result<List<TagVO>> allTags() {
        return Result.success(tagService.allTags(LoginContext.getCurrentUserIdOrThrow()));
    }

    @GetMapping("/favorites")
    public Result<List<TagVO>> favorites() {
        return Result.success(tagService.favoriteTags(LoginContext.getCurrentUserIdOrThrow()));
    }

    @GetMapping("/mine")
    public Result<List<TagVO>> mine() {
        return Result.success(tagService.mineTags(LoginContext.getCurrentUserIdOrThrow()));
    }

    @GetMapping("/blocked")
    public Result<List<TagVO>> blocked() {
        return Result.success(tagService.blockedTags(LoginContext.getCurrentUserIdOrThrow()));
    }

    @PostMapping
    public Result<TagVO> create(@RequestBody java.util.Map<String, String> body) {
        return Result.success(tagService.createUserTag(LoginContext.getCurrentUserIdOrThrow(), body == null ? null : body.get("name")));
    }

    @PostMapping("/{id}/favorite")
    public Result<Boolean> favorite(@PathVariable Long id) {
        return Result.success(tagService.toggleFavorite(LoginContext.getCurrentUserIdOrThrow(), id));
    }

    /** If "blocked" is omitted, toggle (legacy clients); if true/false, set that state. Returns whether the tag is blocked afterwards */
    @PostMapping("/{id}/block")
    public Result<Boolean> block(@PathVariable Long id, @RequestParam(value = "blocked", required = false) Boolean blocked) {
        return Result.success(tagService.setBlock(LoginContext.getCurrentUserIdOrThrow(), id, blocked));
    }
}
