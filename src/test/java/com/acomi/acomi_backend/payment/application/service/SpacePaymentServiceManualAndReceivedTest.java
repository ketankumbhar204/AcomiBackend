package com.acomi.acomi_backend.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.occupancy.application.service.OccupancyTargetLabelBuilder;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.payment.api.dto.request.CreateSpacePaymentRequest;
import com.acomi.acomi_backend.payment.api.dto.request.MarkPaymentReceivedRequest;
import com.acomi.acomi_backend.payment.domain.model.PaymentTimelineEventType;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentTimelineEventRepository;
import com.acomi.acomi_backend.notification.application.service.PaymentNotificationSyncService;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.storage.application.service.StoredFileService;
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
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class SpacePaymentServiceManualAndReceivedTest {

    @Mock
    private SpacePaymentRepository paymentRepository;

    @Mock
    private SpacePaymentTimelineEventRepository timelineEventRepository;

    @Mock
    private SpacePaymentAccessService accessService;

    @Mock
    private SpacePaymentGenerationService generationService;

    @Mock
    private SpacePaymentTimelineService timelineService;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private OccupancyTargetLabelBuilder occupancyTargetLabelBuilder;

    @Mock
    private PaymentNotificationSyncService paymentNotificationSyncService;

    @Mock
    private MealDaySpacePaymentBridge mealDaySpacePaymentBridge;

    @Mock
    private PaymentMonthSnapshotService snapshotService;

    @Mock
    private PaymentReferenceService paymentReferenceService;

    @Mock
    private StoredFileService storedFileService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private OccupancyRepository occupancyRepository;

    @InjectMocks
    private SpacePaymentService spacePaymentService;

    private UUID spaceId;
    private UUID paymentId;
    private UUID callerId;
    private UUID memberId;
    private SpaceEntity space;
    private MemberEntity member;
    private SpacePaymentEntity pendingPayment;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        space = SpaceEntity.builder().build();
        space.setId(spaceId);

        member = MemberEntity.builder().fullName("Rahul Kumar").role(MembershipRole.TENANT).build();
        member.setId(memberId);

        pendingPayment = SpacePaymentEntity.builder()
                .space(space)
                .member(member)
                .paymentType(SpacePaymentType.OTHER)
                .paymentCategory(SpacePaymentCategory.ELECTRICITY)
                .title("Electricity")
                .amount(new BigDecimal("850"))
                .currencyCode("INR")
                .dueDate(LocalDate.of(2026, 9, 20))
                .month("2026-09")
                .paymentStatus(SpacePaymentStatus.PENDING)
                .build();
        pendingPayment.setId(paymentId);

        lenient().when(mealDaySpacePaymentBridge.resolveMealDates(any())).thenReturn(List.of());
        lenient().when(occupancyRepository.findActiveBySpaceIdAndMemberId(any(), any())).thenReturn(Optional.empty());
        lenient().when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void markReceived_pendingBecomesPaid() {
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId)).thenReturn(Optional.of(pendingPayment));

        var response = spacePaymentService.markReceived(spaceId, paymentId, callerId, new MarkPaymentReceivedRequest());

        assertThat(response.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PAID);
        assertThat(pendingPayment.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PAID);
        verify(timelineService)
                .record(eq(pendingPayment), eq(PaymentTimelineEventType.PAID), any(), eq(callerId));
        verify(accessService).requireManagePayments(spaceId, callerId);
    }

    @Test
    void markReceived_underReviewBecomesPaidWithoutClearingProof() {
        pendingPayment.setPaymentStatus(SpacePaymentStatus.UNDER_REVIEW);
        pendingPayment.setProofFileId(UUID.randomUUID());
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId)).thenReturn(Optional.of(pendingPayment));

        var response = spacePaymentService.markReceived(
                spaceId, paymentId, callerId, new MarkPaymentReceivedRequest());

        assertThat(response.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PAID);
        assertThat(pendingPayment.getProofFileId()).isNotNull();
        verify(timelineService)
                .record(eq(pendingPayment), eq(PaymentTimelineEventType.PAID), any(), eq(callerId));
        verify(mealDaySpacePaymentBridge).syncDayPaymentFromSpaceReview(pendingPayment);
    }

    @Test
    void markReceived_paidIsIdempotent() {
        pendingPayment.setPaymentStatus(SpacePaymentStatus.PAID);
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId)).thenReturn(Optional.of(pendingPayment));

        var response = spacePaymentService.markReceived(
                spaceId, paymentId, callerId, new MarkPaymentReceivedRequest());

        assertThat(response.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PAID);
        verify(timelineService, org.mockito.Mockito.never())
                .record(any(), any(), any(), any());
    }

    @Test
    void markReceived_rejectsInvalidStatus() {
        pendingPayment.setPaymentStatus(SpacePaymentStatus.REJECTED);
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId)).thenReturn(Optional.of(pendingPayment));

        assertThatThrownBy(() ->
                        spacePaymentService.markReceived(
                                spaceId, paymentId, callerId, new MarkPaymentReceivedRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pending or under-review");
    }

    @Test
    void markReceived_rejectsUpdateRequested() {
        pendingPayment.setPaymentStatus(SpacePaymentStatus.UPDATE_REQUESTED);
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId)).thenReturn(Optional.of(pendingPayment));

        assertThatThrownBy(() ->
                        spacePaymentService.markReceived(
                                spaceId, paymentId, callerId, new MarkPaymentReceivedRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pending or under-review");
    }

    @Test
    void markReceived_proofUploadedBecomesPaidWithoutClearingProof() {
        UUID proofId = UUID.randomUUID();
        pendingPayment.setPaymentStatus(SpacePaymentStatus.PROOF_UPLOADED);
        pendingPayment.setProofFileId(proofId);
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId)).thenReturn(Optional.of(pendingPayment));

        var response = spacePaymentService.markReceived(
                spaceId, paymentId, callerId, new MarkPaymentReceivedRequest());

        assertThat(response.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PAID);
        assertThat(pendingPayment.getProofFileId()).isEqualTo(proofId);
    }

    @Test
    void markReceived_wrongSpaceOrUnknownPayment() {
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                        spacePaymentService.markReceived(
                                spaceId, paymentId, callerId, new MarkPaymentReceivedRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Payment not found");
    }

    @Test
    void markReceived_forbiddenForNonManagers() {
        doThrow(new BusinessException("Only OWNER or MANAGER can review payments", HttpStatus.FORBIDDEN))
                .when(accessService)
                .requireManagePayments(spaceId, callerId);

        assertThatThrownBy(() ->
                        spacePaymentService.markReceived(
                                spaceId, paymentId, callerId, new MarkPaymentReceivedRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OWNER or MANAGER");
    }

    @Test
    void createManualPayment_createsPendingElectricity() {
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)).thenReturn(Optional.of(member));

        CreateSpacePaymentRequest request = new CreateSpacePaymentRequest();
        request.setMemberId(memberId);
        request.setPaymentType(SpacePaymentType.OTHER);
        request.setPaymentCategory(SpacePaymentCategory.ELECTRICITY);
        request.setAmount(new BigDecimal("850"));
        request.setMonth("2026-09");
        request.setDueDate(LocalDate.of(2026, 9, 21));

        var response = spacePaymentService.createManualPayment(spaceId, callerId, request);

        assertThat(response.getPaymentType()).isEqualTo(SpacePaymentType.OTHER);
        assertThat(response.getPaymentCategory()).isEqualTo(SpacePaymentCategory.ELECTRICITY);
        assertThat(response.getAmount()).isEqualByComparingTo("850");
        assertThat(response.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PENDING);
        verify(timelineService)
                .record(any(), eq(PaymentTimelineEventType.CREATED), eq("Created manually by owner/manager"), eq(callerId));
    }

    @Test
    void createManualPayment_blocksDuplicateDepositWithoutConfirmation() {
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)).thenReturn(Optional.of(member));
        when(paymentRepository.findBySpaceIdAndMemberIdAndPaymentTypeAndPaymentCategory(
                        spaceId, memberId, SpacePaymentType.DEPOSIT, SpacePaymentCategory.SECURITY))
                .thenReturn(List.of(pendingPayment));

        CreateSpacePaymentRequest request = new CreateSpacePaymentRequest();
        request.setMemberId(memberId);
        request.setPaymentType(SpacePaymentType.DEPOSIT);
        request.setPaymentCategory(SpacePaymentCategory.SECURITY);
        request.setAmount(new BigDecimal("3000"));

        assertThatThrownBy(() -> spacePaymentService.createManualPayment(spaceId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("security deposit already exists");
    }

    @Test
    void createManualPayment_allowsConfirmedDuplicateDeposit() {
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)).thenReturn(Optional.of(member));

        CreateSpacePaymentRequest request = new CreateSpacePaymentRequest();
        request.setMemberId(memberId);
        request.setPaymentType(SpacePaymentType.DEPOSIT);
        request.setPaymentCategory(SpacePaymentCategory.SECURITY);
        request.setAmount(new BigDecimal("1500"));
        request.setConfirmDuplicateDeposit(true);

        var response = spacePaymentService.createManualPayment(spaceId, callerId, request);

        assertThat(response.getPaymentType()).isEqualTo(SpacePaymentType.DEPOSIT);
        assertThat(response.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PENDING);
        ArgumentCaptor<SpacePaymentEntity> captor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("1500");
    }
}
