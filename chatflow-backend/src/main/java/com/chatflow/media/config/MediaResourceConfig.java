package com.chatflow.media.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MediaResourceConfig implements WebMvcConfigurer {

    @Value("${app.media.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadDir.startsWith("/")
                ? "file:" + uploadDir + "/"
                : "file:./" + uploadDir.replaceFirst("^\\./", "") + "/";

        registry.addResourceHandler("/media/**")
                .addResourceLocations(location);
    }
}