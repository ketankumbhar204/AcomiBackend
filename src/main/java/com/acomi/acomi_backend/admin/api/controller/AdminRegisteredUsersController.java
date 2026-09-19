package com.acomi.acomi_backend.admin.api.controller;

import com.acomi.acomi_backend.admin.api.dto.request.AdminCreateRegisteredUserRequest;
import com.acomi.acomi_backend.admin.api.dto.request.AdminUpdateTestUserRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminRegisteredUserResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminRegisteredUsersSummaryResponse;
import com.acomi.acomi_backend.admin.application.service.AdminRegisteredUsersService;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/registered-users")
@RequiredArgsConstructor
@Tag(name = "Admin Registered Users")
@SecurityRequirement(name = "bearerAuth")
public class AdminRegisteredUsersController {

    private final AdminRegisteredUsersService adminRegisteredUsersService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminRegisteredUsersSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.success(adminRegisteredUsersService.summary()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<AdminRegisteredUserResponse>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String onboarding,
            @RequestParam(required = false) String spaceAssociation,
            @RequestParam(required = false) Boolean verified,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(adminRegisteredUsersService.list(
                q, role, onboarding, spaceAssociation, verified, from, to, pageable))));
    }

    @PostMapping
    @Operation(summary = "Create a test registered user with space role (no OTP; password set directly)")
    public ResponseEntity<ApiResponse<AdminRegisteredUserResponse>> create(
            @Valid @RequestBody AdminCreateRegisteredUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Test user created", adminRegisteredUsersService.createTestUser(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminRegisteredUserResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminRegisteredUsersService.getById(id)));
    }

    @PutMapping("/{id}/test-user")
    public ResponseEntity<ApiResponse<AdminRegisteredUserResponse>> setTestUser(
            @PathVariable UUID id, @Valid @RequestBody AdminUpdateTestUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Test user flag updated",
                adminRegisteredUsersService.setTestUser(id, Boolean.TRUE.equals(request.getTestUser()))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminRegisteredUsersService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
