package com.amico.amico_backend.meal.api.dto.request;

import com.amico.amico_backend.payment.domain.model.SpacePaymentMethod;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SubmitBulkMealPollPaymentProofRequest {

    @NotEmpty
    @Size(max = 31)
    private List<LocalDate> dates;

    private String proofImageBase64;

    @Size(max = 100)
    private String referenceNumber;

    @Size(max = 2000)
    private String remarks;

    private SpacePaymentMethod paymentMethod;
}
