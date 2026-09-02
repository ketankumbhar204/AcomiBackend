package com.acomi.acomi_backend.accommodation.api.dto.request.setup;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Optional explicit layout tree from the Quick Setup preview editor")
public class SetupStructureInput {

    @Valid
    private List<SetupFloorNodeInput> floors = new ArrayList<>();

    @Valid
    private List<SetupUnitNodeInput> units = new ArrayList<>();

    public boolean hasNodes() {
        return (floors != null && !floors.isEmpty()) || (units != null && !units.isEmpty());
    }

    @Getter
    @NoArgsConstructor
    public static class SetupFloorNodeInput {
        private String name;
        private Integer number;
        @Valid
        private List<SetupUnitNodeInput> units = new ArrayList<>();
        @Valid
        private List<SetupRoomNodeInput> rooms = new ArrayList<>();
    }

    @Getter
    @NoArgsConstructor
    public static class SetupUnitNodeInput {
        private String name;
        private String number;
        @Valid
        private List<SetupRoomNodeInput> rooms = new ArrayList<>();
    }

    @Getter
    @NoArgsConstructor
    public static class SetupRoomNodeInput {
        private String name;
        private String number;
        private Integer capacity;
        @Valid
        private List<SetupBedNodeInput> beds = new ArrayList<>();
    }

    @Getter
    @NoArgsConstructor
    public static class SetupBedNodeInput {
        private String name;
        @NotBlank(message = "Bed number is required")
        private String number;

        @DecimalMin(value = "0.0", inclusive = true)
        @Schema(description = "Optional default monthly rent")
        private BigDecimal defaultRent;

        @DecimalMin(value = "0.0", inclusive = true)
        @Schema(description = "Optional default deposit")
        private BigDecimal defaultDeposit;
    }
}
