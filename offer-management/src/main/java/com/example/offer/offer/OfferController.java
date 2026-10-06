package com.example.offer.offer;

import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;
import com.example.offer.draft.DraftSnapshot;
import com.example.offer.mediators.DecisionDenied;
import com.example.offer.mediators.OfferLifecycleMediator;
import com.example.offer.tools.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequiredArgsConstructor
class OfferController {

    private final OfferLifecycleMediator mediator;
    private final Clock clock;

    @GetMapping(path = "/products/{productId}/publication", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('content-manager')")
    PublicationView publication(@PathVariable String productId) {
        LocalDate businessDate = LocalDate.now(clock);
        return PublicationView.of(mediator.productOrThrow(productId, businessDate),
                mediator.completenessOf(productId, businessDate), businessDate);
    }

    @PostMapping(path = "/products/{productId}/publications",
            consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('content-manager')")
    @ResponseStatus(HttpStatus.CREATED)
    PublishResponse publish(@PathVariable String productId, @RequestBody @Valid PublishCommand command,
                            @AuthenticationPrincipal Jwt jwt) {
        LocalDate businessDate = LocalDate.now(clock);
        String publicationId = "pub-" + UUID.randomUUID();
        ProductSnapshot snapshot = mediator.publishVersion(productId, publicationId,
                command.descriptionVersion(), command.availableFrom(), businessDate, audit(jwt));
        Publication publication = snapshot.publications().stream()
                .filter(candidate -> candidate.publicationId().equals(publicationId))
                .findFirst()
                .orElseThrow(() -> new DecisionDenied(ErrorCode.VERSION_NOT_FOUND));
        LocalDate visibleFrom = publication.availableFrom() != null
                && publication.availableFrom().isAfter(businessDate)
                ? publication.availableFrom()
                : LocalDate.ofInstant(publication.createdAt(), clock.getZone());
        return new PublishResponse(publicationId, productId, publication.version(),
                publication.stateAt(businessDate).name(), visibleFrom, publication.createdAt());
    }

    @DeleteMapping("/products/{productId}/publications/{publicationId}")
    @PreAuthorize("hasRole('content-manager')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelPublication(@PathVariable String productId, @PathVariable String publicationId,
                           @AuthenticationPrincipal Jwt jwt) {
        mediator.cancelPublication(productId, publicationId, LocalDate.now(clock), audit(jwt));
    }

    @GetMapping(path = "/products/{productId}/versions", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('content-manager')")
    VersionListResponse versions(@PathVariable String productId) {
        LocalDate businessDate = LocalDate.now(clock);
        return VersionListResponse.of(mediator.productOrThrow(productId, businessDate), businessDate);
    }

    @PostMapping(path = "/products/{productId}/versions/{version}/revert", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('content-manager')")
    @ResponseStatus(HttpStatus.CREATED)
    RevertResponse revert(@PathVariable String productId, @PathVariable String version,
                          @AuthenticationPrincipal Jwt jwt) {
        LocalDate businessDate = LocalDate.now(clock);
        String newVersion = "v" + (mediator.productOrThrow(productId, businessDate).versions().size() + 1);
        DraftSnapshot draft = mediator.revert(productId, version, newVersion, businessDate, audit(jwt));
        return new RevertResponse(productId, draft.version(), draft.state().name(), version,
                draft.lastChange().at(), draft.lastChange().who().subject());
    }

    @DeleteMapping("/products/{productId}/offer-presence")
    @PreAuthorize("hasRole('content-manager')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeFromOffer(@PathVariable String productId, @AuthenticationPrincipal Jwt jwt) {
        mediator.removeFromOffer(productId, audit(jwt));
    }

    private Audit audit(Jwt jwt) {
        return new Audit(new Identity(jwt.getSubject()), clock.instant());
    }
}
