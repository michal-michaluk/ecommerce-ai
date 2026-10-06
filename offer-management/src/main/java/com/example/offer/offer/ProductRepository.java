package com.example.offer.offer;

import java.util.Optional;

/** Secondary port: the {@link Product} process aggregate, implemented by the persistence adapter. */
interface ProductRepository {

    Optional<Product> get(String productId);

    void save(Product product);
}
