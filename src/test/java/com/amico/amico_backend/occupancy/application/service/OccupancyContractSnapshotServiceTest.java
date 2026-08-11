package com.amico.amico_backend.occupancy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.occupancy.domain.model.AllocationTargetType;
import com.amico.amico_backend.occupancy.domain.model.TransferRentPolicy;
import com.amico.amico_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.amico.amico_backend.occupancy.infrastructure.persistence.repository.OccupancyChargeSnapshotRepository;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OccupancyContractSnapshotServiceTest {

    @Mock
    private OccupancyPricingCatalogService pricingCatalogService;

    @Mock
    private OccupancyChargeSnapshotRepository chargeSnapshotRepository;

    @InjectMocks
    private OccupancyContractSnapshotService contractSnapshotService;

    private SpaceEntity space;
    private OccupancyTargetService.ResolvedTarget target;
    private OccupancyEntity occupancy;

    @BeforeEach
    void setUp() {
        space = SpaceEntity.builder().defaultFoodCharge(new BigDecimal("2500")).build();
        space.setId(java.util.UUID.randomUUID());

        BuildingEntity building = BuildingEntity.builder().space(space).name("A").code("A").build();
        building.setId(java.util.UUID.randomUUID());
        RoomEntity room = RoomEntity.builder().name("101").roomNumber("101").build();
        room.setId(java.util.UUID.randomUUID());
        BedEntity bed = BedEntity.builder()
                .room(room)
                .name("Bed A")
                .bedNumber("A")
                .defaultRent(new BigDecimal("8000"))
                .defaultDeposit(new BigDecimal("10000"))
                .build();
        bed.setId(java.util.UUID.randomUUID());

        target = OccupancyTargetService.ResolvedTarget.builder()
                .targetType(AllocationTargetType.BED)
                .building(building)
                .room(room)
                .bed(bed)
                .build();

        occupancy = OccupancyEntity.builder().space(space).build();
        occupancy.setId(java.util.UUID.randomUUID());

        when(pricingCatalogService.resolve(target))
                .thenReturn(OccupancyPricingCatalogService.CatalogDefaults.builder()
                        .defaultRent(new BigDecimal("8000"))
                        .defaultDeposit(new BigDecimal("10000"))
                        .build());
        lenient().when(chargeSnapshotRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void applyActivationSnapshot_prefillsFromCatalog() {
        contractSnapshotService.applyActivationSnapshot(
                occupancy,
                OccupancyContractSnapshotService.ContractSnapshotInput.builder().build(),
                target,
                space,
                LocalDateTime.now());

        assertThat(occupancy.getRentSnapshot()).isEqualByComparingTo("8000");
        assertThat(occupancy.getDepositSnapshot()).isEqualByComparingTo("10000");
        assertThat(occupancy.isFoodEnabled()).isTrue();
        assertThat(occupancy.getFoodChargeSnapshot()).isEqualByComparingTo("2500");
        assertThat(occupancy.isFoodIncludedInRent()).isFalse();
        assertThat(occupancy.getPricingLockedAt()).isNotNull();
    }

    @Test
    void applyActivationSnapshot_requiresRentWhenCatalogMissing() {
        when(pricingCatalogService.resolve(target))
                .thenReturn(OccupancyPricingCatalogService.CatalogDefaults.empty());

        assertThatThrownBy(() -> contractSnapshotService.applyActivationSnapshot(
                        occupancy,
                        OccupancyContractSnapshotService.ContractSnapshotInput.builder().build(),
                        target,
                        space,
                        LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo("RENT_SNAPSHOT_REQUIRED");
                    assertThat(businessException.getMessage()).contains("Rent is required");
                });
    }

    @Test
    void applyActivationSnapshot_defaultsDepositToZero() {
        when(pricingCatalogService.resolve(target))
                .thenReturn(OccupancyPricingCatalogService.CatalogDefaults.builder()
                        .defaultRent(new BigDecimal("5000"))
                        .build());

        contractSnapshotService.applyActivationSnapshot(
                occupancy,
                OccupancyContractSnapshotService.ContractSnapshotInput.builder()
                        .foodEnabled(false)
                        .build(),
                target,
                space,
                LocalDateTime.now());

        assertThat(occupancy.getDepositSnapshot()).isEqualByComparingTo("0");
        assertThat(occupancy.isFoodEnabled()).isFalse();
        assertThat(occupancy.getFoodChargeSnapshot()).isNull();
    }

    @Test
    void applyActivationSnapshot_allowsFoodEnabledWithoutDefaultUsingZero() {
        SpaceEntity spaceWithoutFoodDefault = SpaceEntity.builder().build();
        spaceWithoutFoodDefault.setId(java.util.UUID.randomUUID());

        contractSnapshotService.applyActivationSnapshot(
                occupancy,
                OccupancyContractSnapshotService.ContractSnapshotInput.builder()
                        .rentSnapshot(new BigDecimal("8000"))
                        .foodEnabled(true)
                        .build(),
                target,
                spaceWithoutFoodDefault,
                LocalDateTime.now());

        assertThat(occupancy.isFoodEnabled()).isTrue();
        assertThat(occupancy.getFoodChargeSnapshot()).isEqualByComparingTo("0");
        assertThat(occupancy.isFoodIncludedInRent()).isFalse();
    }

    @Test
    void applyActivationSnapshot_requestFoodIncludedInRent_succeedsWithoutSpaceFlag() {
        SpaceEntity separateFoodSpace = SpaceEntity.builder().foodIncludedInRent(false).build();
        separateFoodSpace.setId(java.util.UUID.randomUUID());

        contractSnapshotService.applyActivationSnapshot(
                occupancy,
                OccupancyContractSnapshotService.ContractSnapshotInput.builder()
                        .rentSnapshot(new BigDecimal("7000"))
                        .foodEnabled(true)
                        .foodIncludedInRent(true)
                        .build(),
                target,
                separateFoodSpace,
                LocalDateTime.now());

        assertThat(occupancy.isFoodEnabled()).isTrue();
        assertThat(occupancy.getFoodChargeSnapshot()).isNull();
        assertThat(occupancy.isFoodIncludedInRent()).isTrue();
    }

    @Test
    void applyActivationSnapshot_prefillsFoodFromSpaceDefault() {
        contractSnapshotService.applyActivationSnapshot(
                occupancy,
                OccupancyContractSnapshotService.ContractSnapshotInput.builder()
                        .rentSnapshot(new BigDecimal("8000"))
                        .foodEnabled(true)
                        .build(),
                target,
                space,
                LocalDateTime.now());

        assertThat(occupancy.isFoodEnabled()).isTrue();
        assertThat(occupancy.getFoodChargeSnapshot()).isEqualByComparingTo("2500");
        assertThat(occupancy.isFoodIncludedInRent()).isFalse();
    }

    @Test
    void applyActivationSnapshot_foodIncludedInRent_succeedsWithoutFoodCharge() {
        SpaceEntity bundledFoodSpace = SpaceEntity.builder().foodIncludedInRent(true).build();
        bundledFoodSpace.setId(java.util.UUID.randomUUID());

        contractSnapshotService.applyActivationSnapshot(
                occupancy,
                OccupancyContractSnapshotService.ContractSnapshotInput.builder()
                        .rentSnapshot(new BigDecimal("12000"))
                        .build(),
                target,
                bundledFoodSpace,
                LocalDateTime.now());

        assertThat(occupancy.isFoodEnabled()).isTrue();
        assertThat(occupancy.getFoodChargeSnapshot()).isNull();
        assertThat(occupancy.isFoodIncludedInRent()).isTrue();
    }

    @Test
    void applyActivationSnapshot_foodIncludedInRent_rejectsFoodCharge() {
        SpaceEntity bundledFoodSpace = SpaceEntity.builder().foodIncludedInRent(true).build();
        bundledFoodSpace.setId(java.util.UUID.randomUUID());

        assertThatThrownBy(() -> contractSnapshotService.applyActivationSnapshot(
                        occupancy,
                        OccupancyContractSnapshotService.ContractSnapshotInput.builder()
                                .rentSnapshot(new BigDecimal("12000"))
                                .foodChargeSnapshot(new BigDecimal("2500"))
                                .build(),
                        target,
                        bundledFoodSpace,
                        LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo("FOOD_CHARGE_NOT_ALLOWED");
                });
    }

    @Test
    void applyActivationSnapshot_foodDisabled_setsNullFoodCharge() {
        contractSnapshotService.applyActivationSnapshot(
                occupancy,
                OccupancyContractSnapshotService.ContractSnapshotInput.builder()
                        .rentSnapshot(new BigDecimal("8000"))
                        .foodEnabled(false)
                        .build(),
                target,
                space,
                LocalDateTime.now());

        assertThat(occupancy.isFoodEnabled()).isFalse();
        assertThat(occupancy.getFoodChargeSnapshot()).isNull();
        assertThat(occupancy.isFoodIncludedInRent()).isFalse();
    }

    @Test
    void applyTransferSnapshot_keep_copiesFoodIncludedInRent() {
        OccupancyEntity previous = OccupancyEntity.builder()
                .rentSnapshot(new BigDecimal("9000"))
                .depositSnapshot(new BigDecimal("5000"))
                .foodEnabled(true)
                .foodIncludedInRent(true)
                .build();
        previous.setId(java.util.UUID.randomUUID());

        OccupancyEntity next = OccupancyEntity.builder().space(space).build();
        next.setId(java.util.UUID.randomUUID());

        contractSnapshotService.applyTransferSnapshot(
                next,
                OccupancyContractSnapshotService.ContractSnapshotInput.builder().build(),
                TransferRentPolicy.KEEP,
                previous,
                target,
                space,
                LocalDateTime.now());

        assertThat(next.getRentSnapshot()).isEqualByComparingTo("9000");
        assertThat(next.isFoodEnabled()).isTrue();
        assertThat(next.getFoodChargeSnapshot()).isNull();
        assertThat(next.isFoodIncludedInRent()).isTrue();
    }

    @Test
    void applyTransferSnapshot_applyNew_usesBundledFoodPolicy() {
        SpaceEntity bundledFoodSpace = SpaceEntity.builder().foodIncludedInRent(true).build();
        bundledFoodSpace.setId(java.util.UUID.randomUUID());

        OccupancyEntity previous = OccupancyEntity.builder()
                .rentSnapshot(new BigDecimal("9000"))
                .depositSnapshot(BigDecimal.ZERO)
                .foodEnabled(true)
                .foodChargeSnapshot(new BigDecimal("2000"))
                .foodIncludedInRent(false)
                .build();
        previous.setId(java.util.UUID.randomUUID());

        OccupancyEntity next = OccupancyEntity.builder().space(bundledFoodSpace).build();
        next.setId(java.util.UUID.randomUUID());

        contractSnapshotService.applyTransferSnapshot(
                next,
                OccupancyContractSnapshotService.ContractSnapshotInput.builder()
                        .rentSnapshot(new BigDecimal("10000"))
                        .build(),
                TransferRentPolicy.APPLY_NEW,
                previous,
                target,
                bundledFoodSpace,
                LocalDateTime.now());

        assertThat(next.isFoodEnabled()).isTrue();
        assertThat(next.getFoodChargeSnapshot()).isNull();
        assertThat(next.isFoodIncludedInRent()).isTrue();
    }
}

