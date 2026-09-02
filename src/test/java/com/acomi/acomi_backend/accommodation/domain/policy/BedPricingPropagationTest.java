package com.acomi.acomi_backend.accommodation.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BedPricingPropagationTest {

    @Test
    void corridorCopiesSameBedPositionIntoEmptyRooms() {
        BuildingEntity building = corridorBuilding();
        BedEntity source = corridorBed(building, "101", "A", "5000", "10000");
        BedEntity targetA = corridorBed(building, "102", "A", null, null);
        BedEntity targetB = corridorBed(building, "102", "B", null, null);
        BedEntity room103A = corridorBed(building, "103", "A", null, null);

        List<BedEntity> changed =
                BedPricingPropagation.apply(PropertyLayoutMode.CORRIDOR_PG, source, List.of(targetA, targetB, room103A));

        assertThat(changed).containsExactlyInAnyOrder(targetA, room103A);
        assertThat(targetA.getDefaultRent()).isEqualByComparingTo("5000");
        assertThat(targetA.getDefaultDeposit()).isEqualByComparingTo("10000");
        assertThat(room103A.getDefaultRent()).isEqualByComparingTo("5000");
        assertThat(targetB.getDefaultRent()).isNull();
        assertThat(targetB.getDefaultDeposit()).isNull();
    }

    @Test
    void apartmentCopiesSameRoomAndBedAcrossUnits() {
        BuildingEntity building = apartmentBuilding();
        BedEntity source = apartmentBed(building, "U1", "101", "A", "5000", "10000");
        BedEntity unit2A = apartmentBed(building, "U2", "101", "A", null, null);
        BedEntity unit2B = apartmentBed(building, "U2", "101", "B", null, null);
        BedEntity unit2Room102A = apartmentBed(building, "U2", "102", "A", null, null);

        List<BedEntity> changed = BedPricingPropagation.apply(
                PropertyLayoutMode.APARTMENT_PG, source, List.of(unit2A, unit2B, unit2Room102A));

        assertThat(changed).containsExactly(unit2A);
        assertThat(unit2A.getDefaultRent()).isEqualByComparingTo("5000");
        assertThat(unit2A.getDefaultDeposit()).isEqualByComparingTo("10000");
        assertThat(unit2B.getDefaultRent()).isNull();
        assertThat(unit2Room102A.getDefaultRent()).isNull();
    }

    @Test
    void neverOverwritesExistingRent() {
        BuildingEntity building = corridorBuilding();
        BedEntity source = corridorBed(building, "101", "A", "5000", null);
        BedEntity target = corridorBed(building, "102", "A", "6000", null);

        BedPricingPropagation.apply(PropertyLayoutMode.CORRIDOR_PG, source, List.of(target));

        assertThat(target.getDefaultRent()).isEqualByComparingTo("6000");
    }

    @Test
    void fillsEmptyDepositWithoutTouchingExistingRent() {
        BuildingEntity building = corridorBuilding();
        BedEntity source = corridorBed(building, "101", "A", "5000", "10000");
        BedEntity target = corridorBed(building, "102", "A", "6000", null);

        BedPricingPropagation.apply(PropertyLayoutMode.CORRIDOR_PG, source, List.of(target));

        assertThat(target.getDefaultRent()).isEqualByComparingTo("6000");
        assertThat(target.getDefaultDeposit()).isEqualByComparingTo("10000");
    }

    @Test
    void laterSourceChangeDoesNotOverwriteFilledTarget() {
        BuildingEntity building = corridorBuilding();
        BedEntity source = corridorBed(building, "101", "A", "5000", "10000");
        BedEntity target = corridorBed(building, "102", "A", null, null);

        BedPricingPropagation.apply(PropertyLayoutMode.CORRIDOR_PG, source, List.of(target));
        source.setDefaultRent(new BigDecimal("7000"));
        BedPricingPropagation.apply(PropertyLayoutMode.CORRIDOR_PG, source, List.of(target));

        assertThat(target.getDefaultRent()).isEqualByComparingTo("5000");
    }

    @Test
    void clearedTargetCanReceiveFuturePropagation() {
        BuildingEntity building = corridorBuilding();
        BedEntity source = corridorBed(building, "101", "A", "5000", null);
        BedEntity target = corridorBed(building, "102", "A", "6000", null);

        target.setDefaultRent(null);
        BedPricingPropagation.apply(PropertyLayoutMode.CORRIDOR_PG, source, List.of(target));

        assertThat(target.getDefaultRent()).isEqualByComparingTo("5000");
    }

    @Test
    void emptySourceDoesNotClearTargets() {
        BuildingEntity building = corridorBuilding();
        BedEntity source = corridorBed(building, "101", "A", null, null);
        BedEntity target = corridorBed(building, "102", "A", "6000", "12000");

        List<BedEntity> changed = BedPricingPropagation.apply(PropertyLayoutMode.CORRIDOR_PG, source, List.of(target));

        assertThat(changed).isEmpty();
        assertThat(target.getDefaultRent()).isEqualByComparingTo("6000");
        assertThat(target.getDefaultDeposit()).isEqualByComparingTo("12000");
    }

    @Test
    void existingAccommodationWithoutPricesIsUnchanged() {
        BuildingEntity building = corridorBuilding();
        BedEntity source = corridorBed(building, "101", "A", null, null);
        BedEntity other = corridorBed(building, "102", "A", null, null);

        List<BedEntity> changed = BedPricingPropagation.apply(PropertyLayoutMode.CORRIDOR_PG, source, List.of(other));

        assertThat(changed).isEmpty();
        assertThat(other.getDefaultRent()).isNull();
        assertThat(other.getDefaultDeposit()).isNull();
    }

    private static BuildingEntity corridorBuilding() {
        return BuildingEntity.builder().name("B1").layoutMode(PropertyLayoutMode.CORRIDOR_PG).build();
    }

    private static BuildingEntity apartmentBuilding() {
        return BuildingEntity.builder().name("B1").layoutMode(PropertyLayoutMode.APARTMENT_PG).build();
    }

    private static BedEntity corridorBed(
            BuildingEntity building, String roomNumber, String bedNumber, String rent, String deposit) {
        FloorEntity floor = FloorEntity.builder().building(building).name("Floor 1").floorNumber(1).build();
        RoomEntity room = RoomEntity.builder().floor(floor).name("Room " + roomNumber).roomNumber(roomNumber).build();
        return bed(room, bedNumber, rent, deposit);
    }

    private static BedEntity apartmentBed(
            BuildingEntity building, String unitNumber, String roomNumber, String bedNumber, String rent, String deposit) {
        UnitEntity unit = UnitEntity.builder().building(building).name(unitNumber).unitNumber(unitNumber).build();
        RoomEntity room = RoomEntity.builder().unit(unit).name("Room " + roomNumber).roomNumber(roomNumber).build();
        return bed(room, bedNumber, rent, deposit);
    }

    private static BedEntity bed(RoomEntity room, String bedNumber, String rent, String deposit) {
        BedEntity entity = BedEntity.builder()
                .room(room)
                .name("Bed " + bedNumber)
                .bedNumber(bedNumber)
                .defaultRent(rent == null ? null : new BigDecimal(rent))
                .defaultDeposit(deposit == null ? null : new BigDecimal(deposit))
                .build();
        entity.setId(UUID.randomUUID());
        return entity;
    }
}
