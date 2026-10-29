package com.acomi.acomi_backend.servicecontact.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.servicecontact.api.dto.request.SaveServiceContactRequest;
import com.acomi.acomi_backend.servicecontact.api.dto.response.ServiceContactCategoryResponse;
import com.acomi.acomi_backend.servicecontact.api.dto.response.ServiceContactResponse;
import com.acomi.acomi_backend.servicecontact.application.service.ServiceContactService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1/service-contacts")
@RequiredArgsConstructor
@Tag(name = "Service Contacts", description = "Account-level directory of external service providers")
@SecurityRequirement(name = "bearerAuth")
public class ServiceContactController {

    private final ServiceContactService serviceContactService;

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<ServiceContactCategoryResponse>>> categories() {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Service contact categories fetched successfully",
                serviceContactService.categories(callerId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ServiceContactResponse>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID spaceId,
            @RequestParam(required = false, defaultValue = "updated") String sort,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Service contacts fetched successfully",
                serviceContactService.list(
                        callerId, q, category, spaceId, sort, includeArchived, page, size)));
    }

    @GetMapping("/{contactId}")
    public ResponseEntity<ApiResponse<ServiceContactResponse>> get(@PathVariable UUID contactId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Service contact fetched successfully",
                serviceContactService.get(callerId, contactId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ServiceContactResponse>> create(
            @RequestBody @Valid SaveServiceContactRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Service contact saved successfully",
                        serviceContactService.create(callerId, request)));
    }

    @PutMapping("/{contactId}")
    public ResponseEntity<ApiResponse<ServiceContactResponse>> update(
            @PathVariable UUID contactId, @RequestBody @Valid SaveServiceContactRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Service contact updated successfully",
                serviceContactService.update(callerId, contactId, request)));
    }

    @PostMapping("/{contactId}/archive")
    public ResponseEntity<ApiResponse<Void>> archive(@PathVariable UUID contactId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        serviceContactService.archive(callerId, contactId);
        return ResponseEntity.ok(ApiResponse.success("Service contact archived successfully"));
    }

    @PostMapping("/{contactId}/spaces/{spaceId}")
    public ResponseEntity<ApiResponse<ServiceContactResponse>> addSpace(
            @PathVariable UUID contactId, @PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Space associated successfully",
                serviceContactService.addSpace(callerId, contactId, spaceId)));
    }

    @DeleteMapping("/{contactId}/spaces/{spaceId}")
    public ResponseEntity<ApiResponse<ServiceContactResponse>> removeSpace(
            @PathVariable UUID contactId, @PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Space association removed successfully",
                serviceContactService.removeSpace(callerId, contactId, spaceId)));
    }
}
