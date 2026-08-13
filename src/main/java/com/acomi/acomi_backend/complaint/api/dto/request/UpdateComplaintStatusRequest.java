package com.acomi.acomi_backend.complaint.api.dto.request;

import com.acomi.acomi_backend.complaint.domain.model.ComplaintStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateComplaintStatusRequest {

    @NotNull
    private ComplaintStatus status;

    @Size(max = 2000)
    private String note;
}
