package com.acomi.acomi_backend.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acomi.acomi_backend.common.exception.BusinessException;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationPhotoCodecTest {

    @Test
    void acceptsJpegPngAndWebpWithinTheLimit() {
        List<RegistrationPhotoCodec.DecodedPhoto> photos = RegistrationPhotoCodec.decodeAll(List.of(
                dataUrl("image/jpeg", jpeg()),
                dataUrl("image/png", png()),
                dataUrl("image/webp", webp())));

        assertThat(photos).hasSize(3);
        assertThat(photos).extracting(RegistrationPhotoCodec.DecodedPhoto::contentType)
                .containsExactly("image/jpeg", "image/png", "image/webp");
    }

    @Test
    void rejectsAFourthPhoto() {
        assertThatThrownBy(() -> RegistrationPhotoCodec.decodeAll(List.of(
                        dataUrl("image/jpeg", jpeg()),
                        dataUrl("image/jpeg", jpeg()),
                        dataUrl("image/jpeg", jpeg()),
                        dataUrl("image/jpeg", jpeg()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("You can upload at most 3 photos");
    }

    @Test
    void rejectsAPhotoLargerThan3Mb() {
        byte[] oversized = new byte[RegistrationPhotoCodec.MAX_BYTES + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;

        assertThatThrownBy(() -> RegistrationPhotoCodec.decodeAll(List.of(dataUrl("image/jpeg", oversized))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Each photo must be 3 MB or smaller");
    }

    @Test
    void rejectsAnUnsupportedTypeEvenWhenTheHeaderClaimsJpeg() {
        assertThatThrownBy(() -> RegistrationPhotoCodec.decodeAll(List.of(dataUrl("image/jpeg", new byte[] {1, 2, 3, 4}))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JPEG, PNG, or WebP");
    }

    @Test
    void rejectsAGif() {
        byte[] gif = new byte[] {'G', 'I', 'F', '8', '9', 'a'};
        assertThatThrownBy(() -> RegistrationPhotoCodec.decodeAll(List.of(dataUrl("image/gif", gif))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JPEG, PNG, or WebP");
    }

    @Test
    void allowsNoPhotos() {
        assertThat(RegistrationPhotoCodec.decodeAll(null)).isEmpty();
        assertThat(RegistrationPhotoCodec.decodeAll(List.of())).isEmpty();
    }

    private static String dataUrl(String type, byte[] bytes) {
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] jpeg() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
    }

    private static byte[] png() {
        return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    }

    private static byte[] webp() {
        return new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    }
}
