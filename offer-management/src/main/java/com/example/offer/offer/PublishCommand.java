package com.example.offer.offer;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.LocalDate;

/** {@code availableFrom} null means "publish now" (D4/T1–T2). */
@Builder
public record PublishCommand(@NotBlank String descriptionVersion, LocalDate availableFrom) {
}
