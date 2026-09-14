package com.acomi.acomi_backend.admin.api.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminEnquiriesTrendResponse {

    private LocalDate from;
    private LocalDate to;
    private List<AdminEnquiriesTrendPointResponse> points;
    private long total;
}
