package com.acomi.acomi_backend.member.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.common.util.MobileNumberNormalizer;
import com.acomi.acomi_backend.member.api.dto.request.CreateInvitationRequest;
import com.acomi.acomi_backend.member.api.dto.response.InvitationResponse;
import com.acomi.acomi_backend.member.api.dto.response.MyInvitationResponse;
import com.acomi.acomi_backend.member.api.dto.response.SpaceMembershipResponse;
import com.acomi.acomi_backend.member.domain.model.InvitationStatus;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.InvitationEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.InvitationRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.meal.application.service.MealParticipationService;
import com.acomi.acomi_backend.notification.application.service.InvitationNotificationSyncService;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final SpaceMembershipRepository spaceMembershipRepository;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;
    private final MemberMasterService memberMasterService;
    private final InvitationProvisioner invitationProvisioner;
    private final MealParticipationService mealParticipationService;
    private final InvitationNotificationSyncService invitationNotificationSyncService;

    @Transactional
    public InvitationResponse createInvitation(CreateInvitationRequest request) {
        SpaceEntity space = spaceRepository.findByIdAndIsActiveTrue(request.getSpaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", request.getSpaceId()));

        UserEntity invitedBy = userRepository.findByIdAndIsActiveTrue(request.getInvitedByUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getInvitedByUserId()));

        boolean hasPermission = spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                invitedBy.getId(),
                space.getId(),
                List.of(MembershipRole.OWNER, MembershipRole.MANAGER));

        if (!hasPermission) {
            throw new BusinessException(
                    "Only OWNER or MANAGER can send invitations", HttpStatus.FORBIDDEN);
        }

        String normalizedMobile = MobileNumberNormalizer.normalize(request.getMobileNumber());
        if (invitationRepository.existsBySpaceIdAndMobileNumberAndStatus(
                space.getId(), normalizedMobile, InvitationStatus.PENDING)) {
            throw new BusinessException(
                    "A pending invitation already exists for this mobile number in the space");
        }

        InvitationEntity invitation = invitationProvisioner
                .ensurePendingInvitation(space, invitedBy, normalizedMobile, request.getRole())
                .orElseThrow(() -> new BusinessException(
                        "User already has an active membership in this space", HttpStatus.CONFLICT));

        invitationNotificationSyncService.onInvitationCreated(invitation);
        return InvitationResponse.from(invitation);
    }

    @Transactional(readOnly = true)
    public List<MyInvitationResponse> getMyPendingInvitations(UUID userId) {
        UserEntity user = userRepository
                .findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        LocalDateTime now = LocalDateTime.now();
        return invitationRepository.findActivePendingByMobileNumber(user.getMobileNumber(), now).stream()
                .filter(invitation -> !spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                        userId, invitation.getSpace().getId(), MembershipStatus.ACTIVE))
                .map(MyInvitationResponse::from)
                .toList();
    }

    @Transactional
    public SpaceMembershipResponse acceptInvitation(UUID invitationId, UUID userId) {
        InvitationEntity invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", "id", invitationId));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BusinessException(
                    "Invitation is no longer valid. Status: " + invitation.getStatus());
        }

        if (LocalDateTime.now().isAfter(invitation.getExpiresAt())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            invitationNotificationSyncService.onInvitationCancelledOrExpired(invitation);
            throw new BusinessException("Invitation has expired");
        }

        UserEntity user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!invitation.getMobileNumber().equals(user.getMobileNumber())) {
            throw new BusinessException(
                    "This invitation was sent to a different mobile number", HttpStatus.FORBIDDEN);
        }

        boolean alreadyMember = spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                userId, invitation.getSpace().getId(), MembershipStatus.ACTIVE);

        if (alreadyMember) {
            throw new BusinessException(
                    "User already has an active membership in this space", HttpStatus.CONFLICT);
        }

        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(user)
                .space(invitation.getSpace())
                .role(invitation.getRole())
                .status(MembershipStatus.ACTIVE)
                .invitation(invitation)
                .joinedAt(LocalDateTime.now())
                .build();

        membership = spaceMembershipRepository.save(membership);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(LocalDateTime.now());
        invitationRepository.save(invitation);

        MemberEntity member = memberMasterService.linkMemberToMembership(
                membership, user.getFullName(), user.getMobileNumber());
        mealParticipationService.enrollDefaultForMemberIfEligible(member, user);

        invitationNotificationSyncService.onInvitationAccepted(invitation, userId);

        return SpaceMembershipResponse.from(membership);
    }
}
