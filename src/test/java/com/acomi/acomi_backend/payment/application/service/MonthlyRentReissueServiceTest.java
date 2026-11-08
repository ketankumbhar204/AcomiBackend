package com.acomi.acomi_backend.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.payment.api.dto.request.ReissueMonthlyRentRequest;
import com.acomi.acomi_backend.payment.domain.model.PaymentTimelineEventType;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class MonthlyRentReissueServiceTest {

    @Mock private SpacePaymentRepository paymentRepository;
    @Mock private com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentTimelineEventRepository timelineEventRepository;
    @Mock private SpacePaymentAccessService accessService;
    @Mock private SpacePaymentGenerationService generationService;
    @Mock private SpacePaymentTimelineService timelineService;
    @Mock private com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository spaceRepository;
    @Mock private com.acomi.acomi_backend.occupancy.application.service.OccupancyTargetLabelBuilder occupancyTargetLabelBuilder;
    @Mock private com.acomi.acomi_backend.notification.application.service.PaymentNotificationSyncService paymentNotificationSyncService;
    @Mock private MealDaySpacePaymentBridge mealDaySpacePaymentBridge;
    @Mock private PaymentMonthSnapshotService snapshotService;
    @Mock private PaymentReferenceService paymentReferenceService;
    @Mock private com.acomi.acomi_backend.storage.application.service.StoredFileService storedFileService;
    @Mock private com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository memberRepository;
    @Mock private com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository occupancyRepository;

    @InjectMocks private SpacePaymentService service;

    private final UUID spaceId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private SpacePaymentEntity original;

    @BeforeEach
    void setUp() {
        SpaceEntity space = SpaceEntity.builder().name("Sunrise PG").timezone("Asia/Kolkata").build();
        space.setId(spaceId);
        MemberEntity member = MemberEntity.builder().fullName("Tenant A").mobileNumber("9000000001").build();
        member.setId(UUID.randomUUID());
        original = SpacePaymentEntity.builder()
                .space(space)
                .member(member)
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .paymentStatus(SpacePaymentStatus.CANCELLED)
                .title("Rent — November 2026")
                .amount(new BigDecimal("6000.00"))
                .currencyCode("INR")
                .dueDate(LocalDate.of(2026, 11, 1))
                .month("2026-11")
                .build();
        original.setId(paymentId);
    }

    @Test
    void reissueCreatesALinkedPendingRowAndLeavesTheOriginalCancelled() {
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId)).thenReturn(Optional.of(original));
        when(paymentRepository.findAllBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        spaceId, original.getMember().getId(), "2026-11",
                        SpacePaymentType.RENT, SpacePaymentCategory.MONTHLY))
                .thenReturn(List.of(original));
        when(paymentRepository.findByRelatedPayment_Id(paymentId)).thenReturn(List.of());
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            SpacePaymentEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        ReissueMonthlyRentRequest request = new ReissueMonthlyRentRequest();
        request.setReason("Cancelled by mistake");
        var response = service.reissueMonthlyRent(spaceId, paymentId, callerId, request);

        assertThat(original.getPaymentStatus()).isEqualTo(SpacePaymentStatus.CANCELLED);
        assertThat(original.getAmount()).isEqualByComparingTo("6000.00");
        assertThat(response.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PENDING);
        assertThat(response.getReplacesPaymentId()).isEqualTo(paymentId);
        assertThat(response.getAmount()).isEqualByComparingTo("6000.00");
        ArgumentCaptor<SpacePaymentEntity> saved = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getRelatedPayment()).isSameAs(original);
        verify(timelineService).record(
                any(),
                org.mockito.ArgumentMatchers.eq(PaymentTimelineEventType.CREATED),
                org.mockito.ArgumentMatchers.contains("Cancelled by mistake"),
                org.mockito.ArgumentMatchers.eq(callerId));
        verify(generationService, never()).ensureAutomaticMonthlyRent(any(), any(), any());
    }

    @Test
    void secondReissueIsRejectedWhileTheReplacementIsOpen() {
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId)).thenReturn(Optional.of(original));
        SpacePaymentEntity open = SpacePaymentEntity.builder()
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .paymentStatus(SpacePaymentStatus.PENDING)
                .amount(new BigDecimal("6000.00"))
                .relatedPayment(original)
                .build();
        when(paymentRepository.findAllBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        any(), any(), any(), any(), any()))
                .thenReturn(List.of(original, open));

        ReissueMonthlyRentRequest request = new ReissueMonthlyRentRequest();
        request.setReason("Again");
        assertThatThrownBy(() -> service.reissueMonthlyRent(spaceId, paymentId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void pendingRentCannotBeReissued() {
        original.setPaymentStatus(SpacePaymentStatus.PENDING);
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId)).thenReturn(Optional.of(original));
        ReissueMonthlyRentRequest request = new ReissueMonthlyRentRequest();
        request.setReason("Not closed");
        assertThatThrownBy(() -> service.reissueMonthlyRent(spaceId, paymentId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cancelled or fully refunded");
        verify(paymentRepository, never()).save(any());
    }
}
