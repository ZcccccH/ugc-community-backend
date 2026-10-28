package com.ugccommunity.backend.controller;

import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.common.utils.LoginContext;
import com.ugccommunity.backend.service.MessageService;
import com.ugccommunity.backend.vo.MessageVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public Result<List<MessageVO>> list() {
        return Result.success(messageService.list(LoginContext.getCurrentUserIdOrThrow()));
    }

    @GetMapping("/unread-count")
    public Result<Integer> unreadCount() {
        return Result.success(messageService.unreadCount(LoginContext.getCurrentUserIdOrThrow()));
    }

    @PostMapping("/read-all")
    public Result<Void> readAll() {
        messageService.readAll(LoginContext.getCurrentUserIdOrThrow());
        return Result.success();
    }

    @PostMapping("/{id}/read")
    public Result<Void> readOne(@PathVariable Long id) {
        messageService.readOne(LoginContext.getCurrentUserIdOrThrow(), id);
        return Result.success();
    }
}
