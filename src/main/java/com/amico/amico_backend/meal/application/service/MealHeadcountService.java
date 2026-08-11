package com.amico.amico_backend.meal.application.service;



import com.amico.amico_backend.common.exception.BusinessException;

import com.amico.amico_backend.meal.api.dto.response.MealHeadcountDeliveryLocationResponse;
import com.amico.amico_backend.meal.api.dto.response.MealHeadcountDayResponse;

import com.amico.amico_backend.meal.api.dto.response.MealHeadcountDetailResponse;

import com.amico.amico_backend.meal.api.dto.response.MealHeadcountMemberResponse;

import com.amico.amico_backend.meal.api.dto.response.MealHeadcountOptionResponse;
import com.amico.amico_backend.meal.api.dto.response.MealPollOptionResponse;

import com.amico.amico_backend.meal.api.dto.response.MealHeadcountSlotResponse;

import com.amico.amico_backend.meal.domain.model.MealPollOptionType;

import com.amico.amico_backend.meal.domain.model.MealPollPaymentStatus;

import com.amico.amico_backend.meal.domain.model.MealType;

import com.amico.amico_backend.meal.domain.policy.MealPollEligibilityPolicy;
import com.amico.amico_backend.meal.domain.policy.MealOccupancyPolicy;

import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;

import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPollDayPaymentEntity;

import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPollEntity;

import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPollMemberDeliveryEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPollOptionEntity;

import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPollResponseEntity;

import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealParticipationRepository;

import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealPollDayPaymentRepository;

import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealPollOptionRepository;

import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealPollMemberDeliveryRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealPollRepository;

import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealPollResponseRepository;

import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;

import com.amico.amico_backend.space.domain.model.SpaceType;

import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;

import java.time.LocalDate;

import java.util.HashMap;
import java.util.ArrayList;

import java.util.Comparator;

import java.util.List;

import java.util.Map;

import java.util.Set;

import java.util.UUID;

import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;



@Service

@RequiredArgsConstructor

public class MealHeadcountService {



    private final MealPollRepository pollRepository;

    private final MealPollOptionRepository optionRepository;

    private final MealPollResponseRepository responseRepository;

    private final MealPollDayPaymentRepository dayPaymentRepository;

    private final MealPollMemberDeliveryRepository memberDeliveryRepository;

    private final MealParticipationRepository participationRepository;

    private final MealAccessService mealAccessService;

    private final SpaceRepository spaceRepository;

    private final MealPollEligibilityPolicy pollEligibilityPolicy;

    private final MealOccupancyPolicy occupancyPolicy;



    @Transactional(readOnly = true)

    public MealHeadcountDayResponse getDaySummary(UUID spaceId, UUID callerId, LocalDate date) {

        mealAccessService.requireViewMeals(spaceId, callerId);

        LocalDate targetDate = date != null ? date : LocalDate.now();

        boolean quantityMode = isMessSpace(spaceId);



        List<MealHeadcountSlotResponse> slots = new ArrayList<>();
        for (MealPollEntity poll :
                pollRepository.findBySpaceIdAndPollDateOrderByMealTypeAsc(spaceId, targetDate)) {
            slots.add(MealHeadcountSlotResponse.builder()
                    .mealType(poll.getMealType())
                    .pollId(poll.getId())
                    .pollStatus(poll.getStatus())
                    .mealsToPrepare(countMealsToPrepare(poll.getId(), quantityMode))
                    .build());
        }

        return MealHeadcountDayResponse.builder().date(targetDate).slots(slots).build();
    }



    @Transactional(readOnly = true)

