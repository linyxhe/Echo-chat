package com.echo;

import com.echo.service.ChatService;
import com.echo.vo.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OnlineChatRoomApplicationTests {

    @Autowired
    private ChatService chatService;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() {
    }

    @Test
    void uploadFileSavesToUploadDir() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "a.jpg",
                "image/jpeg",
                "abc".getBytes(StandardCharsets.UTF_8)
        );

        Result<Object> res = chatService.uploadFile(file, 1L);
        assertEquals(200, res.getCode());
        assertNotNull(res.getData());

        Map<String, Object> data = (Map<String, Object>) res.getData();
        String fileUrl = String.valueOf(data.get("fileUrl"));
        assertTrue(fileUrl.startsWith("/upload/"));

        String savedFileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
        Path savedPath = Paths.get(System.getProperty("user.dir"), "upload", savedFileName).toAbsolutePath().normalize();
        assertTrue(Files.exists(savedPath));
        Files.deleteIfExists(savedPath);
    }

    @Test
    void uploadDirFileIsPubliclyAccessible() throws Exception {
        Path uploadDir = Paths.get(System.getProperty("user.dir"), "upload").toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        String fileName = "static-test-" + System.currentTimeMillis() + ".txt";
        Path filePath = uploadDir.resolve(fileName);
        Files.writeString(filePath, "ok", StandardCharsets.UTF_8);

        try {
            ResponseEntity<String> resp = restTemplate.getForEntity("http://localhost:" + port + "/upload/" + fileName, String.class);
            assertEquals(200, resp.getStatusCode().value());
            assertEquals("ok", resp.getBody());
        } finally {
            Files.deleteIfExists(filePath);
        }
    }
}
