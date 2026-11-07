package com.chatatda.api.transfer;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.CurrentUser;
import com.chatatda.api.common.ErrorCode;
import com.chatatda.api.common.PageResponse;
import com.chatatda.api.listing.ListingQuery;
import com.chatatda.api.transfer.TransferDtos.StatusRequest;
import com.chatatda.api.transfer.TransferDtos.TransferDetail;
import com.chatatda.api.transfer.TransferDtos.TransferRequest;
import com.chatatda.api.transfer.TransferDtos.TransferSummary;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TransferSummary> list(@RequestParam(required = false) String bbox,
                                              @RequestParam(defaultValue = "OPEN") TransferStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @CurrentUser(required = false) Long userId) {
        if (page < 0 || size < 1) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않아요.",
                    Map.of(page < 0 ? "page" : "size", page < 0 ? "0 이상이어야 해요." : "1 이상이어야 해요."));
        }
        return service.list(ListingQuery.Bbox.parse(bbox), status, page, Math.min(size, MAX_PAGE_SIZE), userId);
    }

    @GetMapping("/{id}")
    public TransferDetail detail(@PathVariable long id, @CurrentUser(required = false) Long userId) {
        return service.detail(id, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferDetail create(@CurrentUser Long userId, @Valid @RequestBody TransferRequest request) {
        return service.create(userId, request);
    }

    @PutMapping("/{id}")
    public TransferDetail update(@CurrentUser Long userId, @PathVariable long id, @Valid @RequestBody TransferRequest request) {
        return service.update(userId, id, request);
    }

    @PatchMapping("/{id}/status")
    public TransferDetail changeStatus(@CurrentUser Long userId, @PathVariable long id,
                                       @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(userId, id, request.status());
    }
}
