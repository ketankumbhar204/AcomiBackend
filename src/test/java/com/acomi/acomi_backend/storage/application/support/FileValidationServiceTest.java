package com.acomi.acomi_backend.storage.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.storage.domain.model.FileErrorCodes;
import com.acomi.acomi_backend.storage.domain.model.FilePurpose;
import org.junit.jupiter.api.Test;

class FileValidationServiceTest {

    private final FileValidationService validation = new FileValidationService();

    @Test
    void rejectsHtmlAndOctetStream() {
        assertThatThrownBy(() -> validation.normalizeContentType("text/html"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(FileErrorCodes.FILE_TYPE_NOT_ALLOWED);
        assertThatThrownBy(() -> validation.normalizeContentType("application/octet-stream"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validation.normalizeContentType("image/svg+xml"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validation.normalizeContentType("application/pdf"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void acceptsJpegPngWebp() {
        assertThat(validation.normalizeContentType("image/jpg")).isEqualTo("image/jpeg");
        assertThat(validation.normalizeContentType("image/png; charset=binary")).isEqualTo("image/png");
        assertThat(validation.normalizeContentType("IMAGE/WEBP")).isEqualTo("image/webp");
    }

    @Test
    void enforcesPurposeSizeLimits() {
        assertThatThrownBy(() -> validation.validateSize(FilePurpose.PROFILE_PHOTO, 2 * 1024 * 1024 + 1))
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(FileErrorCodes.FILE_TOO_LARGE);
        validation.validateSize(FilePurpose.PAYMENT_PROOF, 4 * 1024 * 1024);
        validation.validateSize(FilePurpose.MEMBER_DOCUMENT, FilePurpose.ABSOLUTE_MAX_BYTES);
        assertThatThrownBy(() -> validation.validateSize(FilePurpose.MEMBER_DOCUMENT, FilePurpose.ABSOLUTE_MAX_BYTES + 1))
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(FileErrorCodes.FILE_TOO_LARGE);
        assertThatThrownBy(() -> validation.validateSize(FilePurpose.IDENTITY_DOCUMENT, 8 * 1024 * 1024))
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(FileErrorCodes.FILE_TOO_LARGE);
        validation.validateSize(FilePurpose.BUILDING_PHOTO, FilePurpose.ABSOLUTE_MAX_BYTES);
        validation.validateSize(FilePurpose.MENU_ITEM_PHOTO, FilePurpose.ABSOLUTE_MAX_BYTES);
        assertThatThrownBy(() -> validation.validateSize(FilePurpose.COMBO_PHOTO, FilePurpose.ABSOLUTE_MAX_BYTES + 1))
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(FileErrorCodes.FILE_TOO_LARGE);
    }

    @Test
    void downloadFilenamePrefersOriginalThenPurpose() {
        assertThat(validation.downloadFilename(FilePurpose.PROFILE_PHOTO, "me.PNG", "image/jpeg"))
                .isEqualTo("me.PNG");
        assertThat(validation.downloadFilename(FilePurpose.PAYMENT_PROOF, null, "image/jpeg"))
                .isEqualTo("payment-proof.jpg");
    }

    @Test
    void sanitizesFilename() {
        assertThat(validation.sanitizeFilename("../../etc/passwd")).isEqualTo("passwd");
        assertThat(validation.sanitizeFilename("a<>.jpg")).isEqualTo("a__.jpg");
    }

    @Test
    void rejectsMismatchedMagicBytes() {
        byte[] png = new byte[16];
        png[0] = (byte) 0x89;
        png[1] = 0x50;
        png[2] = 0x4E;
        png[3] = 0x47;
        assertThatThrownBy(() -> validation.validateMagicBytes("image/jpeg", png))
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(FileErrorCodes.FILE_TYPE_NOT_ALLOWED);
    }
}
