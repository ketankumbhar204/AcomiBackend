package com.acomi.acomi_backend.accommodation.application.service;

import com.acomi.acomi_backend.accommodation.api.dto.response.BedPricingPreviewResponse;
import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.accommodation.domain.policy.BedPricingPropagation;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Read-only preview of fill-empty matching. Does not persist or mutate stored beds.
     */
    @Transactional(readOnly = true)
    public BedPricingPreviewResponse preview(
            UUID spaceId, BedEntity source, BigDecimal proposedRent, BigDecimal proposedDeposit) {
        if (source == null) {
            return BedPricingPreviewResponse.builder()
                    .affectedBedCount(0)
                    .affectedLocations(List.of())
                    .build();
        }

        BedEntity loaded = source.getId() == null
                ? source
                : bedRepository.findByIdAndSpaceId(source.getId(), spaceId).orElse(source);

        BedEntity proposed = proposedSource(loaded, proposedRent, proposedDeposit);
        BuildingEntity building = BedPricingPropagation.resolveBuilding(loaded);
        List<BedEntity> affected = new ArrayList<>();
        affected.add(loaded);

        if (building != null && building.getId() != null) {
            PropertyLayoutMode layoutMode = building.getLayoutMode() == null
                    ? PropertyLayoutMode.CORRIDOR_PG
                    : building.getLayoutMode();
            List<BedEntity> candidates = bedRepository.findActiveFetchedByBuildingId(building.getId());
            affected.addAll(BedPricingPropagation.matchingTargets(layoutMode, proposed, candidates));
        }

        return BedPricingPreviewResponse.builder()
                .affectedBedCount(affected.size())
                .affectedLocations(BedPricingPropagation.locationLabels(building, affected))
                .build();
    }

    private static BedEntity proposedSource(BedEntity loaded, BigDecimal rent, BigDecimal deposit) {
        BedEntity proposed = new BedEntity();
        proposed.setId(loaded.getId());
        proposed.setRoom(loaded.getRoom());
        proposed.setName(loaded.getName());
        proposed.setBedNumber(loaded.getBedNumber());
        proposed.setDefaultRent(rent);
        proposed.setDefaultDeposit(deposit);
        return proposed;
    }
}
