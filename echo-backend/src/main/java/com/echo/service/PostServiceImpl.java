package com.echo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.echo.mapper.*;
import com.echo.pojo.*;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements PostService {

    @Autowired
    private PostMapper postMapper;
    
    @Autowired
    private PostLikeMapper postLikeMapper;
    
    @Autowired
    private CommentMapper commentMapper;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private FriendshipMapper friendshipMapper;
    
    @Autowired
    private SystemConfigMapper systemConfigMapper;

    @Autowired
    private ContentModerationService contentModerationService;

    @Override
    public Result<Object> createPost(Long userId, Post post) {
        String matchedPostWord = contentModerationService.findMatchedWord(post.getContent());
        if (matchedPostWord != null) return Result.fail("内容包含敏感词: " + matchedPostWord);
        
        post.setUserId(userId);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setStatus(1);
        post.setCreatedAt(LocalDateTime.now());
        
        if (post.getVisibility() == null) {
            post.setVisibility("PUBLIC");
        }
        
        postMapper.insert(post);
        
        return Result.success("发布成功", post);
    }

    @Override
    public Result<Object> getPosts(Long currentUserId, Long targetUserId, String type, Integer page, Integer size) {
        Page<Post> pageParam = new Page<>(page, size);
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        // 移除 queryWrapper.eq("status", 1); 以便前端能接收到状态为 0 的帖子并显示为“已屏蔽”
        // 但需要注意隐私逻辑
        
        if (targetUserId != null) {
            // 查看指定用户的动态
            queryWrapper.eq("user_id", targetUserId);
            // 权限检查：如果是私密，只有自己能看；如果是好友可见，只有好友能看
            if (!currentUserId.equals(targetUserId)) {
                // 检查是否好友
                boolean isFriend = checkIsFriend(currentUserId, targetUserId);
                if (isFriend) {
                    queryWrapper.in("visibility", "PUBLIC", "FRIENDS");
                } else {
                    queryWrapper.eq("visibility", "PUBLIC");
                }
            }
        } else {
            // 查看时间线 (FRIENDS or ALL)
            if ("ALL".equals(type)) {
                // 所有公开动态 + 好友动态 + 自己的动态
                // 这需要复杂的查询，MyBatis-Plus如果不写XML比较难搞。
                // 简化：只看所有公开动态
                queryWrapper.eq("visibility", "PUBLIC");
            } else {
                // 默认为好友动态
                // 查找所有好友ID
                List<Long> friendIds = getFriendIds(currentUserId);
                friendIds.add(currentUserId); // 加上自己
                
                queryWrapper.in("user_id", friendIds);
                // 修正：自己的动态可以看到所有（包括私密），好友的只能看公开和好友可见
                queryWrapper.and(w -> w
                    .eq("user_id", currentUserId)
                    .or()
                    .in("visibility", "PUBLIC", "FRIENDS")
                );
            }
        }
        
        queryWrapper.orderByDesc("created_at");
        
        IPage<Post> resultPage = postMapper.selectPage(pageParam, queryWrapper);
        
        List<Map<String, Object>> list = resultPage.getRecords().stream().map(post -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", post.getId());
            map.put("userId", post.getUserId());
            map.put("content", post.getContent());
            map.put("status", post.getStatus()); // 增加 status 字段
            map.put("imageUrls", post.getMediaUrls());
            map.put("likeCount", post.getLikeCount());
            map.put("commentCount", post.getCommentCount());
            map.put("visibility", post.getVisibility());
            map.put("createdAt", post.getCreatedAt());
            
            User user = userMapper.selectById(post.getUserId());
            if (user != null) {
                map.put("userNickname", user.getNickname());
                map.put("userAvatar", user.getAvatarUrl());
            }
            
            // 检查当前用户是否已点赞
            QueryWrapper<PostLike> likeQuery = new QueryWrapper<>();
            likeQuery.eq("post_id", post.getId()).eq("user_id", currentUserId);
            map.put("isLiked", postLikeMapper.exists(likeQuery));
            
            return map;
        }).collect(Collectors.toList());
        
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", resultPage.getTotal());
        data.put("page", page);
        data.put("size", size);
        
        return Result.success(data);
    }

    private boolean checkIsFriend(Long userId1, Long userId2) {
        QueryWrapper<Friendship> query = new QueryWrapper<>();
        query.eq("user_id", userId1).eq("friend_id", userId2).eq("status", 1);
        return friendshipMapper.exists(query);
    }
    
    private List<Long> getFriendIds(Long userId) {
        QueryWrapper<Friendship> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("status", 1);
        return friendshipMapper.selectList(query).stream()
                .map(Friendship::getFriendId)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public Result<Object> likePost(Long userId, Long postId) {
        QueryWrapper<PostLike> query = new QueryWrapper<>();
        query.eq("post_id", postId).eq("user_id", userId);
        PostLike existing = postLikeMapper.selectOne(query);
        
        Post post = postMapper.selectById(postId);
        if (post == null) return Result.fail("动态不存在");

        if (existing != null) {
            // 取消点赞
            postLikeMapper.deleteById(existing.getId());
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
        } else {
            // 点赞
            PostLike like = new PostLike();
            like.setPostId(postId);
            like.setUserId(userId);
            like.setCreatedAt(LocalDateTime.now());
            postLikeMapper.insert(like);
            post.setLikeCount(post.getLikeCount() + 1);
        }
        postMapper.updateById(post);
        
        return Result.success("操作成功");
    }

    @Transactional
    @Override
    public Result<Object> createComment(Long userId, Long postId, Comment comment) {
        Post post = postMapper.selectById(postId);
        if (post == null) return Result.fail("动态不存在");

        String matchedCommentWord = contentModerationService.findMatchedWord(comment.getContent());
        if (matchedCommentWord != null) return Result.fail("评论包含敏感词: " + matchedCommentWord);
        
        comment.setUserId(userId);
        comment.setPostId(postId);
        comment.setCreatedAt(LocalDateTime.now());
        
        commentMapper.insert(comment);
        
        post.setCommentCount(post.getCommentCount() + 1);
        postMapper.updateById(post);
        
        return Result.success("评论成功");
    }

    @Override
    public Result<Object> getComments(Long postId, Integer page, Integer size) {
        Page<Comment> pageParam = new Page<>(page, size);
        QueryWrapper<Comment> query = new QueryWrapper<>();
        query.eq("post_id", postId);
        query.orderByAsc("created_at");
        
        IPage<Comment> resultPage = commentMapper.selectPage(pageParam, query);
        
        List<Map<String, Object>> list = resultPage.getRecords().stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("userId", c.getUserId());
            map.put("content", c.getContent());
            map.put("parentId", c.getParentId());
            map.put("createdAt", c.getCreatedAt());
            
            User user = userMapper.selectById(c.getUserId());
            if (user != null) {
                map.put("userNickname", user.getNickname());
                map.put("userAvatar", user.getAvatarUrl());
            }
            
            return map;
        }).collect(Collectors.toList());
        
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", resultPage.getTotal());
        
        return Result.success(data);
    }

    @Override
    public Result<Object> deletePost(Long userId, Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) return Result.fail("动态不存在");
        
        if (!post.getUserId().equals(userId)) {
            return Result.fail("无权删除");
        }
        
        post.setStatus(0); // 逻辑删除
        postMapper.deleteById(postId); // 硬删除
        
        return Result.success("删除成功");
    }
}
