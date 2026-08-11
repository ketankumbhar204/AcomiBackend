package com.amico.amico_backend.payment.application.service;

import com.amico.amico_backend.dashboard.application.support.MealLedgerContribution;
import com.amico.amico_backend.dashboard.application.support.OccupancyBillingCalculator;
import com.amico.amico_backend.dashboard.application.support.PayPerMealBillingCalculator;
import com.amico.amico_backend.meal.application.support.MealBillingResolver;
import com.amico.amico_backend.meal.application.support.MealPricingPolicy;
import com.amico.amico_backend.meal.domain.model.MealParticipationStatus;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealParticipationRepository;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.amico.amico_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.amico.amico_backend.occupancy.application.service.OccupancyTargetLabelBuilder;
import com.amico.amico_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.amico.amico_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.amico.amico_backend.payment.domain.model.PaymentTimelineEventType;
import com.amico.amico_backend.payment.domain.model.SpacePaymentCategory;
import com.amico.amico_backend.payment.domain.model.SpacePaymentStatus;
import com.amico.amico_backend.payment.domain.model.SpacePaymentType;
import com.amico.amico_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.amico.amico_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.amico.amico_backend.space.domain.model.MealBillingType;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;
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
        SpaceEntity space = spaceRepository.findById(spaceId).orElse(null);
        if (space == null) {
            return;
        }

        SpaceMembershipEntity membership = accessService.requireActiveMembership(spaceId, callerId);
        UUID ownMemberFilter = accessService.isOwnScopeOnly(membership)
                ? accessService.resolveOwnMemberId(spaceId, callerId)
                : null;

        String monthKey = month.toString();
        LocalDate dueDate = month.atEndOfMonth();

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
                if (OccupancyBillingCalculator.isBillableInMonth(occupancy, month)) {
                    syncRentPayment(space, occupancy, month, monthKey, dueDate);
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
            syncMealPayment(space, member, month, monthKey, dueDate, callerId);
        }
    }

    private void syncRentPayment(
            SpaceEntity space,
            OccupancyEntity occupancy,
            YearMonth month,
            String monthKey,
            LocalDate dueDate) {
        BigDecimal amount = OccupancyBillingCalculator.computeMonthlyExpected(occupancy, month);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
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
                amount,
                DEFAULT_CURRENCY,
                dueDate,
                monthKey,
                buildTargetLabel(occupancy));
    }

    private String buildTargetLabel(OccupancyEntity occupancy) {
        return occupancyTargetLabelBuilder.build(occupancy);
    }

    private void syncMealPayment(
            SpaceEntity space,
            MemberEntity member,
            YearMonth month,
            String monthKey,
            LocalDate dueDate,
            UUID callerId) {
        MealLedgerContribution contribution =
                payPerMealBillingCalculator.computeMemberContribution(
                        space.getId(), member.getId(), callerId, month);
        BigDecimal amount = contribution.getExpected();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

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
                amount,
                currency,
                dueDate,
                monthKey,
                targetLabel);
    }

    private void upsertPayment(
            SpaceEntity space,
            MemberEntity member,
            OccupancyEntity occupancy,
            SpacePaymentType paymentType,
            SpacePaymentCategory paymentCategory,
            String title,
            BigDecimal amount,
            String currencyCode,
            LocalDate dueDate,
            String monthKey,
            String targetLabel) {
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
                    .amount(amount)
                    .currencyCode(currencyCode)
                    .dueDate(dueDate)
                    .month(monthKey)
                    .paymentStatus(SpacePaymentStatus.PENDING)
                    .targetLabel(targetLabel)
                    .build();
            paymentRepository.save(payment);
            timelineService.record(payment, PaymentTimelineEventType.CREATED, null, null);
            return;
        }

        if (payment.getPaymentStatus() == SpacePaymentStatus.PAID
                || payment.getPaymentStatus() == SpacePaymentStatus.UNDER_REVIEW
                || payment.getPaymentStatus() == SpacePaymentStatus.PROOF_UPLOADED) {
            return;
        }

        payment.setTitle(title);
        payment.setAmount(amount);
        payment.setCurrencyCode(currencyCode);
        payment.setDueDate(dueDate);
        payment.setTargetLabel(targetLabel);
        if (occupancy != null) {
            payment.setOccupancy(occupancy);
        }
        paymentRepository.save(payment);
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
                || type == SpaceType.RENTAL;
    }
}
