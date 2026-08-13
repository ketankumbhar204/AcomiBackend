package com.acomi.acomi_backend.meal.application.service;

import com.acomi.acomi_backend.meal.api.dto.response.MealPollPaymentEventResponse;
import com.acomi.acomi_backend.meal.domain.model.MealPollPaymentChoice;
import com.acomi.acomi_backend.meal.domain.model.MealPollPaymentEventType;
import com.acomi.acomi_backend.meal.domain.model.MealPollPaymentStatus;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPollPaymentEventEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPollDayPaymentEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealPollPaymentEventRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MealPollPaymentEventService {

    private final MealPollPaymentEventRepository eventRepository;
    private final MealAccessService mealAccessService;
    private final MemberRepository memberRepository;

    @Transactional
    public void recordEvent(
            SpaceEntity space,
            MemberEntity member,
            LocalDate pollDate,
            MealPollPaymentEventType eventType,
            MealPollPaymentStatus paymentStatus,
            MealPollPaymentChoice paymentChoice,
            BigDecimal amount,
            String remarks,
            UUID actorId) {
        eventRepository.save(MealPollPaymentEventEntity.builder()
                .space(space)
                .member(member)
                .pollDate(pollDate)
                .eventType(eventType)
                .paymentStatus(paymentStatus)
                .paymentChoice(paymentChoice)
                .amount(amount)
                .remarks(trimToNull(remarks))
                .actorId(actorId)
                .build());
    }

    @Transactional
    public void recordFromPayment(
            MealPollDayPaymentEntity payment,
            MealPollPaymentEventType eventType,
            String remarks,
            UUID actorId) {
        recordEvent(
                payment.getSpace(),
                payment.getMember(),
                payment.getPollDate(),
                eventType,
                payment.getPaymentStatus(),
                payment.getPaymentChoice(),
                payment.getPrepaidOverflowAmount(),
                remarks,
                actorId);
    }

    @Transactional(readOnly = true)
    public List<MealPollPaymentEventResponse> listForMemberMonth(
            UUID spaceId, UUID memberId, UUID callerId, String monthParam) {
        MemberEntity member = memberRepository
                .findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));
        mealAccessService.requireViewParticipation(spaceId, memberId, callerId, member);

        YearMonth month = parseMonth(monthParam);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        return eventRepository
                .findBySpaceIdAndMemberIdAndPollDateBetweenOrderByCreatedAtAsc(spaceId, memberId, from, to)
                .stream()
                .map(MealPollPaymentEventResponse::from)
                .toList();
    }

    private YearMonth parseMonth(String monthParam) {
        if (monthParam == null || monthParam.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(monthParam);
        } catch (DateTimeParseException ex) {
            return YearMonth.now();
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
