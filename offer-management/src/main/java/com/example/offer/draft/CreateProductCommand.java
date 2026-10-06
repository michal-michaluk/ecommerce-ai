package com.example.offer.draft;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record CreateProductCommand(@NotBlank String title, String category) {
}
