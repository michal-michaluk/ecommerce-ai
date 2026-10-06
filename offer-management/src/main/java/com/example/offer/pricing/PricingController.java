package com.example.offer.pricing;

import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;
import com.example.offer.mediators.OfferLifecycleMediator;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequiredArgsConstructor
class PricingController {

    private final PricingService prices;
    private final OfferLifecycleMediator mediator;
    private final Clock clock;

    @GetMapping(path = "/products/{productId}/prices", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('sales')")
    PriceListResponse prices(@PathVariable String productId) {
        LocalDate businessDate = LocalDate.now(clock);
        mediator.productOrThrow(productId, businessDate);
        PriceScheduleSnapshot snapshot = prices.get(productId)
                .orElseGet(() -> new PriceScheduleSnapshot(productId, List.of(), List.of()));
        List<PriceEntryResponse> items = new ArrayList<>();
        snapshot.prices().forEach(price -> items.add(PriceEntryResponse.of(price, businessDate)));
        snapshot.discounts().forEach(discount -> items.add(PriceEntryResponse.of(discount, businessDate)));
        return new PriceListResponse(productId, items);
    }

    @PostMapping(path = "/products/{productId}/prices",
            consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('sales')")
    @ResponseStatus(HttpStatus.CREATED)
    PriceEntryResponse schedule(@PathVariable String productId,
                                @RequestBody @Valid SchedulePriceCommand command,
                                @AuthenticationPrincipal Jwt jwt) {
        LocalDate businessDate = LocalDate.now(clock);
        Audit audit = audit(jwt);
        mediator.productOrThrow(productId, businessDate);
        String entryId = (command.kind() == PriceKind.PRICE ? "pr-" : "disc-") + UUID.randomUUID();
        DateRange validity = new DateRange(command.validFrom(), command.validTo());
        PriceScheduleSnapshot snapshot = command.kind() == PriceKind.PRICE
                ? prices.schedulePrice(productId, entryId, command.amount().toMoney(), validity, audit)
                : prices.scheduleDiscount(productId, entryId, Percent.of(command.percent()), validity, audit);
        return find(snapshot, entryId, businessDate);
    }

    @PutMapping(path = "/products/{productId}/prices/{priceId}",
            consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('sales')")
    PriceEntryResponse change(@PathVariable String productId, @PathVariable String priceId,
                              @RequestBody @Valid ChangePriceCommand command,
                              @AuthenticationPrincipal Jwt jwt) {
        LocalDate businessDate = LocalDate.now(clock);
        Audit audit = audit(jwt);
        mediator.productOrThrow(productId, businessDate);
        PriceScheduleSnapshot current = prices.get(productId)
                .orElseThrow(() -> new EntryNotEditable(priceId));
        DateRange validity = new DateRange(command.validFrom(), command.validTo());
        PriceScheduleSnapshot snapshot;
        if (hasPrice(current, priceId)) {
            snapshot = prices.changePrice(productId, priceId, requiredAmount(command), validity,
                    businessDate, audit);
        } else if (hasDiscount(current, priceId)) {
            snapshot = prices.changeDiscount(productId, priceId, Percent.of(command.percent()), validity,
                    businessDate, audit);
        } else {
            throw new EntryNotEditable(priceId);
        }
        return find(snapshot, priceId, businessDate);
    }

    @DeleteMapping("/products/{productId}/prices/{priceId}")
    @PreAuthorize("hasRole('sales')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String productId, @PathVariable String priceId,
                @AuthenticationPrincipal Jwt jwt) {
        LocalDate businessDate = LocalDate.now(clock);
        Audit audit = audit(jwt);
        mediator.productOrThrow(productId, businessDate);
        PriceScheduleSnapshot current = prices.get(productId)
                .orElseThrow(() -> new EntryNotEditable(priceId));
        if (hasPrice(current, priceId)) {
            prices.deletePrice(productId, priceId, businessDate, audit);
        } else if (hasDiscount(current, priceId)) {
            prices.deleteDiscount(productId, priceId, businessDate, audit);
        } else {
            throw new EntryNotEditable(priceId);
        }
    }

    private static PriceEntryResponse find(PriceScheduleSnapshot snapshot, String entryId,
                                           LocalDate businessDate) {
        return snapshot.prices().stream()
                .filter(price -> price.priceId().equals(entryId)).findFirst()
                .map(price -> PriceEntryResponse.of(price, businessDate))
                .or(() -> snapshot.discounts().stream()
                        .filter(discount -> discount.discountId().equals(entryId)).findFirst()
                        .map(discount -> PriceEntryResponse.of(discount, businessDate)))
                .orElseThrow(() -> new EntryNotEditable(entryId));
    }

    private static Money requiredAmount(ChangePriceCommand command) {
        if (command.amount() == null) {
            throw new IllegalArgumentException("amount is required for a PRICE");
        }
        return command.amount().toMoney();
    }

    private static boolean hasPrice(PriceScheduleSnapshot snapshot, String entryId) {
        return snapshot.prices().stream().anyMatch(price -> price.priceId().equals(entryId));
    }

    private static boolean hasDiscount(PriceScheduleSnapshot snapshot, String entryId) {
        return snapshot.discounts().stream().anyMatch(discount -> discount.discountId().equals(entryId));
    }

    private Audit audit(Jwt jwt) {
        return new Audit(new Identity(jwt.getSubject()), clock.instant());
    }
}
