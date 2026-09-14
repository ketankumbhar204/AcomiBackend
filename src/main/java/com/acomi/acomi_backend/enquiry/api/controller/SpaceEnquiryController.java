package com.acomi.acomi_backend.enquiry.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.enquiry.api.dto.request.CreateSpaceEnquiryRequest;
import com.acomi.acomi_backend.enquiry.api.dto.response.SpaceEnquiryResponse;
import com.acomi.acomi_backend.enquiry.application.service.SpaceEnquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Space Enquiries", description = "Authenticated contact-detail enquiries. Never returns owner contact.")
@SecurityRequirement(name = "bearerAuth")
public class SpaceEnquiryController {

    private final SpaceEnquiryService spaceEnquiryService;

    @PostMapping("/spaces/{spaceId}/enquiries")
    @Operation(summary = "Submit a contact enquiry for a Space")
    public ResponseEntity<ApiResponse<SpaceEnquiryResponse>> create(
            @PathVariable UUID spaceId, @RequestBody(required = false) @Valid CreateSpaceEnquiryRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        CreateSpaceEnquiryRequest payload = request != null ? request : new CreateSpaceEnquiryRequest();
        SpaceEnquiryResponse response = spaceEnquiryService.create(callerId, spaceId, payload);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Enquiry sent", response));
    }

    @GetMapping("/enquiries/me")
    @Operation(summary = "List my contact enquiries")
    public ResponseEntity<ApiResponse<PagedResponse<SpaceEnquiryResponse>>> listMine(
            @PageableDefault(size = 20) Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(spaceEnquiryService.listMine(callerId, pageable)));
    }

    @GetMapping("/enquiries/{enquiryId}")
    @Operation(summary = "Get one of my contact enquiries")
    public ResponseEntity<ApiResponse<SpaceEnquiryResponse>> getMine(@PathVariable UUID enquiryId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(spaceEnquiryService.getMine(callerId, enquiryId)));
    }
}
