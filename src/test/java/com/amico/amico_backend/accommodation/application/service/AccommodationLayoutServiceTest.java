package com.amico.amico_backend.accommodation.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amico.amico_backend.accommodation.domain.model.PropertyLayoutMode;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.amico.amico_backend.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccommodationLayoutServiceTest {

    private AccommodationLayoutService layoutService;
    private BuildingEntity corridorBuilding;
    private BuildingEntity apartmentBuilding;
    private FloorEntity floor;
    private UnitEntity visibleUnit;
    private UnitEntity syntheticUnit;

    @BeforeEach
    void setUp() {
        layoutService = new AccommodationLayoutService();

        corridorBuilding = BuildingEntity.builder()
                .name("Corridor PG")
                .layoutMode(PropertyLayoutMode.CORRIDOR_PG)
                .build();
        apartmentBuilding = BuildingEntity.builder()
                .name("Apartment PG")
                .layoutMode(PropertyLayoutMode.APARTMENT_PG)
                .build();

        floor = FloorEntity.builder()
                .building(apartmentBuilding)
                .name("Floor 1")
                .floorNumber(1)
                .sortOrder(1)
                .build();

        visibleUnit = UnitEntity.builder()
                .building(apartmentBuilding)
                .floor(floor)
                .name("Flat 101")
                .unitNumber("101")
                .synthetic(false)
                .build();

        syntheticUnit = UnitEntity.builder()
                .building(corridorBuilding)
                .floor(floor)
                .name("Unit 101")
                .unitNumber("101")
                .synthetic(true)
                .build();
    }

    @Test
    void corridorPg_rejectsVisibleApartmentCreation() {
        assertThatThrownBy(() -> layoutService.assertVisibleUnitCreationAllowed(corridorBuilding))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("corridor PG");
    }

    @Test
    void apartmentPg_rejectsRoomDirectlyUnderFloor() {
        assertThatThrownBy(() -> layoutService.assertRoomCreationUnderFloor(apartmentBuilding))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("apartment PG");
    }

    @Test
    void apartmentPg_allowsRoomUnderVisibleUnit() {
        assertThatCode(() -> layoutService.assertRoomCreationUnderUnit(apartmentBuilding, visibleUnit))
                .doesNotThrowAnyException();
    }

    @Test
    void corridorPg_allowsRoomUnderSyntheticUnit() {
        assertThatCode(() -> layoutService.assertRoomCreationUnderUnit(corridorBuilding, syntheticUnit))
                .doesNotThrowAnyException();
    }

    @Test
    void corridorPg_rejectsRoomUnderVisibleUnit() {
        UnitEntity visibleOnCorridor = UnitEntity.builder()
                .building(corridorBuilding)
                .floor(floor)
                .name("Flat 101")
                .unitNumber("101")
                .synthetic(false)
                .build();

        assertThatThrownBy(() -> layoutService.assertRoomCreationUnderUnit(corridorBuilding, visibleOnCorridor))
                .isInstanceOf(BusinessException.class);
    }
}
