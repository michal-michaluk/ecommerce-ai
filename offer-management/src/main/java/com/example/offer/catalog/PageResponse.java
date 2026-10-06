package com.example.offer.catalog;

import java.util.List;

/** The paged envelope of element 02 ({@code items · page · size · totalElements · totalPages}). */
record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    PageResponse {
        items = List.copyOf(items);
    }
}
