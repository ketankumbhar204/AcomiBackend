package com.acomi.acomi_backend.payment.api.dto.request;

import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Owner/manager manual payment obligation")
public class CreateSpacePaymentRequest {

    @NotNull
    private UUID memberId;

    @NotNull
    private SpacePaymentType paymentType;

    @NotNull
    private SpacePaymentCategory paymentCategory;

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @Schema(description = "Billing month YYYY-MM; defaults from dueDate")
    private String month;

    @Schema(description = "Due date; defaults to today")
    private LocalDate dueDate;

    @Size(max = 200)
    @Schema(description = "Display title; defaults from type")
    private String title;

    @Size(max = 500)
    @Schema(description = "Optional charge description")
    private String description;

    @Size(max = 2000)
    private String remarks;

    @Schema(description = "Required when a DEPOSIT/SECURITY row already exists for the member")
    private boolean confirmDuplicateDeposit;
}
