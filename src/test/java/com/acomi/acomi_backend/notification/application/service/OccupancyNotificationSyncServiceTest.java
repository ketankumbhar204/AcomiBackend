package com.acomi.acomi_backend.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OccupancyNotificationSyncServiceTest {

    @Mock
    private OccupancyRepository occupancyRepository;

    @Mock
    private SpaceMembershipRepository membershipRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private OccupancyNotificationSyncService service;

    private UUID spaceId;
    private UUID occupancyId;
    private UUID tenantUserId;
    private OccupancyEntity occupancy;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        occupancyId = UUID.randomUUID();
        tenantUserId = UUID.randomUUID();

        SpaceEntity space = SpaceEntity.builder().name("Sunrise PG").isActive(true).build();
        space.setId(spaceId);

        UserEntity tenant = UserEntity.builder().fullName("Ravi").mobileNumber("9111111111").build();
        tenant.setId(tenantUserId);

        MemberEntity member = MemberEntity.builder().space(space).user(tenant).fullName("Ravi").build();
        member.setId(UUID.randomUUID());

        occupancy = OccupancyEntity.builder().space(space).member(member).build();
        occupancy.setId(occupancyId);
    }

    @Test
    void onAllocationCreated_notifiesLinkedTenantOnly() {
        service.onAllocationCreated(occupancy);

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        PublishNotificationCommand command = captor.getValue();
        assertThat(command.getUserId()).isEqualTo(tenantUserId);
        assertThat(command.getNotificationType()).isEqualTo(NotificationType.ALLOCATION_CREATED);
        assertThat(command.getActionRoute()).isEqualTo("Dashboard");
        assertThat(command.getEntityId()).isEqualTo(occupancyId);
    }

    @Test
    void onAllocationCreated_skipsWhenMemberHasNoLinkedUser() {
        occupancy.getMember().setUser(null);

        service.onAllocationCreated(occupancy);

        verify(notificationService, never()).publish(any());
    }

    @Test
    void onOccupancyTransferred_notifiesLinkedTenant() {
        service.onOccupancyTransferred(occupancy);

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.ALLOCATION_CREATED);
        assertThat(captor.getValue().getTitle()).contains("updated");
    }

    @Test
    void onReservationCancelled_notifiesLinkedTenant() {
        service.onReservationCancelled(occupancy);

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.RESERVATION_CANCELLED);
        assertThat(captor.getValue().getUserId()).isEqualTo(tenantUserId);
        assertThat(captor.getValue().getActionRoute()).isEqualTo("Dashboard");
    }

    @Test
    void onMoveOutCompleted_notifiesTenantWithDashboardRoute() {
        when(membershipRepository.findBySpaceIdAndStatus(any(), any())).thenReturn(java.util.List.of());

        service.onMoveOutCompleted(occupancy);

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        PublishNotificationCommand command = captor.getValue();
        assertThat(command.getUserId()).isEqualTo(tenantUserId);
        assertThat(command.getNotificationType()).isEqualTo(NotificationType.MOVE_OUT_COMPLETED);
        assertThat(command.getActionRoute()).isEqualTo("Dashboard");
        assertThat(command.getActionRoute()).isNotEqualTo("DashboardOccupancyList");
    }
}
