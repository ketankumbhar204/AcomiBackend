package com.acomi.acomi_backend.storage.api.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ContentUrlResponse {

    private UUID fileId;
    private String contentUrl;
    private String contentType;
    private String originalFilename;
    private String downloadFilename;
    private LocalDateTime expiresAt;
}
