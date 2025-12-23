package com.echo.controller;

import com.echo.service.FriendshipService;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 好友控制器
 */
@RestController
@RequestMapping("/friends")
public class FriendController {

    @Autowired
    private FriendshipService friendshipService;

    /**
     * 搜索用户
     */
    @GetMapping("/search")
    public Result<Object> searchUsers(@RequestParam String keyword,
                                      @RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "10") Integer size) {
        return friendshipService.searchUsers(keyword, page, size);
    }

    /**
     * 发送好友请求
     */
    @PostMapping("/request")
    public Result<Object> sendRequest(@RequestBody Map<String, Object> payload) {
        Long currentUserId = getCurrentUserId(); // 需实现获取ID逻辑
        if (currentUserId == null) return Result.fail("未登录");
        
        Integer targetUserIdInt = (Integer) payload.get("targetUserId");
        Long targetUserId = Long.valueOf(targetUserIdInt);
        String remark = (String) payload.get("remark");
        
        return friendshipService.sendRequest(currentUserId, targetUserId, remark);
    }

    /**
     * 获取好友请求列表
     */
    @GetMapping("/requests")
    public Result<Object> getRequests(@RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "10") Integer size) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        return friendshipService.getRequests(currentUserId, status, page, size);
    }

    /**
     * 处理好友请求
     */
    @PutMapping("/request/{requestId}/handle")
    public Result<Object> handleRequest(@PathVariable Long requestId,
                                        @RequestBody Map<String, String> payload) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        
        String action = payload.get("action");
        String remark = payload.get("remark");
        
        return friendshipService.handleRequest(currentUserId, requestId, action, remark);
    }

    /**
     * 获取好友列表
     */
    @GetMapping("/list")
    public Result<Object> getFriendList(@RequestParam(required = false) String keyword,
                                        @RequestParam(defaultValue = "1") Integer page,
                                        @RequestParam(defaultValue = "20") Integer size) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        return friendshipService.getFriendList(currentUserId, keyword, page, size);
    }

    /**
     * 更新好友备注
     */
    @PutMapping("/{friendId}/remark")
    public Result<Object> updateRemark(@PathVariable Long friendId,
                                       @RequestBody Map<String, String> payload) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        
        String remark = payload.get("remark");
        return friendshipService.updateRemark(currentUserId, friendId, remark);
    }

    /**
     * 删除好友
     */
    @DeleteMapping("/{friendId}")
    public Result<Object> deleteFriend(@PathVariable Long friendId) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        return friendshipService.deleteFriend(currentUserId, friendId);
    }

    private Long getCurrentUserId() {
        // 从SecurityContext中获取
        // 这里假设Principal中存储了Details，Details中有userId
        // 实际上需要看JwtAuthenticationFilter怎么放的
        // 之前Filter里是 userDetails.loadUserByUsername -> standard User object.
        // I need to fetch ID from DB or encoded in token/principal.
        // Let's use JwtUtil to decode token from header again? Or easier: look up by username.
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
             // 注入UserService来查ID? 或者把ID放到Authentication里
             // 这里为了简单，我先注入UserService
             return userService.findByUsername(auth.getName()).getId();
        }
        return null;
    }
    
    @Autowired
    private com.echo.service.UserService userService;
}
