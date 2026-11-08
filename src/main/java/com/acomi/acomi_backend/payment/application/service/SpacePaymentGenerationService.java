package com.acomi.acomi_backend.payment.application.service;

import com.acomi.acomi_backend.dashboard.application.support.MealLedgerContribution;
import com.acomi.acomi_backend.dashboard.application.support.OccupancyBillingCalculator;
import com.acomi.acomi_backend.dashboard.application.support.PayPerMealBillingCalculator;
import com.acomi.acomi_backend.meal.application.support.MealBillingResolver;
import com.acomi.acomi_backend.meal.application.support.MealPricingPolicy;
import com.acomi.acomi_backend.meal.domain.model.MealParticipationStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealParticipationRepository;
import com.acomi.acomi_backend.occupancy.application.service.OccupancyTargetLabelBuilder;
import com.acomi.acomi_backend.occupancy.application.support.ShortStayRules;
import com.acomi.acomi_backend.occupancy.application.support.StayExtensionRules;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.payment.application.support.BillingAmountCalculator;
import com.acomi.acomi_backend.payment.application.support.BillingAmountCalculator.BillingAmountResult;
import com.acomi.acomi_backend.payment.application.support.BillingAmountCalculator.TaxInput;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.payment.domain.model.PaymentTimelineEventType;
import org.springframework.dao.DataIntegrityViolationException;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentMethod;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.space.domain.model.MealBillingType;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpacePaymentGenerationService {

    private static final String DEFAULT_CURRENCY = "INR";
    private static final DateTimeFormatter MONTH_TITLE =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    /**
     * Stored on rows this scheduler inserts. Existing move-in and owner-sync rows keep
     * their own remarks, so a later run can tell a new obligation from a covered period.
     */
    public static final String AUTOMATIC_CALENDAR_MONTH_RENT = "Automatic calendar-month rent";

    /** Result of an idempotent monthly-rent insert. Never means a second row was written. */
    public enum MonthlyRentWrite {
        CREATED,
        ALREADY_PRESENT,
        FROZEN,
        NOT_CHARGEABLE
    }

    public record MonthlyRentEnsureResult(MonthlyRentWrite write, SpacePaymentEntity payment) {}

    private final SpaceRepository spaceRepository;
    private final SpacePaymentRepository paymentRepository;
    private final OccupancyRepository occupancyRepository;
    private final MealParticipationRepository participationRepository;
    private final MemberRepository memberRepository;
    private final PayPerMealBillingCalculator payPerMealBillingCalculator;
    private final MealBillingResolver mealBillingResolver;
    private final SpacePaymentTimelineService timelineService;
    private final SpacePaymentAccessService accessService;
    private final OccupancyTargetLabelBuilder occupancyTargetLabelBuilder;

    @Transactional
    public void syncExpectedPayments(UUID spaceId, UUID callerId, YearMonth month) {
        SpaceMembershipEntity membership = accessService.requireActiveMembership(spaceId, callerId);
        UUID ownMemberFilter = accessService.isOwnScopeOnly(membership)
                ? accessService.resolveOwnMemberId(spaceId, callerId)
                : null;
        syncExpectedPaymentsInternal(spaceId, month, ownMemberFilter, callerId);
    }

    /**
     * System month-close path — no membership gate; bills all members for the closed month.
     * Safe to retry (upsert is idempotent).
     */
    @Transactional
    public void syncExpectedPaymentsInternal(UUID spaceId, YearMonth month) {
        syncExpectedPaymentsInternal(spaceId, month, null, null);
    }

    private void syncExpectedPaymentsInternal(
            UUID spaceId, YearMonth month, UUID ownMemberFilter, UUID mealCallerId) {
        SpaceEntity space = spaceRepository.findById(spaceId).orElse(null);
        if (space == null) {
            return;
        }

        String monthKey = month.toString();

        if (isAccommodationApplicable(space.getType())) {
            LocalDateTime monthStartTime = month.atDay(1).atStartOfDay();
            LocalDateTime monthEndExclusive = month.plusMonths(1).atDay(1).atStartOfDay();
            for (OccupancyEntity occupancy :
                    occupancyRepository.findBillableBySpaceIdForMonth(
                            spaceId,
                            monthStartTime,
                            monthEndExclusive,
                            month.atEndOfMonth())) {
                if (ownMemberFilter != null
                        && !occupancy.getMember().getId().equals(ownMemberFilter)) {
                    continue;
                }
                if (OccupancyBillingCalculator.shouldGeneratePayableRent(occupancy, month)) {
                    syncRentPayment(space, occupancy, month, monthKey, null);
                }
                // Deposit remains one-time at activation month (independent of OPEN_ENDED rent gate).
                if (OccupancyBillingCalculator.isBillableInMonth(occupancy, month)) {
                    LocalDate occupancyStart =
                            OccupancyBillingCalculator.resolveOccupancyStartDate(occupancy);
                    if (occupancyStart != null && YearMonth.from(occupancyStart).equals(month)) {
                        syncDepositPayment(space, occupancy, month, monthKey);
                    }
                }
            }
        }

        for (MemberEntity member : resolveMealBillingMembers(space)) {
            if (!MealPricingPolicy.usesSeparateMealBilling(space)) {
                continue;
            }
            if (ownMemberFilter != null && !member.getId().equals(ownMemberFilter)) {
                continue;
            }
            MealBillingType billingType = mealBillingResolver.resolve(space, member);
            if (billingType != MealBillingType.PAY_PER_MEAL) {
                continue;
            }
            // Meal sync needs a caller for contribution calc; skip on pure system path.
            if (mealCallerId == null) {
                continue;
            }
            syncMealPayment(space, member, month, monthKey, mealCallerId);
        }
    }

    private void syncRentPayment(
            SpaceEntity space,
            OccupancyEntity occupancy,
            YearMonth month,
            String monthKey,
            String createRemarks) {
        // Reservation booking-advance remaining balance must not be overwritten by month sync
        // with a full first-period rent amount.
        if (isReservationFirstPeriodRentFrozen(occupancy, month)) {
            return;
        }

        BillingAmountResult billing =
                OccupancyBillingCalculator.computeBilling(occupancy, month, space);
        if (billing == null
                || billing.getTotalAmount() == null
                || billing.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String title = "Rent — " + MONTH_TITLE.format(month);
        upsertPayment(
                space,
                occupancy.getMember(),
                occupancy,
                SpacePaymentType.RENT,
                SpacePaymentCategory.MONTHLY,
                title,
                billing,
                DEFAULT_CURRENCY,
                monthKey,
                buildTargetLabel(occupancy),
                createRemarks,
                false);
    }

    /**
     * Insert the calendar-month rent row once. Does not update an existing row, so a
     * cancelled, paid, or move-in first-period charge is left as stored.
     *
     * <p>A full month is due on the 1st. A partial first or final period keeps the
     * existing due-on-period-start rule.
     */
    @Transactional
    public MonthlyRentEnsureResult ensureAutomaticMonthlyRent(
            SpaceEntity space, OccupancyEntity occupancy, YearMonth month) {
        if (space == null || occupancy == null || occupancy.getMember() == null || month == null) {
            return new MonthlyRentEnsureResult(MonthlyRentWrite.NOT_CHARGEABLE, null);
        }
        if (occupancy.getStayKind() == StayKind.SHORT
                || OccupancyBillingCalculator.isOpenEndedDailyRate(occupancy)) {
            return new MonthlyRentEnsureResult(MonthlyRentWrite.NOT_CHARGEABLE, null);
        }
        if (isReservationFirstPeriodRentFrozen(occupancy, month)) {
            return new MonthlyRentEnsureResult(MonthlyRentWrite.NOT_CHARGEABLE, null);
        }
        BillingAmountResult billing = dueOnFirstForFullMonth(
                OccupancyBillingCalculator.computeBilling(occupancy, month, space), month);
        if (billing == null
                || billing.getTotalAmount() == null
                || billing.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return new MonthlyRentEnsureResult(MonthlyRentWrite.NOT_CHARGEABLE, null);
        }
        return upsertPayment(
                space,
                occupancy.getMember(),
                occupancy,
                SpacePaymentType.RENT,
                SpacePaymentCategory.MONTHLY,
                "Rent — " + MONTH_TITLE.format(month),
                billing,
                DEFAULT_CURRENCY,
                month.toString(),
                buildTargetLabel(occupancy),
                AUTOMATIC_CALENDAR_MONTH_RENT,
                true);
    }

    /**
     * Confirmed rule: a full calendar month is due on the 1st. Prorated periods stay
     * on the existing calculator due date (the period start).
     */
    static BillingAmountResult dueOnFirstForFullMonth(BillingAmountResult billing, YearMonth month) {
        if (billing == null || billing.getPeriod() == null || billing.getPeriod().isProrated() || month == null) {
            return billing;
        }
        LocalDate first = month.atDay(1);
        if (first.equals(billing.getDueDate())) {
            return billing;
        }
        return BillingAmountResult.builder()
                .period(billing.getPeriod())
                .configuredMonthlyAmount(billing.getConfiguredMonthlyAmount())
                .baseAmount(billing.getBaseAmount())
                .taxAmount(billing.getTaxAmount())
                .totalAmount(billing.getTotalAmount())
                .taxEnabled(billing.isTaxEnabled())
                .taxRatePercent(billing.getTaxRatePercent())
                .priceTaxMode(billing.getPriceTaxMode())
                .dueDate(first)
                .build();
    }

    /**
     * First-period MONTHLY rent was already split at reservation (advance + remaining).
     * Month ensure / sync must not replace the remaining obligation with full computed rent.
     */
    public static boolean isReservationFirstPeriodRentFrozen(OccupancyEntity occupancy, YearMonth month) {
        if (!hasReservationBookingAdvance(occupancy) || month == null) {
            return false;
        }
        LocalDate start = OccupancyBillingCalculator.resolveOccupancyStartDate(occupancy);
        if (start == null) {
            start = occupancy.getMoveInDate();
        }
        return start != null && YearMonth.from(start).equals(month);
    }

    /**
     * On reservation cancel: reject unpaid reservation rent obligations so they stop
     * appearing as dues. Paid advance rows are retained for audit/refund handling.
     */
    @Transactional
    public void cancelReservationRentObligations(OccupancyEntity occupancy) {
        if (!hasReservationBookingAdvance(occupancy) || occupancy.getId() == null) {
            return;
        }
        List<SpacePaymentEntity> rows = paymentRepository.findByOccupancy_Id(occupancy.getId());
        for (SpacePaymentEntity payment : rows) {
            if (payment.getPaymentType() != SpacePaymentType.RENT) {
                continue;
            }
            if (payment.getPaymentStatus() == SpacePaymentStatus.PAID) {
                String prior = payment.getRemarks();
                payment.setRemarks(
                        (prior == null || prior.isBlank() ? "" : prior + " | ")
                                + "Reservation cancelled — paid advance retained for refund/forfeiture");
                paymentRepository.save(payment);
                continue;
            }
            if (payment.getPaymentStatus() == SpacePaymentStatus.REJECTED) {
                continue;
            }
            payment.setPaymentStatus(SpacePaymentStatus.REJECTED);
            payment.setRejectionReason("Reservation cancelled");
            String prior = payment.getRemarks();
            payment.setRemarks(
                    (prior == null || prior.isBlank() ? "" : prior + " | ")
                            + "Rejected because reservation was cancelled");
            paymentRepository.save(payment);
            timelineService.record(
                    payment,
                    PaymentTimelineEventType.REJECTED,
                    "Reservation cancelled",
                    null);
        }
    }

    /**
     * Reservation-only first-period rent split when a booking advance is provided.
     *
     * <p>Agreed rent is the first-period commitment (monthly rent, SHORT stay total, or OPEN_ENDED
     * daily rate). Advance is a portion of that rent (not additional). Creates {@code RENT/ADVANCE}
     * + remaining {@code RENT/MONTHLY} (or {@code RENT/EXTRA} for SHORT). Deposit is not created
     * here.
     */
    @Transactional
    public void createReservationFirstPeriodRentSplit(
            OccupancyEntity occupancy,
            BigDecimal bookingAdvanceAmount,
            boolean collected,
            SpacePaymentMethod paymentMethod,
            UUID callerId) {
        if (occupancy == null || occupancy.getSpace() == null || occupancy.getMember() == null) {
            return;
        }
        if (bookingAdvanceAmount == null || bookingAdvanceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        SpaceEntity space = occupancy.getSpace();
        if (!isAccommodationApplicable(space.getType())) {
            return;
        }

        StayKind kind = occupancy.getStayKind();

        BigDecimal commitment = resolveFirstPeriodRentCommitment(occupancy);
        if (commitment == null || commitment.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "BOOKING_ADVANCE_RENT_REQUIRED",
                    "Enter the first-period rent before recording a booking advance.");
        }
        if (bookingAdvanceAmount.compareTo(commitment) > 0) {
            throw new BusinessException(
                    "BOOKING_ADVANCE_EXCEEDS_RENT",
                    "Booking advance cannot exceed the first-period rent.");
        }

        LocalDate start = occupancy.getMoveInDate() != null
                ? occupancy.getMoveInDate()
                : LocalDate.now();
        YearMonth month = YearMonth.from(start);
        String monthKey = month.toString();
        String targetLabel = buildTargetLabel(occupancy);
        BigDecimal remaining = commitment.subtract(bookingAdvanceAmount);

        // Idempotent: do not recreate if advance row already exists for this occupancy/month.
        SpacePaymentEntity existingAdvance = paymentRepository
                .findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        space.getId(),
                        occupancy.getMember().getId(),
                        monthKey,
                        SpacePaymentType.RENT,
                        SpacePaymentCategory.ADVANCE)
                .orElse(null);
        if (existingAdvance == null) {
            SpacePaymentEntity advance = SpacePaymentEntity.builder()
                    .space(space)
                    .member(occupancy.getMember())
                    .occupancy(occupancy)
                    .paymentType(SpacePaymentType.RENT)
                    .paymentCategory(SpacePaymentCategory.ADVANCE)
                    .title("Rent — Booking advance")
                    .amount(bookingAdvanceAmount)
                    .currencyCode(DEFAULT_CURRENCY)
                    .dueDate(start)
                    .month(monthKey)
                    .paymentStatus(
                            collected ? SpacePaymentStatus.PAID : SpacePaymentStatus.PENDING)
                    .paymentMethod(collected ? paymentMethod : null)
                    .paymentDate(collected ? LocalDate.now() : null)
                    .targetLabel(targetLabel)
                    .remarks("Reservation booking advance (portion of first-period rent)")
                    .billingPeriodStart(start)
                    .billingPeriodEnd(month.atEndOfMonth())
                    .billableDays(null)
                    .daysInMonth(month.lengthOfMonth())
                    .configuredMonthlyAmount(commitment)
                    .prorated(false)
                    .taxEnabled(false)
                    .baseAmount(bookingAdvanceAmount)
                    .taxAmount(BigDecimal.ZERO)
                    .build();
            advance = paymentRepository.save(advance);
            timelineService.record(
                    advance,
                    PaymentTimelineEventType.CREATED,
                    collected
                            ? "Reservation booking advance collected"
                            : "Reservation booking advance pending",
                    null);
            if (collected) {
                timelineService.record(
                        advance, PaymentTimelineEventType.PAID, "Collected at reservation", null);
            }
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            if (kind == StayKind.SHORT) {
                createReservationShortStayRemaining(space, occupancy, remaining, start, month, targetLabel);
            } else {
                BillingAmountResult billing = BillingAmountCalculator.calculateFixedPeriodAmount(
                        remaining,
                        BillingAmountCalculator.fullMonthPeriod(month),
                        TaxInput.none(),
                        space.getBillingDueDay() > 0 ? space.getBillingDueDay() : 1);
                String title = "Rent — Remaining balance — " + MONTH_TITLE.format(month);
                upsertPayment(
                        space,
                        occupancy.getMember(),
                        occupancy,
                        SpacePaymentType.RENT,
                        SpacePaymentCategory.MONTHLY,
                        title,
                        billing,
                        DEFAULT_CURRENCY,
                        monthKey,
                        targetLabel,
                        "Reservation remaining rent after booking advance",
                        false);
            }
        }
    }

    private void createReservationShortStayRemaining(
            SpaceEntity space,
            OccupancyEntity occupancy,
            BigDecimal remaining,
            LocalDate start,
            YearMonth month,
            String targetLabel) {
        LocalDate checkout = occupancy.getExpectedExitDate() != null
                ? occupancy.getExpectedExitDate()
                : occupancy.getExpectedCheckoutDate();
        SpacePaymentEntity existing = paymentRepository
                .findByOccupancy_IdAndPaymentTypeAndPaymentCategoryAndBillingPeriodStartAndBillingPeriodEnd(
                        occupancy.getId(),
                        SpacePaymentType.RENT,
                        SpacePaymentCategory.EXTRA,
                        start,
                        checkout)
                .orElse(null);
        if (existing != null) {
            return;
        }
        int nights = StayExtensionRules.nights(start, checkout);
        SpacePaymentEntity charge = SpacePaymentEntity.builder()
                .space(space)
                .member(occupancy.getMember())
                .occupancy(occupancy)
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.EXTRA)
                .title("Short stay — Remaining balance")
                .amount(remaining)
                .currencyCode(DEFAULT_CURRENCY)
                .dueDate(start)
                .month(month.toString())
                .paymentStatus(SpacePaymentStatus.PENDING)
                .targetLabel(targetLabel)
                .billingPeriodStart(start)
                .billingPeriodEnd(checkout)
                .billableDays(nights)
                .daysInMonth(month.lengthOfMonth())
                .configuredMonthlyAmount(remaining)
                .prorated(false)
                .taxEnabled(false)
                .baseAmount(remaining)
                .taxAmount(BigDecimal.ZERO)
                .remarks("Reservation remaining short-stay rent after booking advance")
                .build();
        charge = paymentRepository.save(charge);
        timelineService.record(charge, PaymentTimelineEventType.CREATED, "Short stay remaining", null);
    }

    private static BigDecimal resolveFirstPeriodRentCommitment(OccupancyEntity occupancy) {
        if (occupancy.getStayKind() == StayKind.SHORT) {
            LocalDate start = OccupancyBillingCalculator.resolveOccupancyStartDate(occupancy);
            LocalDate checkout = occupancy.getExpectedExitDate() != null
                    ? occupancy.getExpectedExitDate()
                    : occupancy.getExpectedCheckoutDate();
            return ShortStayRules.resolveStayRent(
                    start, checkout, occupancy.getRentSnapshot(), occupancy.getAgreedTotal());
        }
        return occupancy.getRentSnapshot();
    }

    /** True when reservation already created first-period rent obligations via booking advance. */
    public static boolean hasReservationBookingAdvance(OccupancyEntity occupancy) {
        return occupancy != null
                && occupancy.getBookingAdvanceAmount() != null
                && occupancy.getBookingAdvanceAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Activation charges for allocate / move-in. Meal dues stay on the monthly generator.
     * Idempotent via existing upsert.
     *
     * <p>OPEN_ENDED {@code DAILY_RATE}: deposits only — rent is generated after the billing
     * period closes (or on vacate). MONTHLY / legacy OPEN_ENDED still create first-month rent.
     *
     * <p>When a reservation booking advance already split first-period rent, skip regenerating
     * that rent so the customer is not charged the full period again.
     */
    @Transactional
    public void createActivationPayments(OccupancyEntity occupancy, UUID callerId) {
        if (occupancy == null || occupancy.getSpace() == null || occupancy.getMember() == null) {
            return;
        }
        SpaceEntity space = occupancy.getSpace();
        if (!isAccommodationApplicable(space.getType())) {
            return;
        }
        boolean reservationRentAlreadyCreated = hasReservationBookingAdvance(occupancy);
        if (occupancy.getStayKind() == StayKind.SHORT) {
            if (!reservationRentAlreadyCreated) {
                createOriginalShortStayCharge(space, occupancy);
            }
            LocalDate start = OccupancyBillingCalculator.resolveOccupancyStartDate(occupancy);
            YearMonth month = YearMonth.from(start);
            syncDepositPayment(space, occupancy, month, month.toString());
            return;
        }
        LocalDate start = OccupancyBillingCalculator.resolveOccupancyStartDate(occupancy);
        YearMonth month = YearMonth.from(start);
        String monthKey = month.toString();
        if (!reservationRentAlreadyCreated
                && OccupancyBillingCalculator.shouldGeneratePayableRent(occupancy, month)) {
            syncRentPayment(
                    space, occupancy, month, monthKey, "Created at occupancy activation");
        }
        syncDepositPayment(space, occupancy, month, monthKey);
    }

    /**
     * After vacate: close OPEN_ENDED daily-rate rent through the checkout date for that month.
     * Idempotent. Does not rewrite historical months already billed.
     */
    @Transactional
    public void settleOpenEndedRentOnVacate(OccupancyEntity occupancy) {
        if (occupancy == null || occupancy.getSpace() == null || occupancy.getMember() == null) {
            return;
        }
        if (!OccupancyBillingCalculator.isOpenEndedDailyRate(occupancy)) {
            return;
        }
        SpaceEntity space = occupancy.getSpace();
        if (!isAccommodationApplicable(space.getType())) {
            return;
        }
        LocalDate vacated = OccupancyBillingCalculator.resolveOccupancyEndDate(occupancy);
        if (vacated == null) {
            return;
        }
        YearMonth month = YearMonth.from(vacated);
        if (!OccupancyBillingCalculator.shouldGeneratePayableRent(occupancy, month, vacated)) {
            return;
        }
        syncRentPayment(
                space,
                occupancy,
                month,
                month.toString(),
                "Created on vacate for actual occupied days");
    }

    private void createOriginalShortStayCharge(SpaceEntity space, OccupancyEntity occupancy) {
        LocalDate start = OccupancyBillingCalculator.resolveOccupancyStartDate(occupancy);
        LocalDate checkout = occupancy.getExpectedExitDate() != null
                ? occupancy.getExpectedExitDate()
                : occupancy.getExpectedCheckoutDate();
        BigDecimal amount = ShortStayRules.resolveStayRent(
                start, checkout, occupancy.getRentSnapshot(), occupancy.getAgreedTotal());
        SpacePaymentEntity existing = paymentRepository
                .findByOccupancy_IdAndPaymentTypeAndPaymentCategoryAndBillingPeriodStartAndBillingPeriodEnd(
                        occupancy.getId(),
                        SpacePaymentType.RENT,
                        SpacePaymentCategory.EXTRA,
                        start,
                        checkout)
                .orElse(null);
        if (existing != null) {
            return;
        }

        int nights = StayExtensionRules.nights(start, checkout);
        YearMonth month = YearMonth.from(start);
        SpacePaymentEntity charge = SpacePaymentEntity.builder()
                .space(space)
                .member(occupancy.getMember())
                .occupancy(occupancy)
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.EXTRA)
                .title("Short stay")
                .amount(amount)
                .currencyCode(DEFAULT_CURRENCY)
                .dueDate(start)
                .month(month.toString())
                .paymentStatus(SpacePaymentStatus.PENDING)
                .targetLabel(buildTargetLabel(occupancy))
                .billingPeriodStart(start)
                .billingPeriodEnd(checkout)
                .billableDays(nights)
                .daysInMonth(month.lengthOfMonth())
                .configuredMonthlyAmount(amount)
                .prorated(false)
                .taxEnabled(false)
                .baseAmount(amount)
                .taxAmount(BigDecimal.ZERO)
                .remarks("Checkout " + checkout + " is not a charged night.")
                .build();
        charge = paymentRepository.save(charge);
        timelineService.record(charge, PaymentTimelineEventType.CREATED, "Short stay", null);
    }

    private void syncDepositPayment(
            SpaceEntity space, OccupancyEntity occupancy, YearMonth month, String monthKey) {
        java.math.BigDecimal deposit = occupancy.getDepositSnapshot();
        if (deposit == null || deposit.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return;
        }

        int dueDay = space.getBillingDueDay() > 0 ? space.getBillingDueDay() : 1;
        BillingAmountResult billing = BillingAmountCalculator.calculateFixedPeriodAmount(
                deposit,
                BillingAmountCalculator.fullMonthPeriod(month),
                TaxInput.none(),
                dueDay);

        upsertPayment(
                space,
                occupancy.getMember(),
                occupancy,
                SpacePaymentType.DEPOSIT,
                SpacePaymentCategory.SECURITY,
                "Security deposit",
                billing,
                DEFAULT_CURRENCY,
                monthKey,
                buildTargetLabel(occupancy),
                "Created at move-in",
                false);
    }

    private String buildTargetLabel(OccupancyEntity occupancy) {
        return occupancyTargetLabelBuilder.build(occupancy);
    }

    private void syncMealPayment(
            SpaceEntity space,
            MemberEntity member,
            YearMonth month,
            String monthKey,
            UUID callerId) {
        MealLedgerContribution contribution =
                payPerMealBillingCalculator.computeMemberContribution(
                        space.getId(), member.getId(), callerId, month);
        BigDecimal activityAmount = contribution.getExpected();
        if (activityAmount == null || activityAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // Pay-per-meal monthly expected = activity sum (already mid-month aware via usage).
        // Apply space tax snapshot; do not calendar-prorate activity again.
        TaxInput tax = OccupancyBillingCalculator.resolveTaxInput(space);
        int dueDay = space.getBillingDueDay() > 0 ? space.getBillingDueDay() : 1;
        BillingAmountResult billing = BillingAmountCalculator.calculateFixedPeriodAmount(
                activityAmount,
                BillingAmountCalculator.fullMonthPeriod(month),
                tax,
                dueDay);

        String currency = contribution.getCurrencyCode() != null
                ? contribution.getCurrencyCode()
                : DEFAULT_CURRENCY;
        String title = "Meals — " + MONTH_TITLE.format(month);
        OccupancyEntity occupancy = null;
        String targetLabel = null;
        if (isAccommodationApplicable(space.getType())) {
            occupancy = occupancyRepository
                    .findActiveBySpaceIdAndMemberId(space.getId(), member.getId())
                    .orElse(null);
            if (occupancy != null) {
                targetLabel = buildTargetLabel(occupancy);
            }
        }
        upsertPayment(
                space,
                member,
                occupancy,
                SpacePaymentType.MEAL,
                SpacePaymentCategory.MONTHLY,
                title,
                billing,
                currency,
                monthKey,
                targetLabel,
                null,
                false);
    }

    private MonthlyRentEnsureResult upsertPayment(
            SpaceEntity space,
            MemberEntity member,
            OccupancyEntity occupancy,
            SpacePaymentType paymentType,
            SpacePaymentCategory paymentCategory,
            String title,
            BillingAmountResult billing,
            String currencyCode,
            String monthKey,
            String targetLabel,
            String createRemarks,
            boolean createOnly) {
        SpacePaymentEntity payment = paymentRepository
                .findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        space.getId(), member.getId(), monthKey, paymentType, paymentCategory)
                .orElse(null);

        if (payment == null) {
            payment = SpacePaymentEntity.builder()
                    .space(space)
                    .member(member)
                    .occupancy(occupancy)
                    .paymentType(paymentType)
                    .paymentCategory(paymentCategory)
                    .title(title)
                    .amount(billing.getTotalAmount())
                    .currencyCode(currencyCode)
                    .dueDate(billing.getDueDate())
                    .month(monthKey)
                    .paymentStatus(SpacePaymentStatus.PENDING)
                    .targetLabel(targetLabel)
                    .remarks(createRemarks)
                    .build();
            applyBillingSnapshot(payment, billing);
            try {
                payment = paymentRepository.save(payment);
            } catch (DataIntegrityViolationException ex) {
                // Concurrent insert won the generated-period unique index.
                return new MonthlyRentEnsureResult(MonthlyRentWrite.ALREADY_PRESENT, null);
            }
            timelineService.record(payment, PaymentTimelineEventType.CREATED, createRemarks, null);
            return new MonthlyRentEnsureResult(MonthlyRentWrite.CREATED, payment);
        }

        if (payment.getPaymentStatus() == SpacePaymentStatus.CANCELLED
                || payment.getPaymentStatus() == SpacePaymentStatus.REFUNDED) {
            return new MonthlyRentEnsureResult(MonthlyRentWrite.FROZEN, payment);
        }
        if (createOnly) {
            return new MonthlyRentEnsureResult(MonthlyRentWrite.ALREADY_PRESENT, payment);
        }

        if (isFrozenBeforeRentChange(occupancy, paymentType, paymentCategory, monthKey)) {
            return new MonthlyRentEnsureResult(MonthlyRentWrite.FROZEN, payment);
        }
        if (paymentType == SpacePaymentType.RENT
                && paymentCategory == SpacePaymentCategory.MONTHLY
                && occupancy != null
                && isReservationFirstPeriodRentFrozen(occupancy, YearMonth.parse(monthKey))) {
            return new MonthlyRentEnsureResult(MonthlyRentWrite.FROZEN, payment);
        }

        if (payment.getPaymentStatus() == SpacePaymentStatus.PAID
                || payment.getPaymentStatus() == SpacePaymentStatus.UNDER_REVIEW
                || payment.getPaymentStatus() == SpacePaymentStatus.PROOF_UPLOADED) {
            return new MonthlyRentEnsureResult(MonthlyRentWrite.ALREADY_PRESENT, payment);
        }

        payment.setTitle(title);
        payment.setAmount(billing.getTotalAmount());
        payment.setCurrencyCode(currencyCode);
        if (!keepsAutomaticFirstDueDate(payment)) {
            payment.setDueDate(billing.getDueDate());
        }
        payment.setTargetLabel(targetLabel);
        applyBillingSnapshot(payment, billing);
        if (occupancy != null) {
            payment.setOccupancy(occupancy);
        }
        paymentRepository.save(payment);
        return new MonthlyRentEnsureResult(MonthlyRentWrite.ALREADY_PRESENT, payment);
    }

    /** Scheduler-created full months stay due on the 1st when owner sync refreshes the amount. */
    private static boolean keepsAutomaticFirstDueDate(SpacePaymentEntity payment) {
        return payment.getDueDate() != null
                && payment.getDueDate().getDayOfMonth() == 1
                && AUTOMATIC_CALENDAR_MONTH_RENT.equals(payment.getRemarks());
    }

    private void applyBillingSnapshot(SpacePaymentEntity payment, BillingAmountResult billing) {
        payment.setBillingPeriodStart(billing.getPeriod().getStart());
        payment.setBillingPeriodEnd(billing.getPeriod().getEnd());
        payment.setBillableDays(billing.getPeriod().getBillableDays());
        payment.setDaysInMonth(billing.getPeriod().getDaysInMonth());
        payment.setConfiguredMonthlyAmount(billing.getConfiguredMonthlyAmount());
        payment.setProrated(billing.getPeriod().isProrated());
        payment.setTaxEnabled(billing.isTaxEnabled());
        payment.setTaxRatePercent(billing.getTaxRatePercent());
        payment.setPriceTaxMode(billing.getPriceTaxMode());
        payment.setBaseAmount(billing.getBaseAmount());
        payment.setTaxAmount(billing.getTaxAmount());
    }

    private List<MemberEntity> resolveMealBillingMembers(SpaceEntity space) {
        if (space.getType() == SpaceType.MESS) {
            return memberRepository.findBySpaceIdAndActiveTrue(space.getId());
        }
        Set<UUID> memberIds = new HashSet<>();
        participationRepository.findAllNonStoppedBySpaceId(space.getId()).stream()
                .filter(row -> row.getStatus() == MealParticipationStatus.ACTIVE)
                .forEach(row -> memberIds.add(row.getMember().getId()));
        return memberRepository.findBySpaceIdAndActiveTrue(space.getId()).stream()
                .filter(member -> memberIds.contains(member.getId()))
                .toList();
    }

    private boolean isAccommodationApplicable(SpaceType type) {
        return type == SpaceType.PG
                || type == SpaceType.HOSTEL
                || type == SpaceType.CO_LIVING
                || type == SpaceType.RENTAL
                || type == SpaceType.CORPORATE_STAY;
    }

    /**
     * Pending and rejected rent for a month before the selected effective month
     * stays as stored. Paid, under-review, and proof-uploaded rows are already
     * left unchanged by the status check above.
     */
    private static boolean isFrozenBeforeRentChange(
            OccupancyEntity occupancy,
            SpacePaymentType paymentType,
            SpacePaymentCategory paymentCategory,
            String monthKey) {
        if (paymentType != SpacePaymentType.RENT
                || paymentCategory != SpacePaymentCategory.MONTHLY
                || occupancy == null
                || occupancy.getRentEffectiveMonth() == null
                || monthKey == null) {
            return false;
        }
        return YearMonth.parse(monthKey).isBefore(YearMonth.from(occupancy.getRentEffectiveMonth()));
    }
}
