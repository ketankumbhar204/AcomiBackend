package com.acomi.acomi_backend.complaint.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddComplaintAttachmentRequest {

    @NotBlank
    private String imageBase64;

    private String fileName;

    private String contentType;
}
