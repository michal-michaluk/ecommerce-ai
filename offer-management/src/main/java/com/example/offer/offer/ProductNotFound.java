package com.example.offer.offer;

public class ProductNotFound extends RuntimeException {

    public ProductNotFound(String productId) {
        super("product " + productId + " not found");
    }
}
