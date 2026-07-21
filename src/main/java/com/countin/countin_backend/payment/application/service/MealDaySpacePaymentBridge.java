package com.countin.countin_backend.payment.application.service;

import com.countin.countin_backend.meal.domain.model.MealPollPaymentStatus;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealPollDayPaymentEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealPollDayPaymentRepository;
import com.countin.countin_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.countin.countin_backend.notification.application.service.PaymentNotificationSyncService;
import com.countin.countin_backend.payment.domain.model.PaymentTimelineEventType;
import com.countin.countin_backend.payment.domain.model.SpacePaymentCategory;
import com.countin.countin_backend.payment.domain.model.SpacePaymentStatus;
import com.countin.countin_backend.payment.domain.model.SpacePaymentType;
import com.countin.countin_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.countin.countin_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mess pay-per-meal customers submit proof on {@link MealPollDayPaymentEntity}. Owner Payments /
 * Pending Review reads {@link SpacePaymentEntity}. This bridge mirrors day proofs into daily meal
 * space payments so the owner review queue is not empty.
 *
 * <p>Single-day proofs map 1:1 to a DAILY space payment. Bulk proofs (shared {@code paymentBatchId})
 * map to one space payment whose amount is the sum of the batch days.
 */
@Service
@RequiredArgsConstructor
public class MealDaySpacePaymentBridge {

    private static final DateTimeFormatter DAY_TITLE =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_SHORT =
            DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final SpacePaymentRepository paymentRepository;
    private final MealPollDayPaymentRepository dayPaymentRepository;
    private final SpacePaymentTimelineService timelineService;
    private final PaymentNotificationSyncService paymentNotificationSyncService;

    @Transactional
    public void mirrorProofSubmitted(MealPollDayPaymentEntity dayPayment, UUID actorUserId) {
        if (dayPayment == null || dayPayment.getPaymentStatus() != MealPollPaymentStatus.PENDING_APPROVAL) {
            return;
        }
        // Bulk days are mirrored once via {@link #mirrorBulkProofSubmitted}.
        if (hasBatchId(dayPayment)) {
            return;
        }
        SpacePaymentEntity payment = upsertDailyMealPayment(dayPayment);
        applyProofFields(payment, dayPayment);
        boolean wasReview = isUnderReview(payment);
        payment.setPaymentStatus(SpacePaymentStatus.UNDER_REVIEW);
        clearReviewFields(payment);
        paymentRepository.save(payment);
        recordProofTimeline(payment, dayPayment.getRemarks(), actorUserId, wasReview);
        paymentNotificationSyncService.onProofSubmitted(payment, actorUserId, wasReview);
    }

