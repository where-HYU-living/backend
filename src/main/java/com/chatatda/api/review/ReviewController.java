package com.chatatda.api.review;

import com.chatatda.api.common.CurrentUser;
import com.chatatda.api.review.ReviewDtos.ReviewItem;
import com.chatatda.api.review.ReviewDtos.ReviewList;
import com.chatatda.api.review.ReviewDtos.ReviewRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @GetMapping
    public ReviewList list(@RequestParam ReviewTargetType targetType, @RequestParam long targetId) {
        return service.list(targetType, targetId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewItem create(@CurrentUser Long userId, @Valid @RequestBody ReviewRequest request) {
        return service.create(userId, request);
    }
}
