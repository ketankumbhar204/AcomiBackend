package com.acomi.acomi_backend.admin.api.controller;

import com.acomi.acomi_backend.admin.api.dto.response.AdminActivityItemResponse;
import com.acomi.acomi_backend.admin.application.service.AdminActivityService;
import com.acomi.acomi_backend.admin.domain.model.AdminActivityType;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/activity")
@RequiredArgsConstructor
@Tag(name = "Admin Activity")
@SecurityRequirement(name = "bearerAuth")
public class AdminActivityController {

    private final AdminActivityService adminActivityService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<AdminActivityItemResponse>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AdminActivityType activityType,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        LocalDateTime fromAt = from == null ? null : from.atStartOfDay();
        LocalDateTime toAt = to == null ? null : to.plusDays(1).atStartOfDay();
        return ResponseEntity.ok(ApiResponse.success(
                adminActivityService.list(fromAt, toAt, activityType, pageable)));
    }
}
