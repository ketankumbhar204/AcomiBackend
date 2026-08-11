package com.amico.amico_backend.meal.application.service;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.common.exception.ResourceNotFoundException;
import com.amico.amico_backend.common.web.PagedResponse;
import com.amico.amico_backend.meal.api.dto.request.CreateMealParticipationRequest;
import com.amico.amico_backend.meal.api.dto.request.UpdateMealParticipationRequest;
import com.amico.amico_backend.meal.api.dto.response.MealParticipationDetailResponse;
import com.amico.amico_backend.meal.api.dto.response.MealParticipationHistoryEntryResponse;
import com.amico.amico_backend.meal.api.dto.response.MealParticipationResponse;
import com.amico.amico_backend.meal.api.dto.response.MemberMealParticipationSummaryResponse;
import com.amico.amico_backend.meal.domain.policy.MealOccupancyPolicy;
import com.amico.amico_backend.meal.domain.model.MealParticipationHistoryAction;
import com.amico.amico_backend.meal.domain.model.MealParticipationStatus;
import com.amico.amico_backend.meal.domain.model.MealPlanCode;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealParticipationHistoryEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPlanEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealParticipationHistoryRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealParticipationRepository;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.amico.amico_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.amico.amico_backend.user.infrastructure.persistence.entity.UserEntity;
import com.amico.amico_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MealParticipationService {

    private final MealParticipationRepository participationRepository;
    private final MealParticipationHistoryRepository historyRepository;
    private final MemberRepository memberRepository;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;
    private final MealPlanService mealPlanService;
    private final MealAccessService mealAccessService;
    private final MealDeliveryLocationService deliveryLocationService;
    private final MealOccupancyPolicy occupancyPolicy;

    @Transactional(readOnly = true)
    public PagedResponse<MealParticipationResponse> listParticipations(
            UUID spaceId,
            UUID callerId,
            MealParticipationStatus status,
            MealPlanCode mealPlanCode,
            String search,
            Pageable pageable) {
        SpaceMembershipEntity membership = mealAccessService.requireViewMeals(spaceId, callerId);
        UUID memberFilter = null;
        if (mealAccessService.isParticipantScopeOnly(membership)) {
            memberFilter = mealAccessService.resolveOwnMemberId(spaceId, callerId);
        }
        String normalizedSearch = normalizeSearch(search);
        Page<MealParticipationEntity> page = participationRepository.search(
                spaceId, status, mealPlanCode, memberFilter, normalizedSearch, pageable);
        return PagedResponse.from(page.map(MealParticipationResponse::from));
    }

    @Transactional(readOnly = true)
    public MealParticipationDetailResponse getParticipation(UUID spaceId, UUID participationId, UUID callerId) {
        MealParticipationEntity participation = loadParticipation(spaceId, participationId);
        mealAccessService.requireViewParticipation(
                spaceId, participation.getMember().getId(), callerId, participation.getMember());
        return toDetail(participation);
    }

    @Transactional(readOnly = true)
    public MealParticipationDetailResponse getMemberParticipation(UUID spaceId, UUID memberId, UUID callerId) {
        MemberEntity member = loadMember(spaceId, memberId);
        mealAccessService.requireViewParticipation(spaceId, memberId, callerId, member);
        return participationRepository
                .findBySpaceIdAndMemberIdAndStatus(spaceId, memberId, MealParticipationStatus.ACTIVE)
                .map(this::toDetail)
                .orElse(MealParticipationDetailResponse.builder()
                        .participation(null)
                        .history(java.util.List.of())
                        .build());
    }

    @Transactional(readOnly = true)
    public Optional<MemberMealParticipationSummaryResponse> findActiveSummaryForMember(UUID spaceId, UUID memberId) {
        return participationRepository
                .findBySpaceIdAndMemberIdAndStatus(spaceId, memberId, MealParticipationStatus.ACTIVE)
                .map(MemberMealParticipationSummaryResponse::from);
    }

    @Transactional
    public MealParticipationResponse enroll(UUID spaceId, UUID callerId, CreateMealParticipationRequest request) {
        mealAccessService.requireManageParticipation(spaceId, callerId);
        UserEntity actor = loadUser(callerId);
        SpaceEntity space = loadSpace(spaceId);
        MemberEntity member = loadMember(spaceId, request.getMemberId());
        assertOccupancyForEnrollment(space, member, request.getEffectiveFrom());
        assertNoActiveParticipation(spaceId, member.getId());

        MealPlanEntity plan = mealPlanService.loadPlan(spaceId, request.getMealPlanId());
        MealParticipationEntity participation = MealParticipationEntity.builder()
                .space(space)
                .member(member)
                .mealPlan(plan)
                .status(MealParticipationStatus.ACTIVE)
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .defaultDeliveryLocation(resolveDefaultDeliveryLocation(space, request.getDefaultDeliveryLocationId()))
                .build();
        participation = participationRepository.save(participation);
        recordHistory(participation, MealParticipationHistoryAction.CREATED, null, plan.getCode().name(), actor);
        return MealParticipationResponse.from(participation);
    }

    @Transactional
    public MealParticipationResponse updateParticipation(
            UUID spaceId, UUID participationId, UUID callerId, UpdateMealParticipationRequest request) {
        mealAccessService.requireManageParticipation(spaceId, callerId);
        UserEntity actor = loadUser(callerId);
        MealParticipationEntity participation = loadParticipation(spaceId, participationId);

        if (request.getMealPlanId() != null
                && !request.getMealPlanId().equals(participation.getMealPlan().getId())) {
            MealPlanEntity newPlan = mealPlanService.loadPlan(spaceId, request.getMealPlanId());
            String oldCode = participation.getMealPlan().getCode().name();
            participation.setMealPlan(newPlan);
            recordHistory(participation, MealParticipationHistoryAction.PLAN_CHANGED, oldCode, newPlan.getCode().name(), actor);
        }
        if (request.getEffectiveFrom() != null) {
            participation.setEffectiveFrom(request.getEffectiveFrom());
        }
        if (request.getEffectiveTo() != null) {
            participation.setEffectiveTo(request.getEffectiveTo());
        }
        if (request.getStatus() != null && request.getStatus() != participation.getStatus()) {
            applyStatusChange(participation, request.getStatus(), actor);
        }
        if (request.getDefaultDeliveryLocationId() != null) {
            participation.setDefaultDeliveryLocation(
                    resolveDefaultDeliveryLocation(participation.getSpace(), request.getDefaultDeliveryLocationId()));
        }
        return MealParticipationResponse.from(participationRepository.save(participation));
    }

    @Transactional
    public MealParticipationResponse pause(UUID spaceId, UUID participationId, UUID callerId) {
        return changeStatus(spaceId, participationId, callerId, MealParticipationStatus.PAUSED);
    }

    @Transactional
    public MealParticipationResponse resume(UUID spaceId, UUID participationId, UUID callerId) {
        return changeStatus(spaceId, participationId, callerId, MealParticipationStatus.ACTIVE);
    }

    @Transactional
    public MealParticipationResponse stop(UUID spaceId, UUID participationId, UUID callerId) {
        return changeStatus(spaceId, participationId, callerId, MealParticipationStatus.STOPPED);
    }

    @Transactional
    public void createFromOccupancy(OccupancyEntity occupancy, UserEntity actor) {
        UUID spaceId = occupancy.getSpace().getId();
        UUID memberId = occupancy.getMember().getId();
        if (participationRepository.existsBySpaceIdAndMemberIdAndStatus(
                spaceId, memberId, MealParticipationStatus.ACTIVE)) {
            return;
        }
        MealPlanEntity fullPlan = mealPlanService.ensurePresetPlans(spaceId);
        MealParticipationEntity participation = MealParticipationEntity.builder()
                .space(occupancy.getSpace())
                .member(occupancy.getMember())
                .mealPlan(fullPlan)
                .status(MealParticipationStatus.ACTIVE)
                .effectiveFrom(occupancy.getMoveInDate() != null ? occupancy.getMoveInDate() : LocalDate.now())
                .sourceOccupancy(occupancy)
                .build();
        participation = participationRepository.save(participation);
        recordHistory(participation, MealParticipationHistoryAction.CREATED, null, MealPlanCode.FULL.name(), actor);
    }

    @Transactional
    public void stopOnVacate(MemberEntity member, UserEntity actor) {
        UUID spaceId = member.getSpace().getId();
        participationRepository
                .findBySpaceIdAndMemberIdAndStatus(spaceId, member.getId(), MealParticipationStatus.ACTIVE)
                .ifPresent(participation -> {
                    applyStatusChange(participation, MealParticipationStatus.STOPPED, actor);
                    participationRepository.save(participation);
                });
    }

    /**
     * Enables default meal access when a member joins via invitation (Mess customer / PG tenant).
     */
    @Transactional
    public void enrollDefaultForMemberIfEligible(MemberEntity member, UserEntity actor) {
        if (!isDefaultMealEligible(member)) {
            return;
        }

        UUID spaceId = member.getSpace().getId();
        UUID memberId = member.getId();

        if (participationRepository.existsBySpaceIdAndMemberIdAndStatus(
                spaceId, memberId, MealParticipationStatus.ACTIVE)) {
            return;
        }

        Optional<MealParticipationEntity> paused = participationRepository.findBySpaceIdAndMemberIdAndStatus(
                spaceId, memberId, MealParticipationStatus.PAUSED);
        if (paused.isPresent()) {
            applyStatusChange(paused.get(), MealParticipationStatus.ACTIVE, actor);
            participationRepository.save(paused.get());
            return;
        }

        MealPlanEntity fullPlan = mealPlanService.ensurePresetPlans(spaceId);
        MealParticipationEntity participation = MealParticipationEntity.builder()
                .space(member.getSpace())
                .member(member)
                .mealPlan(fullPlan)
                .status(MealParticipationStatus.ACTIVE)
                .effectiveFrom(LocalDate.now())
                .build();
        participation = participationRepository.save(participation);
        recordHistory(participation, MealParticipationHistoryAction.CREATED, null, MealPlanCode.FULL.name(), actor);
    }

    private static boolean isDefaultMealEligible(MemberEntity member) {
        SpaceType spaceType = member.getSpace().getType();
        MembershipRole role = member.getRole();
        return spaceType == SpaceType.MESS && role == MembershipRole.CUSTOMER;
    }

    private void assertOccupancyForEnrollment(SpaceEntity space, MemberEntity member, LocalDate effectiveFrom) {
        if (!occupancyPolicy.requiresActiveOccupancy(space.getType())) {
            return;
        }
        LocalDate targetDate = effectiveFrom != null ? effectiveFrom : LocalDate.now();
        if (!occupancyPolicy.hasOccupancyOnDate(space, member.getId(), targetDate)) {
            throw new BusinessException(
                    "Move in the tenant to a bed before enabling meals",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private MealParticipationResponse changeStatus(
            UUID spaceId, UUID participationId, UUID callerId, MealParticipationStatus status) {
        mealAccessService.requireManageParticipation(spaceId, callerId);
        UserEntity actor = loadUser(callerId);
        MealParticipationEntity participation = loadParticipation(spaceId, participationId);
        applyStatusChange(participation, status, actor);
        return MealParticipationResponse.from(participationRepository.save(participation));
    }

    private void applyStatusChange(
            MealParticipationEntity participation, MealParticipationStatus newStatus, UserEntity actor) {
        MealParticipationStatus oldStatus = participation.getStatus();
        if (oldStatus == newStatus) {
            return;
        }
        if (newStatus == MealParticipationStatus.ACTIVE) {
            assertNoActiveParticipation(
                    participation.getSpace().getId(),
                    participation.getMember().getId(),
                    participation.getId());
        }
        participation.setStatus(newStatus);
        if (newStatus == MealParticipationStatus.STOPPED) {
            participation.setStoppedAt(LocalDateTime.now());
            recordHistory(
                    participation,
                    MealParticipationHistoryAction.STOPPED,
                    oldStatus.name(),
                    newStatus.name(),
                    actor);
        } else {
            recordHistory(
                    participation,
                    MealParticipationHistoryAction.STATUS_CHANGED,
                    oldStatus.name(),
                    newStatus.name(),
                    actor);
        }
    }

    private MealParticipationDetailResponse toDetail(MealParticipationEntity participation) {
        return MealParticipationDetailResponse.builder()
                .participation(MealParticipationResponse.from(participation))
                .history(historyRepository.findByParticipationIdOrderByChangedAtDesc(participation.getId()).stream()
                        .map(entry -> MealParticipationHistoryEntryResponse.builder()
                                .id(entry.getId())
                                .action(entry.getAction())
                                .oldValue(entry.getOldValue())
                                .newValue(entry.getNewValue())
                                .changedBy(entry.getChangedBy().getId())
                                .changedAt(entry.getChangedAt())
                                .build())
                        .toList())
                .build();
    }

    private void recordHistory(
            MealParticipationEntity participation,
            MealParticipationHistoryAction action,
            String oldValue,
            String newValue,
            UserEntity actor) {
        historyRepository.save(MealParticipationHistoryEntity.builder()
                .participation(participation)
                .space(participation.getSpace())
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .changedBy(actor)
                .changedAt(LocalDateTime.now())
                .build());
    }

    private void assertNoActiveParticipation(UUID spaceId, UUID memberId) {
        assertNoActiveParticipation(spaceId, memberId, null);
    }

    private void assertNoActiveParticipation(UUID spaceId, UUID memberId, UUID excludeId) {
        Optional<MealParticipationEntity> existing =
                participationRepository.findBySpaceIdAndMemberIdAndStatus(
                        spaceId, memberId, MealParticipationStatus.ACTIVE);
        if (existing.isPresent() && (excludeId == null || !existing.get().getId().equals(excludeId))) {
            throw new BusinessException("Member already has an active meal participation", HttpStatus.CONFLICT);
        }
    }

    private MealParticipationEntity loadParticipation(UUID spaceId, UUID participationId) {
        return participationRepository
                .findByIdAndSpaceId(participationId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("MealParticipation", "id", participationId));
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

    private UserEntity loadUser(UUID userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private com.amico.amico_backend.meal.infrastructure.persistence.entity.MealDeliveryLocationEntity
            resolveDefaultDeliveryLocation(SpaceEntity space, UUID locationId) {
        if (locationId == null) {
            return null;
        }
        if (space.getType() != SpaceType.MESS) {
            throw new BusinessException(
                    "Default delivery location is only available for mess spaces", HttpStatus.BAD_REQUEST);
        }
        return deliveryLocationService.loadActiveLocation(space.getId(), locationId);
    }

    private static String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }
}
