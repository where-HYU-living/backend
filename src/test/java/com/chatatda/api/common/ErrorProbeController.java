package com.chatatda.api.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/__probe")
class ErrorProbeController {

    enum Level { LOW, HIGH }

    record Body(@NotBlank String name) {
    }

    @GetMapping("/enum")
    String enumParam(@RequestParam Level level) {
        return level.name();
    }

    @GetMapping("/required")
    String required(@RequestParam String q) {
        return q;
    }

    @PostMapping("/body")
    String body(@Valid @RequestBody Body body) {
        return body.name();
    }

    @GetMapping("/boom")
    String boom() {
        throw new IllegalStateException("내부 사정: 비밀 정보가 응답에 새면 안 된다");
    }
}
