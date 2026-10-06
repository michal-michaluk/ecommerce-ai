package com.example.offer.draft;

import com.example.offer.ControllerTestSupport;
import com.example.offer.IntegrationTest;
import com.example.offer.TestSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@IntegrationTest
@Import(TestSecurityConfiguration.class)
class DraftControllerTest extends ControllerTestSupport {

    private static final String AUTH = HttpHeaders.AUTHORIZATION;

    @Autowired
    ObjectMapper json;

    @Test
    void createDraftEditRequestReviewAndReadFormats() throws Exception {
        String productId = createProduct("Prosto z półki / Kosiarka");

        client.get().uri("/products/{id}/description-draft", productId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.productId").isEqualTo(productId)
                .jsonPath("$.version").isEqualTo("v1")
                .jsonPath("$.state").isEqualTo("EDITING")
                .jsonPath("$.completeness.complete").isEqualTo(false)
                .jsonPath("$.completeness.missing[0].code").isEqualTo("DESCRIPTION_REQUIRED")
                .jsonPath("$.issues").isArray()
                .jsonPath("$.reviewRequestId").isEmpty();

        client.put().uri("/products/{id}/description-draft", productId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .contentType(APPLICATION_JSON)
                .bodyValue(Map.of("title", "Kosiarka ręczna 340",
                        "description", "Solidna kosiarka ręczna do trawy i chwastów."))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Kosiarka ręczna 340")
                .jsonPath("$.description").isNotEmpty();

        client.post().uri("/products/{id}/description-draft/review-requests", productId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PENDING")
                .jsonPath("$.author").isEqualTo("a.kowalska")
                .jsonPath("$.submittedAt").isNotEmpty();

        client.get().uri("/product-photo-formats")
                .header(AUTH, bearer(CONTENT_MANAGER))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.formats[0].mime").isEqualTo("image/jpeg")
                .jsonPath("$.formats[0].minWidth").isEqualTo(1000);
    }

    @Test
    void addAndRemovePhoto() throws Exception {
        String productId = createProduct("Kosiarka");
        byte[] jpeg = image("jpg", 1200, 1200);

        String photoId = json.readTree(upload(productId, jpeg, "photo.jpeg", "image/jpeg")
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.width").isEqualTo(1200)
                .jsonPath("$.position").isEqualTo(0)
                .returnResult().getResponseBody()).get("photoId").asString();

        client.delete().uri("/products/{id}/description-draft/photos/{photoId}", productId, photoId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void unknownProductIsNotFound() {
        client.get().uri("/products/p-does-not-exist/description-draft")
                .header(AUTH, bearer(CONTENT_MANAGER))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("PRODUCT_NOT_FOUND")
                .jsonPath("$.message").isNotEmpty();
    }

    @Test
    void draftIsNotEditableWhileUnderReview() throws Exception {
        String productId = createProduct("Kosiarka");
        requestReview(productId);

        client.put().uri("/products/{id}/description-draft", productId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .contentType(APPLICATION_JSON)
                .bodyValue(Map.of("title", "changed"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DRAFT_NOT_EDITABLE");
    }

    @Test
    void openingASecondReviewIsAConflict() throws Exception {
        String productId = createProduct("Kosiarka");
        requestReview(productId);

        client.post().uri("/products/{id}/description-draft/review-requests", productId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("REVIEW_ALREADY_PENDING");
    }

    @Test
    void blankTitleIsAValidationFailure() {
        client.post().uri("/products")
                .header(AUTH, bearer(CONTENT_MANAGER))
                .contentType(APPLICATION_JSON)
                .bodyValue(Map.of("title", "", "category", "Ogród"))
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.details.fields[0]").isEqualTo("title");
    }

    @Test
    void unsupportedPhotoFormatIsUnprocessable() throws Exception {
        String productId = createProduct("Kosiarka");

        upload(productId, "%PDF-1.7".getBytes(), "scan.pdf", "application/pdf")
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PHOTO_FORMAT_UNSUPPORTED");
    }

    @Test
    void tooSmallPhotoIsUnprocessable() throws Exception {
        String productId = createProduct("Kosiarka");

        upload(productId, image("png", 10, 10), "tiny.png", "image/png")
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PHOTO_TOO_SMALL");
    }

    @Test
    void missingTokenIsUnauthenticated() {
        client.get().uri("/product-photo-formats").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void wrongRoleIsForbidden() {
        client.get().uri("/product-photo-formats")
                .header(AUTH, bearer(SALES))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN")
                .jsonPath("$.message").isNotEmpty();
    }

    private String createProduct(String title) throws Exception {
        byte[] body = client.post().uri("/products")
                .header(AUTH, bearer(CONTENT_MANAGER))
                .contentType(APPLICATION_JSON)
                .bodyValue(Map.of("title", title, "category", "Ogród"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.state").isEqualTo("DRAFT")
                .jsonPath("$.descriptionVersion").isEqualTo("v1")
                .returnResult().getResponseBody();
        return json.readTree(body).get("productId").asString();
    }

    private void requestReview(String productId) {
        client.post().uri("/products/{id}/description-draft/review-requests", productId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .exchange()
                .expectStatus().isCreated();
    }

    private WebTestClient.ResponseSpec upload(String productId, byte[] bytes, String filename, String mime) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(MediaType.parseMediaType(mime));
        return client.post().uri("/products/{id}/description-draft/photos", productId)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange();
    }

    private static byte[] image(String format, int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }
}
