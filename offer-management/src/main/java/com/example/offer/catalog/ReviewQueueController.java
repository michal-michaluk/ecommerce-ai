package com.example.offer.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-side controller of the review queue (element 02 screens 05/06). */
@RestController
@RequiredArgsConstructor
class ReviewQueueController {

    private static final String CONTENT_MANAGER = "hasAuthority('content-manager')";

    private final ReviewQueueProjection reviews;

    @GetMapping("/review-requests")
    @PreAuthorize(CONTENT_MANAGER)
    PageResponse<ReviewRequestRead> list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(required = false) String status) {
        Page<ReviewRequestRead> result = reviews.list(status, PageRequest.of(page, size));
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/review-requests/{reviewRequestId}")
    @PreAuthorize(CONTENT_MANAGER)
    ReviewRequestRead detail(@PathVariable String reviewRequestId) {
        return reviews.find(reviewRequestId).orElseThrow(() -> new ReviewRequestNotFound(reviewRequestId));
    }
}
