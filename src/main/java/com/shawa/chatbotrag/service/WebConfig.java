package com.shawa.chatbotrag.service;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Mapping URL /files/** ke folder uploads/
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:uploads/");
    }
}

