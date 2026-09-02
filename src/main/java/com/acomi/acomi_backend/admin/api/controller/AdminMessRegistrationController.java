package com.acomi.acomi_backend.admin.api.controller;

import com.acomi.acomi_backend.admin.application.service.AdminMessRegistrationService;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.mess.api.dto.request.AdminCreateMessRegistrationRequest;
import com.acomi.acomi_backend.mess.api.dto.response.MessRegistrationDetailResponse;
import com.acomi.acomi_backend.mess.api.dto.response.MessRegistrationListItemResponse;
import com.acomi.acomi_backend.mess.api.dto.response.MessRegistrationResponse;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.registration.api.dto.request.AdminUpdateRegistrationContactRequest;
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

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<MessRegistrationListItemResponse>>> list(
            @RequestParam(required = false) MessRegistrationSource source,
            @RequestParam(defaultValue = "false") boolean leadsOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.from(adminMessRegistrationService.list(source, leadsOnly, pageable))));
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminMessRegistrationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
