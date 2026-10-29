package com.acomi.acomi_backend.servicecontact.api.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ServiceContactDuplicateMatchResponse {

    private final UUID id;
    private final String businessName;
    private final String category;
    private final String categoryLabel;
    private final String customCategoryName;
    private final String primaryPhone;
    private final boolean nameAndPhoneMatch;
}
