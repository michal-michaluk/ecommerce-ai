package com.example.offer.draft;

import java.util.List;

public record PhotoFormat(String mime, List<String> extensions, int minWidth, int minHeight, long maxBytes) {
    public PhotoFormat {
        extensions = List.copyOf(extensions);
    }
}
