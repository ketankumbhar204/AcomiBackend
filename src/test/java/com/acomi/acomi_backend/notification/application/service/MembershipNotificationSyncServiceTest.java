package com.acomi.acomi_backend.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MembershipNotificationSyncServiceTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private SpaceMembershipRepository membershipRepository;

    @InjectMocks
    private MembershipNotificationSyncService service;

    private UUID spaceId;
    private UUID actorId;
    private UUID tenantUserId;
    private SpaceEntity space;
    private MemberEntity member;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        tenantUserId = UUID.randomUUID();

        space = SpaceEntity.builder().name("Sunrise PG").isActive(true).build();
        space.setId(spaceId);

        UserEntity tenant = UserEntity.builder().fullName("Ravi").mobileNumber("9111111111").build();
        tenant.setId(tenantUserId);
        member = MemberEntity.builder().space(space).user(tenant).fullName("Ravi").build();
        member.setId(UUID.randomUUID());
    }

    @Test
    void onMemberRemoved_notifiesLinkedUser() {
        service.onMemberRemoved(member);

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.MEMBERSHIP_REMOVED);
        assertThat(captor.getValue().getUserId()).isEqualTo(tenantUserId);
        assertThat(captor.getValue().getActionRoute()).isEqualTo("MySpaces");
    }

    @Test
    void onMemberRemoved_skipsWhenUserMissing() {
        member.setUser(null);

        service.onMemberRemoved(member);

        verify(notificationService, never()).publish(any());
    }

    @Test
    void onMemberRoleChanged_notifiesLinkedUser() {
        service.onMemberRoleChanged(member, MembershipRole.TENANT, MembershipRole.MANAGER);

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.MEMBERSHIP_ROLE_CHANGED);
        assertThat(captor.getValue().getMessage()).contains("MANAGER");
    }

    @Test
    void onMemberRoleChanged_skipsWhenUnchanged() {
        service.onMemberRoleChanged(member, MembershipRole.TENANT, MembershipRole.TENANT);

        verify(notificationService, never()).publish(any());
    }

    @Test
    void onSpaceDeactivated_notifiesActiveMembersOnceAndSkipsActor() {
        UserEntity actor = UserEntity.builder().fullName("Owner").build();
        actor.setId(actorId);
        UserEntity other = UserEntity.builder().fullName("Ravi").build();
        other.setId(tenantUserId);
        SpaceMembershipEntity actorMembership = SpaceMembershipEntity.builder()
                .user(actor)
                .space(space)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();
        SpaceMembershipEntity tenantMembership = SpaceMembershipEntity.builder()
                .user(other)
                .space(space)
                .role(MembershipRole.TENANT)
                .status(MembershipStatus.ACTIVE)
                .build();
        SpaceMembershipEntity duplicate = SpaceMembershipEntity.builder()
                .user(other)
                .space(space)
                .role(MembershipRole.TENANT)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(membershipRepository.findBySpaceIdAndStatus(spaceId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(actorMembership, tenantMembership, duplicate));

        service.onSpaceDeactivated(space, actorId);

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService, times(1)).publish(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(tenantUserId);
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.SPACE_DEACTIVATED);
        verify(membershipRepository).findBySpaceIdAndStatus(spaceId, MembershipStatus.ACTIVE);
    }

    @Test
    void onOwnershipTransferred_notifiesPreviousAndNewOwnerDistinctly() {
        UUID previous = UUID.randomUUID();
        UUID next = UUID.randomUUID();

        service.onOwnershipTransferred(space, previous, next);

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService, times(2)).publish(captor.capture());
        List<PublishNotificationCommand> commands = captor.getAllValues();
        assertThat(commands).extracting(PublishNotificationCommand::getUserId).containsExactly(previous, next);
        assertThat(commands.get(0).getTitle()).isEqualTo("Ownership transferred");
        assertThat(commands.get(0).getActionRoute()).isEqualTo("MySpaces");
        assertThat(commands.get(1).getTitle()).isEqualTo("You are the new owner");
        assertThat(commands.get(1).getActionRoute()).isEqualTo("Dashboard");
    }
}
