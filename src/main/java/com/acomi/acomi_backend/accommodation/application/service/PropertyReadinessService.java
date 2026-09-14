package com.acomi.acomi_backend.accommodation.application.service;

import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mirrors client {@code isPropertyReady} for lodging spaces.
 * Mess is always considered ready (no accommodation).
 */
@Service
@RequiredArgsConstructor
public class PropertyReadinessService {

    private final BuildingRepository buildingRepository;
    private final UnitRepository unitRepository;
    private final BedRepository bedRepository;

    @Transactional(readOnly = true)
    public boolean isPropertyReady(UUID spaceId, SpaceType spaceType) {
        if (spaceType == null || spaceType == SpaceType.MESS) {
            return true;
        }
        long buildings = buildingRepository.countActiveBySpaceId(spaceId);
        if (buildings <= 0) {
            return false;
        }
        if (spaceType == SpaceType.RENTAL) {
            return unitRepository.countVisibleActiveBySpaceId(spaceId) > 0;
        }
        // PG / HOSTEL / CO_LIVING — allocatable beds required
        return bedRepository.countActiveBySpaceId(spaceId) > 0;
    }
}
