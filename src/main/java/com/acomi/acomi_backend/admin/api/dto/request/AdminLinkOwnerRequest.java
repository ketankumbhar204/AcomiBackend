package com.acomi.acomi_backend.admin.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdminLinkOwnerRequest {

    @NotNull(message = "Owner user id is required")
    private UUID userId;
}
