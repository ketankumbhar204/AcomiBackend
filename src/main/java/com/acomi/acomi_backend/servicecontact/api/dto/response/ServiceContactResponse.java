package com.acomi.acomi_backend.servicecontact.api.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ServiceContactResponse {

    private final UUID id;
    private final String businessName;
    private final String category;
    private final String categoryLabel;
    private final String customCategoryName;
    private final String contactPersonName;
    private final String primaryPhone;
    private final String alternatePhone;
    private final String whatsappPhone;
    private final String email;
    private final String description;
    private final String address;
    private final String notes;
    private final boolean active;
    private final boolean canEdit;
    private final boolean canArchive;
    private final List<ServiceContactSpaceResponse> spaces;
    private final long version;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
