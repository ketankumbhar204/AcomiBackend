package com.acomi.acomi_backend.enquiry.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.enquiry.api.dto.request.CreateSpaceEnquiryRequest;
import com.acomi.acomi_backend.enquiry.api.dto.request.DeliverEnquiryEmailRequest;
import com.acomi.acomi_backend.enquiry.api.dto.response.EnquiryInboxResponse;
import com.acomi.acomi_backend.enquiry.api.dto.response.EnquiryEmailStatusResponse;
import com.acomi.acomi_backend.enquiry.api.dto.response.ReceivedEnquiryResponse;
import com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import com.acomi.acomi_backend.enquiry.api.dto.response.MyInquiredListingIdsResponse;
import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.enquiry.api.dto.response.SpaceEnquiryResponse;
import com.acomi.acomi_backend.enquiry.application.service.SpaceEnquiryService;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryClientChannel;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(
        name = "Space Enquiries",
        description =
                "Authenticated contact-detail enquiries. Owner contact is returned only for ANDROID SHARED enquiries; WEB uses email delivery.")
@SecurityRequirement(name = "bearerAuth")
public class SpaceEnquiryController {

    private final SpaceEnquiryService spaceEnquiryService;

    @PostMapping("/spaces/{spaceId}/enquiries")
    @Operation(summary = "Submit a contact enquiry for a Space")
    public ResponseEntity<ApiResponse<SpaceEnquiryResponse>> create(
            @PathVariable UUID spaceId,
            @RequestBody(required = false) @Valid CreateSpaceEnquiryRequest request,
            @RequestHeader(value = "X-ACOMI-CLIENT", required = false) String clientHeader) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        CreateSpaceEnquiryRequest payload = request != null ? request : new CreateSpaceEnquiryRequest();
        InquiryClientChannel channel = InquiryClientChannel.fromHeader(clientHeader);
        SpaceEnquiryResponse response = spaceEnquiryService.create(callerId, spaceId, payload, channel);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Enquiry sent", response));
    }

    @PostMapping("/spaces/{spaceId}/enquiries/send-again")
    @Operation(summary = "Expire the current enquiry for this listing and send a new one")
    public ResponseEntity<ApiResponse<SpaceEnquiryResponse>> sendAgain(
            @PathVariable UUID spaceId,
            @RequestHeader(value = "X-ACOMI-CLIENT", required = false) String clientHeader) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        InquiryClientChannel channel = InquiryClientChannel.fromHeader(clientHeader);
        SpaceEnquiryResponse response = spaceEnquiryService.sendAgain(callerId, spaceId, channel);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Enquiry sent", response));
    }

    @GetMapping("/spaces/{spaceId}/revealed-contact")
    @Operation(summary = "Owner contact already revealed on the website for this listing. Does not create an enquiry.")
    public ResponseEntity<ApiResponse<OwnerContactResponse>> revealedContact(@PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(spaceEnquiryService.revealedContact(callerId, spaceId)));
    }

    @GetMapping("/enquiries/me/listing-ids")
    @Operation(summary = "Listing ids the current user has already enquired about")
    public ResponseEntity<ApiResponse<MyInquiredListingIdsResponse>> listInquiredListingIds() {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(spaceEnquiryService.listInquiredListingIds(callerId)));
    }

    @GetMapping("/enquiries/inbox")
    @Operation(summary = "Whether this account sees its own enquiries or enquiries received for its listings")
    public ResponseEntity<ApiResponse<EnquiryInboxResponse>> inbox() {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(spaceEnquiryService.inbox(callerId)));
    }

    @GetMapping("/enquiries/received")
    @Operation(summary = "Enquiries members raised for listings the caller owns or manages")
    public ResponseEntity<ApiResponse<PagedResponse<ReceivedEnquiryResponse>>> listReceived(
            @RequestParam(required = false) UUID spaceId,
            @RequestParam(required = false) SpaceEnquiryStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                spaceEnquiryService.listReceived(callerId, spaceId, status, from, to, pageable)));
    }

    @GetMapping("/enquiries/received/{enquiryId}")
    @Operation(summary = "One received enquiry for a listing the caller owns or manages")
    public ResponseEntity<ApiResponse<ReceivedEnquiryResponse>> getReceived(@PathVariable UUID enquiryId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(spaceEnquiryService.getReceived(callerId, enquiryId)));
    }

    @GetMapping("/enquiries/me")
    @Operation(summary = "List my contact enquiries")
    public ResponseEntity<ApiResponse<PagedResponse<SpaceEnquiryResponse>>> listMine(
            @PageableDefault(size = 20) Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(spaceEnquiryService.listMine(callerId, pageable)));
    }

    @GetMapping("/enquiries/{enquiryId}/email-status")
    @Operation(summary = "Whether the owner-contact email for this enquiry has been sent")
    public ResponseEntity<ApiResponse<EnquiryEmailStatusResponse>> emailStatus(@PathVariable UUID enquiryId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(spaceEnquiryService.emailStatus(callerId, enquiryId)));
    }

    @GetMapping("/enquiries/{enquiryId}")
    @Operation(summary = "Get one of my contact enquiries")
    public ResponseEntity<ApiResponse<SpaceEnquiryResponse>> getMine(@PathVariable UUID enquiryId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(spaceEnquiryService.getMine(callerId, enquiryId)));
    }

    @PostMapping("/enquiries/{enquiryId}/email-contact")
    @Operation(summary = "Email owner contact details for a WEB enquiry to any address the user chooses")
    public ResponseEntity<ApiResponse<SpaceEnquiryResponse>> deliverContactByEmail(
            @PathVariable UUID enquiryId, @Valid @RequestBody DeliverEnquiryEmailRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        SpaceEnquiryResponse response =
                spaceEnquiryService.deliverContactByEmail(callerId, enquiryId, request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Contact details email queued", response));
    }
}
