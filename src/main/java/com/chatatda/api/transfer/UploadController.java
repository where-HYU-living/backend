package com.chatatda.api.transfer;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.CurrentUser;
import com.chatatda.api.common.ErrorCode;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    public record UploadResponse(String url) {
    }

    private final StoragePort storage;

    public UploadController(StoragePort storage) {
        this.storage = storage;
    }

    @PostMapping
    public UploadResponse upload(@CurrentUser Long userId, @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw invalid("빈 파일이에요.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw invalid("5MB 이하만 올릴 수 있어요.");
        }
        byte[] bytes = file.getBytes();
        String extension = startsWith(bytes, PNG) ? "png" : startsWith(bytes, JPG) ? "jpg" : null;
        if (extension == null) {
            throw invalid("jpg·png 이미지만 올릴 수 있어요.");
        }
        return new UploadResponse(storage.save(bytes, extension));
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static ApiException invalid(String reason) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "올릴 수 없는 파일이에요.", Map.of("file", reason));
    }
}
