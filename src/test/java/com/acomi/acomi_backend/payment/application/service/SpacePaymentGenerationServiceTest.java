package com.acomi.acomi_backend.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.dashboard.application.support.PayPerMealBillingCalculator;
import com.acomi.acomi_backend.meal.application.support.MealBillingResolver;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealParticipationRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.occupancy.application.service.OccupancyTargetLabelBuilder;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpacePaymentGenerationServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpacePaymentRepository paymentRepository;

    @Mock
    private OccupancyRepository occupancyRepository;

    @Mock
    private MealParticipationRepository participationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PayPerMealBillingCalculator payPerMealBillingCalculator;

    @Mock
    private MealBillingResolver mealBillingResolver;

    @Mock
    private SpacePaymentTimelineService timelineService;

    @Mock
    private SpacePaymentAccessService accessService;

    @Mock
    private OccupancyTargetLabelBuilder occupancyTargetLabelBuilder;

    @InjectMocks
    private SpacePaymentGenerationService generationService;

    private UUID spaceId;
    private UUID memberId;
    private UUID callerId;
    private SpaceEntity space;
    private MemberEntity member;
    private OccupancyEntity occupancy;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        callerId = UUID.randomUUID();

        space = SpaceEntity.builder().type(SpaceType.PG).billingDueDay(1).taxEnabled(false).build();
        space.setId(spaceId);

        member = MemberEntity.builder().fullName("Rahul Kumar").space(space).build();
        member.setId(memberId);

        occupancy = OccupancyEntity.builder()
                .space(space)
                .member(member)
                .status(OccupancyStatus.ACTIVE)
                .moveInDate(LocalDate.of(2026, 9, 1))
                .actualMoveInAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .rentSnapshot(new BigDecimal("3400"))
                .depositSnapshot(new BigDecimal("3000"))
                .foodEnabled(false)
                .foodIncludedInRent(false)
                .build();
        occupancy.setId(UUID.randomUUID());

        lenient()
                .when(occupancyTargetLabelBuilder.build(any()))
                .thenReturn("Bed A");
        lenient()
                .when(paymentRepository.findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(paymentRepository.save(any())).thenAnswer(invocation -> {
            SpacePaymentEntity saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
    }

    @Test
    void createActivationPayments_createsRentAndOneTimeDeposit() {
        generationService.createActivationPayments(occupancy, callerId);

        ArgumentCaptor<SpacePaymentEntity> captor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());

        List<SpacePaymentEntity> saved = captor.getAllValues();
        assertThat(saved)
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.RENT);
                    assertThat(payment.getAmount()).isEqualByComparingTo("3400");
                    assertThat(payment.getMonth()).isEqualTo("2026-09");
                });
        assertThat(saved)
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.DEPOSIT);
                    assertThat(payment.getPaymentCategory()).isEqualTo(SpacePaymentCategory.SECURITY);
                    assertThat(payment.getAmount()).isEqualByComparingTo("3000");
                    assertThat(payment.getMonth()).isEqualTo("2026-09");
                });
    }

    @Test
    void syncExpectedPayments_doesNotCreateDepositAfterMoveInMonth() {
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(accessService.requireActiveMembership(spaceId, callerId))
                .thenReturn(SpaceMembershipEntity.builder().build());
        when(accessService.isOwnScopeOnly(any())).thenReturn(false);
        when(occupancyRepository.findBillableBySpaceIdForMonth(eq(spaceId), any(), any(), any()))
                .thenReturn(List.of(occupancy));

        generationService.syncExpectedPayments(spaceId, callerId, YearMonth.of(2026, 10));

        verify(paymentRepository, never())
                .findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        spaceId,
                        memberId,
                        "2026-10",
                        SpacePaymentType.DEPOSIT,
                        SpacePaymentCategory.SECURITY);
        verify(paymentRepository)
                .findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        spaceId,
                        memberId,
                        "2026-10",
                        SpacePaymentType.RENT,
                        SpacePaymentCategory.MONTHLY);
    }
}
