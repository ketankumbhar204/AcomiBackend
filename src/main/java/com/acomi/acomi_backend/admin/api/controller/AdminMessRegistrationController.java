package com.acomi.acomi_backend.admin.api.controller;

import com.acomi.acomi_backend.admin.api.dto.request.AdminLinkOwnerRequest;
import com.acomi.acomi_backend.admin.api.dto.request.AdminUpdateRegistrationReviewRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminMessRegistrationsSummaryResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminRegistrationConvertResponse;
import com.acomi.acomi_backend.admin.application.service.AdminMessRegistrationService;
import com.acomi.acomi_backend.admin.application.service.AdminRegistrationConversionService;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.mess.api.dto.request.AdminCreateMessRegistrationRequest;
import com.acomi.acomi_backend.mess.api.dto.response.MessRegistrationDetailResponse;
import com.acomi.acomi_backend.mess.api.dto.response.MessRegistrationListItemResponse;
import com.acomi.acomi_backend.mess.api.dto.response.MessRegistrationResponse;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus;
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
@RequestMapping("/api/v1/admin/mess-registrations")
@RequiredArgsConstructor
@Tag(name = "Admin Mess Registrations")
@SecurityRequirement(name = "bearerAuth")
public class AdminMessRegistrationController {

    private final AdminMessRegistrationService adminMessRegistrationService;
    private final AdminRegistrationConversionService adminRegistrationConversionService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<MessRegistrationListItemResponse>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) MessRegistrationSource source,
            @RequestParam(required = false) MessRegistrationStatus status,
            @RequestParam(defaultValue = "false") boolean leadsOnly,
            @RequestParam(required = false) Boolean claimed,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(
                adminMessRegistrationService.list(q, source, status, leadsOnly, claimed, pageable))));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminMessRegistrationsSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.success(adminMessRegistrationService.summary()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MessRegistrationDetailResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminMessRegistrationService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MessRegistrationResponse>> create(
            @RequestBody @Valid AdminCreateMessRegistrationRequest request) {
        MessRegistrationResponse response = adminMessRegistrationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Mess registration created", response));
    }

    @PutMapping("/{id}/contact")
    public ResponseEntity<ApiResponse<MessRegistrationDetailResponse>> updateContact(
            @PathVariable UUID id, @RequestBody @Valid AdminUpdateRegistrationContactRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Owner contact updated", adminMessRegistrationService.updateContact(id, request)));
    }

    @PutMapping("/{id}/link-owner")
    public ResponseEntity<ApiResponse<MessRegistrationDetailResponse>> linkOwner(
            @PathVariable UUID id, @RequestBody @Valid AdminLinkOwnerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Owner linked", adminRegistrationConversionService.linkMessOwner(id, request)));
    }

    @PutMapping("/{id}/review")
    public ResponseEntity<ApiResponse<MessRegistrationDetailResponse>> updateReview(
            @PathVariable UUID id, @RequestBody @Valid AdminUpdateRegistrationReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Registration updated", adminRegistrationConversionService.updateMessReview(id, request)));
    }

    @PostMapping("/publish-open-admin-leads")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> publishOpenAdminLeads() {
        int published = adminRegistrationConversionService.publishOpenAdminMessLeads();
        return ResponseEntity.ok(ApiResponse.success(
                "Published open admin mess leads to discoverable Spaces",
                Map.of("published", published)));
    }

    @PostMapping("/{id}/convert")
    public ResponseEntity<ApiResponse<AdminRegistrationConvertResponse>> convert(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Converted to live space", adminRegistrationConversionService.convertMess(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminMessRegistrationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
