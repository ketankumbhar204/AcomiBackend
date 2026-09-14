package com.acomi.acomi_backend.admin.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.enquiry.api.dto.request.RejectSpaceEnquiryRequest;
import com.acomi.acomi_backend.enquiry.api.dto.response.AdminEnquirySummaryResponse;
import com.acomi.acomi_backend.enquiry.api.dto.response.AdminSpaceEnquiryDetailResponse;
import com.acomi.acomi_backend.enquiry.api.dto.response.AdminSpaceEnquiryListItemResponse;
import com.acomi.acomi_backend.enquiry.application.service.SpaceEnquiryService;
import com.acomi.acomi_backend.enquiry.domain.model.EnquiryRequesterType;
import com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/admin/enquiries")
@RequiredArgsConstructor
@Tag(name = "Admin Enquiries")
@SecurityRequirement(name = "bearerAuth")
public class AdminSpaceEnquiryController {

    private final SpaceEnquiryService spaceEnquiryService;

    @GetMapping("/summary")
    @Operation(summary = "Enquiry status summary counts")
    public ResponseEntity<ApiResponse<AdminEnquirySummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.success(spaceEnquiryService.summaryForAdmin()));
    }

    @GetMapping
    @Operation(summary = "List contact enquiries")
    public ResponseEntity<ApiResponse<PagedResponse<AdminSpaceEnquiryListItemResponse>>> list(
            @RequestParam(required = false) SpaceEnquiryStatus status,
            @RequestParam(required = false) EnquiryRequesterType requesterType,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                spaceEnquiryService.listForAdmin(status, requesterType, q, from, to, pageable)));
    }

    @GetMapping("/{enquiryId}")
    @Operation(summary = "Get enquiry details including owner contact")
    public ResponseEntity<ApiResponse<AdminSpaceEnquiryDetailResponse>> get(@PathVariable UUID enquiryId) {
        return ResponseEntity.ok(ApiResponse.success(spaceEnquiryService.getForAdmin(enquiryId)));
    }

    @PostMapping("/{enquiryId}/share")
    @Operation(summary = "Share owner contact details with the requester by email")
    public ResponseEntity<ApiResponse<AdminSpaceEnquiryDetailResponse>> share(@PathVariable UUID enquiryId) {
        UUID adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Contact details shared", spaceEnquiryService.share(enquiryId, adminId)));
    }

    @PostMapping("/{enquiryId}/reject")
    @Operation(summary = "Reject a pending contact enquiry")
    public ResponseEntity<ApiResponse<AdminSpaceEnquiryDetailResponse>> reject(
            @PathVariable UUID enquiryId, @RequestBody(required = false) @Valid RejectSpaceEnquiryRequest request) {
        UUID adminId = SecurityUtils.getCurrentUserId();
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(ApiResponse.success(
                "Enquiry rejected", spaceEnquiryService.reject(enquiryId, adminId, reason)));
    }

    @PostMapping("/{enquiryId}/expire")
    @Operation(summary = "Expire a pending or shared contact enquiry")
    public ResponseEntity<ApiResponse<AdminSpaceEnquiryDetailResponse>> expire(@PathVariable UUID enquiryId) {
        UUID adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Enquiry expired", spaceEnquiryService.expire(enquiryId, adminId)));
    }

    @DeleteMapping("/{enquiryId}")
    @Operation(summary = "Delete a contact enquiry")
    public ResponseEntity<Void> delete(@PathVariable UUID enquiryId) {
        spaceEnquiryService.deleteForAdmin(enquiryId);
        return ResponseEntity.noContent().build();
    }
}
