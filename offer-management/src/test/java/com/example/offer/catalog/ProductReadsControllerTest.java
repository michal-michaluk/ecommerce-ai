package com.example.offer.catalog;

import com.example.offer.IntegrationTest;
import com.example.offer.draft.DraftState;
import com.example.offer.offer.OfferPresence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;

import static com.example.offer.catalog.CatalogFixture.draft;
import static com.example.offer.catalog.CatalogFixture.id;
import static com.example.offer.catalog.CatalogFixture.pendingReview;
import static com.example.offer.catalog.CatalogFixture.product;
import static com.example.offer.catalog.CatalogFixture.publication;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@WithMockUser(roles = "content-manager")
class ProductReadsControllerTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    ApplicationEventPublisher publisher;

    MockMvc mvc;

    @BeforeEach
    void mockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void listIsPaged() throws Exception {
        seedPublished(id());

        mvc.perform(get("/products").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalPages", greaterThanOrEqualTo(1)));
    }

    @Test
    void detailCarriesStateCategoryAndUpdatedBy() throws Exception {
        String productId = seedPublished(id());

        mvc.perform(get("/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.title").value("Kosiarka ręczna 340"))
                .andExpect(jsonPath("$.state").value("PUBLISHED"))
                .andExpect(jsonPath("$.category").value(CatalogFixture.CATEGORY))
                .andExpect(jsonPath("$.descriptionVersion").value("v1"))
                .andExpect(jsonPath("$.publishedVersion").value("v1"))
                .andExpect(jsonPath("$.availableFrom").value(LocalDate.now().minusDays(1).toString()))
                .andExpect(jsonPath("$.activePrice.value").value("259.00"))
                .andExpect(jsonPath("$.activePrice.currency").value("PLN"))
                .andExpect(jsonPath("$.activeDiscountPercent").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.photoCount").value(1))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedBy").value("a.kowalska"));
    }

    @Test
    void stateFilterReturnsOnlyThatState() throws Exception {
        String productId = seedPublished(id());

        mvc.perform(get("/products").param("state", "PUBLISHED").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.state != 'PUBLISHED')]", hasSize(0)))
                .andExpect(jsonPath("$.items[?(@.productId == '" + productId + "')]", hasSize(1)));
    }

    @Test
    void queryFilterMatchesTheProductId() throws Exception {
        String productId = seedPublished(id());
        String fragment = productId.substring(0, 12);

        mvc.perform(get("/products").param("query", fragment).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.productId == '" + productId + "')]", hasSize(1)));
    }

    @Test
    void queryFilterMatchesTheTitle() throws Exception {
        String productId = seedPublished(id());

        mvc.perform(get("/products").param("query", "kosiarka").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.productId == '" + productId + "')]", hasSize(1)));
    }

    @Test
    void unknownProductReturnsTheContractuallyShaped404() throws Exception {
        mvc.perform(get("/products/{productId}", "p-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Product p-does-not-exist does not exist."));
    }

    @Test
    void reviewQueueIsPagedAndFiltered() throws Exception {
        String productId = id();
        publishDraft(draft(productId, DraftState.IN_REVIEW, pendingReview("rr-" + productId)));

        mvc.perform(get("/review-requests").param("status", "PENDING").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.status != 'PENDING')]", hasSize(0)))
                .andExpect(jsonPath("$.items[?(@.reviewRequestId == 'rr-" + productId + "')]", hasSize(1)))
                .andExpect(jsonPath("$.items[?(@.reviewRequestId == 'rr-" + productId + "')].submittedAt")
                        .value(hasItem(CatalogFixture.AT.toString())));
    }

    @Test
    void unknownReviewRequestReturnsTheContractuallyShaped404() throws Exception {
        mvc.perform(get("/review-requests/{reviewRequestId}", "rr-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Review request rr-missing does not exist."));
    }

    @Test
    @WithMockUser(roles = "sales")
    void wrongRoleIsForbiddenWithContractBody() throws Exception {
        mvc.perform(get("/products"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @WithAnonymousUser
    void missingTokenIsUnauthenticatedWithContractBody() throws Exception {
        mvc.perform(get("/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private String seedPublished(String productId) {
        publishDraft(draft(productId, DraftState.EDITING, null));
        publisher.publishEvent(CatalogFixture.activePrice(productId));
        publisher.publishEvent(product(productId, OfferPresence.PRESENT,
                List.of(publication(productId, "v1", LocalDate.now().minusDays(1)))));
        return productId;
    }

    private void publishDraft(com.example.offer.draft.DraftSnapshot snapshot) {
        publisher.publishEvent(snapshot);
    }
}
