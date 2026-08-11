package com.amico.amico_backend.dashboard.application.service;

import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.AccommodationSummaryRepository;
import com.amico.amico_backend.common.exception.ResourceNotFoundException;
import com.amico.amico_backend.dashboard.api.dto.response.DashboardAccommodationOperationsResponse;
import com.amico.amico_backend.dashboard.api.dto.response.DashboardFinancialSummaryResponse;
import com.amico.amico_backend.dashboard.api.dto.response.DashboardMessOperationsResponse;
import com.amico.amico_backend.dashboard.api.dto.response.DashboardSummaryResponse;
import com.amico.amico_backend.notification.api.dto.response.PendingActionsSummaryResponse;
import com.amico.amico_backend.notification.application.service.PendingActionService;
import com.amico.amico_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.amico.amico_backend.payment.application.service.PaymentMonthSnapshotService;
import com.amico.amico_backend.payment.infrastructure.persistence.entity.SpacePaymentMonthSummaryEntity;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpaceDashboardSummaryService {

    private final SpaceRepository spaceRepository;
    private final PaymentMonthSnapshotService paymentMonthSnapshotService;
    private final PendingActionService pendingActionService;
    private final AccommodationSummaryRepository accommodationSummaryRepository;
    private final OccupancyRepository occupancyRepository;

    /**
     * Financial KPIs read from the Payments month snapshot (same source as GET /payments/summary).
     * Avoids rebuilding the full member ledger on every dashboard open.
     */
    @Transactional
    public DashboardSummaryResponse getSummary(UUID spaceId, UUID callerId, String monthParam) {
        SpaceEntity space = spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));

        YearMonth month = parseMonth(monthParam);
        String monthKey = month.toString();

        paymentMonthSnapshotService.ensureMonth(spaceId, callerId, month);
        SpacePaymentMonthSummaryEntity summaryRow =
                paymentMonthSnapshotService.requireSummary(spaceId, monthKey);

        DashboardFinancialSummaryResponse financial = summaryRow != null
                ? PaymentMonthSnapshotService.toFinancial(summaryRow)
                : DashboardFinancialSummaryResponse.builder().currencyCode("INR").build();
        int pendingPaymentsCount = summaryRow != null ? summaryRow.getPendingMembers() : 0;

        DashboardMessOperationsResponse messOperations = null;
        DashboardAccommodationOperationsResponse accommodationOperations = null;
        if (isAccommodationApplicable(space.getType())) {
            accommodationOperations =
                    buildAccommodationOperations(spaceId, monthKey, pendingPaymentsCount);
        }

        PendingActionsSummaryResponse pendingActions =
                pendingActionService.getPendingActions(spaceId, callerId, monthKey);

        return DashboardSummaryResponse.builder()
                .spaceType(space.getType())
                .month(monthKey)
                .financial(financial)
                .messOperations(messOperations)
                .accommodationOperations(accommodationOperations)
                .attention(List.of())
                .pendingActions(pendingActions)
                .build();
    }

    private DashboardAccommodationOperationsResponse buildAccommodationOperations(
            UUID spaceId, String monthParam, int pendingPaymentsCount) {
        YearMonth month = YearMonth.parse(monthParam != null && !monthParam.isBlank()
                ? monthParam
                : YearMonth.now().toString());
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        Map<AccommodationStatus, Long> bedCounts = countBedStatusesForSpace(spaceId);
        long occupiedBeds = bedCounts.getOrDefault(AccommodationStatus.OCCUPIED, 0L);
        long vacantBeds = bedCounts.getOrDefault(AccommodationStatus.AVAILABLE, 0L);

        int moveInsThisMonth = (int) occupancyRepository.countMoveInsBetween(
                spaceId,
                monthStart,
                monthEnd,
                monthStart.atStartOfDay(),
                monthEnd.plusDays(1).atStartOfDay());

        return DashboardAccommodationOperationsResponse.builder()
                .occupiedBeds((int) occupiedBeds)
                .vacantBeds((int) vacantBeds)
                .moveInsThisMonth(moveInsThisMonth)
                .pendingPaymentsCount(pendingPaymentsCount)
                .build();
    }

    private Map<AccommodationStatus, Long> countBedStatusesForSpace(UUID spaceId) {
        Map<AccommodationStatus, Long> counts = new EnumMap<>(AccommodationStatus.class);
        for (Object[] row : accommodationSummaryRepository.countBedStatusesForSpace(spaceId)) {
            counts.put((AccommodationStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    private boolean isAccommodationApplicable(SpaceType spaceType) {
        return spaceType != SpaceType.MESS;
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
}
