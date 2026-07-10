package com.countin.countin_backend.complaint.api.controller;

import com.countin.countin_backend.common.security.SecurityUtils;
import com.countin.countin_backend.common.web.ApiResponse;
import com.countin.countin_backend.complaint.api.dto.request.AddComplaintAttachmentRequest;
import com.countin.countin_backend.complaint.api.dto.request.AddComplaintCommentRequest;
import com.countin.countin_backend.complaint.api.dto.request.AssignComplaintRequest;
import com.countin.countin_backend.complaint.api.dto.request.CreateComplaintRequest;
import com.countin.countin_backend.complaint.api.dto.request.ReopenComplaintRequest;
import com.countin.countin_backend.complaint.api.dto.request.UpdateComplaintResolutionRequest;
import com.countin.countin_backend.complaint.api.dto.request.UpdateComplaintStatusRequest;
import com.countin.countin_backend.complaint.api.dto.response.ComplaintListResponse;
import com.countin.countin_backend.complaint.api.dto.response.ComplaintResponse;
import com.countin.countin_backend.complaint.application.service.SpaceComplaintService;
import com.countin.countin_backend.complaint.domain.model.ComplaintCategory;
import com.countin.countin_backend.complaint.domain.model.ComplaintPriority;
import com.countin.countin_backend.complaint.domain.model.ComplaintStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/complaints")
@RequiredArgsConstructor
@Tag(name = "Complaints", description = "Space complaint lifecycle APIs")
@SecurityRequirement(name = "bearerAuth")
public class ComplaintController {

    private final SpaceComplaintService complaintService;

    @PostMapping
    @Operation(summary = "Raise a complaint")
    public ResponseEntity<ApiResponse<ComplaintResponse>> create(
            @PathVariable UUID spaceId, @Valid @RequestBody CreateComplaintRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Complaint created successfully", complaintService.create(spaceId, callerId, request)));
    }

    @GetMapping
    @Operation(summary = "List complaints", description = "Role-aware list with optional filters.")
    public ResponseEntity<ApiResponse<ComplaintListResponse>> list(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) ComplaintPriority priority,
            @RequestParam(required = false) ComplaintCategory category,
            @RequestParam(required = false) UUID assigneeMembershipId,
            @RequestParam(required = false) Boolean mine) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Complaints fetched successfully",
                complaintService.list(
                        spaceId, callerId, status, priority, category, assigneeMembershipId, mine)));
    }

    @GetMapping("/{complaintId}")
    @Operation(summary = "Get complaint detail")
    public ResponseEntity<ApiResponse<ComplaintResponse>> get(
            @PathVariable UUID spaceId, @PathVariable UUID complaintId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Complaint fetched successfully",
                complaintService.get(spaceId, complaintId, callerId)));
    }

    @PatchMapping("/{complaintId}/status")
    @Operation(summary = "Update complaint status")
    public ResponseEntity<ApiResponse<ComplaintResponse>> updateStatus(
            @PathVariable UUID spaceId,
            @PathVariable UUID complaintId,
            @Valid @RequestBody UpdateComplaintStatusRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Complaint status updated",
                complaintService.updateStatus(spaceId, complaintId, callerId, request)));
    }

    @PostMapping("/{complaintId}/comments")
    @Operation(summary = "Add a comment or internal note")
    public ResponseEntity<ApiResponse<ComplaintResponse>> addComment(
            @PathVariable UUID spaceId,
            @PathVariable UUID complaintId,
            @Valid @RequestBody AddComplaintCommentRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Comment added",
                complaintService.addComment(spaceId, complaintId, callerId, request)));
    }

    @PostMapping("/{complaintId}/attachments")
    @Operation(summary = "Add a photo attachment")
    public ResponseEntity<ApiResponse<ComplaintResponse>> addAttachment(
            @PathVariable UUID spaceId,
            @PathVariable UUID complaintId,
            @Valid @RequestBody AddComplaintAttachmentRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Attachment added",
                complaintService.addAttachment(spaceId, complaintId, callerId, request)));
    }

    @PostMapping("/{complaintId}/reopen")
    @Operation(summary = "Reopen a resolved complaint")
    public ResponseEntity<ApiResponse<ComplaintResponse>> reopen(
            @PathVariable UUID spaceId,
            @PathVariable UUID complaintId,
            @RequestBody(required = false) ReopenComplaintRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        ReopenComplaintRequest body = request != null ? request : new ReopenComplaintRequest();
        return ResponseEntity.ok(ApiResponse.success(
                "Complaint reopened",
                complaintService.reopen(spaceId, complaintId, callerId, body)));
    }

    @PostMapping("/{complaintId}/assign")
    @Operation(summary = "Assign complaint to a membership")
    public ResponseEntity<ApiResponse<ComplaintResponse>> assign(
            @PathVariable UUID spaceId,
            @PathVariable UUID complaintId,
            @Valid @RequestBody AssignComplaintRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Complaint assigned",
                complaintService.assign(spaceId, complaintId, callerId, request)));
    }

    @PatchMapping("/{complaintId}/resolution")
    @Operation(summary = "Set resolution summary (optionally mark resolved)")
    public ResponseEntity<ApiResponse<ComplaintResponse>> updateResolution(
            @PathVariable UUID spaceId,
            @PathVariable UUID complaintId,
            @Valid @RequestBody UpdateComplaintResolutionRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Resolution updated",
                complaintService.updateResolution(spaceId, complaintId, callerId, request)));
    }
}
