package com.acomi.acomi_backend.admin.application.service;

import com.acomi.acomi_backend.admin.api.dto.request.AdminUpsertListingMarketingTemplateRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminListingMarketingTemplateResponse;
import com.acomi.acomi_backend.admin.application.support.ListingMarketingPlaceholders;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingTemplateApplicability;
import com.acomi.acomi_backend.admin.infrastructure.persistence.entity.AdminListingMarketingTemplateEntity;
import com.acomi.acomi_backend.admin.infrastructure.persistence.repository.AdminListingMarketingTemplateRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.common.security.SecurityUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminListingMarketingTemplateService {

    private final AdminListingMarketingTemplateRepository repository;

    @Transactional(readOnly = true)
    public List<AdminListingMarketingTemplateResponse> list(boolean activeOnly) {
        List<AdminListingMarketingTemplateEntity> entities = activeOnly
                ? repository.findByActiveTrueOrderByDisplayOrderAscNameAsc()
                : repository.findAllByOrderByDisplayOrderAscNameAsc();
        return entities.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AdminListingMarketingTemplateResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public AdminListingMarketingTemplateResponse create(AdminUpsertListingMarketingTemplateRequest request) {
        validateRequest(request);
        UUID actorId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        AdminListingMarketingTemplateEntity entity = AdminListingMarketingTemplateEntity.builder()
                .name(request.name().trim())
                .description(trimToNull(request.description()))
                .body(request.body().trim())
                .applicability(request.applicability())
                .active(request.active() == null || request.active())
                .displayOrder(request.displayOrder() == null ? 0 : request.displayOrder())
                .createdBy(actorId)
                .updatedBy(actorId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return toResponse(repository.save(entity));
    }

    @Transactional
    public AdminListingMarketingTemplateResponse update(
            UUID id, AdminUpsertListingMarketingTemplateRequest request) {
        validateRequest(request);
        AdminListingMarketingTemplateEntity entity = require(id);
        entity.setName(request.name().trim());
        entity.setDescription(trimToNull(request.description()));
        entity.setBody(request.body().trim());
        entity.setApplicability(request.applicability());
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        if (request.displayOrder() != null) {
            entity.setDisplayOrder(request.displayOrder());
        }
        entity.setUpdatedBy(SecurityUtils.getCurrentUserId());
        entity.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public AdminListingMarketingTemplateResponse duplicate(UUID id) {
        AdminListingMarketingTemplateEntity source = require(id);
        UUID actorId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        String copyName = truncate(source.getName() + " (copy)", 120);
        AdminListingMarketingTemplateEntity copy = AdminListingMarketingTemplateEntity.builder()
                .name(copyName)
                .description(source.getDescription())
                .body(source.getBody())
                .applicability(source.getApplicability())
                .active(source.isActive())
                .displayOrder(source.getDisplayOrder())
                .createdBy(actorId)
                .updatedBy(actorId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return toResponse(repository.save(copy));
    }

    @Transactional
    public void delete(UUID id) {
        AdminListingMarketingTemplateEntity entity = require(id);
        repository.delete(entity);
    }

    private void validateRequest(AdminUpsertListingMarketingTemplateRequest request) {
        if (!StringUtils.hasText(request.name())) {
            throw new BusinessException("Template name is required", HttpStatus.BAD_REQUEST);
        }
        if (!StringUtils.hasText(request.body())) {
            throw new BusinessException("Template body is required", HttpStatus.BAD_REQUEST);
        }
        if (request.applicability() == null) {
            throw new BusinessException("Applicability is required", HttpStatus.BAD_REQUEST);
        }
        Set<String> unsupported = ListingMarketingPlaceholders.unsupported(request.body());
        if (!unsupported.isEmpty()) {
            String joined = unsupported.stream().sorted().collect(Collectors.joining(", "));
            throw new BusinessException(
                    "Unsupported placeholders: " + joined, HttpStatus.BAD_REQUEST);
        }
    }

    private AdminListingMarketingTemplateEntity require(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ListingMarketingTemplate", "id", id));
    }

    private AdminListingMarketingTemplateResponse toResponse(AdminListingMarketingTemplateEntity entity) {
        return new AdminListingMarketingTemplateResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getBody(),
                entity.getApplicability(),
                entity.isActive(),
                entity.getDisplayOrder(),
                entity.getCreatedBy(),
                entity.getUpdatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                false);
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    /** Exposed for tests. */
    public static boolean appliesTo(
            ListingMarketingTemplateApplicability applicability, String listingKind) {
        if (applicability == ListingMarketingTemplateApplicability.BOTH) {
            return true;
        }
        return applicability.name().equalsIgnoreCase(listingKind);
    }
}
