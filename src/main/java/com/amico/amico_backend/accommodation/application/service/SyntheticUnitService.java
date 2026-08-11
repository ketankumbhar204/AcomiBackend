package com.amico.amico_backend.accommodation.application.service;

import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.accommodation.domain.policy.AccommodationNumberingService;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SyntheticUnitService {

    private final UnitRepository unitRepository;
    private final AccommodationNumberingService numberingService;

    @Transactional
    public UnitEntity createSyntheticUnitForRoom(FloorEntity floor, String roomNumber) {
        UnitEntity unit = UnitEntity.builder()
                .building(floor.getBuilding())
                .floor(floor)
                .name(numberingService.unitDisplayName(roomNumber))
                .unitNumber(roomNumber)
                .status(AccommodationStatus.AVAILABLE)
                .synthetic(true)
                .build();
        return unitRepository.save(unit);
    }
}
