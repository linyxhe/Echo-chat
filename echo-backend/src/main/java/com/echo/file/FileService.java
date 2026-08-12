package com.echo.file;

import com.echo.file.dto.ChatUploadIntentRequest;
import com.echo.pojo.FileAsset;
import com.echo.vo.Result;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface FileService {
    Result<Object> createChatUploadIntent(Long ownerId, ChatUploadIntentRequest request);
    /** 小文件 multipart 一次性上传：同步建 READY file_asset，返回 /files/{id}/content?access=... 的受控 URL。 */
    Result<Object> uploadSmallFile(Long ownerId, MultipartFile file, Long receiverId);
    /** 群小文件 multipart 一次性上传：purpose=GROUP，receiverId=groupId，校验群成员。 */
    Result<Object> uploadSmallFileToGroup(Long ownerId, MultipartFile file, Long groupId);
    Result<Object> completeUpload(Long ownerId, String fileId);
    Result<Object> getUploadStatus(Long ownerId, String fileId);
    Result<Object> cancelUpload(Long ownerId, String fileId);
    Map<String, Object> handleTusHook(Map<String, Object> hookRequest);
    FileAsset findReadyChatFile(String url, Long ownerId, Long receiverId);
    /** 群文件就绪校验：purpose=GROUP 且 receiverId=groupId，且 ownerId 为上传者。 */
    FileAsset findReadyGroupFile(String url, Long ownerId, Long groupId);
}
