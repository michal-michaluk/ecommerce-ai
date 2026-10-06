package com.example.offer.offer;

import com.example.offer.ControllerTestSupport;
import com.example.offer.IntegrationTest;
import com.example.offer.TestSecurityConfiguration;
import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;
import com.example.offer.draft.Description;
import com.example.offer.draft.Photo;
import com.example.offer.draft.ReviewRequest;
import com.example.offer.draft.Title;
import com.example.offer.draft.UpdateDraft;
import com.example.offer.mediators.OfferLifecycleMediator;
import com.example.offer.pricing.DateRange;
import com.example.offer.pricing.Money;
import com.example.offer.pricing.PricingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@IntegrationTest
@Import(TestSecurityConfiguration.class)
class OfferControllerTest extends ControllerTestSupport {

    private static final String AUTH = HttpHeaders.AUTHORIZATION;
    private static final Identity AUTHOR = new Identity("a.kowalska");
    private static final Identity APPROVER = new Identity("m.nowak");

    @Autowired
    OfferLifecycleMediator mediator;
    @Autowired
    PricingService prices;
    @Autowired
    Clock clock;
    @Autowired
    ObjectMapper json;

    @Test
    void publishImmediatelyAndReadThePublication() throws Exception {
        String productId = approvedProduct(true);

        client.post().uri("/products/{id}/publications", productId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .contentType(APPLICATION_JSON)
                .bodyValue(Map.of("descriptionVersion", "v1", "availableFrom", LocalDate.now(clock).toString()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.descriptionVersion").isEqualTo("v1")
                .jsonPath("$.state").isEqualTo("PUBLISHED")
                .jsonPath("$.publicationId").isNotEmpty();

        client.get().uri("/products/{id}/publication", productId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.gate.passed").isEqualTo(true)
                .jsonPath("$.publishedVersion").isEqualTo("v1")
                .jsonPath("$.versions[0].version").isEqualTo("v1")
                .jsonPath("$.versions[0].state").isEqualTo("PUBLISHED");

        client.get().uri("/products/{id}/versions", productId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].version").isEqualTo("v1");
    }

    @Test
    void publishScheduledThenCancelIt() throws Exception {
        String productId = approvedProduct(true);
        String availableFrom = LocalDate.now(clock).plusDays(10).toString();

        byte[] body = client.post().uri("/products/{id}/publications", productId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .contentType(APPLICATION_JSON)
                .bodyValue(Map.of("descriptionVersion", "v1", "availableFrom", availableFrom))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.state").isEqualTo("SCHEDULED")
                .jsonPath("$.visibleFrom").isEqualTo(availableFrom)
                .returnResult().getResponseBody();
        String publicationId = json.readTree(body).get("publicationId").asString();

        client.delete().uri("/products/{id}/publications/{publicationId}", productId, publicationId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void publishWithoutAPriceIsBlocked() throws Exception {
        String productId = approvedProduct(false);

        client.post().uri("/products/{id}/publications", productId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .contentType(APPLICATION_JSON)
                .bodyValue(Map.of("descriptionVersion", "v1", "availableFrom", LocalDate.now(clock).toString()))
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PUBLICATION_BLOCKED")
                .jsonPath("$.details.blocking[0].code").isEqualTo("PRICE_REQUIRED");
    }

    @Test
    void publishAnUnapprovedVersionIsAConflict() {
        String productId = editingProduct();

        client.post().uri("/products/{id}/publications", productId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .contentType(APPLICATION_JSON)
                .bodyValue(Map.of("descriptionVersion", "v1", "availableFrom", LocalDate.now(clock).toString()))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("VERSION_NOT_APPROVED");
    }

    @Test
    void revertToAnUnknownVersionIsNotFound() throws Exception {
        String productId = approvedProduct(true);
        publishNow(productId);

        client.post().uri("/products/{id}/versions/{version}/revert", productId, "v99")
                .header(AUTH, bearer(CONTENT_MANAGER))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VERSION_NOT_FOUND");
    }

    @Test
    void revertOpensANewDraft() throws Exception {
        String productId = approvedProduct(true);
        publishNow(productId);

        client.post().uri("/products/{id}/versions/{version}/revert", productId, "v1")
                .header(AUTH, bearer(CONTENT_MANAGER))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.version").isEqualTo("v2")
                .jsonPath("$.state").isEqualTo("EDITING")
                .jsonPath("$.basedOnVersion").isEqualTo("v1")
                .jsonPath("$.updatedBy").isEqualTo("a.kowalska");
    }

    @Test
    void removalIsIdempotent() {
        String productId = editingProduct();

        client.delete().uri("/products/{id}/offer-presence", productId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .exchange()
                .expectStatus().isNoContent();
        client.delete().uri("/products/{id}/offer-presence", productId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void unknownProductPublicationIsNotFound() {
        client.get().uri("/products/p-does-not-exist/publication")
                .header(AUTH, bearer(CONTENT_MANAGER))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void missingTokenIsUnauthenticated() {
        client.get().uri("/products/{id}/versions", editingProduct())
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void wrongRoleIsForbidden() {
        client.get().uri("/products/{id}/versions", editingProduct())
                .header(AUTH, bearer(SALES))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    private String editingProduct() {
        String productId = product();
        Audit audit = new Audit(AUTHOR, Instant.now());
        mediator.createProduct(productId, "v1", new Title("Kosiarka"), audit);
        mediator.editDraft(productId, UpdateDraft.builder()
                .description(new Description("Opis")).build(), audit);
        return productId;
    }

    private String approvedProduct(boolean withPrice) {
        String productId = product();
        LocalDate today = LocalDate.now(clock);
        Audit authorAudit = new Audit(AUTHOR, Instant.now());
        mediator.createProduct(productId, "v1", new Title("Kosiarka"), authorAudit);
        mediator.editDraft(productId, UpdateDraft.builder()
                .description(new Description("Opis")).build(), authorAudit);
        mediator.attachPhoto(productId, new Photo(UUID.randomUUID().toString(), "p.jpg",
                "image/jpeg", 1200, 1200, 1000, 0, authorAudit.at()), authorAudit);
        if (withPrice) {
            prices.schedulePrice(productId, "pr-" + productId, Money.of("259.00", "PLN"),
                    DateRange.from(today), authorAudit);
        }
        String reviewRequestId = "rr-" + productId;
        mediator.requestReview(productId, new ReviewRequest(reviewRequestId, AUTHOR, null, authorAudit.at()),
                today, authorAudit);
        Audit approverAudit = new Audit(APPROVER, Instant.now());
        mediator.approve(productId, new ReviewRequest(reviewRequestId, AUTHOR, APPROVER, approverAudit.at()),
                approverAudit);
        return productId;
    }

    private void publishNow(String productId) {
        client.post().uri("/products/{id}/publications", productId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .contentType(APPLICATION_JSON)
                .bodyValue(Map.of("descriptionVersion", "v1", "availableFrom", LocalDate.now(clock).toString()))
                .exchange()
                .expectStatus().isCreated();
    }

    private static String product() {
        return "p-" + UUID.randomUUID();
    }
}
