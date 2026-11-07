package com.chatatda.api.housing;

import com.chatatda.api.common.CurrentUser;
import com.chatatda.api.housing.HousingDtos.AnnouncementDto;
import com.chatatda.api.housing.HousingDtos.HousingDetail;
import com.chatatda.api.housing.HousingDtos.HousingSummary;
import com.chatatda.api.housing.HousingService.HousingQuery;
import com.chatatda.api.listing.ListingQuery;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/housing")
public class HousingController {

    private final HousingService service;

    public HousingController(HousingService service) {
        this.service = service;
    }

    @GetMapping
    public List<HousingSummary> list(@RequestParam(required = false) Long maxRent,
                                     @RequestParam(required = false) Long maxDeposit,
                                     @RequestParam(required = false) Integer maxWalkMin,
                                     @RequestParam(required = false) Long buildingId,
                                     @RequestParam(required = false) String bbox,
                                     @CurrentUser(required = false) Long userId) {
        return service.list(new HousingQuery(maxRent, maxDeposit, maxWalkMin, buildingId, ListingQuery.Bbox.parse(bbox)),
                userId);
    }

    @GetMapping("/{id}")
    public HousingDetail detail(@PathVariable long id, @RequestParam(required = false) Long buildingId,
                                @CurrentUser(required = false) Long userId) {
        return service.detail(id, buildingId, userId);
    }

    @GetMapping("/announcements")
    public List<AnnouncementDto> announcements(@RequestParam(required = false) AnnouncementStatus status,
                                               @RequestParam(required = false) Long housingId) {
        return service.announcements(status, housingId);
    }

    @PostMapping("/{id}/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@PathVariable long id, @CurrentUser Long userId) {
        service.subscribe(userId, id);
    }

    @DeleteMapping("/{id}/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@PathVariable long id, @CurrentUser Long userId) {
        service.unsubscribe(userId, id);
    }
}
