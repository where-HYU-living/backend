package com.chatatda.api.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    public record DepartmentView(long id, String name, String college, List<Long> defaultBuildingIds) {
    }

    public record BuildingView(long id, String name, BuildingType type, double lat, double lng) {
    }

    private final DepartmentRepository departments;
    private final BuildingRepository buildings;

    public CatalogController(DepartmentRepository departments, BuildingRepository buildings) {
        this.departments = departments;
        this.buildings = buildings;
    }

    @GetMapping("/departments")
    public List<DepartmentView> departments() {
        return departments.findAllByOrderByIdAsc().stream()
                .map(d -> new DepartmentView(d.getId(), d.getName(), d.getCollege(), List.copyOf(d.getDefaultBuildingIds())))
                .toList();
    }

    @GetMapping("/buildings")
    public List<BuildingView> buildings(@RequestParam(required = false) BuildingType type) {
        List<Building> found = type == null ? buildings.findAllByOrderByIdAsc() : buildings.findByTypeOrderByIdAsc(type);
        return found.stream()
                .map(b -> new BuildingView(b.getId(), b.getName(), b.getType(), b.getLat(), b.getLng()))
                .toList();
    }
}
