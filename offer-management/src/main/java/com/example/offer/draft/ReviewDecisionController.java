package com.example.offer.draft;

import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;
import com.example.offer.mediators.OfferLifecycleMediator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * The reviewer's decision on a pending review request (element 02 screen 06). The URL is keyed by
 * the review, so the mediator resolves its product; separation of duties (D5) stays in the
 * mediator, never here.
 */
@RestController
@RequiredArgsConstructor
class ReviewDecisionController {

    private final OfferLifecycleMediator mediator;
    private final Clock clock;

    @PostMapping(path = "/review-requests/{reviewRequestId}/approval", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('content-manager')")
    ReviewDecisionResponse approve(@PathVariable String reviewRequestId, @AuthenticationPrincipal Jwt jwt) {
        return ReviewDecisionResponse.approved(mediator.approveReview(reviewRequestId, audit(jwt)));
    }

    @PostMapping(path = "/review-requests/{reviewRequestId}/rejection",
            consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('content-manager')")
    ReviewDecisionResponse reject(@PathVariable String reviewRequestId,
                                  @RequestBody @Valid RejectReviewCommand command,
                                  @AuthenticationPrincipal Jwt jwt) {
        return ReviewDecisionResponse.rejected(mediator.rejectReview(reviewRequestId, command.reason(), audit(jwt)),
                command.reason());
    }

    private Audit audit(Jwt jwt) {
        return new Audit(new Identity(jwt.getSubject()), clock.instant());
    }
}
