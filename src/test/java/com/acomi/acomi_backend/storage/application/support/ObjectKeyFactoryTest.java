package com.acomi.acomi_backend.storage.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.storage.domain.model.FilePurpose;
import com.acomi.acomi_backend.storage.domain.model.FileVisibility;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObjectKeyFactoryTest {

    @Test
    void usesVisibilityPurposeDateAndFileIdOnly() {
        UUID fileId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String key = ObjectKeyFactory.create(
                FileVisibility.PRIVATE, FilePurpose.PROFILE_PHOTO, fileId, LocalDate.of(2026, 9, 14));
        assertThat(key).isEqualTo("private/profile-photo/2026/09/" + fileId);
        assertThat(key).doesNotContain("@");
        assertThat(key).doesNotContain("phone");
    }
}
