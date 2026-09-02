package com.acomi.acomi_backend.admin.api.controller;

import com.acomi.acomi_backend.admin.api.dto.response.AdminRegisteredUserResponse;
import com.acomi.acomi_backend.admin.application.service.AdminRegisteredUsersService;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/registered-users")
@RequiredArgsConstructor
@Tag(name = "Admin Registered Users")
@SecurityRequirement(name = "bearerAuth")
public class AdminRegisteredUsersController {

    private final AdminRegisteredUsersService adminRegisteredUsersService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<AdminRegisteredUserResponse>>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(PagedResponse.from(adminRegisteredUsersService.list(pageable))));
    }
}
