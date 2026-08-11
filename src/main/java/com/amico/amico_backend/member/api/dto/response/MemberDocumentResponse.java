package com.amico.amico_backend.member.api.dto.response;

import com.amico.amico_backend.member.domain.model.DocumentVerificationStatus;
import com.amico.amico_backend.member.domain.model.MemberDocumentType;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberDocumentEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Member identity document metadata")
public class MemberDocumentResponse {

    private UUID documentId;
    private MemberDocumentType documentType;
    private String documentNumber;
    private String fileUrl;

    @Schema(description = "Document verification status")
    private DocumentVerificationStatus verificationStatus;

    private LocalDateTime uploadedAt;

    public static MemberDocumentResponse from(MemberDocumentEntity document) {
        return MemberDocumentResponse.builder()
                .documentId(document.getId())
                .documentType(document.getDocumentType())
                .documentNumber(document.getDocumentNumber())
                .fileUrl(document.getFileUrl())
                .verificationStatus(document.getVerificationStatus())
                .uploadedAt(document.getUploadedAt())
                .build();
    }
}
