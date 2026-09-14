package com.acomi.acomi_backend.admin.api.controller;

import com.acomi.acomi_backend.admin.api.dto.request.AdminLinkOwnerRequest;
import com.acomi.acomi_backend.admin.api.dto.request.AdminUpdateRegistrationReviewRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminPropertyRegistrationsSummaryResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminRegistrationConvertResponse;
import com.acomi.acomi_backend.admin.application.service.AdminPropertyRegistrationService;
import com.acomi.acomi_backend.admin.application.service.AdminRegistrationConversionService;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.property.api.dto.request.AdminCreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationDetailResponse;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationListItemResponse;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationResponse;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.registration.api.dto.request.AdminUpdateRegistrationContactRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/admin/property-registrations")
@RequiredArgsConstructor
@Tag(name = "Admin Property Registrations")
@SecurityRequirement(name = "bearerAuth")
public class AdminPropertyRegistrationController {

    private final AdminPropertyRegistrationService adminPropertyRegistrationService;
    private final AdminRegistrationConversionService adminRegistrationConversionService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<PropertyRegistrationListItemResponse>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PropertyRegistrationSource source,
            @RequestParam(required = false) PropertyRegistrationStatus status,
            @RequestParam(defaultValue = "false") boolean leadsOnly,
            @RequestParam(required = false) Boolean claimed,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(
                adminPropertyRegistrationService.list(q, source, status, leadsOnly, claimed, pageable))));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminPropertyRegistrationsSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.success(adminPropertyRegistrationService.summary()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PropertyRegistrationDetailResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminPropertyRegistrationService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PropertyRegistrationResponse>> create(
            @RequestBody @Valid AdminCreatePropertyRegistrationRequest request) {
        PropertyRegistrationResponse response = adminPropertyRegistrationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Property registration created", response));
    }

    @PutMapping("/{id}/contact")
    public ResponseEntity<ApiResponse<PropertyRegistrationDetailResponse>> updateContact(
            @PathVariable UUID id, @RequestBody @Valid AdminUpdateRegistrationContactRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Owner contact updated", adminPropertyRegistrationService.updateContact(id, request)));
    }

    @PutMapping("/{id}/link-owner")
    public ResponseEntity<ApiResponse<PropertyRegistrationDetailResponse>> linkOwner(
            @PathVariable UUID id, @RequestBody @Valid AdminLinkOwnerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Owner linked", adminRegistrationConversionService.linkPropertyOwner(id, request)));
    }

    @PutMapping("/{id}/review")
    public ResponseEntity<ApiResponse<PropertyRegistrationDetailResponse>> updateReview(
            @PathVariable UUID id, @RequestBody @Valid AdminUpdateRegistrationReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Registration updated", adminRegistrationConversionService.updatePropertyReview(id, request)));
    }

    @PostMapping("/publish-open-admin-leads")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> publishOpenAdminLeads() {
        int published = adminRegistrationConversionService.publishOpenAdminPropertyLeads();
        return ResponseEntity.ok(ApiResponse.success(
                "Published open admin property leads to discoverable Spaces",
                Map.of("published", published)));
    }

    @PostMapping("/{id}/convert")
    public ResponseEntity<ApiResponse<AdminRegistrationConvertResponse>> convert(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Converted to live space", adminRegistrationConversionService.convertProperty(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminPropertyRegistrationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
