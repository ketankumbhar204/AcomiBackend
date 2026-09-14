package com.acomi.acomi_backend.member.api.dto.request;

import com.acomi.acomi_backend.member.domain.model.MemberDocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Request body for registering a member document (metadata only)")
public class CreateMemberDocumentRequest {

    @NotNull(message = "Document type is required")
    @Schema(description = "Type of identity document", example = "AADHAAR", implementation = MemberDocumentType.class)
    private MemberDocumentType documentType;

    @NotBlank(message = "Document number is required")
    @Schema(description = "Document identification number", example = "1234-5678-9012")
    private String documentNumber;

    @Schema(description = "Legacy URL or placeholder. Prefer fileId for new uploads.", example = "pending-upload")
    private String fileUrl;

    @Schema(description = "Canonical stored file id from POST /api/v1/files/upload-sessions")
    private java.util.UUID fileId;
}
