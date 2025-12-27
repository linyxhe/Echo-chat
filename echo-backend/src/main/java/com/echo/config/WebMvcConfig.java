package com.echo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload-dir:upload}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = resolveUploadPath();
        String location = uploadPath.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/upload/**")
                .addResourceLocations(location);
    }

    private Path resolveUploadPath() {
        Path path = Paths.get(uploadDir);
        if (path.isAbsolute()) {
            return path.toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.dir")).resolve(path).toAbsolutePath().normalize();
    }
}
