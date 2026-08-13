package com.acomi.acomi_backend.complaint.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReopenComplaintRequest {

    @Size(max = 2000)
    private String reason;
}
