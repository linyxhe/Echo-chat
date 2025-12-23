package com.echo.controller;

import com.echo.service.ChatService;
import com.echo.service.UserService;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 聊天控制器
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;
    
    @Autowired
    private UserService userService;

    /**
     * 获取聊天记录
     */
    @GetMapping("/messages")
    public Result<Object> getMessages(@RequestParam Long friendId,
                                      @RequestParam(required = false) String beforeTime,
                                      @RequestParam(defaultValue = "20") Integer limit) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        return chatService.getMessages(currentUserId, friendId, beforeTime, limit);
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/conversations")
    public Result<Object> getConversations(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "20") Integer size) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        return chatService.getConversations(currentUserId, page, size);
    }

    /**
     * 文件上传
     */
    @PostMapping("/file/upload")
    public Result<Object> uploadFile(@RequestParam("file") MultipartFile file,
                                     @RequestParam("receiverId") Long receiverId) {
        return chatService.uploadFile(file, receiverId);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
             return userService.findByUsername(auth.getName()).getId();
        }
        return null;
    }
}