    public MealHeadcountDetailResponse getMealDetail(

            UUID spaceId, UUID callerId, LocalDate date, MealType mealType) {

        mealAccessService.requireManageMeals(spaceId, callerId);

        LocalDate targetDate = date != null ? date : LocalDate.now();

        boolean quantityMode = isMessSpace(spaceId);



        MealPollEntity poll = pollRepository

                .findBySpaceIdAndPollDateAndMealType(spaceId, targetDate, mealType)

                .orElseThrow(() -> new BusinessException("Poll not found for this meal slot", HttpStatus.NOT_FOUND));



        List<MealPollOptionEntity> optionEntities =

                optionRepository.findByPollIdWithEntriesOrderBySortOrderAsc(poll.getId());

        List<MealPollResponseEntity> responses =

                responseRepository.findByPollIdWithMemberAndOption(poll.getId());

        Map<UUID, MealPollDayPaymentEntity> paymentsByMember = loadPaymentsByMember(spaceId, targetDate);

        Map<UUID, MealPollMemberDeliveryEntity> deliveriesByMember =
                memberDeliveryRepository.findByPollIdWithLocation(poll.getId()).stream()
                        .collect(Collectors.toMap(
                                delivery -> delivery.getMember().getId(), delivery -> delivery, (left, right) -> left));



        Map<UUID, List<MealPollResponseEntity>> responsesByOption = responses.stream()

                .collect(Collectors.groupingBy(response -> response.getSelectedOption().getId()));



        List<MealHeadcountOptionResponse> options = optionEntities.stream()

                .map(option -> {

                    List<MealPollResponseEntity> optionResponses =

                            responsesByOption.getOrDefault(option.getId(), List.of());

                    List<MealHeadcountMemberResponse> members = optionResponses.stream()

                            .map(response -> toMemberResponse(response, quantityMode, paymentsByMember, deliveriesByMember))

                            .sorted(Comparator.comparing(MealHeadcountMemberResponse::getMemberName))

                            .toList();

                    int count = quantityMode

                            ? optionResponses.stream().mapToInt(MealPollResponseEntity::getQuantity).sum()

                            : members.size();

                    MealPollOptionResponse optionMeta = MealPollOptionResponse.from(option);

                    return MealHeadcountOptionResponse.builder()

                            .optionId(option.getId())

                            .optionType(option.getOptionType())

                            .sortOrder(option.getSortOrder())

                            .label(option.getLabel())

                            .detail(option.getDetail())

                            .price(optionMeta.getPrice())

                            .currencyCode(optionMeta.getCurrencyCode())

                            .count(count)

                            .members(members)

                            .build();

                })

                .toList();



        Set<UUID> respondedMemberIds = responses.stream()

                .map(response -> response.getMember().getId())

                .collect(Collectors.toSet());



        List<MealHeadcountMemberResponse> noResponseMembers =

                listEligibleMembers(spaceId, targetDate, mealType).stream()

                        .filter(member -> !respondedMemberIds.contains(member.getId()))

                        .map(this::toMemberResponse)

                        .sorted(Comparator.comparing(MealHeadcountMemberResponse::getMemberName))

                        .toList();



        int eligibleCount = listEligibleMembers(spaceId, targetDate, mealType).size();

        List<MealHeadcountDeliveryLocationResponse> deliveryBreakdown =
                quantityMode ? buildDeliveryBreakdown(responses, deliveriesByMember, quantityMode) : List.of();



        return MealHeadcountDetailResponse.builder()

                .date(targetDate)

                .mealType(mealType)

                .pollId(poll.getId())

                .pollStatus(poll.getStatus())

                .mealsToPrepare(countMealsToPrepare(poll.getId(), quantityMode))

                .eligibleCount(eligibleCount)

                .options(options)

                .noResponseMembers(noResponseMembers)

                .deliveryBreakdown(deliveryBreakdown)

                .build();

    }



    private int countMealsToPrepare(UUID pollId, boolean quantityMode) {

        List<MealPollResponseEntity> responses = responseRepository.findByPollIdWithMemberAndOption(pollId);

        return responses.stream()

                .filter(response -> response.getSelectedOption().getOptionType()

                        != MealPollOptionType.NOT_AVAILABLE)

                .mapToInt(response -> quantityMode ? response.getQuantity() : 1)

                .sum();

    }



    private boolean isMessSpace(UUID spaceId) {

        return spaceRepository

                .findById(spaceId)

                .map(space -> space.getType() == SpaceType.MESS)

                .orElse(false);

    }



    private List<MemberEntity> listEligibleMembers(UUID spaceId, LocalDate date, MealType mealType) {

        SpaceEntity space = spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new BusinessException("Space not found", HttpStatus.NOT_FOUND));
        Set<UUID> occupiedMemberIds = occupancyPolicy
                .occupiedMemberIdsForDate(space, date)
                .orElse(null);
        List<MealParticipationEntity> participations = participationRepository.findAllNonStoppedBySpaceId(spaceId);

