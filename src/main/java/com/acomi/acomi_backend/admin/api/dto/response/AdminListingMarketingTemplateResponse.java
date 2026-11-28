package com.acomi.acomi_backend.admin.api.dto.response;

import com.acomi.acomi_backend.admin.domain.model.ListingMarketingTemplateApplicability;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdminListingMarketingTemplateResponse(
        UUID id,
        String name,
        String description,
        String body,
        ListingMarketingTemplateApplicability applicability,
        boolean active,
        int displayOrder,
        UUID createdBy,
        UUID updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean builtin) {}
