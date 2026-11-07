package com.chatatda.api.transfer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class LocalDiskStorage implements StoragePort {

    static final String PATH = "/files/";

    private final Path dir;
    private final String publicBaseUrl;

    public LocalDiskStorage(@Value("${app.upload.dir}") String dir,
                            @Value("${app.upload.public-base-url:}") String publicBaseUrl) {
        this.dir = Path.of(dir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
    }

    public Path dir() {
        return dir;
    }

    @Override
    public String save(byte[] bytes, String extension) {
        String name = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 파일을 저장하지 못했어요.", e);
        }
        return base() + PATH + name;
    }

    @Override
    public boolean owns(String url) {
        return url != null && url.startsWith(base() + PATH) && !url.substring((base() + PATH).length()).contains("/");
    }

    private String base() {
        if (!publicBaseUrl.isEmpty()) {
            return publicBaseUrl;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
