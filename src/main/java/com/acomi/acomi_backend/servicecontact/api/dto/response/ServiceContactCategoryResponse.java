package com.acomi.acomi_backend.servicecontact.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ServiceContactCategoryResponse {

    private final String code;
    private final String label;
}
