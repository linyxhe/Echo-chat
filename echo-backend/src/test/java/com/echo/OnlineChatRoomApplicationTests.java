package com.echo;

import com.echo.file.FileService;
import com.echo.mapper.FileAssetMapper;
import com.echo.pojo.FileAsset;
import com.echo.service.ChatService;
import com.echo.vo.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OnlineChatRoomApplicationTests {

    @Autowired
    private ChatService chatService;

    @Autowired
    private FileService fileService;

    @MockitoBean
    private FileAssetMapper fileAssetMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void uploadSmallFileCreatesControlledAsset() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "a.jpg",
                "image/jpeg",
                "abc".getBytes(StandardCharsets.UTF_8)
        );

        // receiverId = 0：走通用（头像/动态图）分支，不校验好友关系。FileAssetMapper 用 mock，避免测试连真实库。
        Result<Object> res = fileService.uploadSmallFile(1L, file, 0L);
        assertEquals(200, res.getCode());
        assertNotNull(res.getData());

        Map<String, Object> data = (Map<String, Object>) res.getData();
        String fileUrl = String.valueOf(data.get("fileUrl"));
        assertTrue(fileUrl.startsWith("/files/"), "受控小文件应返回 /files/ URL，实际: " + fileUrl);

        String fileId = String.valueOf(data.get("fileId"));
        Path savedPath = Paths.get(System.getProperty("user.dir"), "upload", "files", fileId).toAbsolutePath().normalize();
        try {
            assertTrue(Files.exists(savedPath), "受控文件应写入 upload/files/" + fileId);
            assertEquals(3L, Files.size(savedPath));
        } finally {
            Files.deleteIfExists(savedPath);
        }

        verify(fileAssetMapper).insert(any(FileAsset.class));
        verify(fileAssetMapper).updateById(any(FileAsset.class));
    }
}
