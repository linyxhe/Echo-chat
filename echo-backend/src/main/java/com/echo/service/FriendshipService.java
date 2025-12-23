package com.echo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.echo.pojo.Friendship;
import com.echo.vo.Result;

/**
 * 好友服务接口
 */
public interface FriendshipService extends IService<Friendship> {
    
    /**
     * 搜索用户
     */
    Result<Object> searchUsers(String keyword, Integer page, Integer size);
    
    /**
     * 发送好友请求
     */
    Result<Object> sendRequest(Long currentUserId, Long targetUserId, String remark);
    
    /**
     * 获取好友请求列表
     */
    Result<Object> getRequests(Long currentUserId, String status, Integer page, Integer size);
    
    /**
     * 处理好友请求
     */
    Result<Object> handleRequest(Long currentUserId, Long requestId, String action, String remark);
    
    /**
     * 获取好友列表
     */
    Result<Object> getFriendList(Long currentUserId, String keyword, Integer page, Integer size);
    
    /**
     * 更新好友备注
     */
    Result<Object> updateRemark(Long currentUserId, Long friendId, String remark);
    
    /**
     * 删除好友
     */
    Result<Object> deleteFriend(Long currentUserId, Long friendId);
}
