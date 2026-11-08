package com.chatatda.api.floorplan;

import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/floorplans")
public class FloorPlanController {

    private final FloorPlanService service;
    private final FloorPlanAiClient ai;

    public FloorPlanController(FloorPlanService service, FloorPlanAiClient ai) {
        this.service = service;
        this.ai = ai;
    }

    public record EstimateRequest(long id, String name, double areaM2, Integer rooms) {
    }

    /** 추정 평면도. AI 서버가 꺼져 있으면 fixtures 평면도를 돌려준다 (저장하지 않음). 로그인 필요 */
    @PostMapping(value = "/estimate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> estimate(@RequestBody EstimateRequest req) {
        if (!ai.enabled()) {
            return ResponseEntity.ok(service.json(req.id()));
        }
        return ResponseEntity.ok(ai.estimateFromMeta(req.id(), req.name(), req.areaM2(), req.rooms()).toString());
    }

    /** 실내 3D 용 FloorPlan. 평면도는 배포 때만 바뀌어서 한 시간 캐시한다 */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> detail(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(service.json(id));
    }
}
