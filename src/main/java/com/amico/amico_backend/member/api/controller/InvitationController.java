package com.amico.amico_backend.member.api.controller;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.common.security.SecurityUtils;
import com.amico.amico_backend.common.web.ApiResponse;
import com.amico.amico_backend.member.api.dto.request.AcceptInvitationRequest;
import com.amico.amico_backend.member.api.dto.request.CreateInvitationRequest;
import com.amico.amico_backend.member.api.dto.response.InvitationResponse;
import com.amico.amico_backend.member.api.dto.response.MyInvitationResponse;
import com.amico.amico_backend.member.api.dto.response.SpaceMembershipResponse;
import com.amico.amico_backend.member.application.service.InvitationService;
import com.amico.amico_backend.member.application.service.MembershipService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
@Tag(name = "Invitations", description = "Space invitation APIs")
@SecurityRequirement(name = "bearerAuth")
public class InvitationController {

    private final InvitationService invitationService;
    private final MembershipService membershipService;

    @GetMapping("/my")
    @Operation(
            summary = "List my pending invitations",
            description = "Returns pending invitations for the signed-in user's mobile number.")
    public ResponseEntity<ApiResponse<List<MyInvitationResponse>>> getMyInvitations() {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Invitations fetched successfully",
                invitationService.getMyPendingInvitations(callerId)));
    }

    @PostMapping
    @Operation(summary = "Create invitation", description = "Sends an invitation to join a space. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<InvitationResponse>> createInvitation(
            @RequestBody @Valid CreateInvitationRequest request) {
        InvitationResponse response = invitationService.createInvitation(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Invitation sent successfully", response));
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept invitation", description = "Accepts a pending invitation and creates an active membership.")
    public ResponseEntity<ApiResponse<SpaceMembershipResponse>> acceptInvitation(
            @PathVariable UUID id,
            @RequestBody @Valid AcceptInvitationRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        if (!callerId.equals(request.getUserId())) {
            throw new BusinessException(
                    "You can only accept invitations for your own account", HttpStatus.FORBIDDEN);
        }
        SpaceMembershipResponse response = invitationService.acceptInvitation(id, callerId);
        return ResponseEntity.ok(ApiResponse.success("Invitation accepted successfully", response));
    }

    @DeleteMapping("/{invitationId}")
    @Operation(
            summary = "Cancel invitation",
            description = "Cancels a pending invitation. OWNER or MANAGER only. "
                    + "Accepted invitations cannot be cancelled.")
    public ResponseEntity<Void> cancelInvitation(@PathVariable UUID invitationId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        membershipService.cancelInvitation(invitationId, callerId);
        return ResponseEntity.noContent().build();
    }
}
