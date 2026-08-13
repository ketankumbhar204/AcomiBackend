package com.acomi.acomi_backend.occupancy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.accommodation.domain.model.RoomType;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OccupancyTargetLabelBuilderTest {

    @Mock
    private BuildingRepository buildingRepository;

    private OccupancyTargetLabelBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new OccupancyTargetLabelBuilder(buildingRepository);
    }

    @Test
    void buildsCorridorPgLabelWithBuildingFloorAndRoomBed() {
        SpaceEntity space = space();
        BuildingEntity building = building(space, PropertyLayoutMode.CORRIDOR_PG, "Building 1");
        FloorEntity floor = floor(building, "1");
        UnitEntity syntheticUnit = unit(building, floor, "Internal", true);
        RoomEntity room = room(floor, syntheticUnit, "Shared");
        BedEntity bed = bed(room, "B");

        OccupancyEntity occupancy = OccupancyEntity.builder()
                .space(space)
                .building(building)
                .floor(floor)
                .unit(syntheticUnit)
                .room(room)
                .bed(bed)
                .build();

        when(buildingRepository.findActiveBySpaceId(space.getId()))
                .thenReturn(List.of(building, building(space, PropertyLayoutMode.CORRIDOR_PG, "Building 2")));

        assertThat(builder.build(occupancy)).isEqualTo("Building 1 • Floor 1 • Room B");
    }

    @Test
    void omitsBuildingForSingleBuildingCorridorPgButKeepsFloor() {
        SpaceEntity space = space();
        BuildingEntity building = building(space, PropertyLayoutMode.CORRIDOR_PG, "Building 1");
        FloorEntity floor = floor(building, "1");
        UnitEntity syntheticUnit = unit(building, floor, "Internal", true);
        RoomEntity room = room(floor, syntheticUnit, "Shared");
        BedEntity bed = bed(room, "B");

        OccupancyEntity occupancy = OccupancyEntity.builder()
                .space(space)
                .building(building)
                .floor(floor)
                .unit(syntheticUnit)
                .room(room)
                .bed(bed)
                .build();

        when(buildingRepository.findActiveBySpaceId(space.getId())).thenReturn(List.of(building));

        assertThat(builder.build(occupancy)).isEqualTo("Floor 1 • Room B");
    }

    @Test
    void omitsBuildingAndFloorForSingleBuildingApartmentPg() {
        SpaceEntity space = space();
        BuildingEntity building = building(space, PropertyLayoutMode.APARTMENT_PG, "Building 1");
        FloorEntity floor = floor(building, "1");
        UnitEntity unit = unit(building, floor, "Unit 101", false);
        BedEntity bed = bed(room(floor, unit, "Shared"), "B");

        OccupancyEntity occupancy = OccupancyEntity.builder()
                .space(space)
                .building(building)
                .unit(unit)
                .room(bed.getRoom())
                .bed(bed)
                .build();

        when(buildingRepository.findActiveBySpaceId(space.getId())).thenReturn(List.of(building));

        assertThat(builder.build(occupancy)).isEqualTo("Unit 101 • Room B");
    }

    @Test
    void buildsApartmentPgLabelWithVisibleUnit() {
        SpaceEntity space = space();
        BuildingEntity building = building(space, PropertyLayoutMode.APARTMENT_PG, "Building 1");
        FloorEntity floor = floor(building, "1");
        UnitEntity unit = unit(building, floor, "Unit 101", false);
        BedEntity bed = bed(room(floor, unit, "Master"), "B");

        OccupancyEntity occupancy = OccupancyEntity.builder()
                .space(space)
                .building(building)
                .unit(unit)
                .room(bed.getRoom())
                .bed(bed)
                .build();

        when(buildingRepository.findActiveBySpaceId(space.getId()))
                .thenReturn(List.of(building, building(space, PropertyLayoutMode.APARTMENT_PG, "Building 2")));

        assertThat(builder.build(occupancy)).isEqualTo("Building 1 • Floor 1 • Unit 101 • Room B");
    }

    private SpaceEntity space() {
        SpaceEntity space = SpaceEntity.builder().build();
        space.setId(UUID.randomUUID());
        return space;
    }

    private BuildingEntity building(SpaceEntity space, PropertyLayoutMode layoutMode, String name) {
        BuildingEntity building = BuildingEntity.builder()
                .space(space)
                .layoutMode(layoutMode)
                .name(name)
                .build();
        building.setId(UUID.randomUUID());
        return building;
    }

    private FloorEntity floor(BuildingEntity building, String name) {
        FloorEntity floor = FloorEntity.builder()
                .building(building)
                .name(name)
                .floorNumber(1)
                .build();
        floor.setId(UUID.randomUUID());
        return floor;
    }

    private UnitEntity unit(BuildingEntity building, FloorEntity floor, String name, boolean synthetic) {
        UnitEntity unit = UnitEntity.builder()
                .building(building)
                .floor(floor)
                .name(name)
                .unitNumber(name)
                .synthetic(synthetic)
                .build();
        unit.setId(UUID.randomUUID());
        return unit;
    }

    private RoomEntity room(FloorEntity floor, UnitEntity unit, String name) {
        RoomEntity room = RoomEntity.builder()
                .floor(floor)
                .unit(unit)
                .name(name)
                .roomNumber(name)
                .roomType(RoomType.SHARED)
                .build();
        room.setId(UUID.randomUUID());
        return room;
    }

    private BedEntity bed(RoomEntity room, String name) {
        BedEntity bed = BedEntity.builder()
                .room(room)
                .name(name)
                .bedNumber(name)
                .build();
        bed.setId(UUID.randomUUID());
        return bed;
    }
}
