package com.echo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.echo.pojo.Message;
import com.echo.vo.Result;
import org.springframework.web.multipart.MultipartFile;

/**
 * 聊天服务接口
 */
public interface ChatService extends IService<Message> {
    
    /**
     * 获取聊天记录
     */
    Result<Object> getMessages(Long currentUserId, Long friendId, String beforeTime, Integer limit);
    
    /**
     * 获取会话列表
     */
    Result<Object> getConversations(Long currentUserId, Integer page, Integer size);
    
    /**
     * 上传文件（receiverId 与 groupId 二选一）
     */
    Result<Object> uploadFile(MultipartFile file, Long receiverId, Long groupId);
    
    /**
     * 删除聊天记录
     */
    Result<Object> deleteMessages(Long currentUserId, Long friendId, String deleteType, String beforeTime);

    /** 隐藏/恢复当前用户视角的会话，不修改消息数据。 */
    Result<Object> setConversationArchived(Long currentUserId, Long friendId, boolean archived);

    /** 设置当前用户视角的会话置顶状态。 */
    Result<Object> setConversationPinned(Long currentUserId, Long friendId, boolean pinned);
}
