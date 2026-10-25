package com.acomi.acomi_backend.listing.api;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.listing.api.dto.MeListingResponse;
import com.acomi.acomi_backend.listing.api.dto.UpdateListingCatalogRequest;
import com.acomi.acomi_backend.listing.api.dto.UpdateOwnedMessLeadRequest;
import com.acomi.acomi_backend.listing.api.dto.UpdateOwnedPropertyLeadRequest;
import com.acomi.acomi_backend.listing.application.MeListingsService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/listings")
@RequiredArgsConstructor
public class MeListingsController {

    private final MeListingsService meListingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MeListingResponse>>> list(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String spaceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                meListingsService.list(callerId, kind, spaceType, status, search)));
    }

    @PutMapping("/property-registrations/{registrationId}")
    public ResponseEntity<ApiResponse<MeListingResponse>> updatePropertyLead(
            @PathVariable UUID registrationId, @RequestBody @Valid UpdateOwnedPropertyLeadRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                meListingsService.updatePropertyLead(SecurityUtils.getCurrentUserId(), registrationId, request)));
    }

    @PutMapping("/mess-registrations/{registrationId}")
    public ResponseEntity<ApiResponse<MeListingResponse>> updateMessLead(
            @PathVariable UUID registrationId, @RequestBody @Valid UpdateOwnedMessLeadRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                meListingsService.updateMessLead(SecurityUtils.getCurrentUserId(), registrationId, request)));
    }

    @PutMapping("/spaces/{spaceId}")
    public ResponseEntity<ApiResponse<MeListingResponse>> updateCatalog(
            @PathVariable UUID spaceId, @RequestBody @Valid UpdateListingCatalogRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                meListingsService.updateCatalog(SecurityUtils.getCurrentUserId(), spaceId, request)));
    }
}
