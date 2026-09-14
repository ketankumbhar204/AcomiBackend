package com.acomi.acomi_backend.storage.application.support;

import com.acomi.acomi_backend.storage.domain.model.FilePurpose;
import com.acomi.acomi_backend.storage.domain.model.FileVisibility;
import java.time.LocalDate;
import java.util.UUID;

public final class ObjectKeyFactory {

    private ObjectKeyFactory() {}

    public static String create(FileVisibility visibility, FilePurpose purpose, UUID fileId, LocalDate now) {
        return visibility.name().toLowerCase()
                + "/"
                + purpose.objectKeySegment()
                + "/"
                + now.getYear()
                + "/"
                + String.format("%02d", now.getMonthValue())
                + "/"
                + fileId;
    }
}
