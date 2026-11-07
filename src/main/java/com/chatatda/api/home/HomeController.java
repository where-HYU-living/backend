package com.chatatda.api.home;

import com.chatatda.api.common.CurrentUser;
import com.chatatda.api.home.HomeService.Alert;
import com.chatatda.api.home.HomeService.HomeDto;
import com.chatatda.api.home.HomeService.Payment;
import com.chatatda.api.home.HomeService.Report;
import com.chatatda.api.home.HomeService.Utility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** H1~H6 내 집 기록 (계약 5-9). 모두 로그인 필요(/me/**). */
@RestController
@RequestMapping("/api/v1/me")
public class HomeController {

    public record AlertRequest(@NotNull @Min(1) @Max(365) Integer daysBefore, Boolean enabled) {
    }

    public record AlertPatch(@NotNull Boolean enabled) {
    }

    private final HomeService service;

    public HomeController(HomeService service) {
        this.service = service;
    }

    @GetMapping("/home")
    public HomeDto home(@CurrentUser Long userId) {
        return service.home(userId);
    }

    @PutMapping("/home")
    public HomeDto saveHome(@CurrentUser Long userId, @Valid @RequestBody HomeDto body) {
        return service.saveHome(userId, body);
    }

    @GetMapping("/payments")
    public List<Payment> payments(@CurrentUser Long userId) {
        return service.payments(userId);
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public Payment addPayment(@CurrentUser Long userId, @Valid @RequestBody Payment body) {
        return service.savePayment(userId, body);
    }

    @GetMapping("/utilities")
    public List<Utility> utilities(@CurrentUser Long userId) {
        return service.utilities(userId);
    }

    @PostMapping("/utilities")
    @ResponseStatus(HttpStatus.CREATED)
    public Utility addUtility(@CurrentUser Long userId, @Valid @RequestBody Utility body) {
        return service.saveUtility(userId, body);
    }

    @GetMapping("/home/alerts")
    public List<Alert> alerts(@CurrentUser Long userId) {
        return service.alerts(userId);
    }

    @PostMapping("/home/alerts")
    @ResponseStatus(HttpStatus.CREATED)
    public Alert addAlert(@CurrentUser Long userId, @Valid @RequestBody AlertRequest body) {
        return service.addAlert(userId, body.daysBefore(), body.enabled() == null || body.enabled());
    }

    @PatchMapping("/home/alerts/{id}")
    public Alert patchAlert(@CurrentUser Long userId, @PathVariable long id, @Valid @RequestBody AlertPatch body) {
        return service.setAlert(userId, id, body.enabled());
    }

    @DeleteMapping("/home/alerts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAlert(@CurrentUser Long userId, @PathVariable long id) {
        service.deleteAlert(userId, id);
    }

    @GetMapping("/report")
    public Report report(@CurrentUser Long userId, @RequestParam String month) {
        return service.report(userId, month);
    }
}
