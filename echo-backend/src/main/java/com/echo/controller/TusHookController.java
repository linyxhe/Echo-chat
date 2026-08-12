package com.echo.controller;

import com.echo.file.FileService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** tusd 的本机 HTTP 生命周期钩子。鉴权依据一次性 uploadToken，而非公开下载目录。 */
@RestController
@RequestMapping("/internal/tusd")
public class TusHookController {

    private final FileService fileService;

    public TusHookController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/hooks", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handleHook(@RequestBody Map<String, Object> hookRequest) {
        return fileService.handleTusHook(hookRequest);
    }
}
