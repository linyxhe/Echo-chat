package com.echo.controller;

import com.echo.pojo.Comment;
import com.echo.pojo.Post;
import com.echo.service.PostService;
import com.echo.service.UserService;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/posts")
public class PostController {

    @Autowired
    private PostService postService;
    
    @Autowired
    private UserService userService;

    @PostMapping
    public Result<Object> createPost(@RequestBody Post post) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        return postService.createPost(currentUserId, post);
    }

    @GetMapping
    public Result<Object> getPosts(@RequestParam(required = false) Long userId,
                                   @RequestParam(defaultValue = "FRIENDS") String type,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "10") Integer size) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        return postService.getPosts(currentUserId, userId, type, page, size);
    }

    @PostMapping("/{postId}/like")
    public Result<Object> likePost(@PathVariable Long postId) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        return postService.likePost(currentUserId, postId);
    }

    @PostMapping("/{postId}/comments")
    public Result<Object> createComment(@PathVariable Long postId, @RequestBody Comment comment) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        return postService.createComment(currentUserId, postId, comment);
    }

    @GetMapping("/{postId}/comments")
    public Result<Object> getComments(@PathVariable Long postId,
                                      @RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "20") Integer size) {
        return postService.getComments(postId, page, size);
    }

    @DeleteMapping("/{postId}")
    public Result<Object> deletePost(@PathVariable Long postId) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) return Result.fail("未登录");
        return postService.deletePost(currentUserId, postId);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
             return userService.findByUsername(auth.getName()).getId();
        }
        return null;
    }
}
