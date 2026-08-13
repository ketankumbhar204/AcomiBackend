package com.acomi.acomi_backend.accommodation.api.dto.request;

import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.domain.model.RoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request body for updating a room")
public class UpdateRoomRequest {

    @NotBlank(message = "Room name is required")
    @Schema(description = "Display name of the room", example = "Room 101")
    private String name;

    @NotBlank(message = "Room number is required")
    @Schema(description = "Room identifier", example = "101")
    private String roomNumber;

    @NotNull(message = "Room type is required")
    @Schema(description = "Room type", example = "SHARED", implementation = RoomType.class)
    private RoomType roomType;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    @Schema(description = "Maximum occupants", example = "2")
    private Integer capacity;

    @NotNull(message = "Status is required")
    @Schema(description = "Accommodation status", implementation = AccommodationStatus.class)
    private AccommodationStatus status;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal defaultRent;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal defaultDeposit;
}
