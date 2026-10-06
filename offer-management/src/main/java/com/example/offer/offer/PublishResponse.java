package com.example.offer.offer;

import java.time.Instant;
import java.time.LocalDate;

public record PublishResponse(String publicationId, String productId, String descriptionVersion,
                              String state, LocalDate visibleFrom, Instant publishedAt) {
}
