package com.acomi.acomi_backend.admin.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUpdateTestUserRequest {

    @NotNull
    private Boolean testUser;
}
