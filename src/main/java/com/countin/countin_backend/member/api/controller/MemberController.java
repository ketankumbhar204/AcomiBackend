package com.countin.countin_backend.member.api.controller;

import com.countin.countin_backend.common.security.SecurityUtils;
import com.countin.countin_backend.common.web.ApiResponse;
import com.countin.countin_backend.member.api.dto.request.CreateMemberDocumentRequest;
import com.countin.countin_backend.member.api.dto.request.CreateMemberNoteRequest;
import com.countin.countin_backend.member.api.dto.request.CreateMemberRequest;
import com.countin.countin_backend.member.api.dto.request.ImportMemberRequest;
import com.countin.countin_backend.member.api.dto.request.UpdateDepositRequest;
import com.countin.countin_backend.member.api.dto.request.UpdateEmergencyContactRequest;
import com.countin.countin_backend.member.api.dto.request.UpdateMemberRequest;
import com.countin.countin_backend.member.api.dto.request.UpdateMemberStatusRequest;
import com.countin.countin_backend.member.api.dto.response.MemberDetailsResponse;
import com.countin.countin_backend.member.api.dto.response.MemberDocumentResponse;
import com.countin.countin_backend.member.api.dto.response.MemberHistoryResponse;
import com.countin.countin_backend.member.api.dto.response.MemberImportCandidateResponse;
import com.countin.countin_backend.member.api.dto.response.MemberNoteResponse;
import com.countin.countin_backend.member.api.dto.response.MemberResponse;
import com.countin.countin_backend.member.api.dto.response.PendingInvitationResponse;
import com.countin.countin_backend.member.application.service.MemberMasterService;
import com.countin.countin_backend.member.application.service.MembershipService;
import com.countin.countin_backend.member.domain.model.MembershipRole;
import com.countin.countin_backend.occupancy.domain.model.MemberOccupancyStatus;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/spaces/{spaceId}")
@RequiredArgsConstructor
@Tag(name = "Members", description = "Member master and space invitation APIs")
@SecurityRequirement(name = "bearerAuth")
public class MemberController {

    private final MemberMasterService memberMasterService;
    private final MembershipService membershipService;