        return participations.stream()

                .filter(participation -> pollEligibilityPolicy.isPollEligible(
                        participation, date, mealType, occupiedMemberIds))

                .map(MealParticipationEntity::getMember)

                .toList();

    }



    private Map<UUID, MealPollDayPaymentEntity> loadPaymentsByMember(UUID spaceId, LocalDate pollDate) {
        return dayPaymentRepository.findBySpaceIdAndPollDate(spaceId, pollDate).stream()
                .collect(Collectors.toMap(
                        payment -> payment.getMember().getId(),
                        payment -> payment,
                        (left, right) -> left));
    }

    private MealHeadcountMemberResponse toMemberResponse(
            MealPollResponseEntity response,
            boolean quantityMode,
            Map<UUID, MealPollDayPaymentEntity> paymentsByMember,
            Map<UUID, MealPollMemberDeliveryEntity> deliveriesByMember) {
        MealPollDayPaymentEntity payment = paymentsByMember.get(response.getMember().getId());
        String proofImageUrl = null;
        if (payment != null && payment.getPaymentStatus() == MealPollPaymentStatus.PENDING_APPROVAL) {
            proofImageUrl = payment.getProofImageUrl();
        }

        MealPollMemberDeliveryEntity delivery = deliveriesByMember.get(response.getMember().getId());

        return MealHeadcountMemberResponse.builder()
                .memberId(response.getMember().getId())
                .memberName(response.getMember().getFullName())
                .quantity(quantityMode ? response.getQuantity() : 1)
                .paymentStatus(payment != null ? payment.getPaymentStatus() : null)
                .paymentProofImageUrl(proofImageUrl)
                .deliveryLocationId(
                        delivery != null ? delivery.getDeliveryLocation().getId() : null)
                .deliveryLocationName(
                        delivery != null ? delivery.getDeliveryLocation().getName() : null)
                .build();
    }

    private List<MealHeadcountDeliveryLocationResponse> buildDeliveryBreakdown(
            List<MealPollResponseEntity> responses,
            Map<UUID, MealPollMemberDeliveryEntity> deliveriesByMember,
            boolean quantityMode) {
        Map<UUID, Integer> platesByMember = responses.stream()
                .filter(response -> response.getSelectedOption().getOptionType() != MealPollOptionType.NOT_AVAILABLE)
                .collect(Collectors.groupingBy(
                        response -> response.getMember().getId(),
                        Collectors.summingInt(response -> quantityMode ? response.getQuantity() : 1)));

        Map<UUID, MealHeadcountDeliveryLocationResponse> buckets = new HashMap<>();
        for (Map.Entry<UUID, Integer> entry : platesByMember.entrySet()) {
            MealPollMemberDeliveryEntity delivery = deliveriesByMember.get(entry.getKey());
            if (delivery == null) {
                continue;
            }
            UUID locationId = delivery.getDeliveryLocation().getId();
            MealHeadcountDeliveryLocationResponse existing = buckets.get(locationId);
            if (existing == null) {
                buckets.put(
                        locationId,
                        MealHeadcountDeliveryLocationResponse.builder()
                                .locationId(locationId)
                                .locationName(delivery.getDeliveryLocation().getName())
                                .totalPlates(entry.getValue())
                                .build());
            } else {
                buckets.put(
                        locationId,
                        MealHeadcountDeliveryLocationResponse.builder()
                                .locationId(locationId)
                                .locationName(existing.getLocationName())
                                .totalPlates(existing.getTotalPlates() + entry.getValue())
                                .build());
            }
        }

        return buckets.values().stream()
                .sorted(Comparator.comparing(MealHeadcountDeliveryLocationResponse::getLocationName))
                .toList();
    }



    private MealHeadcountMemberResponse toMemberResponse(MemberEntity member) {

        return MealHeadcountMemberResponse.builder()

                .memberId(member.getId())

                .memberName(member.getFullName())

                .quantity(0)

                .build();

    }

}


