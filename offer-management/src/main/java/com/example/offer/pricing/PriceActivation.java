package com.example.offer.pricing;

/**
 * An entry that became {@code ACTIVE} at the business date of a scheduler sweep (E08 T2).
 * {@code entryId} is the entry that crossed, so the emission is idempotent per (entry, effective
 * date). {@code amount} is the base price active then, or {@code null} when no base price applies
 * (RULE-39); {@code discountPercent} is the active discount, or {@code null}.
 */
record PriceActivation(String entryId, Money amount, Percent discountPercent) {
}
