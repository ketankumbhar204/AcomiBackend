package com.acomi.acomi_backend.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.dashboard.api.dto.response.DashboardFinancialSummaryResponse;
import com.acomi.acomi_backend.dashboard.api.dto.response.MemberPaymentLedgerResponse;
import com.acomi.acomi_backend.dashboard.api.dto.response.MemberPaymentLedgerRowResponse;
import com.acomi.acomi_backend.dashboard.application.service.SpaceBillingService;
import com.acomi.acomi_backend.dashboard.application.support.OccupancyBillingCalculator;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.notification.domain.model.DevicePlatform;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.UserDeviceTokenEntity;
import com.acomi.acomi_backend.notification.infrastructure.persistence.repository.UserDeviceTokenRepository;
import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.occupancy.domain.model.MemberOccupancyStatus;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyPricingBasis;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.payment.api.dto.request.CancelSpacePaymentRequest;
import com.acomi.acomi_backend.payment.api.dto.request.ReissueMonthlyRentRequest;
import com.acomi.acomi_backend.payment.api.dto.request.CreateSpacePaymentRequest;
import com.acomi.acomi_backend.payment.api.dto.request.SettleSpacePaymentRequest;
import com.acomi.acomi_backend.payment.application.job.MonthlyCalendarBillingScheduler;
import com.acomi.acomi_backend.payment.domain.model.MonthlyBillingNotificationStatus;
import com.acomi.acomi_backend.payment.domain.model.MonthlyBillingRunStatus;
import com.acomi.acomi_backend.payment.domain.model.PaymentSettlementAction;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.MonthlyBillingControlEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.MonthlyBillingNotificationEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.MonthlyBillingRunEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.MonthlyBillingControlRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.MonthlyBillingNotificationRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.MonthlyBillingRunRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs {@link MonthlyCalendarBillingScheduler#onFirstOfMonth()} against an empty database.
 * Occupancy setup is persisted. Payment rows asserted below are created only by that job,
 * by move-in activation, or by the existing owner settlement/manual-charge services.
 *
 * <p>Enable with {@code -Dacomi.billing.verify=true} and {@code DB_NAME=acomi_billing_verify}.
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.task.scheduling.enabled=false",
        "spring.jpa.show-sql=false",
        "acomi.billing.monthly-rent.enabled=true",
        "acomi.billing.monthly-rent.catch-up-on-startup=false",
        "acomi.billing.monthly-rent.recovery-months=2",
        "acomi.billing.monthly-rent.zone=Asia/Kolkata"
})
@EnabledIfSystemProperty(named = "acomi.billing.verify", matches = "true")
class MonthlyCalendarBillingSchedulerVerificationTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final AtomicInteger PHONE = new AtomicInteger(813000000);

    @Autowired private MonthlyCalendarBillingScheduler scheduler;
    @Autowired private MonthlyBillingMonitorService monitorService;
    @Autowired private MonthlyBillingNotificationService notificationService;
    @Autowired private SpacePaymentGenerationService generationService;
    @Autowired private SpacePaymentService paymentService;
    @Autowired private PaymentSettlementService settlementService;
    @Autowired private SpaceBillingService billingService;
    @Autowired private UserRepository userRepository;
    @Autowired private SpaceRepository spaceRepository;
    @Autowired private BuildingRepository buildingRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private OccupancyRepository occupancyRepository;
    @Autowired private SpaceMembershipRepository membershipRepository;
    @Autowired private SpacePaymentRepository paymentRepository;
    @Autowired private MonthlyBillingControlRepository controlRepository;
    @Autowired private MonthlyBillingRunRepository runRepository;
    @Autowired private MonthlyBillingNotificationRepository notificationRepository;
    @Autowired private UserDeviceTokenRepository deviceTokenRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void scheduledJobGeneratesCurrentMonthRentOnceAndKeepsSettlementRules() throws Exception {
        YearMonth current = YearMonth.now(KOLKATA);
        YearMonth previous = current.minusMonths(1);
        YearMonth beforeWindow = previous.minusMonths(1);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("kolkataDate", LocalDate.now(KOLKATA).toString());
        evidence.put("currentMonth", current.toString());
        evidence.put("previousMonth", previous.toString());
        evidence.put("beforeWindow", beforeWindow.toString());
        evidence.put("zone", KOLKATA.getId());

        MonthlyBillingControlEntity control = controlRepository.findById((short) 1).orElseThrow();
        control.setEnabledFromMonth(previous.atDay(1));
        controlRepository.save(control);
        evidence.put("enabledFrom", previous.atDay(1).toString());
        evidence.put("recoveryMonths", 2);

        UserEntity owner = user("Verify Owner");
        SpaceEntity ledger = space(SpaceType.PG, "Ledger PG", owner, 5);
        SpaceEntity guard = space(SpaceType.PG, "Guard PG", owner, 5);
        SpaceEntity hostel = space(SpaceType.HOSTEL, "Verify Hostel", owner, 5);
        SpaceEntity coliving = space(SpaceType.CO_LIVING, "Verify Co-living", owner, 5);
        SpaceEntity rental = space(SpaceType.RENTAL, "Verify Rental", owner, 5);
        SpaceEntity corporate = space(SpaceType.CORPORATE_STAY, "Verify Corporate", owner, 5);
        SpaceEntity mess = space(SpaceType.MESS, "Verify Mess", owner, 1);
        membership(owner, ledger);
        member(mess, "Mess Customer", MembershipRole.CUSTOMER, true, null);

        Stay finA = stay(ledger, "Tenant A", current.atDay(1), null, "6000", StayKind.MONTHLY, true);
        Stay finB = stay(ledger, "Tenant B", current.atDay(1), null, "7000", StayKind.MONTHLY, true);
        Stay finC = stay(ledger, "Tenant C", current.atDay(1), null, "5000", StayKind.MONTHLY, true);
        Stay finD = stay(ledger, "Tenant D", current.atDay(1), null, "8000", StayKind.MONTHLY, true);
        Stay finE = stay(ledger, "Tenant E", current.atDay(1), null, "9000", StayKind.MONTHLY, true);
        Stay finF = stay(ledger, "Tenant F", current.atDay(1), null, "4500", StayKind.MONTHLY, true);

        Stay hostelStay = stay(hostel, "Hostel Tenant", current.atDay(1), null, "6100", StayKind.MONTHLY, true);
        Stay colivingStay = stay(coliving, "Co-living Resident", current.atDay(1), null, "6200", StayKind.MONTHLY, true);
        Stay rentalStay = stay(rental, "Rental Tenant", current.atDay(1), null, "6300", StayKind.MONTHLY, true);
        Stay corporateStay = stay(corporate, "Corporate Occupant", current.atDay(1), null, "6400", StayKind.MONTHLY, true);

        Stay moveInFirst = stay(guard, "Moved In On First", current.atDay(1), null, "6000", StayKind.MONTHLY, true);
        generationService.createActivationPayments(moveInFirst.occupancy, owner.getId());
        Stay midCovered = stay(guard, "Mid Month Covered", current.atDay(15), null, "6000", StayKind.MONTHLY, true);
        generationService.createActivationPayments(midCovered.occupancy, owner.getId());
        Stay midOpen = stay(guard, "Mid Month Open", current.atDay(15), null, "4000", StayKind.MONTHLY, true);
        Stay previousOnly = stay(guard, "Previous Month Only", previous.atDay(1), null, "5000", StayKind.MONTHLY, true);
        generationService.createActivationPayments(previousOnly.occupancy, owner.getId());
        Stay vacatedDuringPrevious = stay(
                guard, "Vacated Previous", previous.atDay(1), previous.atDay(20).atTime(12, 0),
                "5500", StayKind.MONTHLY, true);
        Stay vacatedBeforeWindow = stay(
                guard, "Vacated Before Window", beforeWindow.atDay(1), beforeWindow.atEndOfMonth().atTime(18, 0),
                "5100", StayKind.MONTHLY, true);
        Stay shortStay = stay(guard, "Short Stay", current.atDay(1), null, "2000", StayKind.SHORT, true);
        shortStay.occupancy.setAgreedTotal(new BigDecimal("2000"));
        occupancyRepository.save(shortStay.occupancy);
        Stay openEnded = stay(guard, "Open Ended", current.atDay(1), null, "1000", StayKind.OPEN_ENDED, true);
        openEnded.occupancy.setPricingBasis(OccupancyPricingBasis.DAILY_RATE);
        occupancyRepository.save(openEnded.occupancy);
        Stay reserved = reserved(guard, "Reserved", current.atDay(10));
        Stay cancelledReservation = cancelledBeforeMoveIn(guard, "Cancelled Reservation", current.atDay(10));
        Stay inactive = stay(guard, "Inactive Member", current.atDay(1), null, "6600", StayKind.MONTHLY, false);

        List<UUID> beforeIds = monthlyRentIds();
        evidence.put("monthlyRentRowsBeforeScheduler", beforeIds.size());

        scheduler.onFirstOfMonth();

        List<UUID> afterFirst = monthlyRentIds();
        evidence.put("monthlyRentRowsAfterFirstRun", afterFirst.size());
        evidence.put("generatedPaymentIds", afterFirst.stream().filter(id -> !beforeIds.contains(id)).map(UUID::toString).toList());

        Rent firstA = one(finA, current);
        assertThat(firstA.remarks).isEqualTo(SpacePaymentGenerationService.AUTOMATIC_CALENDAR_MONTH_RENT);
        assertThat(firstA.dueDate).isEqualTo(current.atDay(1));
        assertThat(firstA.amount).isEqualByComparingTo("6000.00");
        assertThat(firstA.status).isEqualTo(SpacePaymentStatus.PENDING);
        assertThat(firstA.memberId).isEqualTo(finA.member.getId());
        evidence.put("tenantA", firstA.toMap());

        for (Stay typed : List.of(hostelStay, colivingStay, rentalStay, corporateStay)) {
            Rent row = one(typed, current);
            assertThat(row.dueDate).isEqualTo(current.atDay(1));
            assertThat(row.status).isEqualTo(SpacePaymentStatus.PENDING);
            assertThat(row.memberId).isEqualTo(typed.member.getId());
            assertThat(row.remarks).isEqualTo(SpacePaymentGenerationService.AUTOMATIC_CALENDAR_MONTH_RENT);
        }
        evidence.put("hostel", one(hostelStay, current).toMap());
        evidence.put("coliving", one(colivingStay, current).toMap());
        evidence.put("rental", one(rentalStay, current).toMap());
        evidence.put("corporatePayerIsOccupant", one(corporateStay, current).toMap());

        Rent activated = one(moveInFirst, current);
        assertThat(activated.remarks).contains("activation");
        assertThat(activated.dueDate).isEqualTo(current.atDay(5));
        assertThat(monthly(moveInFirst)).hasSize(1);

        Rent midExisting = one(midCovered, current);
        assertThat(midExisting.dueDate).isEqualTo(current.atDay(15));
        assertThat(monthly(midCovered)).hasSize(1);
        BigDecimal midExpected = OccupancyBillingCalculator.computeBilling(midOpen.occupancy, current, guard).getTotalAmount();
        Rent midGenerated = one(midOpen, current);
        assertThat(midGenerated.amount).isEqualByComparingTo(midExpected);
        assertThat(midGenerated.dueDate).isEqualTo(current.atDay(15));

        assertThat(monthly(previousOnly)).hasSize(2);
        assertThat(one(previousOnly, previous).remarks).contains("activation");
        assertThat(one(previousOnly, current).remarks)
                .isEqualTo(SpacePaymentGenerationService.AUTOMATIC_CALENDAR_MONTH_RENT);
        assertThat(one(previousOnly, current).dueDate).isEqualTo(current.atDay(1));

        assertThat(monthly(vacatedDuringPrevious)).hasSize(1);
        assertThat(one(vacatedDuringPrevious, previous).month).isEqualTo(previous.toString());
        assertThat(monthlyMonths(vacatedBeforeWindow)).isEmpty();
        assertThat(monthly(shortStay)).isEmpty();
        assertThat(monthly(openEnded)).isEmpty();
        assertThat(monthly(reserved)).isEmpty();
        assertThat(monthly(cancelledReservation)).isEmpty();
        assertThat(monthly(inactive)).isEmpty();
        assertThat(paymentRepository.findBySpaceIdAndMonth(mess.getId(), current.toString())).isEmpty();

        scheduler.onFirstOfMonth();
        assertThat(monthlyRentIds()).containsExactlyInAnyOrderElementsOf(afterFirst);

        MonthlyBillingRunEntity ledgerRun = run(ledger.getId(), current);
        MonthlyBillingRunEntity messRun = run(mess.getId(), current);
        assertThat(ledgerRun.getBusinessTimezone()).isEqualTo("Asia/Kolkata");
        assertThat(ledgerRun.getBillingMonth()).isEqualTo(current.atDay(1));
        assertThat(ledgerRun.getStatus()).isEqualTo(MonthlyBillingRunStatus.SUCCESS);
        assertThat(ledgerRun.getPaymentsFailed()).isZero();
        assertThat(messRun.getPaymentsCreated()).isZero();
        assertThat(messRun.getSkipSummary()).contains("MESS_NOT_CALENDAR_MONTH");
        assertThat(runRepository.findBySpaceIdAndBillingMonth(guard.getId(), beforeWindow.atDay(1))).isEmpty();
        evidence.put("ledgerRun", runMap(ledgerRun));
        evidence.put("messRun", runMap(messRun));
        evidence.put("guardPreviousRun", runMap(run(guard.getId(), previous)));
        evidence.put("guardCurrentRun", runMap(run(guard.getId(), current)));

        MemberPaymentLedgerResponse ledgerBeforeSettlement = billingService.buildLedger(
                ledger.getId(), owner.getId(), current.toString());
        evidence.put("ledgerAfterGeneration", summaryMap(ledgerBeforeSettlement.getSummary()));
        evidence.put("membersAfterGeneration", memberMaps(ledgerBeforeSettlement));
        assertMember(ledgerBeforeSettlement, finA, "6000.00", null, "6000.00");
        assertMember(ledgerBeforeSettlement, finB, "7000.00", null, "7000.00");
        assertMember(ledgerBeforeSettlement, finC, "5000.00", null, "5000.00");

        paymentService.markReceived(ledger.getId(), idOf(finB, current), owner.getId(), null);
        MemberPaymentLedgerResponse afterCollection = billingService.buildLedger(
                ledger.getId(), owner.getId(), current.toString());
        evidence.put("ledgerAfterCollection", summaryMap(afterCollection.getSummary()));
        assertMember(afterCollection, finB, "7000.00", "7000.00", null);

        CancelSpacePaymentRequest cancel = new CancelSpacePaymentRequest();
        cancel.setReason("Verification unpaid cancellation");
        settlementService.cancelUnpaidPayment(ledger.getId(), idOf(finC, current), owner.getId(), cancel);
        scheduler.onFirstOfMonth();
        Rent cancelled = one(finC, current);
        assertThat(cancelled.status).isEqualTo(SpacePaymentStatus.CANCELLED);
        assertThat(monthly(finC)).hasSize(1);
        MemberPaymentLedgerResponse afterCancel = billingService.buildLedger(
                ledger.getId(), owner.getId(), current.toString());
        evidence.put("ledgerAfterUnpaidCancellation", summaryMap(afterCancel.getSummary()));
        MemberPaymentLedgerRowResponse cancelledRow = row(afterCancel, finC);
        assertThat(cancelledRow.getExpectedCharges()).isNull();
        assertThat(cancelledRow.getPending()).isNull();

        SettleSpacePaymentRequest full = new SettleSpacePaymentRequest();
        full.setAction(PaymentSettlementAction.FULL_REFUND);
        full.setReason("Verification full refund, obligation row kept");
        settlementService.settleCollectedPayment(ledger.getId(), idOf(finB, current), owner.getId(), full);
        scheduler.onFirstOfMonth();
        assertThat(one(finB, current).status).isEqualTo(SpacePaymentStatus.REFUNDED);
        assertThat(monthly(finB)).hasSize(1);
        MemberPaymentLedgerResponse afterRefund = billingService.buildLedger(
                ledger.getId(), owner.getId(), current.toString());
        evidence.put("ledgerAfterFullRefund", summaryMap(afterRefund.getSummary()));
        evidence.put("tenantBAfterFullRefund", rowMap(row(afterRefund, finB)));
        assertThat(row(afterRefund, finB).getPending()).isNull();
        assertThat(afterRefund.getSummary().getPending()).isEqualByComparingTo("27500.00");

        paymentService.markReceived(ledger.getId(), idOf(finD, current), owner.getId(), null);
        SettleSpacePaymentRequest partial = new SettleSpacePaymentRequest();
        partial.setAction(PaymentSettlementAction.PARTIAL_REFUND);
        partial.setRefundAmount(new BigDecimal("3000.00"));
        partial.setReason("Verification partial refund");
        settlementService.settleCollectedPayment(ledger.getId(), idOf(finD, current), owner.getId(), partial);
        paymentService.markReceived(ledger.getId(), idOf(finE, current), owner.getId(), null);
        SettleSpacePaymentRequest retain = new SettleSpacePaymentRequest();
        retain.setAction(PaymentSettlementAction.RETAIN);
        retain.setReason("Verification retention");
        settlementService.settleCollectedPayment(ledger.getId(), idOf(finE, current), owner.getId(), retain);
        SpacePaymentEntity underReview = paymentRepository.findById(idOf(finF, current)).orElseThrow();
        underReview.setPaymentStatus(SpacePaymentStatus.UNDER_REVIEW);
        paymentRepository.save(underReview);

        MemberPaymentLedgerResponse afterSettlement = billingService.buildLedger(
                ledger.getId(), owner.getId(), current.toString());
        evidence.put("ledgerAfterPartialRetainReview", summaryMap(afterSettlement.getSummary()));
        evidence.put("membersAfterSettlement", memberMaps(afterSettlement));
        assertThat(row(afterSettlement, finD).getCollected()).isEqualByComparingTo("5000.00");
        assertThat(row(afterSettlement, finD).getPending()).isNull();
        assertThat(row(afterSettlement, finE).getCollected()).isEqualByComparingTo("9000.00");
        assertThat(row(afterSettlement, finE).getPending()).isNull();
        assertThat(row(afterSettlement, finF).getUnderReview()).isEqualByComparingTo("4500.00");
        assertThat(row(afterSettlement, finF).getPending()).isNull();
        assertThat(row(afterSettlement, finB).getPending()).isNull();
        assertThat(row(afterSettlement, finA).getPending()).isEqualByComparingTo("6000.00");
        assertThat(afterSettlement.getSummary().getPending()).isEqualByComparingTo("6000.00");

        CreateSpacePaymentRequest monthlyAgain = new CreateSpacePaymentRequest();
        monthlyAgain.setMemberId(finC.member.getId());
        monthlyAgain.setPaymentType(SpacePaymentType.RENT);
        monthlyAgain.setPaymentCategory(SpacePaymentCategory.MONTHLY);
        monthlyAgain.setAmount(new BigDecimal("5000.00"));
        monthlyAgain.setMonth(current.toString());
        monthlyAgain.setDueDate(current.atDay(1));
        monthlyAgain.setRemarks("Attempted reissue of cancelled November rent");
        assertThatThrownBy(() -> paymentService.createManualPayment(ledger.getId(), owner.getId(), monthlyAgain))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("generated automatically");
        assertThat(monthly(finC)).hasSize(1);

        CreateSpacePaymentRequest extra = new CreateSpacePaymentRequest();
        extra.setMemberId(finC.member.getId());
        extra.setPaymentType(SpacePaymentType.RENT);
        extra.setPaymentCategory(SpacePaymentCategory.EXTRA);
        extra.setAmount(new BigDecimal("1500.00"));
        extra.setMonth(current.toString());
        extra.setDueDate(current.atDay(1));
        extra.setTitle("Owner correction");
        extra.setRemarks("Auditable extra charge. This is not a reissued RENT/MONTHLY row.");
        var extraResponse = paymentService.createManualPayment(ledger.getId(), owner.getId(), extra);
        evidence.put("ownerExtraChargeId", extraResponse.getPaymentId().toString());
        evidence.put("ownerExtraCategory", extraResponse.getPaymentCategory().name());
        assertThat(monthly(finC)).hasSize(1);

        UUID cancelledId = cancelled.id;
        ReissueMonthlyRentRequest reissue = new ReissueMonthlyRentRequest();
        reissue.setReason("Verification: cancelled monthly rent was closed by mistake");
        var reissued = paymentService.reissueMonthlyRent(ledger.getId(), cancelledId, owner.getId(), reissue);
        assertThat(reissued.getReplacesPaymentId()).isEqualTo(cancelledId);
        assertThat(reissued.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PENDING);
        assertThat(reissued.getAmount()).isEqualByComparingTo("5000.00");
        Rent stillCancelled = monthly(finC).stream()
                .filter(row -> row.id.equals(cancelledId))
                .findFirst()
                .orElseThrow();
        assertThat(stillCancelled.status).isEqualTo(SpacePaymentStatus.CANCELLED);
        assertThat(stillCancelled.amount).isEqualByComparingTo("5000.00");
        assertThat(monthly(finC)).hasSize(2);
        assertThatThrownBy(() -> paymentService.reissueMonthlyRent(
                        ledger.getId(), cancelledId, owner.getId(), reissue))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
        scheduler.onFirstOfMonth();
        assertThat(monthly(finC)).hasSize(2);
        MemberPaymentLedgerResponse afterReissue = billingService.buildLedger(
                ledger.getId(), owner.getId(), current.toString());
        evidence.put("ledgerAfterReissue", summaryMap(afterReissue.getSummary()));
        evidence.put("reissuedPaymentId", reissued.getPaymentId().toString());
        assertThat(row(afterReissue, finC).getPending()).isEqualByComparingTo("6500.00");
        assertThat(afterReissue.getSummary().getPending()).isEqualByComparingTo("12500.00");

        List<UUID> beforeNotifyRetry = monthlyRentIds();
        MonthlyBillingNotificationEntity notice = notificationRepository
                .findByPaymentIdAndEventType(firstA.id, MonthlyBillingNotificationEntity.EVENT_MONTHLY_RENT_GENERATED)
                .orElseThrow();
        assertThat(notice.getRecipientUserId()).isEqualTo(finA.user.getId());
        assertThat(notice.getRecipientUserId()).isNotEqualTo(owner.getId());
        notificationService.deliverOne(notice.getId());
        notice = notificationRepository.findById(notice.getId()).orElseThrow();
        evidence.put("notificationWithoutToken", Map.of(
                "id", notice.getId().toString(),
                "paymentId", notice.getPaymentId().toString(),
                "recipientUserId", notice.getRecipientUserId().toString(),
                "status", notice.getStatus().name(),
                "attempts", notice.getAttemptCount(),
                "lastError", String.valueOf(notice.getLastError())));
        assertThat(notice.getStatus()).isEqualTo(MonthlyBillingNotificationStatus.PENDING);
        assertThat(monthlyRentIds()).containsExactlyInAnyOrderElementsOf(beforeNotifyRetry);

        String tokenSuffix = finA.user.getId().toString();
        deviceTokenRepository.save(token(finA.user.getId(), "device-a-" + tokenSuffix, "invalid-token-a-" + tokenSuffix));
        deviceTokenRepository.save(token(finA.user.getId(), "device-b-" + tokenSuffix, "invalid-token-b-" + tokenSuffix));
        notificationService.deliverOne(notice.getId());
        notice = notificationRepository.findById(notice.getId()).orElseThrow();
        evidence.put("notificationWithTwoTokens", Map.of(
                "status", notice.getStatus().name(),
                "attempts", notice.getAttemptCount(),
                "lastError", String.valueOf(notice.getLastError()),
                "fcmAcceptedAt", String.valueOf(notice.getFcmAcceptedAt())));
        assertThat(notice.getFcmAcceptedAt()).isNull();
        assertThat(monthlyRentIds()).containsExactlyInAnyOrderElementsOf(beforeNotifyRetry);
        evidence.put("fcmAcceptanceMeansDisplayed", false);

        notice.setStatus(MonthlyBillingNotificationStatus.FAILED);
        notice.setAttemptCount(8);
        notificationRepository.save(notice);
        var retried = monitorService.retryFailedNotifications();
        MonthlyBillingNotificationEntity afterRetry = notificationRepository.findById(notice.getId()).orElseThrow();
        evidence.put("notificationAfterFailedRetry", Map.of(
                "status", afterRetry.getStatus().name(),
                "attempts", afterRetry.getAttemptCount(),
                "pending", retried.getNotificationsPending()));
        assertThat(monthlyRentIds()).containsExactlyInAnyOrderElementsOf(beforeNotifyRetry);

        ledgerRun = run(ledger.getId(), current);
        ledgerRun.setStatus(MonthlyBillingRunStatus.RUNNING);
        ledgerRun.setStartedAt(LocalDateTime.now().minusMinutes(30));
        runRepository.save(ledgerRun);
        scheduler.onFirstOfMonth();
        assertThat(monthlyRentIds()).containsExactlyInAnyOrderElementsOf(beforeNotifyRetry);
        MonthlyBillingRunEntity reclaimed = run(ledger.getId(), current);
        assertThat(reclaimed.getStatus()).isIn(MonthlyBillingRunStatus.SUCCESS, MonthlyBillingRunStatus.PARTIAL);
        evidence.put("reclaimedRun", runMap(reclaimed));

        var admin = monitorService.runNow();
        assertThat(monthlyRentIds()).containsExactlyInAnyOrderElementsOf(beforeNotifyRetry);
        evidence.put("adminRerunLastSuccess", String.valueOf(admin.getLastSuccessfulFinishedAt()));
        evidence.put("adminRunCount", admin.getRuns().size());

        Path evidencePath = Path.of("K:/AcomiMobile/docs/implementation/monthly-billing-verification-evidence.json");
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        Files.createDirectories(evidencePath.getParent());
        mapper.writeValue(evidencePath.toFile(), evidence);
    }

    private void assertMember(
            MemberPaymentLedgerResponse ledger, Stay stay, String expected, String collected, String pending) {
        MemberPaymentLedgerRowResponse row = row(ledger, stay);
        if (expected == null) {
            assertThat(row.getExpectedCharges()).isNull();
        } else {
            assertThat(row.getExpectedCharges()).isEqualByComparingTo(expected);
        }
        if (collected == null) {
            assertThat(row.getCollected()).isNull();
        } else {
            assertThat(row.getCollected()).isEqualByComparingTo(collected);
        }
        if (pending == null) {
            assertThat(row.getPending()).isNull();
        } else {
            assertThat(row.getPending()).isEqualByComparingTo(pending);
        }
    }

    private MemberPaymentLedgerRowResponse row(MemberPaymentLedgerResponse ledger, Stay stay) {
        return ledger.getMembers().stream()
                .filter(member -> stay.member.getId().equals(member.getMemberId()))
                .findFirst()
                .orElseThrow();
    }

    private List<Map<String, Object>> memberMaps(MemberPaymentLedgerResponse ledger) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MemberPaymentLedgerRowResponse row : ledger.getMembers()) {
            rows.add(rowMap(row));
        }
        return rows;
    }

    private Map<String, Object> rowMap(MemberPaymentLedgerRowResponse row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("member", row.getMemberName());
        map.put("expected", money(row.getExpectedCharges()));
        map.put("collected", money(row.getCollected()));
        map.put("pending", money(row.getPending()));
        map.put("underReview", money(row.getUnderReview()));
        return map;
    }

    private Map<String, Object> summaryMap(DashboardFinancialSummaryResponse summary) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("expected", money(summary.getExpectedCharges()));
        map.put("collected", money(summary.getCollected()));
        map.put("pending", money(summary.getPending()));
        map.put("underReview", money(summary.getUnderReview()));
        map.put("source", summary.getSource() != null ? summary.getSource().name() : null);
        return map;
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private UUID idOf(Stay stay, YearMonth month) {
        return one(stay, month).id;
    }

    private Rent one(Stay stay, YearMonth month) {
        return monthly(stay).stream()
                .filter(row -> month.toString().equals(row.month))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No monthly rent for " + stay.member.getFullName() + " " + month));
    }

    private List<String> monthlyMonths(Stay stay) {
        return monthly(stay).stream().map(row -> row.month).toList();
    }

    private List<Rent> monthly(Stay stay) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> paymentRepository.findByOccupancy_Id(stay.occupancy.getId()).stream()
                .filter(payment -> payment.getPaymentType() == SpacePaymentType.RENT
                        && payment.getPaymentCategory() == SpacePaymentCategory.MONTHLY)
                .map(Rent::from)
                .toList());
    }

    private List<UUID> monthlyRentIds() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> paymentRepository.findAll().stream()
                .filter(payment -> payment.getPaymentType() == SpacePaymentType.RENT
                        && payment.getPaymentCategory() == SpacePaymentCategory.MONTHLY)
                .map(SpacePaymentEntity::getId)
                .sorted()
                .toList());
    }

    private MonthlyBillingRunEntity run(UUID spaceId, YearMonth month) {
        return runRepository.findBySpaceIdAndBillingMonth(spaceId, month.atDay(1)).orElseThrow();
    }

    private Map<String, Object> runMap(MonthlyBillingRunEntity run) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", run.getId().toString());
        map.put("spaceId", run.getSpaceId().toString());
        map.put("billingMonth", run.getBillingMonth().toString());
        map.put("timezone", run.getBusinessTimezone());
        map.put("status", run.getStatus().name());
        map.put("eligibleAccommodation", run.getEligibleAccommodation());
        map.put("eligibleMess", run.getEligibleMess());
        map.put("created", run.getPaymentsCreated());
        map.put("skipped", run.getPaymentsSkipped());
        map.put("failed", run.getPaymentsFailed());
        map.put("notificationsQueued", run.getNotificationsQueued());
        map.put("skipSummary", run.getSkipSummary());
        return map;
    }

    private UserDeviceTokenEntity token(UUID userId, String deviceId, String fcmToken) {
        return UserDeviceTokenEntity.builder()
                .userId(userId)
                .deviceId(deviceId)
                .fcmToken(fcmToken)
                .platform(DevicePlatform.ANDROID)
                .active(true)
                .lastSeenAt(LocalDateTime.now())
                .build();
    }

    private SpaceEntity space(SpaceType type, String name, UserEntity owner, int dueDay) {
        SpaceEntity space = spaceRepository.save(SpaceEntity.builder()
                .owner(owner)
                .name(name)
                .type(type)
                .timezone("Asia/Kolkata")
                .isActive(true)
                .billingDueDay(dueDay)
                .build());
        buildingRepository.save(BuildingEntity.builder()
                .space(space)
                .name(name + " building")
                .layoutMode(type == SpaceType.RENTAL || type == SpaceType.CORPORATE_STAY
                        ? PropertyLayoutMode.RENTAL
                        : PropertyLayoutMode.CORRIDOR_PG)
                .isActive(true)
                .build());
        return space;
    }

    private void membership(UserEntity user, SpaceEntity space) {
        membershipRepository.save(SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build());
    }

    private Stay stay(
            SpaceEntity space,
            String name,
            LocalDate moveIn,
            LocalDateTime vacatedAt,
            String rent,
            StayKind stayKind,
            boolean activeMember) {
        UserEntity user = user(name);
        MemberEntity member = member(space, name, MembershipRole.TENANT, activeMember, user);
        BuildingEntity building = buildingRepository.findActiveBySpaceId(space.getId()).get(0);
        OccupancyEntity occupancy = occupancyRepository.save(OccupancyEntity.builder()
                .space(space)
                .member(member)
                .targetType(AllocationTargetType.BED)
                .building(building)
                .allocatedAt(moveIn.atStartOfDay())
                .allocatedBy(space.getOwner())
                .moveInDate(moveIn)
                .actualMoveInAt(moveIn.atStartOfDay())
                .vacatedAt(vacatedAt)
                .stayKind(stayKind)
                .rentSnapshot(new BigDecimal(rent))
                .status(vacatedAt == null ? OccupancyStatus.ACTIVE : OccupancyStatus.VACATED)
                .createdBy(space.getOwner())
                .updatedBy(space.getOwner())
                .build());
        return new Stay(member, user, occupancy);
    }

    private Stay reserved(SpaceEntity space, String name, LocalDate moveIn) {
        UserEntity user = user(name);
        MemberEntity member = member(space, name, MembershipRole.TENANT, true, user);
        BuildingEntity building = buildingRepository.findActiveBySpaceId(space.getId()).get(0);
        OccupancyEntity occupancy = occupancyRepository.save(OccupancyEntity.builder()
                .space(space)
                .member(member)
                .targetType(AllocationTargetType.BED)
                .building(building)
                .allocatedAt(moveIn.atStartOfDay())
                .allocatedBy(space.getOwner())
                .reservedAt(moveIn.minusDays(3).atStartOfDay())
                .moveInDate(moveIn)
                .stayKind(StayKind.MONTHLY)
                .rentSnapshot(new BigDecimal("6000"))
                .status(OccupancyStatus.RESERVED)
                .createdBy(space.getOwner())
                .updatedBy(space.getOwner())
                .build());
        return new Stay(member, user, occupancy);
    }

    private Stay cancelledBeforeMoveIn(SpaceEntity space, String name, LocalDate moveIn) {
        Stay stay = reserved(space, name, moveIn);
        stay.occupancy.setStatus(OccupancyStatus.VACATED);
        stay.occupancy.setVacatedAt(moveIn.minusDays(1).atTime(12, 0));
        stay.occupancy.setActualMoveInAt(null);
        occupancyRepository.save(stay.occupancy);
        return stay;
    }

    private MemberEntity member(
            SpaceEntity space, String name, MembershipRole role, boolean active, UserEntity user) {
        return memberRepository.save(MemberEntity.builder()
                .space(space)
                .user(user)
                .fullName(name)
                .mobileNumber(user != null ? user.getMobileNumber() : phone())
                .role(role)
                .isActive(active)
                .occupancyStatus(MemberOccupancyStatus.ALLOCATED)
                .build());
    }

    private UserEntity user(String name) {
        return userRepository.save(UserEntity.builder()
                .fullName(name)
                .mobileNumber(phone())
                .isActive(true)
                .build());
    }

    private static String phone() {
        return String.valueOf(PHONE.incrementAndGet());
    }

    private record Stay(MemberEntity member, UserEntity user, OccupancyEntity occupancy) {}

    private record Rent(
            UUID id,
            UUID memberId,
            String month,
            BigDecimal amount,
            LocalDate dueDate,
            SpacePaymentStatus status,
            String remarks) {
        static Rent from(SpacePaymentEntity payment) {
            return new Rent(
                    payment.getId(),
                    payment.getMember().getId(),
                    payment.getMonth(),
                    payment.getAmount(),
                    payment.getDueDate(),
                    payment.getPaymentStatus(),
                    payment.getRemarks());
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("paymentId", id.toString());
            map.put("memberId", memberId.toString());
            map.put("month", month);
            map.put("amount", amount.toPlainString());
            map.put("dueDate", dueDate.toString());
            map.put("status", status.name());
            map.put("remarks", remarks);
            return map;
        }
    }
}
