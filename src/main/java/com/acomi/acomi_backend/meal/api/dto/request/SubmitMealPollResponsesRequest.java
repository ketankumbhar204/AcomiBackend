package com.acomi.acomi_backend.meal.api.dto.request;

import com.acomi.acomi_backend.meal.domain.model.MealPollPaymentChoice;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitMealPollResponsesRequest {

    @NotEmpty
    @Valid
    private List<SubmitMealPollSelectionRequest> selections;

    /** Required for MESS spaces when saving priced meal selections. */
    private MealPollPaymentChoice paymentChoice;

    /**
     * Optional payment screenshot (data URI or raw base64). Not required for MARK_AS_PAID —
     * method / UTR / remarks may be submitted alone or together with an image.
     */
    private String proofImageBase64;

    @Size(max = 100)
    private String referenceNumber;

    @Size(max = 2000)
    private String remarks;

    private SpacePaymentMethod paymentMethod;
}
