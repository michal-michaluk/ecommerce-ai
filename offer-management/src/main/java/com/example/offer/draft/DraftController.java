package com.example.offer.draft;

import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;
import com.example.offer.mediators.DecisionDenied;
import com.example.offer.mediators.OfferLifecycleMediator;
import com.example.offer.offer.Completeness;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@RestController
@RequiredArgsConstructor
class DraftController {

    private final DraftService drafts;
    private final OfferLifecycleMediator mediator;
    private final Clock clock;

    @PostMapping(path = "/products", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('content-manager')")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedProductResponse createProduct(@RequestBody @Valid CreateProductCommand command,
                                         @AuthenticationPrincipal Jwt jwt) {
        Audit audit = audit(jwt);
        String productId = "p-" + UUID.randomUUID();
        mediator.createProduct(productId, "v1", new Title(command.title()), audit);
        return new CreatedProductResponse(productId, command.title(), command.category(), "DRAFT", "v1",
                null, null, null, null, 0, audit.at(), audit.at());
    }

    @GetMapping(path = "/products/{productId}/description-draft", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('content-manager')")
    DescriptionDraftResponse getDraft(@PathVariable String productId) {
        DraftSnapshot draft = drafts.get(productId)
                .orElseThrow(() -> new DecisionDenied(ErrorCode.PRODUCT_NOT_FOUND));
        return DescriptionDraftResponse.of(draft, completeness(draft, productId));
    }

    @PutMapping(path = "/products/{productId}/description-draft",
            consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('content-manager')")
    DescriptionDraftResponse saveDraft(@PathVariable String productId,
                                       @RequestBody @Valid SaveDraftCommand command,
                                       @AuthenticationPrincipal Jwt jwt) {
        UpdateDraft update = UpdateDraft.builder()
                .title(command.title() == null ? null : new Title(command.title()))
                .description(command.description() == null ? null : new Description(command.description()))
                .attributes(command.attributes() == null ? null
                        : new DraftAttributes(command.attributes().category(), command.attributes().manualUrl()))
                .build();
        DraftSnapshot draft = drafts.edit(productId, update, audit(jwt))
                .orElseThrow(() -> new DecisionDenied(ErrorCode.PRODUCT_NOT_FOUND));
        return DescriptionDraftResponse.of(draft, completeness(draft, productId));
    }

    @PostMapping(path = "/products/{productId}/description-draft/review-requests",
            produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('content-manager')")
    @ResponseStatus(HttpStatus.CREATED)
    ReviewRequestResponse requestReview(@PathVariable String productId, @AuthenticationPrincipal Jwt jwt) {
        Audit audit = audit(jwt);
        String reviewRequestId = "rr-" + UUID.randomUUID();
        mediator.requestReview(productId, new ReviewRequest(reviewRequestId, audit.who(), null, audit.at()),
                LocalDate.now(clock), audit);
        DraftSnapshot draft = mediator.draftOrThrow(productId);
        Completeness completeness = completeness(draft, productId);
        return new ReviewRequestResponse(reviewRequestId, productId, draft.version(), "PENDING",
                audit.who().subject(), audit.at(), completeness.missing().size());
    }

    @GetMapping(path = "/product-photo-formats", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('content-manager')")
    PhotoFormatsResponse photoFormats() {
        return PhotoFormatsResponse.standard();
    }

    @PostMapping(path = "/products/{productId}/description-draft/photos",
            consumes = MULTIPART_FORM_DATA_VALUE, produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('content-manager')")
    @ResponseStatus(HttpStatus.CREATED)
    PhotoResponse addPhoto(@PathVariable String productId, @RequestParam("file") MultipartFile file,
                           @AuthenticationPrincipal Jwt jwt) throws IOException {
        Audit audit = audit(jwt);
        Photo photo = photo(file, audit.at());
        DraftSnapshot draft = drafts.attachPhoto(productId, photo, audit)
                .orElseThrow(() -> new DecisionDenied(ErrorCode.PRODUCT_NOT_FOUND));
        return draft.photos().stream()
                .filter(stored -> stored.photoId().equals(photo.photoId()))
                .findFirst()
                .map(PhotoResponse::of)
                .orElseThrow(() -> new DecisionDenied(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @DeleteMapping("/products/{productId}/description-draft/photos/{photoId}")
    @PreAuthorize("hasRole('content-manager')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removePhoto(@PathVariable String productId, @PathVariable String photoId,
                     @AuthenticationPrincipal Jwt jwt) {
        drafts.detachPhoto(productId, photoId, audit(jwt))
                .orElseThrow(() -> new DecisionDenied(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private Completeness completeness(DraftSnapshot draft, String productId) {
        return mediator.completenessOf(draft, productId, LocalDate.now(clock));
    }

    private Audit audit(Jwt jwt) {
        return new Audit(new Identity(jwt.getSubject()), clock.instant());
    }

    private static Photo photo(MultipartFile file, java.time.Instant at) throws IOException {
        byte[] bytes = file.getBytes();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        return new Photo("ph-" + UUID.randomUUID(), file.getOriginalFilename(), file.getContentType(),
                image == null ? 0 : image.getWidth(), image == null ? 0 : image.getHeight(),
                bytes.length, 0, at);
    }
}
