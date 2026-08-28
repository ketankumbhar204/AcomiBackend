package com.acomi.acomi_backend.admin.api.controller;

import com.acomi.acomi_backend.admin.application.service.AdminPropertyRegistrationService;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.property.api.dto.request.AdminCreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationDetailResponse;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationListItemResponse;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationResponse;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<PropertyRegistrationListItemResponse>>> list(
            @RequestParam(required = false) PropertyRegistrationSource source,
            @RequestParam(defaultValue = "false") boolean leadsOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.from(adminPropertyRegistrationService.list(source, leadsOnly, pageable))));
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminPropertyRegistrationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
