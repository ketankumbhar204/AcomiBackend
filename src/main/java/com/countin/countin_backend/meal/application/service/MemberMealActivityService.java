package com.countin.countin_backend.meal.application.service;

import com.countin.countin_backend.common.exception.BusinessException;
import com.countin.countin_backend.common.exception.ResourceNotFoundException;
import com.countin.countin_backend.meal.api.dto.response.MemberMealActivityDailyChargeResponse;
import com.countin.countin_backend.meal.api.dto.response.MemberMealActivityDayDetailResponse;
import com.countin.countin_backend.meal.api.dto.response.MemberMealActivityDayPaymentResponse;
import com.countin.countin_backend.meal.api.dto.response.MemberMealActivityDayResponse;
import com.countin.countin_backend.meal.api.dto.response.MemberMealActivityMonthResponse;
import com.countin.countin_backend.meal.api.dto.response.MemberMealActivitySelectionResponse;
import com.countin.countin_backend.meal.api.dto.response.MemberMealActivitySlotDetailResponse;
import com.countin.countin_backend.meal.api.dto.response.MemberMealActivitySlotResponse;
import com.countin.countin_backend.meal.api.dto.response.MemberMealActivitySubscriptionResponse;
import com.countin.countin_backend.meal.api.dto.response.MemberMealActivitySummaryResponse;
import com.countin.countin_backend.meal.api.dto.response.MealPollOptionResponse;
import com.countin.countin_backend.meal.domain.model.DailyMenuStatus;
import com.countin.countin_backend.meal.domain.model.MealPollOptionType;
import com.countin.countin_backend.meal.domain.model.MealPollPaymentStatus;
import com.countin.countin_backend.meal.domain.model.MealPollStatus;
import com.countin.countin_backend.meal.domain.model.MealType;
import com.countin.countin_backend.meal.domain.model.MemberMealActivitySlotStatus;
import com.countin.countin_backend.meal.domain.policy.MealPollEligibilityPolicy;
import com.countin.countin_backend.meal.domain.policy.MealOccupancyPolicy;
import com.countin.countin_backend.meal.application.support.MealBillingResolver;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.DailyMenuEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealPollDayPaymentEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealPollEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealPollMemberDeliveryEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealPollResponseEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.DailyMenuRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealParticipationRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealPollDayPaymentRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealPollMemberDeliveryRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealPollRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealPollResponseRepository;
import com.countin.countin_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.countin.countin_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.countin.countin_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.countin.countin_backend.space.domain.model.MealBillingType;
import com.countin.countin_backend.space.domain.model.PrepaidBalanceUnit;
import com.countin.countin_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberMealActivityService {

    private final MemberRepository memberRepository;
    private final MealParticipationRepository participationRepository;
    private final DailyMenuRepository dailyMenuRepository;
    private final MealPollResponseRepository responseRepository;
    private final MealPollMemberDeliveryRepository deliveryRepository;
    private final MealPollRepository pollRepository;
    private final MealPollDayPaymentRepository dayPaymentRepository;
    private final DailyMenuService dailyMenuService;
    private final MealAccessService mealAccessService;
    private final SpaceRepository spaceRepository;
    private final MemberMealBalanceService memberMealBalanceService;
    private final MealBillingResolver mealBillingResolver;
    private final MealPollEligibilityPolicy pollEligibilityPolicy;
    private final MealOccupancyPolicy occupancyPolicy;

    @Transactional(readOnly = true)
    public MemberMealActivityMonthResponse getMonthlyActivity(
            UUID spaceId, UUID memberId, UUID callerId, String monthParam) {
        MemberEntity member = memberRepository
                .findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));
        mealAccessService.requireViewParticipation(spaceId, memberId, callerId, member);

        YearMonth month = parseMonth(monthParam);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        MealParticipationEntity participation = participationRepository
                .findBySpaceIdAndMemberIdAndStatus(
                        spaceId,
                        memberId,
                        com.countin.countin_backend.meal.domain.model.MealParticipationStatus.ACTIVE)
                .orElse(null);

        Optional<OccupancyEntity> memberOccupancy =
                occupancyPolicy.findActiveOccupancy(member.getSpace(), memberId);

        List<DailyMenuEntity> publishedMenus = dailyMenuRepository.findBySpaceAndDateRange(
                spaceId, from, to, true);

        Map<LocalDate, Map<MealType, Boolean>> publishedByDate = new HashMap<>();
        for (DailyMenuEntity menu : publishedMenus) {
            if (menu.getStatus() != DailyMenuStatus.PUBLISHED
                    && menu.getStatus() != DailyMenuStatus.MODIFIED) {
                continue;
            }
            publishedByDate
                    .computeIfAbsent(menu.getMenuDate(), ignored -> new EnumMap<>(MealType.class))
                    .put(menu.getMealType(), dailyMenuService.isPublished(spaceId, menu.getMenuDate(), menu.getMealType()));
        }

        List<MealPollResponseEntity> responses =
                responseRepository.findForMemberInDateRange(memberId, spaceId, from, to);
        Map<LocalDate, Map<MealType, List<MealPollResponseEntity>>> responsesByDate = new HashMap<>();
        for (MealPollResponseEntity response : responses) {
            LocalDate date = response.getPoll().getPollDate();
            MealType mealType = response.getPoll().getMealType();
            responsesByDate
                    .computeIfAbsent(date, ignored -> new EnumMap<>(MealType.class))
                    .computeIfAbsent(mealType, ignored -> new ArrayList<>())
                    .add(response);
        }

        Map<String, MealPollMemberDeliveryEntity> deliveryByPollKey = deliveryRepository
                .findForMemberInDateRange(memberId, spaceId, from, to)
                .stream()
                .collect(Collectors.toMap(
                        delivery -> pollKey(delivery.getPoll().getPollDate(), delivery.getPoll().getMealType()),
                        delivery -> delivery,
                        (left, right) -> left));

        Map<LocalDate, MealPollDayPaymentEntity> paymentsByDate = dayPaymentRepository
                .findForMemberInDateRange(memberId, spaceId, from, to)
                .stream()
                .collect(Collectors.toMap(
                        MealPollDayPaymentEntity::getPollDate, payment -> payment, (left, right) -> left));

        int acceptedMeals = 0;
        int pendingResponses = 0;
        int skippedMeals = 0;
        BigDecimal amountGenerated = BigDecimal.ZERO;
        BigDecimal paidAmount = BigDecimal.ZERO;
        BigDecimal pendingAmount = BigDecimal.ZERO;
        List<MemberMealActivityDayResponse> days = new ArrayList<>();

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<MemberMealActivitySlotResponse> slots = new ArrayList<>();
            for (MealType mealType : MealType.values()) {
                MemberMealActivitySlotResponse slot = buildSlot(
                        member,
                        participation,
                        memberOccupancy,
                        date,
                        mealType,
                        publishedByDate,
                        responsesByDate,
                        deliveryByPollKey);
                slots.add(slot);

                if (slot.getStatus() == MemberMealActivitySlotStatus.ACCEPTED) {
                    acceptedMeals += Math.max(1, slot.getQuantity() != null ? slot.getQuantity() : 1);
                } else if (slot.getStatus() == MemberMealActivitySlotStatus.PENDING) {
                    pendingResponses += 1;
                } else if (slot.getStatus() == MemberMealActivitySlotStatus.SKIPPED) {
                    skippedMeals += 1;
                }
            }
            boolean hasActivity = slots.stream().anyMatch(slot -> {
                MemberMealActivitySlotStatus status = slot.getStatus();
                return status == MemberMealActivitySlotStatus.ACCEPTED
                        || status == MemberMealActivitySlotStatus.PENDING
                        || status == MemberMealActivitySlotStatus.SKIPPED;
            });

            BigDecimal dayTotal = slots.stream()
                    .map(MemberMealActivitySlotResponse::getSlotAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            MealPollDayPaymentEntity dayPayment = paymentsByDate.get(date);
            MealPollPaymentStatus paymentStatus =
                    dayPayment != null ? dayPayment.getPaymentStatus() : null;

            if (dayTotal.compareTo(BigDecimal.ZERO) > 0) {
                amountGenerated = amountGenerated.add(dayTotal);
                if (paymentStatus == MealPollPaymentStatus.PAID) {
                    paidAmount = paidAmount.add(dayTotal);
                } else {
                    pendingAmount = pendingAmount.add(dayTotal);
                }
            }

            days.add(MemberMealActivityDayResponse.builder()
                    .date(date)
                    .hasActivity(hasActivity)
                    .dayTotal(dayTotal.compareTo(BigDecimal.ZERO) > 0 ? dayTotal : null)
                    .currencyCode("INR")
                    .paymentStatus(paymentStatus)
                    .paymentBatchId(dayPayment != null ? dayPayment.getPaymentBatchId() : null)
                    .paymentReference(dayPayment != null ? dayPayment.getPaymentReference() : null)
                    .slots(slots)
                    .build());
        }

        return MemberMealActivityMonthResponse.builder()
                .month(month.toString())
                .summary(buildSummary(
                        member,
                        month,
                        acceptedMeals,
                        pendingResponses,
                        skippedMeals,
                        amountGenerated,
                        paidAmount,
                        pendingAmount))
                .days(days)
                .build();
    }

    private MemberMealActivitySummaryResponse buildSummary(
            MemberEntity member,
            YearMonth month,
            int acceptedMeals,
            int pendingResponses,
            int skippedMeals,
            BigDecimal amountGenerated,
            BigDecimal paidAmount,
            BigDecimal pendingAmount) {
        UUID spaceId = member.getSpace().getId();
        UUID memberId = member.getId();
        MemberMealActivitySummaryResponse.MemberMealActivitySummaryResponseBuilder builder =
                MemberMealActivitySummaryResponse.builder()
                        .acceptedMeals(acceptedMeals)
                        .pendingResponses(pendingResponses)
                        .skippedMeals(skippedMeals)
                        .amountGenerated(amountGenerated.compareTo(BigDecimal.ZERO) > 0 ? amountGenerated : null)
                        .paidAmount(paidAmount.compareTo(BigDecimal.ZERO) > 0 ? paidAmount : null)
                        .pendingAmount(pendingAmount.compareTo(BigDecimal.ZERO) > 0 ? pendingAmount : null)
                        .currencyCode("INR");

        if (mealBillingResolver.isPrepaid(member.getSpace(), member)) {
            var balance = memberMealBalanceService.findBalance(spaceId, memberId);
            builder
                    .balanceRemaining(balance != null ? balance.getBalance() : BigDecimal.ZERO)
                    .balancePurchased(memberMealBalanceService.sumPurchased(spaceId, memberId, month))
                    .balanceConsumed(memberMealBalanceService.sumConsumed(spaceId, memberId, month))
                    .amountPaidThisMonth(memberMealBalanceService.sumPaid(spaceId, memberId, month))
                    .balanceUnit(member.getSpace().getPrepaidBalanceUnit() != null
                            ? member.getSpace().getPrepaidBalanceUnit()
                            : PrepaidBalanceUnit.MEALS);
        }

        return builder.build();
    }

    @Transactional(readOnly = true)
    public MemberMealActivityDayDetailResponse getDayDetail(
            UUID spaceId, UUID memberId, UUID callerId, String dateParam) {
        MemberEntity member = memberRepository
                .findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));
        mealAccessService.requireViewParticipation(spaceId, memberId, callerId, member);

        LocalDate date = parseDate(dateParam);
        MealParticipationEntity participation = participationRepository
                .findBySpaceIdAndMemberIdAndStatus(
                        spaceId,
                        memberId,
                        com.countin.countin_backend.meal.domain.model.MealParticipationStatus.ACTIVE)
                .orElse(null);

        Optional<OccupancyEntity> memberOccupancy =
                occupancyPolicy.findActiveOccupancy(member.getSpace(), memberId);

        Map<MealType, Boolean> publishedByType = new EnumMap<>(MealType.class);
        for (MealType mealType : MealType.values()) {
            publishedByType.put(mealType, dailyMenuService.isPublished(spaceId, date, mealType));
        }

        Map<MealType, MealPollEntity> pollsByType = pollRepository
                .findBySpaceIdAndPollDateOrderByMealTypeAsc(spaceId, date)
                .stream()
                .collect(Collectors.toMap(MealPollEntity::getMealType, poll -> poll, (left, right) -> left));

        Map<MealType, List<MealPollResponseEntity>> responsesByType = new EnumMap<>(MealType.class);
        for (MealPollResponseEntity response :
                responseRepository.findForMemberInDateRange(memberId, spaceId, date, date)) {
            MealType mealType = response.getPoll().getMealType();
            responsesByType.computeIfAbsent(mealType, ignored -> new ArrayList<>()).add(response);
        }

        Map<String, MealPollMemberDeliveryEntity> deliveryByPollKey = deliveryRepository
                .findForMemberInDateRange(memberId, spaceId, date, date)
                .stream()
                .collect(Collectors.toMap(
                        delivery -> pollKey(delivery.getPoll().getPollDate(), delivery.getPoll().getMealType()),
                        delivery -> delivery,
                        (left, right) -> left));

        BigDecimal dayTotal = BigDecimal.ZERO;
        List<MemberMealActivitySlotDetailResponse> slots = new ArrayList<>();
        for (MealType mealType : MealType.values()) {
            MemberMealActivitySlotDetailResponse slot = buildSlotDetail(
                    member,
                    participation,
                    memberOccupancy,
                    date,
                    mealType,
                    publishedByType.getOrDefault(mealType, false),
                    pollsByType.get(mealType),
                    responsesByType.getOrDefault(mealType, List.of()),
                    deliveryByPollKey);
            slots.add(slot);
            if (slot.getSlotTotal() != null) {
                dayTotal = dayTotal.add(slot.getSlotTotal());
            }
        }

        MemberMealActivityDayPaymentResponse payment = dayPaymentRepository
                .findBySpaceIdAndMemberIdAndPollDate(spaceId, memberId, date)
                .map(this::toPaymentResponse)
                .orElse(null);

        LocalDateTime responseSubmittedAt = slots.stream()
                .map(MemberMealActivitySlotDetailResponse::getRespondedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        List<MemberMealActivityDailyChargeResponse> dailyCharges = slots.stream()
                .filter(slot -> slot.getSlotTotal() != null
                        && slot.getSlotTotal().compareTo(BigDecimal.ZERO) > 0)
                .map(slot -> MemberMealActivityDailyChargeResponse.builder()
                        .mealType(slot.getMealType())
                        .amount(slot.getSlotTotal())
                        .currencyCode("INR")
                        .build())
                .toList();

        boolean hasActivity = payment != null
                || !dailyCharges.isEmpty()
                || slots.stream().anyMatch(slot -> slot.getStatus() != MemberMealActivitySlotStatus.INACTIVE);

        String notes = buildDayNotes(spaceId, date);

        return MemberMealActivityDayDetailResponse.builder()
                .date(date)
                .memberName(member.getFullName())
                .hasActivity(hasActivity)
                .responseSubmittedAt(responseSubmittedAt)
                .dayTotal(dayTotal.compareTo(BigDecimal.ZERO) > 0 ? dayTotal : null)
                .currencyCode("INR")
                .payment(payment)
                .subscription(buildBalanceSubscription(member, date))
                .notes(notes)
                .dailyCharges(dailyCharges)
                .slots(slots)
                .build();
    }

    private MemberMealActivitySubscriptionResponse buildBalanceSubscription(
            MemberEntity member, LocalDate date) {
        if (!mealBillingResolver.isPrepaid(member.getSpace(), member)) {
            return null;
        }
        UUID spaceId = member.getSpace().getId();
        UUID memberId = member.getId();
        var balance = memberMealBalanceService.findBalance(spaceId, memberId);
        BigDecimal remaining = balance != null ? balance.getBalance() : BigDecimal.ZERO;
        YearMonth month = YearMonth.from(date);
        BigDecimal consumed = memberMealBalanceService.sumConsumed(spaceId, memberId, month);
        return MemberMealActivitySubscriptionResponse.builder()
                .planName("Meal balance")
                .creditsRemaining(remaining.intValue())
                .creditsConsumed(consumed.intValue())
                .coveredBySubscription(remaining.compareTo(BigDecimal.ZERO) > 0)
                .build();
    }

    private String buildDayNotes(UUID spaceId, LocalDate date) {
        List<String> parts = dailyMenuRepository
                .findBySpaceAndDate(spaceId, date, false, DailyMenuStatus.PUBLISHED)
                .stream()
                .map(DailyMenuEntity::getNotes)
                .filter(note -> note != null && !note.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (parts.isEmpty()) {
            return null;
        }
        return String.join("\n", parts);
    }

    private MemberMealActivityDayPaymentResponse toPaymentResponse(MealPollDayPaymentEntity payment) {
        return MemberMealActivityDayPaymentResponse.builder()
                .id(payment.getId())
                .pollDate(payment.getPollDate())
                .paymentChoice(payment.getPaymentChoice())
                .paymentStatus(payment.getPaymentStatus())
                .chargedAmount(payment.getChargedAmount())
                .paymentBatchId(payment.getPaymentBatchId())
                .paymentReference(payment.getPaymentReference())
                .proofImageUrl(payment.getProofImageUrl())
                .referenceNumber(payment.getReferenceNumber())
                .remarks(payment.getRemarks())
                .paymentMethod(payment.getPaymentMethod())
                .rejectionReason(payment.getRejectionReason())
                .proofSubmittedAt(payment.getProofSubmittedAt())
                .proofReviewedAt(payment.getProofReviewedAt())
                .prepaidOverflowAmount(payment.getPrepaidOverflowAmount())
                .prepaidDebitedAmount(payment.getPrepaidDebitedAmount())
                .prepaidOverflowPayment(payment.isPrepaidOverflowPayment())
                .build();
    }

    private MemberMealActivitySlotDetailResponse buildSlotDetail(
            MemberEntity member,
            MealParticipationEntity participation,
            Optional<OccupancyEntity> memberOccupancy,
            LocalDate date,
            MealType mealType,
            boolean menuPublished,
            MealPollEntity poll,
            List<MealPollResponseEntity> slotResponses,
            Map<String, MealPollMemberDeliveryEntity> deliveryByPollKey) {
        boolean eligible = participation != null
                && pollEligibilityPolicy.isPollEligible(
                        participation, date, mealType, null, memberOccupancy, true);

        MealPollMemberDeliveryEntity delivery = deliveryByPollKey.get(pollKey(date, mealType));
        String deliveryLocationName = null;
        String deliveryLocationDescription = null;
        if (delivery != null && delivery.getDeliveryLocation() != null) {
            deliveryLocationName = delivery.getDeliveryLocation().getName();
            deliveryLocationDescription = delivery.getDeliveryLocation().getDescription();
        }
        MealPollStatus pollStatus = poll != null ? poll.getStatus() : null;
        LocalDateTime respondedAt = slotResponses.stream()
                .map(MealPollResponseEntity::getRespondedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);

        if (!eligible) {
            return MemberMealActivitySlotDetailResponse.builder()
                    .mealType(mealType)
                    .status(MemberMealActivitySlotStatus.INACTIVE)
                    .menuPublished(menuPublished)
                    .pollStatus(pollStatus)
                    .deliveryLocationName(deliveryLocationName)
                    .deliveryLocationDescription(deliveryLocationDescription)
                    .respondedAt(respondedAt)
                    .slotTotal(BigDecimal.ZERO)
                    .selections(List.of())
                    .build();
        }

        if (!menuPublished && slotResponses.isEmpty()) {
            return MemberMealActivitySlotDetailResponse.builder()
                    .mealType(mealType)
                    .status(MemberMealActivitySlotStatus.NO_MENU)
                    .menuPublished(false)
                    .pollStatus(pollStatus)
                    .deliveryLocationName(deliveryLocationName)
                    .deliveryLocationDescription(deliveryLocationDescription)
                    .respondedAt(respondedAt)
                    .slotTotal(BigDecimal.ZERO)
                    .selections(List.of())
                    .build();
        }

        List<MemberMealActivitySelectionResponse> selections = new ArrayList<>();
        BigDecimal slotTotal = BigDecimal.ZERO;
        int menuQuantity = 0;
        int skippedQuantity = 0;

        for (MealPollResponseEntity response : slotResponses) {
            if (response.getSelectedOption().getOptionType() == MealPollOptionType.NOT_AVAILABLE) {
                skippedQuantity += response.getQuantity();
                continue;
            }
            if (response.getSelectedOption().getOptionType() != MealPollOptionType.MENU_ENTRY
                    || response.getQuantity() <= 0) {
                continue;
            }

            menuQuantity += response.getQuantity();
            MealPollOptionResponse optionMeta = MealPollOptionResponse.from(response.getSelectedOption());
            BigDecimal unitPrice = optionMeta.getPrice() != null ? optionMeta.getPrice() : BigDecimal.ZERO;
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(response.getQuantity()));
            slotTotal = slotTotal.add(lineTotal);

            selections.add(MemberMealActivitySelectionResponse.builder()
                    .label(response.getSelectedOption().getLabel())
                    .price(unitPrice.compareTo(BigDecimal.ZERO) > 0 ? unitPrice : null)
                    .currencyCode(optionMeta.getCurrencyCode())
                    .quantity(response.getQuantity())
                    .itemDetail(response.getSelectedOption().getDetail())
                    .lineTotal(lineTotal.compareTo(BigDecimal.ZERO) > 0 ? lineTotal : null)
                    .build());
        }

        MemberMealActivitySlotStatus status;
        if (menuQuantity > 0) {
            status = MemberMealActivitySlotStatus.ACCEPTED;
        } else if (skippedQuantity > 0) {
            status = MemberMealActivitySlotStatus.SKIPPED;
        } else if (menuPublished) {
            status = MemberMealActivitySlotStatus.PENDING;
        } else {
            status = MemberMealActivitySlotStatus.NO_MENU;
        }

        return MemberMealActivitySlotDetailResponse.builder()
                .mealType(mealType)
                .status(status)
                .menuPublished(menuPublished)
                .pollStatus(pollStatus)
                .deliveryLocationName(deliveryLocationName)
                .deliveryLocationDescription(deliveryLocationDescription)
                .respondedAt(respondedAt)
                .slotTotal(slotTotal.compareTo(BigDecimal.ZERO) > 0 ? slotTotal : null)
                .selections(selections)
                .build();
    }

    private static LocalDate parseDate(String dateParam) {
        if (dateParam == null || dateParam.isBlank()) {
            throw new BusinessException("Date is required. Use YYYY-MM-DD", HttpStatus.BAD_REQUEST);
        }
        try {
            return LocalDate.parse(dateParam.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException("Invalid date format. Use YYYY-MM-DD", HttpStatus.BAD_REQUEST);
        }
    }

    private MemberMealActivitySlotResponse buildSlot(
            MemberEntity member,
            MealParticipationEntity participation,
            Optional<OccupancyEntity> memberOccupancy,
            LocalDate date,
            MealType mealType,
            Map<LocalDate, Map<MealType, Boolean>> publishedByDate,
            Map<LocalDate, Map<MealType, List<MealPollResponseEntity>>> responsesByDate,
            Map<String, MealPollMemberDeliveryEntity> deliveryByPollKey) {
        boolean eligible = participation != null
                && pollEligibilityPolicy.isPollEligible(
                        participation, date, mealType, null, memberOccupancy, true);

        if (!eligible) {
            return MemberMealActivitySlotResponse.builder()
                    .mealType(mealType)
                    .status(MemberMealActivitySlotStatus.INACTIVE)
                    .build();
        }

        boolean menuPublished = publishedByDate
                .getOrDefault(date, Map.of())
                .getOrDefault(mealType, false);

        List<MealPollResponseEntity> slotResponses = responsesByDate
                .getOrDefault(date, Map.of())
                .getOrDefault(mealType, List.of());

        if (!menuPublished && slotResponses.isEmpty()) {
            return MemberMealActivitySlotResponse.builder()
                    .mealType(mealType)
                    .status(MemberMealActivitySlotStatus.NO_MENU)
                    .build();
        }

        int menuQuantity = 0;
        int skippedQuantity = 0;
        List<String> selectionParts = new ArrayList<>();
        BigDecimal slotAmount = BigDecimal.ZERO;

        for (MealPollResponseEntity response : slotResponses) {
            if (response.getSelectedOption().getOptionType() == MealPollOptionType.NOT_AVAILABLE) {
                skippedQuantity += response.getQuantity();
            } else if (response.getSelectedOption().getOptionType() == MealPollOptionType.MENU_ENTRY
                    && response.getQuantity() > 0) {
                menuQuantity += response.getQuantity();
                String label = response.getSelectedOption().getLabel();
                if (response.getQuantity() > 1) {
                    selectionParts.add(label + " × " + response.getQuantity());
                } else {
                    selectionParts.add(label);
                }
                MealPollOptionResponse optionMeta = MealPollOptionResponse.from(response.getSelectedOption());
                BigDecimal unitPrice = optionMeta.getPrice() != null ? optionMeta.getPrice() : BigDecimal.ZERO;
                slotAmount = slotAmount.add(unitPrice.multiply(BigDecimal.valueOf(response.getQuantity())));
            }
        }

        MealPollMemberDeliveryEntity delivery =
                deliveryByPollKey.get(pollKey(date, mealType));
        String deliveryLocationName = delivery != null && delivery.getDeliveryLocation() != null
                ? delivery.getDeliveryLocation().getName()
                : null;

        BigDecimal resolvedSlotAmount = slotAmount.compareTo(BigDecimal.ZERO) > 0 ? slotAmount : null;

        if (menuQuantity > 0) {
            return MemberMealActivitySlotResponse.builder()
                    .mealType(mealType)
                    .status(MemberMealActivitySlotStatus.ACCEPTED)
                    .selectionLabel(String.join(", ", selectionParts))
                    .quantity(menuQuantity)
                    .deliveryLocationName(deliveryLocationName)
                    .slotAmount(resolvedSlotAmount)
                    .currencyCode("INR")
                    .build();
        }

        if (skippedQuantity > 0) {
            return MemberMealActivitySlotResponse.builder()
                    .mealType(mealType)
                    .status(MemberMealActivitySlotStatus.SKIPPED)
                    .selectionLabel(null)
                    .quantity(0)
                    .deliveryLocationName(deliveryLocationName)
                    .slotAmount(BigDecimal.ZERO)
                    .currencyCode("INR")
                    .build();
        }

        if (menuPublished) {
            return MemberMealActivitySlotResponse.builder()
                    .mealType(mealType)
                    .status(MemberMealActivitySlotStatus.PENDING)
                    .deliveryLocationName(deliveryLocationName)
                    .slotAmount(null)
                    .currencyCode("INR")
                    .build();
        }

        return MemberMealActivitySlotResponse.builder()
                .mealType(mealType)
                .status(MemberMealActivitySlotStatus.NO_MENU)
                .slotAmount(null)
                .currencyCode("INR")
                .build();
    }

    private static String pollKey(LocalDate date, MealType mealType) {
        return date + ":" + mealType.name();
    }

    private static YearMonth parseMonth(String monthParam) {
        if (monthParam == null || monthParam.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(monthParam.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException("Invalid month format. Use YYYY-MM", HttpStatus.BAD_REQUEST);
        }
    }
}
