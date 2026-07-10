package com.countin.countin_backend.meal.application.service;

import com.countin.countin_backend.common.exception.BusinessException;
import com.countin.countin_backend.meal.domain.model.MealPollPaymentEventType;
import com.countin.countin_backend.meal.api.dto.request.ApproveMealPollPaymentRequest;
import com.countin.countin_backend.meal.api.dto.request.RejectMealPollPaymentRequest;
import com.countin.countin_backend.meal.api.dto.request.SubmitMealPollPaymentProofRequest;
import com.countin.countin_backend.meal.api.dto.request.SubmitMealPollResponsesRequest;
import com.countin.countin_backend.meal.api.dto.request.SubmitMealPollOptionQuantityRequest;
import com.countin.countin_backend.meal.api.dto.request.SubmitMealPollSelectionRequest;
import com.countin.countin_backend.meal.api.dto.response.MealDeliveryLocationResponse;
import com.countin.countin_backend.meal.api.dto.response.MealPollDayResponse;
import com.countin.countin_backend.meal.api.dto.response.MealPollMySelectionResponse;
import com.countin.countin_backend.meal.api.dto.response.MealPollOptionResponse;
import com.countin.countin_backend.meal.api.dto.response.MealPollResponse;
import com.countin.countin_backend.meal.domain.model.DailyMenuEntryType;
import com.countin.countin_backend.meal.domain.model.DailyMenuStatus;
import com.countin.countin_backend.meal.domain.model.MealParticipationStatus;
import com.countin.countin_backend.meal.domain.model.MealPollOptionType;
import com.countin.countin_backend.meal.domain.model.MealPollPaymentChoice;
import com.countin.countin_backend.meal.domain.model.MealPollPaymentStatus;
import com.countin.countin_backend.meal.domain.model.MealPollResponseSource;
import com.countin.countin_backend.meal.domain.model.MealPollStatus;
import com.countin.countin_backend.meal.domain.model.MealType;
import com.countin.countin_backend.meal.domain.policy.MealPollEligibilityPolicy;
import com.countin.countin_backend.meal.domain.policy.MemberSubscriptionPolicy;
import com.countin.countin_backend.meal.application.support.MealBillingResolver;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.DailyMenuEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.DailyMenuEntryEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealComboItemEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealDeliveryLocationEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealPollDayPaymentEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealPollEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealPollMemberDeliveryEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealPollOptionEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealPollResponseEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.DailyMenuEntryRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.DailyMenuRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.DailyMenuPackageItemRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealComboItemRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealParticipationRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealDeliveryLocationRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealPollOptionRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealPollDayPaymentRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealPollMemberDeliveryRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealPollRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealPollResponseRepository;
import com.countin.countin_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.countin.countin_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.countin.countin_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.countin.countin_backend.space.domain.model.MealBillingType;
import com.countin.countin_backend.space.domain.model.SpaceType;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.countin.countin_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
public class MealPollService {

    private final MealPollRepository pollRepository;
    private final MealPollOptionRepository optionRepository;
    private final MealPollResponseRepository responseRepository;
    private final MealPollMemberDeliveryRepository memberDeliveryRepository;
    private final MealPollDayPaymentRepository dayPaymentRepository;
    private final MealDeliveryLocationRepository deliveryLocationRepository;
    private final MealDeliveryLocationService deliveryLocationService;
    private final MealParticipationLastDeliveryService lastDeliveryService;
    private final DailyMenuRepository dailyMenuRepository;
    private final DailyMenuEntryRepository dailyMenuEntryRepository;
    private final MealComboItemRepository mealComboItemRepository;
    private final DailyMenuPackageItemRepository dailyMenuPackageItemRepository;
    private final MealParticipationRepository participationRepository;
    private final SpaceRepository spaceRepository;
    private final MemberRepository memberRepository;
    private final MealAccessService mealAccessService;
    private final MemberMealBalanceService memberMealBalanceService;
    private final MealBillingResolver mealBillingResolver;
    private final MealPollPaymentEventService paymentEventService;
    private final MealPollEligibilityPolicy pollEligibilityPolicy;
    private final MemberSubscriptionPolicy subscriptionPolicy;

