package com.acomi.acomi_backend.admin.api.dto.request;

import com.acomi.acomi_backend.admin.domain.model.ListingMarketingTemplateApplicability;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminUpsertListingMarketingTemplateRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 255) String description,
        @NotBlank @Size(max = 4000) String body,
        @NotNull ListingMarketingTemplateApplicability applicability,
        Boolean active,
        Integer displayOrder) {}
