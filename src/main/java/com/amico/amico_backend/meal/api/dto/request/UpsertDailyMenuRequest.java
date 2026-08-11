package com.amico.amico_backend.meal.api.dto.request;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpsertDailyMenuRequest {

    @Valid
    private List<DailyMenuOptionRequest> options = new ArrayList<>();

    private String notes;
}
