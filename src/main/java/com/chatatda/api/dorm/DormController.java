package com.chatatda.api.dorm;

import com.chatatda.api.common.CurrentUser;
import com.chatatda.api.dorm.DormService.DormDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dorms")
public class DormController {

    private final DormService service;

    public DormController(DormService service) {
        this.service = service;
    }

    @GetMapping
    public List<DormDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public DormDto detail(@PathVariable long id, @RequestParam(required = false) Long buildingId,
                          @CurrentUser(required = false) Long userId) {
        return service.detail(id, buildingId, userId);
    }
}
