package com.acomi.acomi_backend.meal.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.meal.api.dto.request.CreateSubscriptionActivationRequest;
import com.acomi.acomi_backend.meal.api.dto.request.RecordMealBalancePurchaseRequest;
import com.acomi.acomi_backend.meal.api.dto.request.ResolveSubscriptionActivationRequest;
import com.acomi.acomi_backend.meal.api.dto.response.CustomerSubscriptionStatusResponse;
import com.acomi.acomi_backend.meal.api.dto.response.SubscriptionActivationRequestResponse;
import com.acomi.acomi_backend.meal.domain.model.SubscriptionActivationRequestStatus;
import com.acomi.acomi_backend.meal.domain.policy.MemberSubscriptionPolicy;
import com.acomi.acomi_backend.meal.domain.model.MealBalanceLedgerEntryType;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MemberMealBalanceEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MemberMealBalanceLedgerEntryEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SubscriptionActivationRequestEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SubscriptionPlanEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MemberMealBalanceLedgerRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MemberMealBalanceRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.SubscriptionActivationRequestRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.space.domain.model.MealBillingType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionActivationRequestService {

    private final SubscriptionActivationRequestRepository requestRepository;
    private final SubscriptionPlanService planService;
    private final MemberMealBalanceService mealBalanceService;
    private final MemberRepository memberRepository;
    private final SpaceRepository spaceRepository;
    private final MealAccessService mealAccessService;
    private final MemberSubscriptionPolicy subscriptionPolicy;
    private final MemberMealBalanceRepository balanceRepository;
    private final MemberMealBalanceLedgerRepository ledgerRepository;

    @Transactional(readOnly = true)
    public List<SubscriptionActivationRequestResponse> listPendingForSpace(UUID spaceId, UUID callerId) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        return requestRepository
                .findBySpaceIdAndStatusOrderByCreatedAtDesc(spaceId, SubscriptionActivationRequestStatus.PENDING)
                .stream()
                .map(SubscriptionActivationRequestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionActivationRequestResponse> listForMember(
            UUID spaceId, UUID memberId, UUID callerId) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        MemberEntity member = loadMember(spaceId, memberId);
        mealAccessService.requireViewParticipation(spaceId, memberId, callerId, member);
        return requestRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(SubscriptionActivationRequestResponse::from)
                .toList();
    }

    @Transactional
    public SubscriptionActivationRequestResponse createRequest(
            UUID spaceId, UUID memberId, UUID callerId, CreateSubscriptionActivationRequest request) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        UUID ownMemberId = mealAccessService.resolveOwnMemberId(spaceId, callerId);
        if (!ownMemberId.equals(memberId)) {
            throw new BusinessException("You can only request a subscription for yourself", HttpStatus.FORBIDDEN);
        }
        MemberEntity member = loadMember(spaceId, memberId);
        SpaceEntity space = loadSpace(spaceId);
        if (!subscriptionPolicy.isPrepaidBilling(space, member)) {
            throw new BusinessException(
                    "Subscription plans are only available for subscription billing members",
                    HttpStatus.BAD_REQUEST);
        }
        if (subscriptionPolicy.hasActiveSubscription(spaceId, memberId)) {
            throw new BusinessException("You already have an active subscription", HttpStatus.BAD_REQUEST);
        }
        requestRepository
                .findFirstByMemberIdAndStatusOrderByCreatedAtDesc(
                        memberId, SubscriptionActivationRequestStatus.PENDING)
                .ifPresent(existing -> {
                    throw new BusinessException(
                            "You already have a pending subscription request", HttpStatus.CONFLICT);
                });

        SubscriptionPlanEntity plan = planService.loadPlan(spaceId, request.getPlanId());
        if (!plan.isActive()) {
            throw new BusinessException("Selected plan is no longer available", HttpStatus.BAD_REQUEST);
        }

        SubscriptionActivationRequestEntity entity = requestRepository.save(
                SubscriptionActivationRequestEntity.builder()
                        .space(space)
                        .member(member)
                        .plan(plan)
                        .status(SubscriptionActivationRequestStatus.PENDING)
                        .paymentReference(trim(request.getPaymentReference()))
                        .paymentProofImageUrl(normalizeProofImage(request.getProofImageBase64()))
                        .customerNotes(trim(request.getCustomerNotes()))
                        .build());
        return SubscriptionActivationRequestResponse.from(entity);
    }

    @Transactional
    public SubscriptionActivationRequestResponse approve(
            UUID spaceId, UUID requestId, UUID callerId, ResolveSubscriptionActivationRequest body) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SubscriptionActivationRequestEntity entity = loadRequest(spaceId, requestId);
        if (entity.getStatus() != SubscriptionActivationRequestStatus.PENDING) {
            throw new BusinessException("Request is no longer pending", HttpStatus.BAD_REQUEST);
        }
        SubscriptionPlanEntity plan = entity.getPlan();
        RecordMealBalancePurchaseRequest purchase = new RecordMealBalancePurchaseRequest();
        purchase.setAmount(BigDecimal.valueOf(plan.getMealsIncluded()));
        purchase.setPaidAmount(plan.getPrice());
        purchase.setValidTill(LocalDate.now().plusDays(plan.getValidityDays()));
        purchase.setReplaceBalance(true);
        purchase.setRemarks("Plan: " + plan.getName());
        mealBalanceService.recordPurchase(spaceId, entity.getMember().getId(), callerId, purchase);

        entity.setStatus(SubscriptionActivationRequestStatus.APPROVED);
        entity.setOwnerNotes(trim(body != null ? body.getOwnerNotes() : null));
        entity.setResolvedBy(callerId);
        entity.setResolvedAt(LocalDateTime.now());
        return SubscriptionActivationRequestResponse.from(requestRepository.save(entity));
    }

    @Transactional
    public SubscriptionActivationRequestResponse reject(
            UUID spaceId, UUID requestId, UUID callerId, ResolveSubscriptionActivationRequest body) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SubscriptionActivationRequestEntity entity = loadRequest(spaceId, requestId);
        if (entity.getStatus() != SubscriptionActivationRequestStatus.PENDING) {
            throw new BusinessException("Request is no longer pending", HttpStatus.BAD_REQUEST);
        }
        entity.setStatus(SubscriptionActivationRequestStatus.REJECTED);
        entity.setOwnerNotes(trim(body != null ? body.getOwnerNotes() : null));
        entity.setResolvedBy(callerId);
        entity.setResolvedAt(LocalDateTime.now());
        return SubscriptionActivationRequestResponse.from(requestRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public CustomerSubscriptionStatusResponse getMyCustomerStatus(UUID spaceId, UUID callerId) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        UUID memberId = mealAccessService.resolveOwnMemberId(spaceId, callerId);
        return getCustomerStatus(spaceId, memberId, callerId);
    }

    @Transactional(readOnly = true)
    public CustomerSubscriptionStatusResponse getCustomerStatus(UUID spaceId, UUID memberId, UUID callerId) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        MemberEntity member = loadMember(spaceId, memberId);
        mealAccessService.requireViewParticipation(spaceId, memberId, callerId, member);
        SpaceEntity space = loadSpace(spaceId);
        MealBillingType billing = member.getMealBillingType() != null
                ? member.getMealBillingType()
                : space.getMealBillingType();
        boolean prepaid = billing == MealBillingType.PREPAID_BALANCE;
        String lifecycle = prepaid ? subscriptionPolicy.resolveLifecycleStatus(spaceId, memberId) : "pay_per_meal";

        var pending = requestRepository.findFirstByMemberIdAndStatusOrderByCreatedAtDesc(
                memberId, SubscriptionActivationRequestStatus.PENDING);
        Optional<MemberMealBalanceEntity> balanceOpt =
                balanceRepository.findBySpaceIdAndMemberId(spaceId, memberId);
        MemberMealBalanceLedgerEntryEntity lastPurchase = balanceOpt
                .flatMap(balance -> balance.getId() != null
                        ? ledgerRepository.findFirstByBalanceIdAndEntryTypeOrderByCreatedAtDesc(
                                balance.getId(), MealBalanceLedgerEntryType.PURCHASE)
                        : Optional.empty())
                .orElse(null);
        LocalDate validTillDate = lastPurchase != null ? resolveValidTill(lastPurchase) : null;
        Integer mealsRemaining = balanceOpt
                .map(MemberMealBalanceEntity::getBalance)
                .map(BigDecimal::intValue)
                .orElse(null);

        return CustomerSubscriptionStatusResponse.builder()
                .mealBillingType(billing)
                .prepaidBilling(prepaid)
                .subscriptionActive(prepaid && subscriptionPolicy.hasActiveSubscription(spaceId, memberId))
                .lifecycleStatus(lifecycle)
                .validTill(validTillDate != null ? validTillDate.atStartOfDay() : null)
                .endedAt(balanceOpt.map(MemberMealBalanceEntity::getSubscriptionEndedAt).orElse(null))
                .mealsRemaining(mealsRemaining)
                .pendingActivationStatus(
                        pending.map(SubscriptionActivationRequestEntity::getStatus).orElse(null))
                .pendingActivationRequestId(pending.map(SubscriptionActivationRequestEntity::getId).orElse(null))
                .pendingPlanName(pending.map(r -> r.getPlan().getName()).orElse(null))
                .build();
    }

    private static LocalDate resolveValidTill(MemberMealBalanceLedgerEntryEntity lastPurchase) {
        if (lastPurchase.getValidTill() != null) {
            return lastPurchase.getValidTill();
        }
        if (lastPurchase.getCreatedAt() == null) {
            return null;
        }
        return lastPurchase.getCreatedAt().toLocalDate().with(TemporalAdjusters.lastDayOfMonth());
    }

    private SubscriptionActivationRequestEntity loadRequest(UUID spaceId, UUID requestId) {
        return requestRepository
                .findByIdAndSpaceId(requestId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionActivationRequest", "id", requestId));
    }

    private MemberEntity loadMember(UUID spaceId, UUID memberId) {
        return memberRepository
                .findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));
    }

    private SpaceEntity loadSpace(UUID spaceId) {
        return spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeProofImage(String proofImageBase64) {
        if (proofImageBase64 == null || proofImageBase64.isBlank()) {
            return null;
        }
        String normalized = proofImageBase64.trim();
        if (!normalized.startsWith("data:image/")) {
            throw new BusinessException("Payment screenshot must be an image", HttpStatus.BAD_REQUEST);
        }
        if (normalized.length() > 4_000_000) {
            throw new BusinessException("Payment screenshot is too large", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }
}