    @Transactional(readOnly = true)
    public MealPollDayResponse getPollsForDate(UUID spaceId, UUID callerId, LocalDate date) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        LocalDate pollDate = date != null ? date : LocalDate.now();
        UUID memberId = resolveMemberIdIfParticipant(spaceId, callerId);

        List<MealPollResponse> polls = pollRepository.findBySpaceIdAndPollDateOrderByMealTypeAsc(spaceId, pollDate)
                .stream()
                .map(poll -> toPollResponse(poll, memberId))
                .toList();

        MealPollPaymentStatus myPaymentStatus = null;
        MealPollPaymentChoice myPaymentChoice = null;
        String myProofImageUrl = null;
        String myRejectionReason = null;
        Map<MealType, UUID> myLastDeliveryLocationIds = Map.of();
        List<MealDeliveryLocationResponse> deliveryLocations = List.of();
        MealBillingType myMealBillingType = null;
        BigDecimal myPrepaidOverflowAmount = null;
        BigDecimal myPrepaidDebitedAmount = null;
        Boolean myPrepaidOverflowPayment = null;

        SpaceEntity space = spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new BusinessException("Space not found", HttpStatus.NOT_FOUND));
        if (space.getType() == SpaceType.MESS) {
            deliveryLocations = deliveryLocationRepository
                    .findBySpaceIdAndActiveTrueOrderBySortOrderAscNameAsc(spaceId)
                    .stream()
                    .map(MealDeliveryLocationResponse::from)
                    .toList();
        }

        if (memberId != null) {
            MemberEntity member = memberRepository
                    .findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)
                    .orElse(null);
            if (member != null) {
                myMealBillingType = mealBillingResolver.resolve(space, member);
            }

            Optional<MealPollDayPaymentEntity> payment =
                    dayPaymentRepository.findBySpaceIdAndMemberIdAndPollDate(spaceId, memberId, pollDate);
            if (payment.isPresent()) {
                MealPollDayPaymentEntity row = payment.get();
                myPaymentStatus = row.getPaymentStatus();
                myPaymentChoice = row.getPaymentChoice();
                myProofImageUrl = row.getProofImageUrl();
                myRejectionReason = row.getRejectionReason();
                myPrepaidOverflowAmount = row.getPrepaidOverflowAmount();
                myPrepaidDebitedAmount = row.getPrepaidDebitedAmount();
                myPrepaidOverflowPayment = row.isPrepaidOverflowPayment() ? true : null;
            }

            myLastDeliveryLocationIds = loadLastDeliveryLocationIds(spaceId, memberId);
        }

        return MealPollDayResponse.builder()
                .pollDate(pollDate)
                .polls(polls)
                .myPaymentStatus(myPaymentStatus)
                .myPaymentChoice(myPaymentChoice)
                .myProofImageUrl(myProofImageUrl)
                .myRejectionReason(myRejectionReason)
                .deliveryLocations(deliveryLocations)
                .myLastDeliveryLocationIds(myLastDeliveryLocationIds)
                .myMealBillingType(myMealBillingType)
                .myPrepaidOverflowAmount(myPrepaidOverflowAmount)
                .myPrepaidDebitedAmount(myPrepaidDebitedAmount)
                .myPrepaidOverflowPayment(myPrepaidOverflowPayment)
                .build();
    }

    private Map<MealType, UUID> loadLastDeliveryLocationIds(UUID spaceId, UUID memberId) {
        return participationRepository
                .findBySpaceIdAndMemberIdAndStatus(spaceId, memberId, MealParticipationStatus.ACTIVE)
                .map(lastDeliveryService::loadLastDeliveryLocationIds)
                .orElse(Map.of());
    }

    @Transactional(readOnly = true)
    public MealPollResponse getPoll(UUID spaceId, UUID callerId, LocalDate date, MealType mealType) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        UUID memberId = resolveMemberIdIfParticipant(spaceId, callerId);
        MealPollEntity poll = loadPoll(spaceId, date, mealType);
        return toPollResponse(poll, memberId);
    }

    @Transactional
    public MealPollResponse openPoll(UUID spaceId, UUID callerId, LocalDate date, MealType mealType) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        LocalDate pollDate = date != null ? date : LocalDate.now();

        DailyMenuEntity menu = dailyMenuRepository
                .findBySpaceDateAndType(spaceId, pollDate, mealType)
                .filter(m -> !m.isDeleted() && m.getStatus() == DailyMenuStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(
                        "Publish the menu before opening a poll", HttpStatus.BAD_REQUEST));

        List<DailyMenuEntryEntity> entries = dailyMenuEntryRepository.findByDailyMenuId(menu.getId()).stream()
                .filter(DailyMenuEntryEntity::isAvailable)
                .toList();
        if (entries.isEmpty()) {
            throw new BusinessException("Menu has no available options to poll", HttpStatus.BAD_REQUEST);
        }

        Optional<MealPollEntity> existing =
                pollRepository.findBySpaceIdAndPollDateAndMealType(spaceId, pollDate, mealType);
        if (existing.isPresent()) {
            MealPollEntity poll = existing.get();
            if (poll.getStatus() == MealPollStatus.OPEN) {
                return toPollResponse(poll, null);
            }
            throw new BusinessException("Poll already closed for this meal slot", HttpStatus.CONFLICT);
        }

        SpaceEntity space = spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new BusinessException("Space not found", HttpStatus.NOT_FOUND));

        MealPollEntity poll = MealPollEntity.builder()
                .space(space)
                .dailyMenu(menu)
                .mealType(mealType)
                .pollDate(pollDate)
                .status(MealPollStatus.OPEN)
                .openedAt(LocalDateTime.now())
                .build();
        poll = pollRepository.save(poll);

        List<MealPollOptionEntity> options = buildOptionsFromMenu(poll, entries, mealType);
        optionRepository.saveAll(options);

        return toPollResponse(poll, null);
    }

    @Transactional
    public MealPollResponse closePoll(UUID spaceId, UUID callerId, LocalDate date, MealType mealType) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        MealPollEntity poll = loadPoll(spaceId, date, mealType);
        if (poll.getStatus() == MealPollStatus.CLOSED) {
            return toPollResponse(poll, null);
        }
        poll.setStatus(MealPollStatus.CLOSED);
        poll.setClosedAt(LocalDateTime.now());
        pollRepository.save(poll);
        return toPollResponse(poll, null);
    }

    @Transactional
    public MealPollDayResponse submitResponses(
            UUID spaceId, UUID callerId, LocalDate date, SubmitMealPollResponsesRequest request) {
        List<SubmitMealPollSelectionRequest> selections = request.getSelections();
        MealPollPaymentChoice paymentChoice = request.getPaymentChoice();
        String proofImageBase64 = request.getProofImageBase64();
        SpaceMembershipEntity membership = mealAccessService.requireViewMeals(spaceId, callerId);
        if (!mealAccessService.isParticipantScopeOnly(membership)) {
            throw new BusinessException("Only meal participants can submit poll responses", HttpStatus.FORBIDDEN);
        }

        UUID memberId = mealAccessService.resolveOwnMemberId(spaceId, callerId);
        MemberEntity member = memberRepository
                .findById(memberId)
                .orElseThrow(() -> new BusinessException("Member not found", HttpStatus.NOT_FOUND));

        LocalDate pollDate = date != null ? date : LocalDate.now();
        List<MealParticipationEntity> participations =
                participationRepository.findAllNonStoppedBySpaceId(spaceId);

        SpaceEntity space = spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new BusinessException("Space not found", HttpStatus.NOT_FOUND));
        boolean multiQuantity = space.getType() == SpaceType.MESS;
        MealBillingType memberBillingType = mealBillingResolver.resolve(space, member);
        boolean prepaidBilling = memberBillingType == MealBillingType.PREPAID_BALANCE;

        if (prepaidBilling && !subscriptionPolicy.canParticipateInPolls(space, member)) {
            throw new BusinessException(
                    "An active meal subscription is required to select meals",
                    HttpStatus.FORBIDDEN);
        }

        if (multiQuantity && paymentChoice == null && !prepaidBilling) {
            throw new BusinessException("Select a payment option to continue", HttpStatus.BAD_REQUEST);
        }

        BigDecimal prepaidOverflowCurrency = BigDecimal.ZERO;

        for (SubmitMealPollSelectionRequest selection : selections) {
            MealPollEntity poll = loadPoll(spaceId, pollDate, selection.getMealType());
            if (poll.getStatus() != MealPollStatus.OPEN) {
                throw new BusinessException(
                        "Poll is closed for " + formatMealType(selection.getMealType()), HttpStatus.BAD_REQUEST);
            }

            MealParticipationEntity participation = participations.stream()
                    .filter(p -> p.getMember().getId().equals(memberId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("No meal participation found", HttpStatus.FORBIDDEN));

            if (!pollEligibilityPolicy.isPollEligible(participation, pollDate, selection.getMealType())) {
                throw new BusinessException(
                        "You are not eligible for " + formatMealType(selection.getMealType()),
                        HttpStatus.FORBIDDEN);
            }

            if (multiQuantity) {
                submitMultiQuantityResponses(poll, member, selection);
            } else {
                submitSingleSelectionResponse(poll, member, selection);
            }

            if (multiQuantity && deliveryLocationService.hasActiveLocations(spaceId)) {
                UUID deliveryLocationId = resolveDeliveryLocationId(selection, participation);
                if (deliveryLocationId == null) {
                    throw new BusinessException(
                            "Select a delivery location for " + formatMealType(selection.getMealType()),
                            HttpStatus.BAD_REQUEST);
                }
                upsertMemberDelivery(poll, member, deliveryLocationId);
                lastDeliveryService.saveLastDeliveryLocation(participation, selection.getMealType(), deliveryLocationId);
            }

            if (prepaidBilling) {
                MealPollEntity savedPoll = loadPoll(spaceId, pollDate, selection.getMealType());
                prepaidOverflowCurrency = prepaidOverflowCurrency.add(
                        memberMealBalanceService.syncPollDebit(space, member, savedPoll));
            }
        }

        if (prepaidBilling && prepaidOverflowCurrency.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal debitedAmount =
                    memberMealBalanceService.sumDebitedOnDate(space.getId(), member.getId(), pollDate);
            upsertPrepaidOverflowDayPayment(space, member, pollDate, prepaidOverflowCurrency, debitedAmount);
        } else if (multiQuantity && paymentChoice != null) {
            upsertDayPayment(space, member, pollDate, paymentChoice, proofImageBase64);
        }

        return getPollsForDate(spaceId, callerId, pollDate);
    }

    @Transactional
    public MealPollDayResponse submitPaymentProof(
            UUID spaceId, UUID callerId, LocalDate date, SubmitMealPollPaymentProofRequest request) {
        SpaceMembershipEntity membership = mealAccessService.requireViewMeals(spaceId, callerId);
        if (!mealAccessService.isParticipantScopeOnly(membership)) {
            throw new BusinessException("Only meal participants can submit payment proof", HttpStatus.FORBIDDEN);
        }

        UUID memberId = mealAccessService.resolveOwnMemberId(spaceId, callerId);
        MemberEntity member = memberRepository
                .findById(memberId)
                .orElseThrow(() -> new BusinessException("Member not found", HttpStatus.NOT_FOUND));

        SpaceEntity space = spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new BusinessException("Space not found", HttpStatus.NOT_FOUND));
        if (space.getType() != SpaceType.MESS) {
            throw new BusinessException("Payment proof is only required for mess spaces", HttpStatus.BAD_REQUEST);
        }

        LocalDate pollDate = date != null ? date : LocalDate.now();
        validateProofImage(request.getProofImageBase64());

        MealPollDayPaymentEntity payment = dayPaymentRepository
                .findBySpaceIdAndMemberIdAndPollDate(spaceId, memberId, pollDate)
                .orElse(MealPollDayPaymentEntity.builder()
                        .space(space)
                        .member(member)
                        .pollDate(pollDate)
                        .build());

        payment.setPaymentChoice(MealPollPaymentChoice.MARK_AS_PAID);
        payment.setPaymentStatus(MealPollPaymentStatus.PENDING_APPROVAL);
        payment.setProofImageUrl(normalizeProofImage(request.getProofImageBase64()));
        payment.setProofSubmittedAt(LocalDateTime.now());
        payment.setProofReviewedAt(null);
        payment.setProofReviewedBy(null);
        payment.setRejectionReason(null);
        dayPaymentRepository.save(payment);
        paymentEventService.recordEvent(
                space,
                member,
                pollDate,
                MealPollPaymentEventType.PROOF_SUBMITTED,
                payment.getPaymentStatus(),
                payment.getPaymentChoice(),
                null,
                null,
                callerId);

        return getPollsForDate(spaceId, callerId, pollDate);
    }

    @Transactional
    public MealPollDayResponse approvePayment(
            UUID spaceId,
            UUID callerId,
            LocalDate date,
            UUID memberId,
            ApproveMealPollPaymentRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        LocalDate pollDate = date != null ? date : LocalDate.now();

        MealPollDayPaymentEntity payment = dayPaymentRepository
                .findBySpaceIdAndMemberIdAndPollDate(spaceId, memberId, pollDate)
                .orElseThrow(() -> new BusinessException("Payment record not found", HttpStatus.NOT_FOUND));

        if (payment.getPaymentStatus() != MealPollPaymentStatus.PENDING_APPROVAL) {
            throw new BusinessException("No payment proof is awaiting approval", HttpStatus.BAD_REQUEST);
        }

        payment.setPaymentStatus(MealPollPaymentStatus.PAID);
        payment.setProofReviewedAt(LocalDateTime.now());
        payment.setProofReviewedBy(callerId);
        payment.setRejectionReason(null);
        dayPaymentRepository.save(payment);
        paymentEventService.recordFromPayment(
                payment,
                MealPollPaymentEventType.APPROVED,
                request != null ? request.getApprovalRemarks() : null,
                callerId);

        return getPollsForDate(spaceId, callerId, pollDate);
    }

    @Transactional
    public MealPollDayResponse rejectPayment(
            UUID spaceId, UUID callerId, LocalDate date, UUID memberId, RejectMealPollPaymentRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        LocalDate pollDate = date != null ? date : LocalDate.now();

        MealPollDayPaymentEntity payment = dayPaymentRepository
                .findBySpaceIdAndMemberIdAndPollDate(spaceId, memberId, pollDate)
                .orElseThrow(() -> new BusinessException("Payment record not found", HttpStatus.NOT_FOUND));

        if (payment.getPaymentStatus() != MealPollPaymentStatus.PENDING_APPROVAL) {
            throw new BusinessException("No payment proof is awaiting approval", HttpStatus.BAD_REQUEST);
        }

        payment.setPaymentStatus(MealPollPaymentStatus.REJECTED);
        payment.setProofReviewedAt(LocalDateTime.now());
        payment.setProofReviewedBy(callerId);
        payment.setRejectionReason(request != null ? request.getRejectionReason() : null);
        dayPaymentRepository.save(payment);
        paymentEventService.recordFromPayment(
                payment,
                MealPollPaymentEventType.REJECTED,
                request != null ? request.getRejectionReason() : null,
                callerId);

        return getPollsForDate(spaceId, callerId, pollDate);
    }

    @Transactional
    public MealPollDayResponse sendPaymentReminder(UUID spaceId, UUID callerId, LocalDate date, UUID memberId) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        LocalDate pollDate = date != null ? date : LocalDate.now();

        MealPollDayPaymentEntity payment = dayPaymentRepository
                .findBySpaceIdAndMemberIdAndPollDate(spaceId, memberId, pollDate)
                .orElseThrow(() -> new BusinessException("Payment record not found", HttpStatus.NOT_FOUND));

        if (payment.getPaymentStatus() != MealPollPaymentStatus.PENDING
                && payment.getPaymentStatus() != MealPollPaymentStatus.REJECTED) {
            throw new BusinessException("Payment is not awaiting collection", HttpStatus.BAD_REQUEST);
        }

        paymentEventService.recordFromPayment(
                payment, MealPollPaymentEventType.REMINDER_SENT, "Payment reminder sent", callerId);

        return getPollsForDate(spaceId, callerId, pollDate);
    }

    private void upsertPrepaidOverflowDayPayment(
            SpaceEntity space,
            MemberEntity member,
            LocalDate pollDate,
            BigDecimal overflowAmount,
            BigDecimal debitedAmount) {
        MealPollDayPaymentEntity payment = dayPaymentRepository
                .findBySpaceIdAndMemberIdAndPollDate(space.getId(), member.getId(), pollDate)
                .orElse(MealPollDayPaymentEntity.builder()
                        .space(space)
                        .member(member)
                        .pollDate(pollDate)
                        .build());

        payment.setPaymentChoice(MealPollPaymentChoice.PAY_LATER);
        payment.setPaymentStatus(MealPollPaymentStatus.PENDING);
        payment.setProofImageUrl(null);
        payment.setProofSubmittedAt(null);
        payment.setProofReviewedAt(null);
        payment.setProofReviewedBy(null);
        payment.setRejectionReason(null);
        payment.setPrepaidOverflowPayment(true);
        payment.setPrepaidOverflowAmount(overflowAmount);
        payment.setPrepaidDebitedAmount(debitedAmount);
        dayPaymentRepository.save(payment);
        paymentEventService.recordEvent(
                space,
                member,
                pollDate,
                MealPollPaymentEventType.PREPAID_OVERFLOW_PAY_LATER,
                payment.getPaymentStatus(),
                payment.getPaymentChoice(),
                overflowAmount,
                "Balance used: "
                        + debitedAmount.stripTrailingZeros().toPlainString()
                        + ", pay-per-meal due: "
                        + overflowAmount.stripTrailingZeros().toPlainString(),
                null);
    }

    private void upsertDayPayment(
            SpaceEntity space,
            MemberEntity member,
            LocalDate pollDate,
            MealPollPaymentChoice paymentChoice,
            String proofImageBase64) {
        if (paymentChoice == MealPollPaymentChoice.MARK_AS_PAID) {
            validateProofImage(proofImageBase64);
        }

        MealPollPaymentStatus paymentStatus =
                paymentChoice == MealPollPaymentChoice.PAY_LATER
                        ? MealPollPaymentStatus.PENDING
                        : MealPollPaymentStatus.PENDING_APPROVAL;

        MealPollDayPaymentEntity payment = dayPaymentRepository
                .findBySpaceIdAndMemberIdAndPollDate(space.getId(), member.getId(), pollDate)
                .orElse(MealPollDayPaymentEntity.builder()
                        .space(space)
                        .member(member)
                        .pollDate(pollDate)
                        .build());

        payment.setPaymentChoice(paymentChoice);
        payment.setPaymentStatus(paymentStatus);
        payment.setPrepaidOverflowPayment(false);
        payment.setPrepaidOverflowAmount(null);
        payment.setPrepaidDebitedAmount(null);

        if (paymentChoice == MealPollPaymentChoice.MARK_AS_PAID) {
            payment.setProofImageUrl(normalizeProofImage(proofImageBase64));
            payment.setProofSubmittedAt(LocalDateTime.now());
            payment.setProofReviewedAt(null);
            payment.setProofReviewedBy(null);
            payment.setRejectionReason(null);
        } else {
            payment.setProofImageUrl(null);
            payment.setProofSubmittedAt(null);
            payment.setProofReviewedAt(null);
            payment.setProofReviewedBy(null);
            payment.setRejectionReason(null);
        }

        dayPaymentRepository.save(payment);
        paymentEventService.recordEvent(
                space,
                member,
                pollDate,
                paymentChoice == MealPollPaymentChoice.PAY_LATER
                        ? MealPollPaymentEventType.PAY_LATER_SELECTED
                        : MealPollPaymentEventType.MARK_AS_PAID_SELECTED,
                paymentStatus,
                paymentChoice,
                null,
                null,
                null);
    }

    private void validateProofImage(String proofImageBase64) {
        if (proofImageBase64 == null || proofImageBase64.isBlank()) {
            throw new BusinessException("Upload a payment screenshot to mark as paid", HttpStatus.BAD_REQUEST);
        }
        String normalized = proofImageBase64.trim();
        if (!normalized.startsWith("data:image/")) {
            throw new BusinessException("Payment screenshot must be an image", HttpStatus.BAD_REQUEST);
        }
        if (normalized.length() > 4_000_000) {
            throw new BusinessException("Payment screenshot is too large", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeProofImage(String proofImageBase64) {
        return proofImageBase64 != null ? proofImageBase64.trim() : null;
    }

    private void submitSingleSelectionResponse(
            MealPollEntity poll, MemberEntity member, SubmitMealPollSelectionRequest selection) {
        if (selection.getSelectedOptionId() == null) {
            throw new BusinessException("Select a meal option", HttpStatus.BAD_REQUEST);
        }

        MealPollOptionEntity option = loadOptionForPoll(poll, selection.getSelectedOptionId());
        List<MealPollResponseEntity> existing =
                responseRepository.findAllByPollIdAndMemberId(poll.getId(), member.getId());
        if (existing.size() == 1
                && existing.get(0).getSelectedOption().getId().equals(option.getId())) {
            MealPollResponseEntity response = existing.get(0);
            response.setQuantity(1);
            response.setRespondedAt(LocalDateTime.now());
            response.setSource(MealPollResponseSource.APP);
            responseRepository.save(response);
            return;
        }

        responseRepository.deleteByPollIdAndMemberId(poll.getId(), member.getId());
        responseRepository.save(MealPollResponseEntity.builder()
                .poll(poll)
                .member(member)
                .selectedOption(option)
                .quantity(1)
                .respondedAt(LocalDateTime.now())
                .source(MealPollResponseSource.APP)
                .build());
    }

    private void submitMultiQuantityResponses(
            MealPollEntity poll, MemberEntity member, SubmitMealPollSelectionRequest selection) {
        List<SubmitMealPollOptionQuantityRequest> optionQuantities = selection.getOptions();
        if (optionQuantities == null || optionQuantities.isEmpty()) {
            throw new BusinessException("Select at least one meal option", HttpStatus.BAD_REQUEST);
        }

        int totalPlates = 0;
        for (SubmitMealPollOptionQuantityRequest optionQuantity : optionQuantities) {
            if (optionQuantity.getQuantity() == null || optionQuantity.getQuantity() < 0) {
                throw new BusinessException("Quantity must be zero or greater", HttpStatus.BAD_REQUEST);
            }
            if (optionQuantity.getQuantity() == 0) {
                continue;
            }
            MealPollOptionEntity option = loadOptionForPoll(poll, optionQuantity.getOptionId());
            if (option.getOptionType() == MealPollOptionType.NOT_AVAILABLE) {
                throw new BusinessException(
                        "Use zero quantities when skipping a meal", HttpStatus.BAD_REQUEST);
            }
            totalPlates += optionQuantity.getQuantity();
        }

        if (totalPlates <= 0) {
            throw new BusinessException("Select at least one plate", HttpStatus.BAD_REQUEST);
        }

        responseRepository.deleteByPollIdAndMemberId(poll.getId(), member.getId());
        for (SubmitMealPollOptionQuantityRequest optionQuantity : optionQuantities) {
            if (optionQuantity.getQuantity() == null || optionQuantity.getQuantity() <= 0) {
                continue;
            }
            MealPollOptionEntity option = loadOptionForPoll(poll, optionQuantity.getOptionId());
            responseRepository.save(MealPollResponseEntity.builder()
                    .poll(poll)
                    .member(member)
                    .selectedOption(option)
                    .quantity(optionQuantity.getQuantity())
                    .respondedAt(LocalDateTime.now())
                    .source(MealPollResponseSource.APP)
                    .build());
        }
    }

    private MealPollOptionEntity loadOptionForPoll(MealPollEntity poll, UUID optionId) {
        MealPollOptionEntity option = optionRepository
                .findById(optionId)
                .orElseThrow(() -> new BusinessException("Invalid menu option", HttpStatus.BAD_REQUEST));
        if (!option.getPoll().getId().equals(poll.getId())) {
            throw new BusinessException("Option does not belong to this poll", HttpStatus.BAD_REQUEST);
        }
        return option;
    }

    private List<MealPollOptionEntity> buildOptionsFromMenu(
            MealPollEntity poll, List<DailyMenuEntryEntity> entries, MealType mealType) {
        List<MealPollOptionEntity> options = new ArrayList<>();
        int sortOrder = 1;
        for (DailyMenuEntryEntity entry : entries) {
            options.add(MealPollOptionEntity.builder()
                    .poll(poll)
                    .optionType(MealPollOptionType.MENU_ENTRY)
                    .dailyMenuEntry(entry)
                    .sortOrder(sortOrder++)
                    .label(entry.getLabel())
                    .detail(buildEntryDetail(entry))
                    .build());
        }
        options.add(MealPollOptionEntity.builder()
                .poll(poll)
                .optionType(MealPollOptionType.NOT_AVAILABLE)
                .sortOrder(sortOrder)
                .label("Not available for " + formatMealType(mealType))
                .build());
        return options;
    }

    private String buildEntryDetail(DailyMenuEntryEntity entry) {
        if (entry.getEntryType() == DailyMenuEntryType.COMBO && entry.getCombo() != null) {
            return mealComboItemRepository.findByComboIdWithItems(entry.getCombo().getId()).stream()
                    .map(MealComboItemEntity::getItem)
                    .map(item -> item.getName())
                    .collect(Collectors.joining(", "));
        }
        if (entry.getEntryType() == DailyMenuEntryType.PACKAGE) {
            return dailyMenuPackageItemRepository.findByEntryIdWithItems(entry.getId()).stream()
                    .map(pi -> pi.getItem().getName())
                    .collect(Collectors.joining(", "));
        }
        return null;
    }

    private MealPollEntity loadPoll(UUID spaceId, LocalDate date, MealType mealType) {
        LocalDate pollDate = date != null ? date : LocalDate.now();
        return pollRepository
                .findBySpaceIdAndPollDateAndMealType(spaceId, pollDate, mealType)
                .orElseThrow(() -> new BusinessException("Poll not found for this meal slot", HttpStatus.NOT_FOUND));
    }

    private MealPollResponse toPollResponse(MealPollEntity poll, UUID memberId) {
        List<MealPollOptionEntity> optionEntities =
                optionRepository.findByPollIdWithEntriesOrderBySortOrderAsc(poll.getId());
        List<MealPollOptionResponse> options = optionEntities.stream()
                .map(MealPollOptionResponse::from)
                .toList();

        boolean multiQuantityEnabled = poll.getSpace().getType() == SpaceType.MESS;
        UUID selectedOptionId = null;
        List<MealPollMySelectionResponse> mySelections = List.of();
        UUID myDeliveryLocationId = null;
        String myDeliveryLocationName = null;
        if (memberId != null) {
            List<MealPollResponseEntity> myResponses =
                    responseRepository.findAllByPollIdAndMemberId(poll.getId(), memberId);
            if (multiQuantityEnabled) {
                mySelections = myResponses.stream()
                        .map(response -> MealPollMySelectionResponse.builder()
                                .optionId(response.getSelectedOption().getId())
                                .quantity(response.getQuantity())
                                .build())
                        .toList();
            } else {
                selectedOptionId = myResponses.stream()
                        .findFirst()
                        .map(response -> response.getSelectedOption().getId())
                        .orElse(null);
            }

            Optional<MealPollMemberDeliveryEntity> myDelivery =
                    memberDeliveryRepository.findByPollIdAndMemberId(poll.getId(), memberId);
            if (myDelivery.isPresent()) {
                myDeliveryLocationId = myDelivery.get().getDeliveryLocation().getId();
                myDeliveryLocationName = myDelivery.get().getDeliveryLocation().getName();
            }
        }

        int responseCount = responseRepository.countDistinctRespondingMembers(poll.getId());

        return MealPollResponse.builder()
                .id(poll.getId())
                .pollDate(poll.getPollDate())
                .mealType(poll.getMealType())
                .status(poll.getStatus())
                .dailyMenuId(poll.getDailyMenu().getId())
                .options(options)
                .mySelectedOptionId(selectedOptionId)
                .mySelections(mySelections)
                .multiQuantityEnabled(multiQuantityEnabled)
                .responseCount(responseCount)
                .myDeliveryLocationId(myDeliveryLocationId)
                .myDeliveryLocationName(myDeliveryLocationName)
                .build();
    }

    private UUID resolveDeliveryLocationId(
            SubmitMealPollSelectionRequest selection, MealParticipationEntity participation) {
        if (selection.getDeliveryLocationId() != null) {
            return selection.getDeliveryLocationId();
        }
        return lastDeliveryService.resolveLastDeliveryLocationId(participation, selection.getMealType());
    }

    private void upsertMemberDelivery(MealPollEntity poll, MemberEntity member, UUID deliveryLocationId) {
        MealDeliveryLocationEntity location =
                deliveryLocationService.loadActiveLocation(poll.getSpace().getId(), deliveryLocationId);

        MealPollMemberDeliveryEntity delivery = memberDeliveryRepository
                .findByPollIdAndMemberId(poll.getId(), member.getId())
                .orElse(MealPollMemberDeliveryEntity.builder()
                        .poll(poll)
                        .member(member)
                        .build());
        delivery.setDeliveryLocation(location);
        memberDeliveryRepository.save(delivery);
    }

    private UUID resolveMemberIdIfParticipant(UUID spaceId, UUID callerId) {
        try {
            return mealAccessService.resolveOwnMemberId(spaceId, callerId);
        } catch (BusinessException ex) {
            return null;
        }
    }

    private String formatMealType(MealType mealType) {
        String name = mealType.name().toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
