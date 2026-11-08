package com.acomi.acomi_backend.occupancy.application.service;

import com.acomi.acomi_backend.accommodation.application.service.AccommodationAccessService;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.occupancy.api.dto.request.ExtendOccupancyRequest;
import com.acomi.acomi_backend.occupancy.application.support.StayExtensionRules;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyHistoryEvent;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyHistoryEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyHistoryRepository;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.payment.application.service.SpacePaymentTimelineService;
import com.acomi.acomi_backend.payment.application.support.PaymentDueStatusCalculator;
import com.acomi.acomi_backend.payment.domain.model.PaymentTimelineEventType;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OccupancyExtensionService {

    private final OccupancyRepository occupancyRepository;
    private final OccupancyHistoryRepository occupancyHistoryRepository;
    private final OccupancyAccessService occupancyAccessService;
    private final AccommodationAccessService accommodationAccessService;
    private final UserRepository userRepository;
    private final SpacePaymentRepository spacePaymentRepository;
    private final SpacePaymentTimelineService spacePaymentTimelineService;
    private final StayRangeGuard stayRangeGuard;

    public record Result(OccupancyEntity occupancy, UUID chargeId) {}

    @Transactional
    public Result extend(UUID spaceId, UUID occupancyId, UUID callerId, ExtendOccupancyRequest request) {
        occupancyAccessService.assertCanManageOccupancy(spaceId, callerId);
        SpaceEntity space = accommodationAccessService.loadAccommodationSpace(spaceId);
        UserEntity actor = userRepository.findById(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", callerId));

        OccupancyEntity occupancy = occupancyRepository.findByIdAndSpaceIdForUpdate(occupancyId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Occupancy", "id", occupancyId));
        occupancyAccessService.assertSubjectIsResident(occupancy.getMember());

        String idempotencyKey = request.getIdempotencyKey() == null ? "" : request.getIdempotencyKey().trim();
        if (idempotencyKey.isEmpty() || idempotencyKey.length() > 64) {
            throw new BusinessException("EXTENSION_KEY", "An idempotency key is required.");
        }

        OccupancyHistoryEntity replay = occupancyHistoryRepository
                .findByOccupancy_IdAndIdempotencyKey(occupancyId, idempotencyKey)
                .orElse(null);
        if (replay != null) {
            return new Result(occupancy, replay.getExtensionChargeId());
        }

        if (occupancy.getStatus() != OccupancyStatus.ACTIVE || occupancy.getVacatedAt() != null) {
            throw new BusinessException("EXTENSION_NOT_ACTIVE", StayExtensionRules.ACTIVE_ONLY);
        }

        LocalDate businessDate = PaymentDueStatusCalculator.businessDate(space);
        LocalDate start = occupancy.getMoveInDate();
        boolean shortStay = StayExtensionRules.isShortStay(occupancy.getStayKind());
        boolean openEndedRentOnly = isOpenEndedRentOnlyRevision(occupancy, request);

        if (openEndedRentOnly) {
            return reviseOpenEndedDailyRent(occupancy, space, actor, request, businessDate, start, idempotencyKey);
        }

        LocalDate currentEnd = occupancy.getExpectedExitDate();
        LocalDate newEnd = request.getExpectedExitDate();
        if (newEnd == null) {
            throw new BusinessException("EXTENSION_DATE_INVALID", StayExtensionRules.END_REQUIRED);
        }
        StayExtensionRules.validateNewEnd(start, currentEnd, newEnd, businessDate);

        BigDecimal additionalAmount = request.getAdditionalAmount();
        if (additionalAmount != null && additionalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("EXTENSION_AMOUNT", StayExtensionRules.AMOUNT_NEGATIVE);
        }
        if (!shortStay && StayExtensionRules.hasCharge(additionalAmount)) {
            throw new BusinessException("EXTENSION_AMOUNT", StayExtensionRules.AMOUNT_NOT_FOR_MONTHLY);
        }
        if (shortStay && currentEnd == null) {
            throw new BusinessException("EXTENSION_DATE_INVALID", StayExtensionRules.SHORT_NEEDS_CURRENT_END);
        }
        if (shortStay && StayExtensionRules.crossesCalendarMonth(start, newEnd)) {
            throw new BusinessException("EXTENSION_CROSSES_MONTH", StayExtensionRules.CROSSES_MONTH);
        }

        stayRangeGuard.assertAvailable(
                occupancy.getTargetType(),
                occupancy.getBed() != null ? occupancy.getBed().getId() : null,
                occupancy.getRoom() != null ? occupancy.getRoom().getId() : null,
                occupancy.getUnit() != null ? occupancy.getUnit().getId() : null,
                start,
                newEnd,
                occupancy.getId());

        applyRentChange(occupancy, request, shortStay, start, businessDate, false);

        occupancy.setExpectedExitDate(newEnd);
        occupancy.setExpectedCheckoutDate(newEnd);
        occupancyRepository.save(occupancy);

        UUID chargeId = null;
        if (shortStay && StayExtensionRules.hasCharge(additionalAmount)) {
            SpacePaymentEntity charge = saveExtensionCharge(space, occupancy, currentEnd, newEnd, additionalAmount);
            chargeId = charge.getId();
        }

        saveHistory(occupancy, space, actor, idempotencyKey, chargeId, currentEnd, newEnd, additionalAmount, null);
        return new Result(occupancy, chargeId);
    }

    private static boolean isOpenEndedRentOnlyRevision(OccupancyEntity occupancy, ExtendOccupancyRequest request) {
        return occupancy.getStayKind() == StayKind.OPEN_ENDED
                && request.getExpectedExitDate() == null
                && (request.getRentSnapshot() != null || request.getRentEffectiveMonth() != null);
    }

    private Result reviseOpenEndedDailyRent(
            OccupancyEntity occupancy,
            SpaceEntity space,
            UserEntity actor,
            ExtendOccupancyRequest request,
            LocalDate businessDate,
            LocalDate start,
            String idempotencyKey) {
        if (occupancy.getStayKind() != StayKind.OPEN_ENDED) {
            throw new BusinessException("EXTENSION_RENT", StayExtensionRules.RENT_ONLY_OPEN_ENDED);
        }
        if (request.getAdditionalAmount() != null
                && request.getAdditionalAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("EXTENSION_AMOUNT", StayExtensionRules.AMOUNT_NOT_FOR_MONTHLY);
        }
        if (occupancy.getRentSnapshot() == null) {
            throw new BusinessException("EXTENSION_RENT", StayExtensionRules.CURRENT_RENT_MISSING);
        }
        BigDecimal newRent = request.getRentSnapshot();
        LocalDate effective = request.getRentEffectiveMonth();
        if (newRent == null || effective == null) {
            throw new BusinessException("EXTENSION_RENT", StayExtensionRules.RENT_FIELDS);
        }
        if (newRent.compareTo(occupancy.getRentSnapshot()) == 0) {
            throw new BusinessException("EXTENSION_RENT", StayExtensionRules.RENT_UNCHANGED);
        }

        LocalDate currentEnd = occupancy.getExpectedExitDate();
        applyRentChange(occupancy, request, false, start, businessDate, true);
        occupancyRepository.save(occupancy);

        String reason = request.getReason() == null ? null : request.getReason().trim();
        saveHistory(
                occupancy,
                space,
                actor,
                idempotencyKey,
                null,
                currentEnd,
                currentEnd,
                null,
                reason == null || reason.isEmpty()
                        ? "open-ended daily rent "
                                + occupancy.getPriorRentSnapshot()
                                + " -> "
                                + occupancy.getRentSnapshot()
                                + " from "
                                + occupancy.getRentEffectiveMonth()
                        : "open-ended daily rent revision: " + reason);
        return new Result(occupancy, null);
    }

    private void applyRentChange(
            OccupancyEntity occupancy,
            ExtendOccupancyRequest request,
            boolean shortStay,
            LocalDate start,
            LocalDate businessDate,
            boolean openEndedDayLevel) {
        BigDecimal newRent = request.getRentSnapshot();
        LocalDate effective = request.getRentEffectiveMonth();
        if (newRent == null && effective == null) {
            return;
        }
        if (newRent == null || effective == null) {
            throw new BusinessException("EXTENSION_RENT", StayExtensionRules.RENT_FIELDS);
        }
        if (shortStay || occupancy.getStayKind() == StayKind.SHORT) {
            throw new BusinessException("EXTENSION_RENT", StayExtensionRules.RENT_NOT_FOR_SHORT);
        }
        if (newRent.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("EXTENSION_RENT", StayExtensionRules.RENT_NEGATIVE);
        }

        if (openEndedDayLevel || occupancy.getStayKind() == StayKind.OPEN_ENDED) {
            if (start != null && effective.isBefore(start)) {
                throw new BusinessException("EXTENSION_RENT", StayExtensionRules.RENT_EFFECTIVE_BEFORE_START);
            }
            if (businessDate != null && effective.isBefore(businessDate)) {
                throw new BusinessException("EXTENSION_RENT", StayExtensionRules.RENT_EFFECTIVE_BEFORE_BUSINESS);
            }
            occupancy.setPriorRentSnapshot(occupancy.getRentSnapshot());
            occupancy.setRentSnapshot(newRent);
            occupancy.setRentEffectiveMonth(effective);
            return;
        }

        YearMonth effectiveMonth = YearMonth.from(effective);
        if (start != null && effectiveMonth.isBefore(YearMonth.from(start))) {
            throw new BusinessException("EXTENSION_RENT", StayExtensionRules.RENT_MONTH_BEFORE_START);
        }
        occupancy.setPriorRentSnapshot(occupancy.getRentSnapshot());
        occupancy.setRentSnapshot(newRent);
        occupancy.setRentEffectiveMonth(effectiveMonth.atDay(1));
    }

    private SpacePaymentEntity saveExtensionCharge(
            SpaceEntity space,
            OccupancyEntity occupancy,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal amount) {
        int nights = StayExtensionRules.nights(periodStart, periodEnd);
        YearMonth month = YearMonth.from(periodStart);
        SpacePaymentEntity charge = SpacePaymentEntity.builder()
                .space(space)
                .member(occupancy.getMember())
                .occupancy(occupancy)
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.EXTRA)
                .title("Stay extension")
                .amount(amount)
                .currencyCode("INR")
                .dueDate(periodStart)
                .month(month.toString())
                .paymentStatus(SpacePaymentStatus.PENDING)
                .billingPeriodStart(periodStart)
                .billingPeriodEnd(periodEnd)
                .billableDays(nights)
                .daysInMonth(month.lengthOfMonth())
                .prorated(false)
                .taxEnabled(false)
                .baseAmount(amount)
                .taxAmount(BigDecimal.ZERO)
                .remarks("Added nights " + periodStart + " through " + periodEnd + " (checkout excluded)")
                .build();
        charge = spacePaymentRepository.save(charge);
        spacePaymentTimelineService.record(
                charge, PaymentTimelineEventType.CREATED, "Stay extension", null);
        return charge;
    }

    private void saveHistory(
            OccupancyEntity occupancy,
            SpaceEntity space,
            UserEntity actor,
            String idempotencyKey,
            UUID chargeId,
            LocalDate currentEnd,
            LocalDate newEnd,
            BigDecimal additionalAmount,
            String remarksOverride) {
        String amountNote = StayExtensionRules.hasCharge(additionalAmount)
                ? additionalAmount.toPlainString()
                : "none";
        String remarks = remarksOverride != null
                ? remarksOverride
                : "planned end " + currentEnd + " -> " + newEnd + "; amount " + amountNote;
        OccupancyHistoryEntity history = OccupancyHistoryEntity.builder()
                .occupancy(occupancy)
                .space(space)
                .member(occupancy.getMember())
                .eventType(OccupancyHistoryEvent.EXTENDED)
                .fromTargetType(occupancy.getTargetType())
                .fromBuildingId(occupancy.getBuilding() != null ? occupancy.getBuilding().getId() : null)
                .fromFloorId(occupancy.getFloor() != null ? occupancy.getFloor().getId() : null)
                .fromUnitId(occupancy.getUnit() != null ? occupancy.getUnit().getId() : null)
                .fromRoomId(occupancy.getRoom() != null ? occupancy.getRoom().getId() : null)
                .fromBedId(occupancy.getBed() != null ? occupancy.getBed().getId() : null)
                .toTargetType(occupancy.getTargetType())
                .toBuildingId(occupancy.getBuilding() != null ? occupancy.getBuilding().getId() : null)
                .toFloorId(occupancy.getFloor() != null ? occupancy.getFloor().getId() : null)
                .toUnitId(occupancy.getUnit() != null ? occupancy.getUnit().getId() : null)
                .toRoomId(occupancy.getRoom() != null ? occupancy.getRoom().getId() : null)
                .toBedId(occupancy.getBed() != null ? occupancy.getBed().getId() : null)
                .performedBy(actor)
                .performedAt(LocalDateTime.now())
                .idempotencyKey(idempotencyKey)
                .extensionChargeId(chargeId)
                .remarks(remarks)
                .build();
        occupancyHistoryRepository.save(history);
    }
}
