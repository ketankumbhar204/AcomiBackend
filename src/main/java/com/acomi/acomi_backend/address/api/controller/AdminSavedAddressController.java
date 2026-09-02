package com.acomi.acomi_backend.address.api.controller;

import com.acomi.acomi_backend.address.api.dto.request.SavedAddressRequest;
import com.acomi.acomi_backend.address.api.dto.response.SavedAddressResponse;
import com.acomi.acomi_backend.address.application.service.SavedAddressService;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
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
@RequestMapping("/api/v1/admin/saved-addresses")
@RequiredArgsConstructor
@Tag(name = "Admin Saved Addresses")
@SecurityRequirement(name = "bearerAuth")
public class AdminSavedAddressController {

    private final SavedAddressService savedAddressService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<SavedAddressResponse>>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(PagedResponse.from(savedAddressService.list(search, pageable))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SavedAddressResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(savedAddressService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SavedAddressResponse>> create(@RequestBody @Valid SavedAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Saved address created", savedAddressService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SavedAddressResponse>> update(
            @PathVariable UUID id, @RequestBody @Valid SavedAddressRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Saved address updated", savedAddressService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        savedAddressService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
