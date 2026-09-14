package com.acomi.acomi_backend.storage.api.dto.response;

import com.acomi.acomi_backend.storage.domain.model.FilePurpose;
import com.acomi.acomi_backend.storage.domain.model.FileStatus;
import com.acomi.acomi_backend.storage.domain.model.FileVisibility;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StoredFileResponse {

    private UUID fileId;
    private FilePurpose purpose;
    private FileVisibility visibility;
    private FileStatus status;
    private String originalFilename;
    private String contentType;
    private Long byteSize;
    private String checksumSha256;
    private UUID spaceId;
    private LocalDateTime createdAt;
}
