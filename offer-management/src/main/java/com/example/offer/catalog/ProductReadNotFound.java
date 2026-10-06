package com.example.offer.catalog;

/** Raised when a product has no catalog read model — mapped to {@code 404 PRODUCT_NOT_FOUND}. */
class ProductReadNotFound extends RuntimeException {

    private final String productId;

    ProductReadNotFound(String productId) {
        super("product " + productId + " not found");
        this.productId = productId;
    }

    String productId() {
        return productId;
    }
}
