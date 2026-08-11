package com.amico.amico_backend.complaint.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateComplaintResolutionRequest {

    @NotBlank
    @Size(max = 4000)
    private String resolutionSummary;

    /** When true, also transitions to RESOLVED if currently OPEN/IN_PROGRESS. */
    private boolean markResolved = true;
}
