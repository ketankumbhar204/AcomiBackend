package com.acomi.acomi_backend.payment.application.service;

import com.acomi.acomi_backend.payment.api.dto.response.OverduePaymentResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Eligibility boundary for payment reminders.
 * Data only — delivery is handled by {@link PaymentReminderService}.
 */
@Service
@RequiredArgsConstructor
public class PaymentReminderEligibilityService {

    private final OverduePaymentService overduePaymentService;

    @Transactional(readOnly = true)
    public List<OverduePaymentResponse> findObligationsNeedingReminder(UUID spaceId, UUID callerId) {
        return overduePaymentService.listReminderEligible(spaceId, callerId);
    }

    /** System/scheduler path — no caller authorization (caller already trusted). */
    @Transactional(readOnly = true)
    public List<OverduePaymentResponse> findObligationsNeedingReminderInternal(UUID spaceId) {
        return overduePaymentService.listReminderEligibleInternal(spaceId);
    }
}
