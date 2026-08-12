package com.echo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.echo.mapper.FriendRequestMapper;
import com.echo.mapper.FriendshipMapper;
import com.echo.mapper.UserMapper;
import com.echo.pojo.FriendRequest;
import com.echo.pojo.Friendship;
import com.echo.pojo.User;
import com.echo.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 好友服务实现类
 */
@Service
public class FriendshipServiceImpl extends ServiceImpl<FriendshipMapper, Friendship> implements FriendshipService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FriendRequestMapper friendRequestMapper;

    @Autowired
    private FriendshipMapper friendshipMapper;

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private NotificationService notificationService;

    @Override
    public Result<Object> searchUsers(String keyword, Integer page, Integer size) {
        Page<User> userPage = new Page<>(page, size);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("username", keyword).or().like("nickname", keyword);
        // 不显示自己? 暂不处理
        
        IPage<User> resultPage = userMapper.selectPage(userPage, queryWrapper);
        
        // 封装返回数据，这里简单返回User列表，实际可能需要封装是否好友状态
        // 为了符合接口文档，需要检查isFriend状态
        // 但这里为了简化，先返回User列表，后续完善
        
        Map<String, Object> data = new HashMap<>();
        data.put("list", resultPage.getRecords());
        data.put("total", resultPage.getTotal());
        data.put("page", page);
        data.put("size", size);
        
        return Result.success(data);
    }

    @Override
    public Result<Object> sendRequest(Long currentUserId, Long targetUserId, String remark) {
        if (currentUserId.equals(targetUserId)) {
            return Result.fail("不能添加自己为好友");
        }
        
        // 检查是否已经是好友
        QueryWrapper<Friendship> friendshipQuery = new QueryWrapper<>();
        friendshipQuery.eq("user_id", currentUserId).eq("friend_id", targetUserId).eq("status", 1);
        if (friendshipMapper.exists(friendshipQuery)) {
            return Result.fail("已经是好友了");
        }
        
        // 检查是否已发送请求且未处理
        QueryWrapper<FriendRequest> requestQuery = new QueryWrapper<>();
        requestQuery.eq("sender_id", currentUserId).eq("receiver_id", targetUserId).eq("status", "PENDING");
        if (friendRequestMapper.exists(requestQuery)) {
            return Result.fail("已发送过请求，请等待对方处理");
        }
        
        FriendRequest request = new FriendRequest();
        request.setSenderId(currentUserId);
        request.setReceiverId(targetUserId);
        request.setRemark(remark);
        request.setStatus("PENDING");
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        
        friendRequestMapper.insert(request);

        // 通知接收者
        if (notificationService != null) {
            User sender = userMapper.selectById(currentUserId);
            String nickname = sender != null ? sender.getNickname() : "有人";
            notificationService.notify(targetUserId, "FRIEND_REQUEST", "新的好友请求",
                    nickname + " 申请加你为好友", request.getId());
        }

        return Result.success("好友请求已发送");
    }

    @Override
    public Result<Object> getRequests(Long currentUserId, String status, Integer page, Integer size) {
        Page<FriendRequest> requestPage = new Page<>(page, size);
        QueryWrapper<FriendRequest> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("receiver_id", currentUserId);
        if (StringUtils.hasText(status)) {
            queryWrapper.eq("status", status);
        } else {
            queryWrapper.eq("status", "PENDING");
        }
        queryWrapper.orderByDesc("created_at");
        
        IPage<FriendRequest> resultPage = friendRequestMapper.selectPage(requestPage, queryWrapper);
        
        // 需要关联发送者信息
        List<Map<String, Object>> list = resultPage.getRecords().stream().map(req -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", req.getId());
            map.put("senderId", req.getSenderId());
            map.put("remark", req.getRemark());
            map.put("status", req.getStatus());
            map.put("createdAt", req.getCreatedAt());
            
            User sender = userMapper.selectById(req.getSenderId());
            if (sender != null) {
                map.put("nickname", sender.getNickname());
                map.put("avatar", sender.getAvatarUrl());
            }
            return map;
        }).collect(Collectors.toList());
        
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", resultPage.getTotal());
        data.put("page", page);
        data.put("size", size);
        
        return Result.success(data);
    }

    @Transactional
    @Override
    public Result<Object> handleRequest(Long currentUserId, Long requestId, String action, String remark) {
        FriendRequest request = friendRequestMapper.selectById(requestId);
        if (request == null) {
            return Result.fail("请求不存在");
        }
        
        if (!request.getReceiverId().equals(currentUserId)) {
            return Result.fail("无权处理此请求");
        }
        
        if (!"PENDING".equals(request.getStatus())) {
            return Result.fail("请求已处理");
        }
        
        if ("ACCEPT".equals(action)) {
            request.setStatus("ACCEPTED");

            // 创建双向好友关系。验证信息（request.getRemark()）只是请求附言，不作为任何一方的备注；
            // 备注默认留空（显示对方昵称），用户可后续自行修改。
            createFriendship(request.getSenderId(), request.getReceiverId(), null);
            createFriendship(request.getReceiverId(), request.getSenderId(), null);

            // 通知请求方
            if (notificationService != null) {
                notificationService.notify(request.getSenderId(), "FRIEND_REQUEST_ACCEPTED", "好友请求通过",
                        "对方通过了你的好友申请", request.getId());
            }
        } else if ("REJECT".equals(action)) {
            request.setStatus("REJECTED");
        } else {
            return Result.fail("无效的操作");
        }
        
        request.setUpdatedAt(LocalDateTime.now());
        friendRequestMapper.updateById(request);
        
        return Result.success("操作成功");
    }

    private void createFriendship(Long userId, Long friendId, String remark) {
        // 先检查是否存在（可能是之前删除了）
        QueryWrapper<Friendship> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).eq("friend_id", friendId);
        Friendship existing = friendshipMapper.selectOne(queryWrapper);
        
        if (existing != null) {
            existing.setStatus(1);
            existing.setRemark(remark);
            existing.setCreatedAt(LocalDateTime.now()); // 更新时间
            friendshipMapper.updateById(existing);
        } else {
            Friendship friendship = new Friendship();
            friendship.setUserId(userId);
            friendship.setFriendId(friendId);
            friendship.setRemark(remark);
            friendship.setStatus(1);
            friendship.setCreatedAt(LocalDateTime.now());
            friendshipMapper.insert(friendship);
        }
    }

    @Override
    public Result<Object> getFriendList(Long currentUserId, String keyword, Integer page, Integer size) {
        Page<Friendship> pageParam = new Page<>(page, size);
        QueryWrapper<Friendship> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", currentUserId).eq("status", 1);
        
        IPage<Friendship> resultPage = friendshipMapper.selectPage(pageParam, queryWrapper);
        
        List<Map<String, Object>> list = resultPage.getRecords().stream().map(f -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", f.getId());
            map.put("friendId", f.getFriendId());
            map.put("remark", f.getRemark());
            
            User friend = userMapper.selectById(f.getFriendId());
            if (friend != null) {
                // 如果有搜索关键词，进行过滤（这里是在内存中过滤，因为keyword可能匹配User表的字段）
                // 更好的做法是用联表查询，但MyBatis-Plus默认不支持多表，需要XML。这里先简单处理。
                map.put("nickname", friend.getNickname());
                map.put("avatar", friend.getAvatarUrl());
                // 隐私：对方关闭「展示在线状态」时对他人隐藏
                boolean online = presenceService != null && presenceService.isOnline(f.getFriendId())
                        && Boolean.TRUE.equals(friend.getShowOnlineStatus());
                map.put("online", online);
            }
            return map;
        }).collect(Collectors.toList());
        
        // 如果有关键词，内存过滤 (简单的实现，不推荐生产环境)
        if (StringUtils.hasText(keyword)) {
            list = list.stream().filter(m -> {
                String nickname = (String) m.get("nickname");
                String remark = (String) m.get("remark");
                return (nickname != null && nickname.contains(keyword)) || (remark != null && remark.contains(keyword));
            }).collect(Collectors.toList());
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", list.size()); // 注意：这里的total是过滤后的
        data.put("page", page);
        data.put("size", size);
        
        return Result.success(data);
    }

    @Override
    public Result<Object> updateRemark(Long currentUserId, Long friendId, String remark) {
        QueryWrapper<Friendship> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", currentUserId).eq("friend_id", friendId).eq("status", 1);
        Friendship friendship = friendshipMapper.selectOne(queryWrapper);
        
        if (friendship == null) {
            return Result.fail("好友不存在");
        }
        
        friendship.setRemark(remark);
        friendshipMapper.updateById(friendship);
        
        return Result.success("备注更新成功");
    }

    @Override
    public Result<Object> deleteFriend(Long currentUserId, Long friendId) {
        // 双向删除？通常是单向删除或者双向解除
        // 这里实现双向解除
        
        // 删除自己列表中的好友
        QueryWrapper<Friendship> q1 = new QueryWrapper<>();
        q1.eq("user_id", currentUserId).eq("friend_id", friendId);
        Friendship f1 = friendshipMapper.selectOne(q1);
        if (f1 != null) {
            f1.setStatus(0);
            friendshipMapper.updateById(f1);
        }
        
        // 删除对方列表中的自己
        QueryWrapper<Friendship> q2 = new QueryWrapper<>();
        q2.eq("user_id", friendId).eq("friend_id", currentUserId);
        Friendship f2 = friendshipMapper.selectOne(q2);
        if (f2 != null) {
            f2.setStatus(0);
            friendshipMapper.updateById(f2);
        }
        
        return Result.success("好友删除成功");
    }
}
