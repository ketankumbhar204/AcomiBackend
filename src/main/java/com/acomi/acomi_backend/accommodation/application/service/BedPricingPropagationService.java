package com.acomi.acomi_backend.accommodation.application.service;

import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.accommodation.domain.policy.BedPricingPropagation;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BedPricingPropagationService {

    private final BedRepository bedRepository;

    public void propagateFrom(UUID spaceId, BedEntity source) {
        if (source == null
                || (BedPricingPropagation.isEmpty(source.getDefaultRent())
                        && BedPricingPropagation.isEmpty(source.getDefaultDeposit()))) {
            return;
        }

        BedEntity loaded = source.getId() == null
                ? source
                : bedRepository.findByIdAndSpaceId(source.getId(), spaceId).orElse(source);

        BuildingEntity building = BedPricingPropagation.resolveBuilding(loaded);
        if (building == null || building.getId() == null) {
            return;
        }

        PropertyLayoutMode layoutMode = building.getLayoutMode() == null
                ? PropertyLayoutMode.CORRIDOR_PG
                : building.getLayoutMode();

        List<BedEntity> candidates = bedRepository.findActiveFetchedByBuildingId(building.getId());
        List<BedEntity> changed = BedPricingPropagation.apply(layoutMode, loaded, candidates);
        if (!changed.isEmpty()) {
            bedRepository.saveAll(changed);
            log.info(
                    "Propagated bed pricing from {} to {} equivalent beds in building {}",
                    loaded.getId(),
                    changed.size(),
                    building.getId());
        }
    }
}
