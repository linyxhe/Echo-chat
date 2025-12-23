package com.echo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.echo.pojo.Comment;
import com.echo.pojo.Post;
import com.echo.vo.Result;

public interface PostService extends IService<Post> {
    
    Result<Object> createPost(Long userId, Post post);
    
    Result<Object> getPosts(Long currentUserId, Long targetUserId, String type, Integer page, Integer size);
    
    Result<Object> likePost(Long userId, Long postId);
    
    Result<Object> createComment(Long userId, Long postId, Comment comment);
    
    Result<Object> getComments(Long postId, Integer page, Integer size);
    
    Result<Object> deletePost(Long userId, Long postId);
}
