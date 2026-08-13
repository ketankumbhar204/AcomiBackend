package com.acomi.acomi_backend.payment.application.support;

import com.acomi.acomi_backend.dashboard.api.dto.response.MemberPaymentLedgerRowResponse;
import com.acomi.acomi_backend.dashboard.domain.model.MemberPaymentStatus;
import com.acomi.acomi_backend.payment.api.dto.response.OwnerPaymentsMonthCountsResponse;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** Single source of truth for Payments tab / KPI counts. */
public final class PaymentMonthCountsSupport {

    private PaymentMonthCountsSupport() {}

    public static final EnumSet<MemberPaymentStatus> PENDING_MEMBER_STATUSES = EnumSet.of(
            MemberPaymentStatus.PENDING,
            MemberPaymentStatus.PARTIAL,
            MemberPaymentStatus.UPDATE_REQUESTED,
            MemberPaymentStatus.REJECTED);

    public static int countPendingMembers(Collection<MemberPaymentLedgerRowResponse> members) {
        return (int) members.stream()
                .filter(row -> row.getStatus() != null && PENDING_MEMBER_STATUSES.contains(row.getStatus()))
                .count();
    }

    public static boolean isPendingMemberStatus(MemberPaymentStatus status) {
        return status != null && PENDING_MEMBER_STATUSES.contains(status);
    }

    public static OwnerPaymentsMonthCountsResponse fromStatusMap(
            Map<SpacePaymentStatus, Long> byStatus, int pendingMembers) {
        int submitted = (int) (byStatus.getOrDefault(SpacePaymentStatus.UNDER_REVIEW, 0L)
                + byStatus.getOrDefault(SpacePaymentStatus.PROOF_UPLOADED, 0L));
        int changesRequested = byStatus.getOrDefault(SpacePaymentStatus.UPDATE_REQUESTED, 0L).intValue();
        int paid = byStatus.getOrDefault(SpacePaymentStatus.PAID, 0L).intValue();
        int rejected = byStatus.getOrDefault(SpacePaymentStatus.REJECTED, 0L).intValue();
        return OwnerPaymentsMonthCountsResponse.builder()
                .pendingReview(submitted + changesRequested)
                .submitted(submitted)
                .changesRequested(changesRequested)
                .paid(paid)
                .rejected(rejected)
                .history(paid + rejected)
                .pendingMembers(pendingMembers)
                .build();
    }

    public static OwnerPaymentsMonthCountsResponse fromPaymentStatuses(
            List<SpacePaymentStatus> statuses, int pendingMembers) {
        int submitted = 0;
        int changesRequested = 0;
        int paid = 0;
        int rejected = 0;
        for (SpacePaymentStatus status : statuses) {
            if (status == SpacePaymentStatus.UNDER_REVIEW || status == SpacePaymentStatus.PROOF_UPLOADED) {
                submitted += 1;
            } else if (status == SpacePaymentStatus.UPDATE_REQUESTED) {
                changesRequested += 1;
            } else if (status == SpacePaymentStatus.PAID) {
                paid += 1;
            } else if (status == SpacePaymentStatus.REJECTED) {
                rejected += 1;
            }
        }
        return OwnerPaymentsMonthCountsResponse.builder()
                .pendingReview(submitted + changesRequested)
                .submitted(submitted)
                .changesRequested(changesRequested)
                .paid(paid)
                .rejected(rejected)
                .history(paid + rejected)
                .pendingMembers(pendingMembers)
                .build();
    }
}
