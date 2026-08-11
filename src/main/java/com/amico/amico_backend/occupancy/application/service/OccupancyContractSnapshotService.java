package com.amico.amico_backend.occupancy.application.service;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.occupancy.api.dto.request.OccupancyChargeLineRequest;
import com.amico.amico_backend.occupancy.domain.model.TransferRentPolicy;
import com.amico.amico_backend.occupancy.infrastructure.persistence.entity.OccupancyChargeSnapshotEntity;
import com.amico.amico_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.amico.amico_backend.occupancy.infrastructure.persistence.repository.OccupancyChargeSnapshotRepository;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OccupancyContractSnapshotService {

    private static final int MAX_OTHER_CHARGES = 10;

    private final OccupancyPricingCatalogService pricingCatalogService;
    private final OccupancyChargeSnapshotRepository chargeSnapshotRepository;

    public void applyActivationSnapshot(
            OccupancyEntity occupancy,
            ContractSnapshotInput input,
            OccupancyTargetService.ResolvedTarget target,
            SpaceEntity space,
            LocalDateTime lockedAt) {
        ResolvedContractSnapshot snapshot = resolveActivationSnapshot(input, target, space);
        persistSnapshot(occupancy, snapshot, lockedAt);
    }

    public void applyTransferSnapshot(
            OccupancyEntity newOccupancy,
            ContractSnapshotInput input,
            TransferRentPolicy rentPolicy,
            OccupancyEntity previousOccupancy,
            OccupancyTargetService.ResolvedTarget newTarget,
            SpaceEntity space,
            LocalDateTime lockedAt) {
        TransferRentPolicy policy = rentPolicy != null ? rentPolicy : TransferRentPolicy.APPLY_NEW;
        ResolvedContractSnapshot snapshot =
                resolveTransferSnapshot(input, policy, previousOccupancy, newTarget, space);
        persistSnapshot(newOccupancy, snapshot, lockedAt);
    }

    public List<OccupancyChargeSnapshotEntity> loadChargeSnapshots(OccupancyEntity occupancy) {
        return chargeSnapshotRepository.findAllByOccupancyIdOrderByCreatedAtAsc(occupancy.getId());
    }

    private ResolvedContractSnapshot resolveActivationSnapshot(
            ContractSnapshotInput input, OccupancyTargetService.ResolvedTarget target, SpaceEntity space) {
        OccupancyPricingCatalogService.CatalogDefaults catalog = pricingCatalogService.resolve(target);
        BigDecimal rent = firstNonNull(input.getRentSnapshot(), catalog.getDefaultRent());
        if (rent == null) {
            throw new BusinessException("RENT_SNAPSHOT_REQUIRED", "Rent is required when activating occupancy");
        }
        BigDecimal deposit = firstNonNull(input.getDepositSnapshot(), catalog.getDefaultDeposit(), BigDecimal.ZERO);
        return resolveFoodAndCharges(input, space, rent, deposit);
    }

    private ResolvedContractSnapshot resolveTransferSnapshot(
            ContractSnapshotInput input,
            TransferRentPolicy policy,
            OccupancyEntity previous,
            OccupancyTargetService.ResolvedTarget newTarget,
            SpaceEntity space) {
        OccupancyPricingCatalogService.CatalogDefaults catalog = pricingCatalogService.resolve(newTarget);

        BigDecimal rent = switch (policy) {
            case KEEP -> previous.getRentSnapshot();
            case APPLY_NEW -> firstNonNull(input.getRentSnapshot(), catalog.getDefaultRent());
            case CUSTOM -> input.getRentSnapshot();
        };
        if (rent == null) {
            throw new BusinessException(switch (policy) {
                case KEEP -> null;
                case APPLY_NEW -> "RENT_SNAPSHOT_REQUIRED";
                case CUSTOM -> "RENT_SNAPSHOT_REQUIRED";
            }, switch (policy) {
                case KEEP -> "Previous occupancy has no rent snapshot to keep";
                case APPLY_NEW -> "Rent is required when transferring occupancy";
                case CUSTOM -> "Custom rent is required when rentPolicy is CUSTOM";
            });
        }

        BigDecimal deposit = switch (policy) {
            case KEEP -> previous.getDepositSnapshot() != null ? previous.getDepositSnapshot() : BigDecimal.ZERO;
            case APPLY_NEW, CUSTOM -> firstNonNull(input.getDepositSnapshot(), catalog.getDefaultDeposit(), BigDecimal.ZERO);
        };

        if (policy == TransferRentPolicy.KEEP && input.getFoodEnabled() == null) {
            return ResolvedContractSnapshot.builder()
                    .rentSnapshot(rent)
                    .depositSnapshot(deposit)
                    .foodEnabled(previous.isFoodEnabled())
                    .foodChargeSnapshot(previous.getFoodChargeSnapshot())
                    .foodIncludedInRent(previous.isFoodIncludedInRent())
                    .otherCharges(copyChargeLines(previous))
                    .build();
        }

        return resolveFoodAndCharges(input, space, rent, deposit);
    }

    private ResolvedContractSnapshot resolveFoodAndCharges(
            ContractSnapshotInput input, SpaceEntity space, BigDecimal rent, BigDecimal deposit) {
        List<OccupancyChargeLineRequest> otherCharges = input.getOtherCharges() != null
                ? input.getOtherCharges()
                : List.of();
        validateOtherCharges(otherCharges);

        if (isBundledFood(input, space)) {
            if (input.getFoodChargeSnapshot() != null) {
                throw new BusinessException(
                        "FOOD_CHARGE_NOT_ALLOWED",
                        "Food charge is not allowed when food is included in rent");
            }
            boolean foodEnabled = !Boolean.FALSE.equals(input.getFoodEnabled());
            return ResolvedContractSnapshot.builder()
                    .rentSnapshot(rent)
                    .depositSnapshot(deposit)
                    .foodEnabled(foodEnabled)
                    .foodChargeSnapshot(null)
                    .foodIncludedInRent(foodEnabled)
                    .otherCharges(otherCharges)
                    .build();
        }

        boolean foodEnabled = resolveFoodEnabled(input, space);
        BigDecimal foodCharge = null;
        if (foodEnabled) {
            foodCharge = firstNonNull(input.getFoodChargeSnapshot(), space.getDefaultFoodCharge(), BigDecimal.ZERO);
        }

        return ResolvedContractSnapshot.builder()
                .rentSnapshot(rent)
                .depositSnapshot(deposit)
                .foodEnabled(foodEnabled)
                .foodChargeSnapshot(foodCharge)
                .foodIncludedInRent(false)
                .otherCharges(otherCharges)
                .build();
    }

    private boolean isBundledFood(ContractSnapshotInput input, SpaceEntity space) {
        return space.isFoodIncludedInRent() || Boolean.TRUE.equals(input.getFoodIncludedInRent());
    }

    private boolean resolveFoodEnabled(ContractSnapshotInput input, SpaceEntity space) {
        if (input.getFoodEnabled() != null) {
            return Boolean.TRUE.equals(input.getFoodEnabled());
        }
        return space.getDefaultFoodCharge() != null;
    }

    private List<OccupancyChargeLineRequest> copyChargeLines(OccupancyEntity previous) {
        return chargeSnapshotRepository.findAllByOccupancyIdOrderByCreatedAtAsc(previous.getId()).stream()
                .map(line -> {
                    OccupancyChargeLineRequest request = new OccupancyChargeLineRequest();
                    request.setCode(line.getChargeCode());
                    request.setLabel(line.getLabel());
                    request.setAmount(line.getAmount());
                    return request;
                })
                .toList();
    }

    private void persistSnapshot(OccupancyEntity occupancy, ResolvedContractSnapshot snapshot, LocalDateTime lockedAt) {
        validateAmount(snapshot.getRentSnapshot(), "Rent");
        validateAmount(snapshot.getDepositSnapshot(), "Deposit");
        if (snapshot.isFoodEnabled() && !snapshot.isFoodIncludedInRent()) {
            validateAmount(snapshot.getFoodChargeSnapshot(), "Food charge");
        }

        occupancy.setRentSnapshot(snapshot.getRentSnapshot());
        occupancy.setDepositSnapshot(snapshot.getDepositSnapshot());
        occupancy.setFoodEnabled(snapshot.isFoodEnabled());
        occupancy.setFoodChargeSnapshot(snapshot.getFoodChargeSnapshot());
        occupancy.setFoodIncludedInRent(snapshot.isFoodIncludedInRent());
        occupancy.setPricingLockedAt(lockedAt);

        chargeSnapshotRepository.saveAll(snapshot.getOtherCharges().stream()
                .map(line -> OccupancyChargeSnapshotEntity.builder()
                        .occupancy(occupancy)
                        .chargeCode(line.getCode())
                        .label(line.getLabel())
                        .amount(line.getAmount())
                        .build())
                .toList());
    }

    private void validateOtherCharges(List<OccupancyChargeLineRequest> lines) {
        if (lines.size() > MAX_OTHER_CHARGES) {
            throw new BusinessException("At most " + MAX_OTHER_CHARGES + " additional charges are allowed");
        }
        for (OccupancyChargeLineRequest line : lines) {
            validateAmount(line.getAmount(), line.getLabel());
        }
    }

    private void validateAmount(BigDecimal amount, String label) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(label + " must be zero or greater");
        }
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Getter
    @Builder
    public static class ContractSnapshotInput {
        private BigDecimal rentSnapshot;
        private BigDecimal depositSnapshot;
        private Boolean foodEnabled;
        private BigDecimal foodChargeSnapshot;
        private Boolean foodIncludedInRent;
        @Builder.Default
        private List<OccupancyChargeLineRequest> otherCharges = new ArrayList<>();
    }

    @Getter
    @Builder
    private static class ResolvedContractSnapshot {
        private BigDecimal rentSnapshot;
        private BigDecimal depositSnapshot;
        private boolean foodEnabled;
        private BigDecimal foodChargeSnapshot;
        private boolean foodIncludedInRent;
        @Builder.Default
        private List<OccupancyChargeLineRequest> otherCharges = List.of();
    }
}

