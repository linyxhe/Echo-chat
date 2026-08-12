package com.echo.controller;

import com.echo.service.GroupService;
import com.echo.service.UserService;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 群聊 REST。默认需登录。
 */
@RestController
@RequestMapping("/groups")
public class GroupController {

    @Autowired
    private GroupService groupService;

    @Autowired
    private UserService userService;

    @PostMapping
    public Result<Object> create(@RequestBody Map<String, Object> body) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        String name = body.get("name") != null ? String.valueOf(body.get("name")) : null;
        Object memberIdsObj = body.get("memberIds");
        List<Long> memberIds = null;
        if (memberIdsObj instanceof List<?> list) {
            memberIds = list.stream()
                    .map(o -> Long.valueOf(String.valueOf(o)))
                    .collect(java.util.stream.Collectors.toList());
        }
        return groupService.createGroup(uid, name, memberIds);
    }

    @GetMapping
    public Result<Object> list() {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        return groupService.listMyGroups(uid);
    }

    @GetMapping("/{id}")
    public Result<Object> get(@PathVariable Long id) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        if (!groupService.isMember(id, uid)) return Result.fail("非群成员");
        return groupService.getGroup(id);
    }

    @GetMapping("/{id}/messages")
    public Result<Object> messages(@PathVariable Long id,
                                   @RequestParam(required = false) String beforeTime,
                                   @RequestParam(defaultValue = "20") Integer limit) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        if (!groupService.isMember(id, uid)) return Result.fail("非群成员");
        return groupService.getMessages(id, uid, beforeTime, limit);
    }

    @PostMapping("/{id}/members")
    public Result<Object> addMember(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        Object userIdObj = body.get("userId");
        if (userIdObj == null) return Result.fail("参数缺失");
        return groupService.addMember(id, uid, Long.valueOf(String.valueOf(userIdObj)));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Result<Object> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        return groupService.removeMember(id, uid, userId);
    }

    @PostMapping("/{id}/invitations")
    public Result<Object> invite(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        Object inviteeId = body.get("userId");
        if (inviteeId == null) return Result.fail("参数缺失");
        return groupService.inviteMember(id, uid, Long.valueOf(String.valueOf(inviteeId)));
    }

    @GetMapping("/invitations")
    public Result<Object> invitations() {
        Long uid = getCurrentUserId();
        return uid == null ? Result.fail("未登录") : groupService.listMyInvitations(uid);
    }

    @PutMapping("/invitations/{invitationId}")
    public Result<Object> respondInvitation(@PathVariable Long invitationId, @RequestBody Map<String, Object> body) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        Object action = body.get("action");
        return groupService.respondInvitation(invitationId, uid, action == null ? "" : String.valueOf(action));
    }

    @PutMapping("/{id}/read")
    public Result<Object> read(@PathVariable Long id) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        return groupService.markRead(uid, id);
    }

    @PutMapping("/{id}/remark")
    public Result<Object> updateRemark(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        String remark = body == null || body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        return groupService.updateRemark(uid, id, remark);
    }

    @PutMapping("/{id}/archive")
    public Result<Object> archive(@PathVariable Long id) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        return groupService.setArchived(uid, id, true);
    }

    @DeleteMapping("/{id}/archive")
    public Result<Object> unarchive(@PathVariable Long id) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        return groupService.setArchived(uid, id, false);
    }

    @DeleteMapping("/{id}/history")
    public Result<Object> clearHistory(@PathVariable Long id) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        return groupService.clearHistory(uid, id);
    }

    @PutMapping("/{id}/pin")
    public Result<Object> pin(@PathVariable Long id) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        return groupService.setPinned(uid, id, true);
    }

    @DeleteMapping("/{id}/pin")
    public Result<Object> unpin(@PathVariable Long id) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        return groupService.setPinned(uid, id, false);
    }

    @DeleteMapping("/{id}/leave")
    public Result<Object> leave(@PathVariable Long id) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        return groupService.leaveGroup(id, uid);
    }

    @PutMapping("/{id}/settings")
    public Result<Object> updateSettings(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        Object enabled = body == null ? null : body.get("joinVerificationEnabled");
        Boolean value = enabled == null ? null : Boolean.valueOf(String.valueOf(enabled));
        return groupService.updateJoinVerification(id, uid, value);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            com.echo.pojo.User u = userService.findByUsername(auth.getName());
            return u == null ? null : u.getId();
        }
        return null;
    }
}
