package com.chatatda.api.floorplan;

import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/floorplans")
public class FloorPlanController {

    private final FloorPlanService service;

    public FloorPlanController(FloorPlanService service) {
        this.service = service;
    }

    /** 실내 3D 용 FloorPlan. 평면도는 배포 때만 바뀌어서 한 시간 캐시한다 */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> detail(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(service.json(id));
    }
}
