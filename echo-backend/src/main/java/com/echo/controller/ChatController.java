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
     * 文件上传（receiverId 好友 与 groupId 群 二选一）
     */
    @PostMapping("/file/upload")
    public Result<Object> uploadFile(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "receiverId", required = false) Long receiverId,
                                     @RequestParam(value = "groupId", required = false) Long groupId) {
        return chatService.uploadFile(file, receiverId, groupId);
    }

    /**
     * 清空与某好友/AI 助手的全部聊天记录（软删除，只影响当前用户视角）
     */
    @DeleteMapping("/conversations/{friendId}")
    public Result<Object> clearConversation(@PathVariable Long friendId) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        return chatService.deleteMessages(currentUserId, friendId, "ALL", null);
    }

    /** 从当前用户的消息列表隐藏会话；新消息到达时会自动重新出现。 */
    @PutMapping("/conversations/{friendId}/archive")
    public Result<Object> archiveConversation(@PathVariable Long friendId) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        return chatService.setConversationArchived(currentUserId, friendId, true);
    }

    /** 恢复当前用户视角已隐藏的会话。 */
    @DeleteMapping("/conversations/{friendId}/archive")
    public Result<Object> unarchiveConversation(@PathVariable Long friendId) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        return chatService.setConversationArchived(currentUserId, friendId, false);
    }

    @PutMapping("/conversations/{friendId}/pin")
    public Result<Object> pinConversation(@PathVariable Long friendId) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        return chatService.setConversationPinned(currentUserId, friendId, true);
    }

    @DeleteMapping("/conversations/{friendId}/pin")
    public Result<Object> unpinConversation(@PathVariable Long friendId) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        return chatService.setConversationPinned(currentUserId, friendId, false);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
             return userService.findByUsername(auth.getName()).getId();
        }
        return null;
    }
}
