package com.acomi.acomi_backend.complaint.api.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddComplaintAttachmentRequest {

    private String imageBase64;

    private java.util.UUID fileId;

    private String fileName;

    private String contentType;
}
