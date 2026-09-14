package com.acomi.acomi_backend.storage.api.dto.request;

import com.acomi.acomi_backend.storage.domain.model.FilePurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateUploadSessionRequest {

    @NotNull
    private FilePurpose purpose;

    private UUID spaceId;

    private UUID memberId;

    private UUID paymentId;

    private UUID complaintId;

    private LocalDate pollDate;

    @NotBlank
    private String contentType;

    @NotNull
    @Positive
    private Long byteSize;

    private String originalFilename;

    private String checksumSha256;
}
