package com.acomi.acomi_backend.space.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.domain.model.AmenityCode;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceAmenityEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceAmenityRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpaceAmenityService {

    public static final int MAX_AMENITIES = 20;
    public static final int MAX_CUSTOM_LABEL_LENGTH = 120;

    private final SpaceAmenityRepository spaceAmenityRepository;

    public SpaceAmenityService(SpaceAmenityRepository spaceAmenityRepository) {
        this.spaceAmenityRepository = spaceAmenityRepository;
    }

    public static boolean supportsAmenities(SpaceType spaceType) {
        return spaceType == SpaceType.PG || spaceType == SpaceType.HOSTEL || spaceType == SpaceType.CO_LIVING;
    }

    @Transactional(readOnly = true)
    public List<AmenityAssignmentDto> getForSpace(UUID spaceId) {
        return spaceAmenityRepository.findAllBySpaceIdOrderByDisplayOrderAscCreatedAtAsc(spaceId).stream()
                .map(SpaceAmenityService::toDto)
                .toList();
    }

    @Transactional
    public void replaceForSpace(SpaceEntity space, List<AmenityAssignmentDto> amenities) {
        if (!supportsAmenities(space.getType())) {
            return;
        }
        List<AmenityAssignmentDto> normalized = normalizeAssignments(amenities);
        spaceAmenityRepository.deleteBySpaceId(space.getId());
        if (normalized.isEmpty()) {
            return;
        }
        int order = 0;
        for (AmenityAssignmentDto amenity : normalized) {
            spaceAmenityRepository.save(SpaceAmenityEntity.builder()
                    .space(space)
                    .amenityCode(amenity.getCode())
                    .customLabel(AmenityCode.CUSTOM.name().equals(amenity.getCode()) ? amenity.getLabel() : null)
                    .displayOrder(order++)
                    .build());
        }
    }

    public static List<AmenityAssignmentDto> normalizeAssignments(List<AmenityAssignmentDto> amenities) {
        if (amenities == null || amenities.isEmpty()) {
            return List.of();
        }
        if (amenities.size() > MAX_AMENITIES) {
            throw new BusinessException(
                    "A space can have at most " + MAX_AMENITIES + " amenities", HttpStatus.BAD_REQUEST);
        }

        List<AmenityAssignmentDto> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AmenityAssignmentDto raw : amenities) {
            if (raw == null || raw.getCode() == null || raw.getCode().isBlank()) {
                throw new BusinessException("Amenity code is required", HttpStatus.BAD_REQUEST);
            }
            AmenityCode code =
                    AmenityCode.fromValue(raw.getCode()).orElseThrow(() -> new BusinessException(
                            "Unknown amenity code: " + raw.getCode(), HttpStatus.BAD_REQUEST));
            String label = resolveLabel(code, raw.getLabel());
            String key = code.isCustom()
                    ? code.name() + "::" + label.toLowerCase(Locale.ROOT)
                    : code.name();
            if (!seen.add(key)) {
                continue;
            }
            AmenityAssignmentDto dto = new AmenityAssignmentDto();
            dto.setCode(code.name());
            dto.setLabel(label);
            normalized.add(dto);
        }
        return normalized;
    }

    public static String resolveLabel(AmenityCode code, String providedLabel) {
        if (code.isCustom()) {
            String label = providedLabel != null ? providedLabel.trim() : "";
            if (label.isEmpty()) {
                throw new BusinessException("Custom amenity label is required", HttpStatus.BAD_REQUEST);
            }
            if (label.length() > MAX_CUSTOM_LABEL_LENGTH) {
                throw new BusinessException(
                        "Custom amenity label must be at most " + MAX_CUSTOM_LABEL_LENGTH + " characters",
                        HttpStatus.BAD_REQUEST);
            }
            return label;
        }
        if (providedLabel != null && !providedLabel.isBlank()) {
            return providedLabel.trim();
        }
        return code.getDefaultLabel();
    }

    public static void assertSubsetOfSpaceAmenities(
            List<AmenityAssignmentDto> requested, List<AmenityAssignmentDto> spaceAmenities) {
        if (requested == null || requested.isEmpty()) {
            return;
        }
        Set<String> allowed = new HashSet<>();
        for (AmenityAssignmentDto spaceAmenity : spaceAmenities) {
            allowed.add(assignmentKey(spaceAmenity));
        }
        for (AmenityAssignmentDto amenity : normalizeAssignments(requested)) {
            if (!allowed.contains(assignmentKey(amenity))) {
                throw new BusinessException(
                        "Amenity is not available for this space: " + amenity.getLabel(),
                        HttpStatus.BAD_REQUEST);
            }
        }
    }

    public static String assignmentKey(AmenityAssignmentDto amenity) {
        if (AmenityCode.CUSTOM.name().equals(amenity.getCode())) {
            return AmenityCode.CUSTOM.name() + "::"
                    + amenity.getLabel().trim().toLowerCase(Locale.ROOT);
        }
        return amenity.getCode();
    }

    private static AmenityAssignmentDto toDto(SpaceAmenityEntity entity) {
        AmenityCode code = AmenityCode.fromValue(entity.getAmenityCode())
                .orElse(AmenityCode.CUSTOM);
        AmenityAssignmentDto dto = new AmenityAssignmentDto();
        dto.setCode(entity.getAmenityCode());
        dto.setLabel(resolveLabel(code, entity.getCustomLabel() != null ? entity.getCustomLabel() : code.getDefaultLabel()));
        return dto;
    }
}
