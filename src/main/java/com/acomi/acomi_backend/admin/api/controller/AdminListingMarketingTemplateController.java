package com.acomi.acomi_backend.admin.api.controller;

import com.acomi.acomi_backend.admin.api.dto.request.AdminUpsertListingMarketingTemplateRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminListingMarketingTemplateResponse;
import com.acomi.acomi_backend.admin.application.service.AdminListingMarketingTemplateService;
import com.acomi.acomi_backend.admin.application.support.ListingMarketingPlaceholders;
import com.acomi.acomi_backend.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/admin/listing-marketing-templates")
@RequiredArgsConstructor
@Tag(name = "Admin Listing Marketing Templates")
@SecurityRequirement(name = "bearerAuth")
public class AdminListingMarketingTemplateController {

    private final AdminListingMarketingTemplateService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminListingMarketingTemplateResponse>>> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.success(service.list(activeOnly)));
    }

    @GetMapping("/placeholders")
    public ResponseEntity<ApiResponse<Map<String, Set<String>>>> placeholders() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("allowed", ListingMarketingPlaceholders.ALLOWED)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminListingMarketingTemplateResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminListingMarketingTemplateResponse>> create(
            @RequestBody @Valid AdminUpsertListingMarketingTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Template created", service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminListingMarketingTemplateResponse>> update(
            @PathVariable UUID id, @RequestBody @Valid AdminUpsertListingMarketingTemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Template updated", service.update(id, request)));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ApiResponse<AdminListingMarketingTemplateResponse>> duplicate(
            @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Template duplicated", service.duplicate(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
