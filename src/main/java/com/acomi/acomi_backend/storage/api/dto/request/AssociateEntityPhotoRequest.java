package com.acomi.acomi_backend.storage.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssociateEntityPhotoRequest {

    @NotNull
    private UUID fileId;
}
