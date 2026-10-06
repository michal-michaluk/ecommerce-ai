package com.example.offer.draft;

import org.junit.jupiter.api.Test;

import static com.example.offer.draft.DraftFixture.hugePhoto;
import static com.example.offer.draft.DraftFixture.photo;
import static com.example.offer.draft.DraftFixture.smallPhoto;
import static com.example.offer.draft.DraftFixture.unsupportedPhoto;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoFormatPolicyTest {

    private final PhotoFormatPolicy policy = PhotoFormatPolicy.standard();

    @Test
    void acceptsJpegAndPng() {
        assertThatCode(() -> policy.check(photo())).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedMime() {
        assertThatThrownBy(() -> policy.check(unsupportedPhoto()))
                .isInstanceOf(PhotoFormatUnsupported.class);
    }

    @Test
    void rejectsPhotoBelowMinimumSide() {
        assertThatThrownBy(() -> policy.check(smallPhoto()))
                .isInstanceOf(PhotoTooSmall.class);
    }

    @Test
    void rejectsPhotoAboveMaximumSize() {
        assertThatThrownBy(() -> policy.check(hugePhoto()))
                .isInstanceOf(PhotoTooLarge.class);
    }
}