    /**
     * Mirror a multi-day proof into a single space payment (amount = sum of day charges).
     */
    @Transactional
    public void mirrorBulkProofSubmitted(
            List<MealPollDayPaymentEntity> dayPayments, String batchId, UUID actorUserId) {
        if (dayPayments == null || dayPayments.isEmpty() || batchId == null || batchId.isBlank()) {
            return;
        }
        List<MealPollDayPaymentEntity> pending = dayPayments.stream()
                .filter(day -> day != null && day.getPaymentStatus() == MealPollPaymentStatus.PENDING_APPROVAL)
                .sorted(Comparator.comparing(MealPollDayPaymentEntity::getPollDate))
                .toList();
        if (pending.isEmpty()) {
            return;
        }

        MealPollDayPaymentEntity first = pending.get(0);
        SpaceEntity space = first.getSpace();
        MemberEntity member = first.getMember();
        LocalDate earliest = first.getPollDate();
        LocalDate latest = pending.get(pending.size() - 1).getPollDate();
        String monthKey = YearMonth.from(earliest).toString();
        BigDecimal total = pending.stream()
                .map(day -> safeAmount(day.getChargedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SpacePaymentEntity payment = paymentRepository
                .findBySpaceIdAndPaymentBatchId(space.getId(), batchId)
                .orElseGet(() -> {
                    SpacePaymentEntity created = SpacePaymentEntity.builder()
                            .space(space)
                            .member(member)
                            .paymentType(SpacePaymentType.MEAL)
                            .paymentCategory(SpacePaymentCategory.DAILY)
                            .title(bulkTitle(pending.size(), earliest, latest))
                            .amount(total)
                            .currencyCode("INR")
                            .dueDate(earliest)
                            .month(monthKey)
                            .paymentBatchId(batchId)
                            .paymentStatus(SpacePaymentStatus.PENDING)
                            .build();
                    paymentRepository.save(created);
                    timelineService.record(created, PaymentTimelineEventType.CREATED, null, null);
                    return created;
                });

        boolean wasReview = isUnderReview(payment);
        payment.setTitle(bulkTitle(pending.size(), earliest, latest));
        payment.setAmount(total);
        payment.setDueDate(earliest);
        payment.setMonth(monthKey);
        payment.setPaymentBatchId(batchId);
        applyProofFields(payment, first);
        payment.setPaymentStatus(SpacePaymentStatus.UNDER_REVIEW);
        clearReviewFields(payment);
        paymentRepository.save(payment);
        recordProofTimeline(payment, first.getRemarks(), actorUserId, wasReview);
        paymentNotificationSyncService.onProofSubmitted(payment, actorUserId, wasReview);
    }

    @Transactional
    public void mirrorDayApproved(MealPollDayPaymentEntity dayPayment, UUID actorUserId) {
        SpacePaymentEntity payment = findMirroredPayment(dayPayment)
                .orElseGet(() -> upsertDailyMealPayment(dayPayment));
        if (!hasBatchId(dayPayment)) {
            payment.setAmount(safeAmount(dayPayment.getChargedAmount()));
        }
        payment.setProofUrl(dayPayment.getProofImageUrl());
        payment.setPaymentStatus(SpacePaymentStatus.PAID);
        payment.setPaymentDate(dayPayment.getPollDate());
        payment.setReviewedAt(LocalDateTime.now());
        payment.setReviewedBy(actorUserId);
        payment.setRejectionReason(null);
        payment.setRejectionCode(null);
        paymentRepository.save(payment);
        if (hasBatchId(dayPayment)) {
            syncAllBatchDays(payment);
        }
        timelineService.record(payment, PaymentTimelineEventType.APPROVED, null, actorUserId);
        timelineService.record(payment, PaymentTimelineEventType.PAID, null, actorUserId);
        paymentNotificationSyncService.onApproved(payment, actorUserId);
    }

    @Transactional
    public void mirrorDayRejected(MealPollDayPaymentEntity dayPayment, UUID actorUserId, String reason) {
        SpacePaymentEntity payment = findMirroredPayment(dayPayment)
                .orElseGet(() -> upsertDailyMealPayment(dayPayment));
        if (!hasBatchId(dayPayment)) {
            payment.setAmount(safeAmount(dayPayment.getChargedAmount()));
        }
        payment.setProofUrl(dayPayment.getProofImageUrl());
        payment.setPaymentStatus(SpacePaymentStatus.REJECTED);
        payment.setReviewedAt(LocalDateTime.now());
        payment.setReviewedBy(actorUserId);
        payment.setRejectionReason(reason);
        paymentRepository.save(payment);
        if (hasBatchId(dayPayment)) {
            syncAllBatchDays(payment);
        }
        timelineService.record(payment, PaymentTimelineEventType.REJECTED, reason, actorUserId);
        paymentNotificationSyncService.onRejected(payment, actorUserId, reason);
    }

    /**
     * When owner reviews a mirrored daily meal payment from Payments UI, keep the day row(s) in sync.
     */
    @Transactional
    public void syncDayPaymentFromSpaceReview(SpacePaymentEntity payment) {
        if (!isDailyMeal(payment)) {
            return;
        }
        if (payment.getPaymentBatchId() != null && !payment.getPaymentBatchId().isBlank()) {
            syncAllBatchDays(payment);
            return;
        }
        MealPollDayPaymentEntity day = dayPaymentRepository
                .findBySpaceIdAndMemberIdAndPollDate(
                        payment.getSpace().getId(), payment.getMember().getId(), payment.getDueDate())
                .orElse(null);
        if (day == null) {
            return;
        }
        applySpaceStatusToDay(day, payment);
        dayPaymentRepository.save(day);
    }

    /** Poll dates covered by this space payment (batch days or single due date). */
    @Transactional(readOnly = true)
    public List<LocalDate> resolveMealDates(SpacePaymentEntity payment) {
        if (payment == null || !isDailyMeal(payment)) {
            return List.of();
        }
        if (payment.getPaymentBatchId() != null && !payment.getPaymentBatchId().isBlank()) {
            return dayPaymentRepository.findByPaymentBatchId(payment.getPaymentBatchId()).stream()
                    .map(MealPollDayPaymentEntity::getPollDate)
                    .sorted()
                    .toList();
        }
        return payment.getDueDate() != null ? List.of(payment.getDueDate()) : List.of();
    }

    /**
     * Ensure outstanding day proofs appear in Payments Pending Review (for proofs submitted
     * before the bridge existed).
     */
    @Transactional
    public void backfillPendingApprovalsForMonth(UUID spaceId, YearMonth month, UUID actorUserId) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        List<MealPollDayPaymentEntity> pending = dayPaymentRepository.findPendingApprovalInDateRange(spaceId, from, to);
        for (MealPollDayPaymentEntity day : pending) {
            if (hasBatchId(day)) {
                List<MealPollDayPaymentEntity> batchDays =
                        dayPaymentRepository.findByPaymentBatchId(day.getPaymentBatchId());
                SpacePaymentEntity existing = paymentRepository
                        .findBySpaceIdAndPaymentBatchId(spaceId, day.getPaymentBatchId())
                        .orElse(null);
                if (existing != null
                        && (existing.getPaymentStatus() == SpacePaymentStatus.UNDER_REVIEW
                                || existing.getPaymentStatus() == SpacePaymentStatus.PROOF_UPLOADED
                                || existing.getPaymentStatus() == SpacePaymentStatus.PAID)) {
                    continue;
                }
                mirrorBulkProofSubmitted(batchDays, day.getPaymentBatchId(), actorUserId);
                continue;
            }
            SpacePaymentEntity existing = findDaily(day).orElse(null);
            if (existing != null
                    && (existing.getPaymentStatus() == SpacePaymentStatus.UNDER_REVIEW
                            || existing.getPaymentStatus() == SpacePaymentStatus.PROOF_UPLOADED
                            || existing.getPaymentStatus() == SpacePaymentStatus.PAID)) {
                continue;
            }
            mirrorProofSubmitted(day, actorUserId);
        }
    }

    private void syncAllBatchDays(SpacePaymentEntity payment) {
        List<MealPollDayPaymentEntity> days =
                dayPaymentRepository.findByPaymentBatchId(payment.getPaymentBatchId());
        for (MealPollDayPaymentEntity day : days) {
            applySpaceStatusToDay(day, payment);
            dayPaymentRepository.save(day);
        }
    }

    private void applySpaceStatusToDay(MealPollDayPaymentEntity day, SpacePaymentEntity payment) {
        if (payment.getPaymentStatus() == SpacePaymentStatus.PAID) {
            day.setPaymentStatus(MealPollPaymentStatus.PAID);
            day.setProofReviewedAt(payment.getReviewedAt() != null ? payment.getReviewedAt() : LocalDateTime.now());
            day.setProofReviewedBy(payment.getReviewedBy());
            day.setRejectionReason(null);
        } else if (payment.getPaymentStatus() == SpacePaymentStatus.REJECTED
                || payment.getPaymentStatus() == SpacePaymentStatus.UPDATE_REQUESTED) {
            day.setPaymentStatus(MealPollPaymentStatus.REJECTED);
            day.setProofReviewedAt(payment.getReviewedAt() != null ? payment.getReviewedAt() : LocalDateTime.now());
            day.setProofReviewedBy(payment.getReviewedBy());
            day.setRejectionReason(payment.getRejectionReason());
        } else if (payment.getPaymentStatus() == SpacePaymentStatus.UNDER_REVIEW
                || payment.getPaymentStatus() == SpacePaymentStatus.PROOF_UPLOADED) {
            day.setPaymentStatus(MealPollPaymentStatus.PENDING_APPROVAL);
            day.setProofReviewedAt(null);
            day.setProofReviewedBy(null);
            day.setRejectionReason(null);
        }
    }

    private SpacePaymentEntity upsertDailyMealPayment(MealPollDayPaymentEntity dayPayment) {
        SpaceEntity space = dayPayment.getSpace();
        MemberEntity member = dayPayment.getMember();
        LocalDate pollDate = dayPayment.getPollDate();
        String monthKey = YearMonth.from(pollDate).toString();

        return findDaily(dayPayment).orElseGet(() -> {
            SpacePaymentEntity created = SpacePaymentEntity.builder()
                    .space(space)
                    .member(member)
                    .paymentType(SpacePaymentType.MEAL)
                    .paymentCategory(SpacePaymentCategory.DAILY)
                    .title("Meals — " + DAY_TITLE.format(pollDate))
                    .amount(safeAmount(dayPayment.getChargedAmount()))
                    .currencyCode("INR")
                    .dueDate(pollDate)
                    .month(monthKey)
                    .paymentStatus(SpacePaymentStatus.PENDING)
                    .build();
            paymentRepository.save(created);
            timelineService.record(created, PaymentTimelineEventType.CREATED, null, null);
            return created;
        });
    }

    private java.util.Optional<SpacePaymentEntity> findMirroredPayment(MealPollDayPaymentEntity dayPayment) {
        if (hasBatchId(dayPayment)) {
            return paymentRepository.findBySpaceIdAndPaymentBatchId(
                    dayPayment.getSpace().getId(), dayPayment.getPaymentBatchId());
        }
        return findDaily(dayPayment);
    }

    private java.util.Optional<SpacePaymentEntity> findDaily(MealPollDayPaymentEntity dayPayment) {
        String monthKey = YearMonth.from(dayPayment.getPollDate()).toString();
        return paymentRepository.findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategoryAndDueDate(
                dayPayment.getSpace().getId(),
                dayPayment.getMember().getId(),
                monthKey,
                SpacePaymentType.MEAL,
                SpacePaymentCategory.DAILY,
                dayPayment.getPollDate());
    }

    private static void applyProofFields(SpacePaymentEntity payment, MealPollDayPaymentEntity dayPayment) {
        payment.setProofUrl(dayPayment.getProofImageUrl());
        payment.setReferenceNumber(dayPayment.getReferenceNumber());
        payment.setRemarks(dayPayment.getRemarks());
        payment.setPaymentMethod(dayPayment.getPaymentMethod());
    }

    private void recordProofTimeline(
            SpacePaymentEntity payment, String remarks, UUID actorUserId, boolean wasReview) {
        if (!wasReview) {
            timelineService.record(payment, PaymentTimelineEventType.PROOF_UPLOADED, remarks, actorUserId);
            timelineService.record(payment, PaymentTimelineEventType.UNDER_REVIEW, null, actorUserId);
        } else {
            timelineService.record(payment, PaymentTimelineEventType.RESUBMITTED, remarks, actorUserId);
        }
    }

    private static void clearReviewFields(SpacePaymentEntity payment) {
        payment.setRejectionReason(null);
        payment.setRejectionCode(null);
        payment.setReviewedAt(null);
        payment.setReviewedBy(null);
    }

    private static boolean isUnderReview(SpacePaymentEntity payment) {
        return payment.getPaymentStatus() == SpacePaymentStatus.UNDER_REVIEW
                || payment.getPaymentStatus() == SpacePaymentStatus.PROOF_UPLOADED;
    }

    private static boolean hasBatchId(MealPollDayPaymentEntity dayPayment) {
        return dayPayment.getPaymentBatchId() != null && !dayPayment.getPaymentBatchId().isBlank();
    }

    private static boolean isDailyMeal(SpacePaymentEntity payment) {
        return payment.getPaymentType() == SpacePaymentType.MEAL
                && payment.getPaymentCategory() == SpacePaymentCategory.DAILY;
    }

    private static String bulkTitle(int dayCount, LocalDate earliest, LocalDate latest) {
        if (dayCount <= 1 || earliest.equals(latest)) {
            return "Meals — " + DAY_TITLE.format(earliest);
        }
        return "Meals — " + dayCount + " days (" + DAY_SHORT.format(earliest) + " – "
                + DAY_SHORT.format(latest) + ")";
    }

    private static BigDecimal safeAmount(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0 ? amount : BigDecimal.ZERO;
    }
}
