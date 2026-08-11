package com.amico.amico_backend.complaint.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddComplaintCommentRequest {

    @NotBlank
    @Size(max = 4000)
    private String body;

    /** Staff/operator-only notes; ignored/rejected for tenants. */
    private boolean internal;
}
