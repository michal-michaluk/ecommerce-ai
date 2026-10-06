package com.example.offer.pricing;

import java.util.List;

public record PriceListResponse(String productId, List<PriceEntryResponse> items) {

    public PriceListResponse {
        items = List.copyOf(items);
    }
}
