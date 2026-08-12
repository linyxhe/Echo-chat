package com.echo.controller;

import com.echo.service.SearchService;
import com.echo.service.UserService;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 全文搜索：用户 + 聊天记录 + 群消息。默认需登录。
 */
@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired
    private SearchService searchService;

    @Autowired
    private UserService userService;

    @GetMapping
    public Result<Object> search(@RequestParam String keyword,
                                 @RequestParam(defaultValue = "10") Integer limit) {
        Long uid = getCurrentUserId();
        if (uid == null) return Result.fail("未登录");
        int lim = Math.min(limit != null ? limit : 10, 20);
        Map<String, Object> data = new HashMap<>();
        data.put("users", searchService.searchUsers(keyword, uid, lim));
        data.put("friendMessages", searchService.searchFriendMessages(keyword, uid, lim));
        data.put("groupMessages", searchService.searchGroupMessages(keyword, uid, lim));
        return Result.success(data);
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
