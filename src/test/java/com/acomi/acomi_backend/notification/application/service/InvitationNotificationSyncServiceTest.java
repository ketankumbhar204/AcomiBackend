package com.acomi.acomi_backend.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.member.domain.model.InvitationStatus;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.InvitationEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.InvitationRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationNotificationSyncServiceTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private SpaceMembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private InvitationNotificationSyncService service;

    private UUID spaceId;
    private UUID managerId;
    private UUID inviteeId;
    private SpaceEntity space;
    private InvitationEntity invitation;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        inviteeId = UUID.randomUUID();

        space = SpaceEntity.builder().name("Sunrise PG").isActive(true).build();
        space.setId(spaceId);

        UserEntity invitedBy = UserEntity.builder().fullName("Owner").mobileNumber("9999999999").build();
        invitedBy.setId(managerId);

        invitation = InvitationEntity.builder()
                .space(space)
                .invitedBy(invitedBy)
                .mobileNumber("9111111111")
                .role(MembershipRole.TENANT)
                .status(InvitationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        invitation.setId(UUID.randomUUID());
    }

    @Test
    void onInvitationCreated_doesNotPutMobileNumberInManagerBody() {
        UserEntity manager = UserEntity.builder().fullName("Owner").build();
        manager.setId(managerId);
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(manager)
                .space(space)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(invitationRepository.findPendingInvitations(spaceId)).thenReturn(List.of(invitation));
        when(membershipRepository.findBySpaceIdAndStatus(spaceId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(membership));
        when(userRepository.findByMobileNumber("9111111111")).thenReturn(Optional.empty());
        when(notificationService.listOpenActions(spaceId, managerId)).thenReturn(List.of());

        service.onInvitationCreated(invitation);

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        PublishNotificationCommand command = captor.getValue();
        assertThat(command.getNotificationType()).isEqualTo(NotificationType.PENDING_INVITATION);
        assertThat(command.getMessage()).doesNotContain("9111111111");
        assertThat(command.getMessage()).isEqualTo("A new member has been invited to your space");
    }

    @Test
    void onInvitationCancelledOrExpired_usesExpiredTypeOnce() {
        invitation.setStatus(InvitationStatus.EXPIRED);
        UserEntity invitee = UserEntity.builder().fullName("Ravi").mobileNumber("9111111111").isActive(true).build();
        invitee.setId(inviteeId);
        when(userRepository.findByMobileNumber("9111111111")).thenReturn(Optional.of(invitee));

        service.onInvitationCancelledOrExpired(invitation);

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.INVITATION_EXPIRED);
        assertThat(captor.getValue().getUserId()).isEqualTo(inviteeId);
        assertThat(captor.getValue().getActionRoute()).isEqualTo("AcceptInvitations");
        verify(notificationService).resolveOpenForEntity(any(), any(), any(), any());
    }
}