    @PostMapping("/members")
    @Operation(
            summary = "Add member",
            description = "Adds a member directly without invitation. OWNER or MANAGER only. "
                    + "OWNER role is not allowed.")
    public ResponseEntity<ApiResponse<MemberResponse>> createMember(
            @PathVariable UUID spaceId,
            @RequestBody @Valid CreateMemberRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        MemberResponse response = memberMasterService.createMember(spaceId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Member created successfully", response));
    }

    @GetMapping("/members")
    @Operation(
            summary = "List members",
            description = "Returns active member master records for the space. "
                    + "Optional search by name or mobile and filter by occupancyStatus. "
                    + "Any active space member may view.")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> getMembers(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) MemberOccupancyStatus occupancyStatus) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        List<MemberResponse> members =
                memberMasterService.getMembers(spaceId, callerId, search, occupancyStatus);
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @GetMapping("/members/import-candidates")
    @Operation(
            summary = "Search reusable members",
            description = "Lodging targets: eligible vacated TENANT residents from managed "
                    + "accommodation spaces. Mess targets: CUSTOMER members from managed Mess spaces "
                    + "and TENANT residents from managed lodging spaces (PG→Mess). OWNER/MANAGER only. "
                    + "Never returns members outside the caller's managed portfolio.")
    public ResponseEntity<ApiResponse<List<MemberImportCandidateResponse>>> searchImportCandidates(
            @PathVariable UUID spaceId, @RequestParam(required = false) String search) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        List<MemberImportCandidateResponse> candidates =
                memberMasterService.searchImportCandidates(spaceId, callerId, search);
        return ResponseEntity.ok(ApiResponse.success(candidates));
    }

    @PostMapping("/members/import")
    @Operation(
            summary = "Import member from another managed space",
            description = "Lodging: creates a TENANT by copying profile data. Mess: creates a CUSTOMER "
                    + "from a Mess CUSTOMER or lodging TENANT. Idempotent when the mobile already exists "
                    + "with the expected role. Does not copy occupancy, deposits, or meal participation. "
                    + "OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<MemberResponse>> importMember(
            @PathVariable UUID spaceId, @RequestBody @Valid ImportMemberRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        MemberResponse response = memberMasterService.importMember(spaceId, callerId, request);
        return ResponseEntity.ok(ApiResponse.success("Member imported successfully", response));
    }

    @GetMapping("/members/me")
    @Operation(
            summary = "Get my linked member profile",
            description = "Returns the member master record linked to the signed-in user in this space.")
    public ResponseEntity<ApiResponse<MemberResponse>> getMyLinkedMember(@PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        MemberResponse response = memberMasterService.getMyLinkedMember(spaceId, callerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/members/{memberId}")
    @Operation(summary = "Get member details", description = "Returns complete member master details.")
    public ResponseEntity<ApiResponse<MemberDetailsResponse>> getMember(
            @PathVariable UUID spaceId, @PathVariable UUID memberId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        MemberDetailsResponse response = memberMasterService.getMember(spaceId, memberId, callerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/members/{memberId}")
    @Operation(
            summary = "Update member",
            description = "Updates member details. OWNER or MANAGER only. OWNER role cannot be assigned.")
    public ResponseEntity<ApiResponse<MemberDetailsResponse>> updateMember(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            @RequestBody @Valid UpdateMemberRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        MemberDetailsResponse response =
                memberMasterService.updateMember(spaceId, memberId, callerId, request);
        return ResponseEntity.ok(ApiResponse.success("Member updated successfully", response));
    }

    @DeleteMapping("/members/{memberId}")
    @Operation(
            summary = "Remove member",
            description = "Soft-deletes a member record. OWNER only. OWNER member cannot be removed.")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID spaceId, @PathVariable UUID memberId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        memberMasterService.removeMember(spaceId, memberId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/members/{memberId}/status")
    @Operation(
            summary = "Update member status",
            description = "Updates operational member status. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<MemberDetailsResponse>> updateMemberStatus(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            @RequestBody @Valid UpdateMemberStatusRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        MemberDetailsResponse response =
                memberMasterService.updateMemberStatus(spaceId, memberId, callerId, request);
        return ResponseEntity.ok(ApiResponse.success("Member status updated successfully", response));
    }

    @PutMapping("/members/{memberId}/emergency-contact")
    @Operation(
            summary = "Update emergency contact",
            description = "Updates member emergency contact details. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<MemberDetailsResponse>> updateEmergencyContact(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            @RequestBody @Valid UpdateEmergencyContactRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        MemberDetailsResponse response =
                memberMasterService.updateEmergencyContact(spaceId, memberId, callerId, request);
        return ResponseEntity.ok(
                ApiResponse.success("Emergency contact updated successfully", response));
    }

    @PutMapping("/members/{memberId}/deposit")
    @Operation(
            summary = "Update deposit",
            description = "Updates member deposit amounts. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<MemberDetailsResponse>> updateDeposit(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            @RequestBody @Valid UpdateDepositRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        MemberDetailsResponse response =
                memberMasterService.updateDeposit(spaceId, memberId, callerId, request);
        return ResponseEntity.ok(ApiResponse.success("Deposit updated successfully", response));
    }

    @PostMapping("/members/{memberId}/documents")
    @Operation(
            summary = "Add member document",
            description = "Registers document metadata for a member. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<MemberDocumentResponse>> addMemberDocument(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            @RequestBody @Valid CreateMemberDocumentRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        MemberDocumentResponse response =
                memberMasterService.addMemberDocument(spaceId, memberId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document added successfully", response));
    }

    @GetMapping("/members/{memberId}/documents")
    @Operation(summary = "List member documents", description = "Returns all documents for a member.")
    public ResponseEntity<ApiResponse<List<MemberDocumentResponse>>> getMemberDocuments(
            @PathVariable UUID spaceId, @PathVariable UUID memberId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        List<MemberDocumentResponse> documents =
                memberMasterService.getMemberDocuments(spaceId, memberId, callerId);
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    @DeleteMapping("/members/{memberId}/documents/{documentId}")
    @Operation(
            summary = "Delete member document",
            description = "Removes a document record. OWNER or MANAGER only.")
    public ResponseEntity<Void> deleteMemberDocument(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            @PathVariable UUID documentId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        memberMasterService.deleteMemberDocument(spaceId, memberId, documentId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/members/{memberId}/notes")
    @Operation(summary = "Add member note", description = "Adds a note to a member record. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<MemberNoteResponse>> addMemberNote(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            @RequestBody @Valid CreateMemberNoteRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        MemberNoteResponse response =
                memberMasterService.addMemberNote(spaceId, memberId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Note added successfully", response));
    }

    @GetMapping("/members/{memberId}/notes")
    @Operation(summary = "List member notes", description = "Returns all notes for a member.")
    public ResponseEntity<ApiResponse<List<MemberNoteResponse>>> getMemberNotes(
            @PathVariable UUID spaceId, @PathVariable UUID memberId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        List<MemberNoteResponse> notes =
                memberMasterService.getMemberNotes(spaceId, memberId, callerId);
        return ResponseEntity.ok(ApiResponse.success(notes));
    }

    @GetMapping("/members/{memberId}/history")
    @Operation(summary = "Get member history", description = "Returns audit history for a member.")
    public ResponseEntity<ApiResponse<List<MemberHistoryResponse>>> getMemberHistory(
            @PathVariable UUID spaceId, @PathVariable UUID memberId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        List<MemberHistoryResponse> history =
                memberMasterService.getMemberHistory(spaceId, memberId, callerId);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/invitations")
    @Operation(
            summary = "List pending invitations",
            description = "Returns pending invitations for the space. Caller must belong to the space.")
    public ResponseEntity<ApiResponse<List<PendingInvitationResponse>>> getPendingInvitations(
            @PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        List<PendingInvitationResponse> invitations =
                membershipService.getPendingInvitations(spaceId, callerId);
        return ResponseEntity.ok(ApiResponse.success(invitations));
    }
}
