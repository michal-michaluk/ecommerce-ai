package com.example.offer.catalog;

import com.example.offer.IntegrationTest;
import com.example.offer.draft.DraftAttributes;
import com.example.offer.draft.DraftSnapshot;
import com.example.offer.draft.DraftState;
import com.example.offer.draft.Description;
import com.example.offer.draft.Title;
import com.example.offer.offer.OfferPresence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;

import static com.example.offer.catalog.CatalogFixture.CATEGORY;
import static com.example.offer.catalog.CatalogFixture.activePriceWithDiscount;
import static com.example.offer.catalog.CatalogFixture.draft;
import static com.example.offer.catalog.CatalogFixture.id;
import static com.example.offer.catalog.CatalogFixture.product;
import static com.example.offer.catalog.CatalogFixture.publication;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the element-02 declared shape of the catalog read model: every field the UI renders must
 * be present and populated for a fully-populated product, so a dropped or empty field fails here
 * rather than in the browser.
 */
@IntegrationTest
@WithMockUser(roles = "content-manager")
class ProductReadsShapeTest {

    private static final String TITLE = "Kosiarka ręczna 340";
    private static final String AVAILABLE_FROM = LocalDate.now().minusDays(1).toString();

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
    void detailCarriesEveryDeclaredField() throws Exception {
        String productId = seedFullyPopulated();

        mvc.perform(get("/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.title").value(TITLE))
                .andExpect(jsonPath("$.category").value(CATEGORY))
                .andExpect(jsonPath("$.state").value("PUBLISHED"))
                .andExpect(jsonPath("$.descriptionVersion").value("v1"))
                .andExpect(jsonPath("$.publishedVersion").value("v1"))
                .andExpect(jsonPath("$.availableFrom").value(AVAILABLE_FROM))
                .andExpect(jsonPath("$.activePrice.value").value("259.00"))
                .andExpect(jsonPath("$.activePrice.currency").value("PLN"))
                .andExpect(jsonPath("$.activeDiscountPercent").value("10"))
                .andExpect(jsonPath("$.photoCount").value(1))
                .andExpect(jsonPath("$.createdAt").value(CatalogFixture.AT.toString()))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedBy").value("a.kowalska"));
    }

    @Test
    void listItemCarriesEveryDeclaredField() throws Exception {
        String productId = seedFullyPopulated();
        String item = "$.items[?(@.productId == '" + productId + "')]";

        mvc.perform(get("/products").param("query", "kosiarka").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(item + ".title").value(hasItem(TITLE)))
                .andExpect(jsonPath(item + ".category").value(hasItem(CATEGORY)))
                .andExpect(jsonPath(item + ".state").value(hasItem("PUBLISHED")))
                .andExpect(jsonPath(item + ".descriptionVersion").value(hasItem("v1")))
                .andExpect(jsonPath(item + ".publishedVersion").value(hasItem("v1")))
                .andExpect(jsonPath(item + ".availableFrom").value(hasItem(AVAILABLE_FROM)))
                .andExpect(jsonPath(item + ".activePrice.value").value(hasItem("259.00")))
                .andExpect(jsonPath(item + ".activePrice.currency").value(hasItem("PLN")))
                .andExpect(jsonPath(item + ".activeDiscountPercent").value(hasItem("10")))
                .andExpect(jsonPath(item + ".photoCount").value(hasItem(1)))
                .andExpect(jsonPath(item + ".createdAt").value(hasItem(CatalogFixture.AT.toString())))
                .andExpect(jsonPath(item + ".updatedAt").isNotEmpty())
                .andExpect(jsonPath(item + ".updatedBy").value(hasItem("a.kowalska")));
    }

    @Test
    void descriptionVersionIsNotAliasedByPublishedVersion() throws Exception {
        String productId = id();
        publisher.publishEvent(new DraftSnapshot(productId, "v2", DraftState.EDITING, 2,
                new Title(TITLE), new Description("Opis"), new DraftAttributes(CATEGORY, null),
                List.of(), null, null, CatalogFixture.audit()));
        publisher.publishEvent(activePriceWithDiscount(productId));
        publisher.publishEvent(product(productId, OfferPresence.PRESENT,
                List.of(publication(productId, "v1", LocalDate.now().minusDays(1)))));

        mvc.perform(get("/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descriptionVersion").value("v2"))
                .andExpect(jsonPath("$.publishedVersion").value("v1"));
    }

    private String seedFullyPopulated() {
        String productId = id();
        publisher.publishEvent(draft(productId, DraftState.EDITING, null));
        publisher.publishEvent(activePriceWithDiscount(productId));
        publisher.publishEvent(product(productId, OfferPresence.PRESENT,
                List.of(publication(productId, "v1", LocalDate.now().minusDays(1)))));
        return productId;
    }
}
