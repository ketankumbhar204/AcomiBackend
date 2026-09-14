package com.acomi.acomi_backend.admin.api.controller;



import com.acomi.acomi_backend.admin.api.dto.response.AdminActiveSpaceResponse;

import com.acomi.acomi_backend.admin.api.dto.response.AdminDashboardSummaryResponse;

import com.acomi.acomi_backend.admin.api.dto.response.AdminEnquiriesTrendResponse;

import com.acomi.acomi_backend.admin.api.dto.response.AdminUserRegistrationBreakdownResponse;

import com.acomi.acomi_backend.admin.application.service.AdminDashboardService;

import com.acomi.acomi_backend.common.web.ApiResponse;

import com.acomi.acomi_backend.space.domain.model.SpaceType;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;

import java.time.LocalDateTime;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.RestController;



@RestController

@RequestMapping("/api/v1/admin/dashboard")

@RequiredArgsConstructor

@Tag(name = "Admin Dashboard")

@SecurityRequirement(name = "bearerAuth")

public class AdminDashboardController {



    private final AdminDashboardService adminDashboardService;



    @GetMapping("/summary")

    public ResponseEntity<ApiResponse<AdminDashboardSummaryResponse>> summary(

            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDateTime fromAt = from == null ? null : from.atStartOfDay();

        LocalDateTime toAt = to == null ? null : to.plusDays(1).atStartOfDay();

        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getSummary(fromAt, toAt)));

    }



    @GetMapping("/enquiries-trend")

    public ResponseEntity<ApiResponse<AdminEnquiriesTrendResponse>> enquiriesTrend(

            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.enquiriesTrend(from, to)));

    }



    @GetMapping("/user-registration-breakdown")

    public ResponseEntity<ApiResponse<AdminUserRegistrationBreakdownResponse>> userRegistrationBreakdown(

            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDateTime fromAt = from == null ? LocalDate.now().minusDays(6).atStartOfDay() : from.atStartOfDay();

        LocalDateTime toAt = to == null ? LocalDate.now().plusDays(1).atStartOfDay() : to.plusDays(1).atStartOfDay();

        return ResponseEntity.ok(

                ApiResponse.success(adminDashboardService.userRegistrationBreakdown(fromAt, toAt)));

    }



    @GetMapping("/active-spaces")

    public ResponseEntity<ApiResponse<List<AdminActiveSpaceResponse>>> activeSpaces(

            @RequestParam(required = false) SpaceType type) {

        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.listActiveSpaces(type)));

    }

}


