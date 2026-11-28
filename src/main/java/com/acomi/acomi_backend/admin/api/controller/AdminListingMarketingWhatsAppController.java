package com.acomi.acomi_backend.admin.api.controller;

import com.acomi.acomi_backend.admin.api.dto.request.AdminSendListingMarketingWhatsAppRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminListingMarketingWhatsAppCapabilityResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminSendListingMarketingWhatsAppResponse;
import com.acomi.acomi_backend.admin.application.service.AdminListingMarketingWhatsAppService;
import com.acomi.acomi_backend.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Listing Marketing WhatsApp")
@SecurityRequirement(name = "bearerAuth")
public class AdminListingMarketingWhatsAppController {

    private final AdminListingMarketingWhatsAppService service;

    @GetMapping("/listing-marketing/whatsapp-capability")
    public ResponseEntity<ApiResponse<AdminListingMarketingWhatsAppCapabilityResponse>> capability() {
        return ResponseEntity.ok(ApiResponse.success(service.capability()));
    }

    @PostMapping("/property-registrations/{id}/marketing/send-whatsapp")
    public ResponseEntity<ApiResponse<AdminSendListingMarketingWhatsAppResponse>> sendProperty(
            @PathVariable UUID id, @RequestBody @Valid AdminSendListingMarketingWhatsAppRequest request) {
        AdminSendListingMarketingWhatsAppResponse result = service.sendForProperty(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                result.isAccepted() ? "WhatsApp Business message submitted" : result.getExplanation(),
                result));
    }

    @PostMapping("/mess-registrations/{id}/marketing/send-whatsapp")
    public ResponseEntity<ApiResponse<AdminSendListingMarketingWhatsAppResponse>> sendMess(
            @PathVariable UUID id, @RequestBody @Valid AdminSendListingMarketingWhatsAppRequest request) {
        AdminSendListingMarketingWhatsAppResponse result = service.sendForMess(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                result.isAccepted() ? "WhatsApp Business message submitted" : result.getExplanation(),
                result));
    }

    @PostMapping("/spaces/{id}/marketing/send-whatsapp")
    public ResponseEntity<ApiResponse<AdminSendListingMarketingWhatsAppResponse>> sendSpace(
            @PathVariable UUID id, @RequestBody @Valid AdminSendListingMarketingWhatsAppRequest request) {
        AdminSendListingMarketingWhatsAppResponse result = service.sendForSpace(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                result.isAccepted() ? "WhatsApp Business message submitted" : result.getExplanation(),
                result));
    }
}
