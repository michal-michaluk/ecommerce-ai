package com.example.offer.catalog;

import com.example.offer.offer.OfferState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-side controller of the catalog (element 02 screen 01). It queries the projection, never the
 * write model; the read methods stay in the projection so the controller is a thin transport.
 */
@RestController
@RequiredArgsConstructor
class ProductReadsController {

    private static final String CONTENT_MANAGER = "hasAuthority('content-manager')";

    private final ProductReadsProjection reads;

    @GetMapping("/products")
    @PreAuthorize(CONTENT_MANAGER)
    PageResponse<ProductRead> list(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   @RequestParam(required = false) OfferState state,
                                   @RequestParam(required = false) String query) {
        Page<ProductRead> result = reads.list(state, query, PageRequest.of(page, size));
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/products/{productId}")
    @PreAuthorize(CONTENT_MANAGER)
    ProductRead detail(@PathVariable String productId) {
        return reads.find(productId).orElseThrow(() -> new ProductReadNotFound(productId));
    }
}
