package com.countin.countin_backend.meal.application.service;

import com.countin.countin_backend.common.exception.BusinessException;
import com.countin.countin_backend.common.exception.ResourceNotFoundException;
import com.countin.countin_backend.meal.api.dto.request.UpdateMealDeliveryConfigRequest;
import com.countin.countin_backend.meal.api.dto.response.MealDeliveryConfigResponse;
import com.countin.countin_backend.meal.api.dto.response.MealDeliveryLocationResponse;
import com.countin.countin_backend.meal.domain.model.MealParticipationStatus;
import com.countin.countin_backend.meal.domain.model.MealType;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealDeliveryLocationEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealParticipationDeliveryAllowedEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealParticipationDeliveryDefaultEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealParticipationDeliveryAllowedRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealParticipationDeliveryDefaultRepository;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealParticipationRepository;
import com.countin.countin_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.countin.countin_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.countin.countin_backend.space.domain.model.SpaceType;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.countin.countin_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MealParticipationDeliveryConfigurationService {

    private static final String STATUS_CONFIGURED = "CONFIGURED";
    private static final String STATUS_SETUP_REQUIRED = "SETUP_REQUIRED";

    private final MealParticipationRepository participationRepository;
    private final MealParticipationDeliveryAllowedRepository allowedRepository;
    private final MealParticipationDeliveryDefaultRepository defaultRepository;
    private final MemberRepository memberRepository;
    private final SpaceRepository spaceRepository;
    private final MealAccessService mealAccessService;
    private final MealDeliveryLocationService deliveryLocationService;

    @Transactional(readOnly = true)
    public MealDeliveryConfigResponse getConfig(UUID spaceId, UUID memberId, UUID callerId) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        requireMessSpace(spaceId);
        loadMember(spaceId, memberId);
        MealParticipationEntity participation = requireActiveParticipation(spaceId, memberId);
        return toResponse(memberId, participation);
    }

    @Transactional
    public MealDeliveryConfigResponse replaceConfig(
            UUID spaceId, UUID memberId, UUID callerId, UpdateMealDeliveryConfigRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        requireMessSpace(spaceId);
        loadMember(spaceId, memberId);
        MealParticipationEntity participation = requireActiveParticipation(spaceId, memberId);

        Set<MealType> seen = EnumSet.noneOf(MealType.class);
        for (UpdateMealDeliveryConfigRequest.MealDeliveryConfigMealRequest meal : request.getMeals()) {
            if (meal.getMealType() == null) {
                throw new BusinessException("Meal type is required", HttpStatus.BAD_REQUEST);
            }
            if (!seen.add(meal.getMealType())) {
                throw new BusinessException(
                        "Duplicate meal type in delivery configuration: " + meal.getMealType(),
                        HttpStatus.BAD_REQUEST);
            }
            replaceMealConfig(participation, meal);
        }

        return toResponse(memberId, participation);
    }

    private void replaceMealConfig(
            MealParticipationEntity participation,
            UpdateMealDeliveryConfigRequest.MealDeliveryConfigMealRequest meal) {
        MealType mealType = meal.getMealType();
        List<UUID> rawIds = meal.getAllowedLocationIds() != null ? meal.getAllowedLocationIds() : List.of();
        LinkedHashSet<UUID> allowedIds = new LinkedHashSet<>(rawIds);

        List<MealDeliveryLocationEntity> allowedLocations = new ArrayList<>();
        for (UUID locationId : allowedIds) {
            allowedLocations.add(
                    deliveryLocationService.loadActiveLocation(participation.getSpace().getId(), locationId));
        }

        UUID defaultLocationId = meal.getDefaultLocationId();
        if (allowedLocations.isEmpty()) {
            if (defaultLocationId != null) {
                throw new BusinessException(
                        "Default location requires at least one allowed location for " + mealType,
                        HttpStatus.BAD_REQUEST);
            }
        } else if (defaultLocationId != null) {
            boolean defaultAllowed = allowedIds.contains(defaultLocationId);
            if (!defaultAllowed) {
                throw new BusinessException(
                        "Default location must be one of the allowed locations for " + mealType,
                        HttpStatus.BAD_REQUEST);
            }
            deliveryLocationService.loadActiveLocation(participation.getSpace().getId(), defaultLocationId);
        }

        allowedRepository.deleteByParticipationIdAndMealType(participation.getId(), mealType);
        defaultRepository.deleteByParticipationIdAndMealType(participation.getId(), mealType);
        allowedRepository.flush();
        defaultRepository.flush();

        for (MealDeliveryLocationEntity location : allowedLocations) {
            allowedRepository.save(MealParticipationDeliveryAllowedEntity.builder()
                    .participation(participation)
                    .mealType(mealType)
                    .deliveryLocation(location)
                    .build());
        }

        if (defaultLocationId != null) {
            MealDeliveryLocationEntity defaultLocation = allowedLocations.stream()
                    .filter(loc -> loc.getId().equals(defaultLocationId))
                    .findFirst()
                    .orElseThrow();
            defaultRepository.save(MealParticipationDeliveryDefaultEntity.builder()
                    .participation(participation)
                    .mealType(mealType)
                    .deliveryLocation(defaultLocation)
                    .build());
        }
    }

    private MealDeliveryConfigResponse toResponse(UUID memberId, MealParticipationEntity participation) {
        Map<MealType, List<MealParticipationDeliveryAllowedEntity>> allowedByMeal =
                new EnumMap<>(MealType.class);
        for (MealParticipationDeliveryAllowedEntity row :
                allowedRepository.findByParticipationId(participation.getId())) {
            allowedByMeal
                    .computeIfAbsent(row.getMealType(), ignored -> new ArrayList<>())
                    .add(row);
        }

        Map<MealType, UUID> defaultByMeal = new EnumMap<>(MealType.class);
        for (MealParticipationDeliveryDefaultEntity row :
                defaultRepository.findByParticipationId(participation.getId())) {
            defaultByMeal.put(row.getMealType(), row.getDeliveryLocation().getId());
        }

        List<MealDeliveryConfigResponse.MealDeliveryConfigMealResponse> meals = new ArrayList<>();
        boolean allConfigured = true;
        for (MealType mealType : MealType.values()) {
            List<MealParticipationDeliveryAllowedEntity> allowedRows =
                    allowedByMeal.getOrDefault(mealType, List.of());
            List<UUID> allowedIds = allowedRows.stream()
                    .map(row -> row.getDeliveryLocation().getId())
                    .toList();
            List<MealDeliveryLocationResponse> allowedLocations = allowedRows.stream()
                    .map(row -> MealDeliveryLocationResponse.from(row.getDeliveryLocation()))
                    .toList();
            UUID defaultLocationId = defaultByMeal.get(mealType);
            String status = mealStatus(allowedIds, defaultLocationId);
            if (!STATUS_CONFIGURED.equals(status)) {
                allConfigured = false;
            }
            meals.add(MealDeliveryConfigResponse.MealDeliveryConfigMealResponse.builder()
                    .mealType(mealType)
                    .allowedLocationIds(allowedIds)
                    .allowedLocations(allowedLocations)
                    .defaultLocationId(defaultLocationId)
                    .status(status)
                    .build());
        }

        return MealDeliveryConfigResponse.builder()
                .memberId(memberId)
                .participationId(participation.getId())
                .meals(meals)
                .overallStatus(allConfigured ? STATUS_CONFIGURED : STATUS_SETUP_REQUIRED)
                .build();
    }

    private static String mealStatus(List<UUID> allowedIds, UUID defaultLocationId) {
        if (allowedIds != null && !allowedIds.isEmpty() && defaultLocationId != null) {
            return STATUS_CONFIGURED;
        }
        return STATUS_SETUP_REQUIRED;
    }

    private MealParticipationEntity requireActiveParticipation(UUID spaceId, UUID memberId) {
        return participationRepository
                .findBySpaceIdAndMemberIdAndStatus(spaceId, memberId, MealParticipationStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        "Active meal participation is required to configure delivery locations",
                        HttpStatus.BAD_REQUEST));
    }

    private MemberEntity loadMember(UUID spaceId, UUID memberId) {
        return memberRepository
                .findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));
    }

    private SpaceEntity requireMessSpace(UUID spaceId) {
        SpaceEntity space = spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new BusinessException("Space not found", HttpStatus.NOT_FOUND));
        if (space.getType() != SpaceType.MESS) {
            throw new BusinessException(
                    "Delivery configuration is only available for mess spaces", HttpStatus.BAD_REQUEST);
        }
        return space;
    }
}
