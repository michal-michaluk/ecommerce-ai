package com.example.offer.draft;

import java.util.List;

public record PhotoFormatsResponse(List<PhotoFormat> formats) {

    public PhotoFormatsResponse {
        formats = List.copyOf(formats);
    }

    static PhotoFormatsResponse standard() {
        return new PhotoFormatsResponse(PhotoFormatPolicy.standard().allowed());
    }
}
