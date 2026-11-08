package com.acomi.acomi_backend.occupancy.api.dto.response;

import com.acomi.acomi_backend.payment.domain.model.PaymentFinancialDisposition;
import com.acomi.acomi_backend.payment.domain.model.PaymentSettlementAction;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReservationCancellationPreviewResponse {

    private UUID occupancyId;
    private UUID memberId;
    private String memberName;
    private List<Item> payments;
    private boolean requiresCollectedSettlement;

    @Getter
    @Builder
    public static class Item {
        private UUID paymentId;
        private String title;
        private SpacePaymentType paymentType;
        private SpacePaymentCategory paymentCategory;
        private BigDecimal amount;
        private String currencyCode;
        private SpacePaymentStatus paymentStatus;
        private boolean collected;
        private BigDecimal refundableAmount;
        private BigDecimal alreadyRefundedAmount;
        private BigDecimal alreadyRetainedAmount;
        private PaymentFinancialDisposition disposition;
        private List<PaymentSettlementAction> allowedActions;
    }
}
