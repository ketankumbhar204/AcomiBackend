package com.acomi.acomi_backend.complaint.api.dto.response;

import com.acomi.acomi_backend.complaint.infrastructure.persistence.entity.SpaceComplaintAttachmentEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ComplaintAttachmentResponse {

    private UUID attachmentId;
    private String storageUrl;
    private String contentType;
    private String fileName;
    private UUID createdByUserId;
    private LocalDateTime createdAt;

    public static ComplaintAttachmentResponse from(SpaceComplaintAttachmentEntity entity) {
        return ComplaintAttachmentResponse.builder()
                .attachmentId(entity.getId())
                .storageUrl(entity.getStorageUrl())
                .contentType(entity.getContentType())
                .fileName(entity.getFileName())
                .createdByUserId(entity.getCreatedByUserId())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
