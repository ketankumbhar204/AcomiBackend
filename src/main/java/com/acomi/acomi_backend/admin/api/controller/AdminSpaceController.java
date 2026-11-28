package com.acomi.acomi_backend.admin.api.controller;

import com.acomi.acomi_backend.admin.api.dto.request.AdminUpdateListingMarketingRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminListingMarketingResponse;
import com.acomi.acomi_backend.admin.application.service.AdminDashboardService;
import com.acomi.acomi_backend.admin.application.service.AdminListingMarketingService;
import com.acomi.acomi_backend.admin.application.service.AdminRegistrationConversionService;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.space.api.dto.response.SpaceResponse;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/spaces")
@RequiredArgsConstructor
@Tag(name = "Admin Spaces")
@SecurityRequirement(name = "bearerAuth")
public class AdminSpaceController {

    private final AdminRegistrationConversionService adminRegistrationConversionService;
    private final AdminDashboardService adminDashboardService;
    private final AdminListingMarketingService adminListingMarketingService;
    private final SpaceRepository spaceRepository;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SpaceResponse>> get(@PathVariable UUID id) {
        SpaceEntity space = spaceRepository
                .findWithOwnerById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", id));
        return ResponseEntity.ok(ApiResponse.success(SpaceResponse.from(space)));
    }

    @PostMapping("/{id}/enable-discovery")
    public ResponseEntity<ApiResponse<SpaceResponse>> enableDiscovery(@PathVariable UUID id) {
        SpaceEntity space = adminRegistrationConversionService.enableDiscovery(id);
        return ResponseEntity.ok(ApiResponse.success("Discovery enabled", SpaceResponse.from(space)));
    }

    @GetMapping("/{id}/marketing")
    public ResponseEntity<ApiResponse<AdminListingMarketingResponse>> getMarketing(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminListingMarketingService.getSpaceMarketing(id)));
    }

    @PatchMapping("/{id}/marketing")
    public ResponseEntity<ApiResponse<AdminListingMarketingResponse>> updateMarketing(
            @PathVariable UUID id, @RequestBody @Valid AdminUpdateListingMarketingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Marketing updated", adminListingMarketingService.updateSpaceMarketing(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminDashboardService.deleteActiveSpace(id);
        return ResponseEntity.noContent().build();
    }
}
