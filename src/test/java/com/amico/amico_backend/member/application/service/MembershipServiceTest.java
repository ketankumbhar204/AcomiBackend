package com.amico.amico_backend.member.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.common.exception.ResourceNotFoundException;
import com.amico.amico_backend.member.domain.model.InvitationStatus;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.domain.model.MembershipStatus;
import com.amico.amico_backend.member.infrastructure.persistence.entity.InvitationEntity;
import com.amico.amico_backend.member.infrastructure.persistence.repository.InvitationRepository;
import com.amico.amico_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.amico.amico_backend.user.infrastructure.persistence.entity.UserEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private InvitationRepository invitationRepository;

    @InjectMocks
    private MembershipService membershipService;

    private UUID spaceId;
    private UUID ownerId;
    private UUID managerId;
    private UUID otherUserId;
    private SpaceEntity space;
    private UserEntity owner;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        owner = UserEntity.builder()
                .mobileNumber("9876543210")
                .fullName("Owner User")
                .build();
        owner.setId(ownerId);

        space = SpaceEntity.builder()
                .owner(owner)
                .name("Sunrise PG")
                .type(SpaceType.PG)
                .isActive(true)
                .build();
        space.setId(spaceId);
    }

    @Test
    void getPendingInvitations_returnsPendingOnly() {
        InvitationEntity invitation = InvitationEntity.builder()
                .space(space)
                .invitedBy(owner)
                .mobileNumber("9999999999")
                .role(MembershipRole.TENANT)
                .status(InvitationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitation.setId(UUID.randomUUID());
        invitation.setCreatedAt(LocalDateTime.now());

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                ownerId, spaceId, MembershipStatus.ACTIVE)).thenReturn(true);
        when(invitationRepository.findPendingInvitations(spaceId)).thenReturn(List.of(invitation));

        var responses = membershipService.getPendingInvitations(spaceId, ownerId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getMobileNumber()).isEqualTo("9999999999");
        assertThat(responses.get(0).getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void getPendingInvitations_whenCallerDoesNotBelong_throwsForbidden() {
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                otherUserId, spaceId, MembershipStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> membershipService.getPendingInvitations(spaceId, otherUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void cancelInvitation_whenManager_cancelsPendingInvitation() {
        InvitationEntity invitation = InvitationEntity.builder()
                .space(space)
                .invitedBy(owner)
                .mobileNumber("9999999999")
                .role(MembershipRole.TENANT)
                .status(InvitationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        UUID invitationId = UUID.randomUUID();
        invitation.setId(invitationId);

        when(invitationRepository.findPendingInvitation(invitationId)).thenReturn(Optional.of(invitation));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                managerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);

        membershipService.cancelInvitation(invitationId, managerId);

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
        verify(invitationRepository).save(invitation);
    }

    @Test
    void cancelInvitation_whenNotPending_throwsNotFound() {
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findPendingInvitation(invitationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.cancelInvitation(invitationId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
