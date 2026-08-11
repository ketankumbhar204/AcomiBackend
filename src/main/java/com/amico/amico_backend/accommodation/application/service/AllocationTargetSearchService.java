package com.amico.amico_backend.accommodation.application.service;

import com.amico.amico_backend.accommodation.api.dto.response.AllocationTargetSearchResponse;
import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.accommodation.infrastructure.persistence.projection.AllocationTargetSearchRow;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.AllocationTargetSearchRepository;
import com.amico.amico_backend.common.web.PagedResponse;
import com.amico.amico_backend.occupancy.domain.model.AllocationTargetType;
import com.amico.amico_backend.space.domain.model.SpaceType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AllocationTargetSearchService {

    private final AccommodationAccessService accessService;
    private final AllocationTargetSearchRepository searchRepository;

    @Transactional(readOnly = true)
    public PagedResponse<AllocationTargetSearchResponse> searchAllocationTargets(
            UUID spaceId,
            UUID callerId,
            String query,
            AllocationTargetType targetType,
            UUID buildingId,
            UUID floorId,
            UUID unitId,
            AccommodationStatus status,
            Boolean selectableOnly,
            Pageable pageable) {
        log.info(
                "Searching allocation targets: spaceId={}, callerId={}, targetType={}, query={}",
                spaceId,
                callerId,
                targetType,
                query);

        accessService.assertCanSearchAllocationTargets(spaceId, callerId);
        SpaceType spaceType = accessService.loadSpaceType(spaceId);
        AllocationTargetType effectiveTargetType = resolveTargetType(targetType, spaceType);
        String normalizedQuery = normalizeQuery(query);
        boolean onlySelectable = Boolean.TRUE.equals(selectableOnly);

        Page<AllocationTargetSearchRow> page = switch (effectiveTargetType) {
            case BED -> searchRepository.searchBedTargets(
                    spaceId,
                    normalizedQuery,
                    buildingId,
                    floorId,
                    unitId,
                    status,
                    onlySelectable,
                    pageable);
            case UNIT -> searchRepository.searchUnitTargets(
                    spaceId, normalizedQuery, buildingId, unitId, status, onlySelectable, pageable);
            case ROOM -> Page.empty(pageable);
        };

        return PagedResponse.from(page.map(this::toResponse));
    }

    private AllocationTargetType resolveTargetType(AllocationTargetType requested, SpaceType spaceType) {
        if (requested != null) {
            return requested;
        }
        return spaceType == SpaceType.RENTAL ? AllocationTargetType.UNIT : AllocationTargetType.BED;
    }

    private AllocationTargetSearchResponse toResponse(AllocationTargetSearchRow row) {
        boolean selectable = row.getStatus() == AccommodationStatus.AVAILABLE;
        return AllocationTargetSearchResponse.builder()
                .targetType(row.getTargetType())
                .targetId(row.getTargetId())
                .buildingId(row.getBuildingId())
                .buildingName(row.getBuildingName())
                .floorId(row.getFloorId())
                .floorName(row.getFloorName())
                .unitId(row.getUnitId())
                .unitName(row.getUnitName())
                .roomId(row.getRoomId())
                .roomName(row.getRoomName())
                .roomNumber(row.getRoomNumber())
                .bedId(row.getBedId())
                .bedName(row.getBedName())
                .bedNumber(row.getBedNumber())
                .displayPath(AccommodationDisplayPathBuilder.buildDisplayPath(row))
                .displayPathShort(AccommodationDisplayPathBuilder.buildDisplayPathShort(row))
                .status(row.getStatus())
                .defaultRent(row.getDefaultRent())
                .defaultDeposit(row.getDefaultDeposit())
                .selectable(selectable)
                .notSelectableReason(selectable ? null : notSelectableReason(row.getStatus()))
                .build();
    }

    private static String notSelectableReason(AccommodationStatus status) {
        return switch (status) {
            case OCCUPIED -> "Target is occupied";
            case RESERVED -> "Target is reserved";
            case MAINTENANCE -> "Target is under maintenance";
            case BLOCKED -> "Target is blocked";
            case AVAILABLE -> null;
        };
    }

    private static String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
