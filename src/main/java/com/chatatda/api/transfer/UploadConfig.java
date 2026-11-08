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
                .addResourceLocations(storage.dir().toUri().toString())
                // 파일 이름이 UUID 라 내용이 바뀌지 않는다 → 브라우저·CDN 이 1년 동안 다시 받지 않게
                .setCacheControl(org.springframework.http.CacheControl.maxAge(365, java.util.concurrent.TimeUnit.DAYS).cachePublic().immutable());
    }
}
