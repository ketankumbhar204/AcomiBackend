package com.acomi.acomi_backend.admin.api.dto.response;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminEnquiriesTrendPointResponse {

    private LocalDate date;
    private long count;
}
