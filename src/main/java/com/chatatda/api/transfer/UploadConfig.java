package com.chatatda.api.transfer;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UploadConfig implements WebMvcConfigurer {

    private final LocalDiskStorage storage;

    public UploadConfig(LocalDiskStorage storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(LocalDiskStorage.PATH + "**")
                .addResourceLocations(storage.dir().toUri().toString());
    }
}
