package com.acomi.acomi_backend.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class OccupancyNotificationSyncServiceTest {

    @Mock
    private OccupancyRepository occupancyRepository;

    @Mock
    private SpaceMembershipRepository membershipRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private OverdueStayAlertPublisher overdueStayAlertPublisher;

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

    @Test
    void overdueStay_alertsWithoutChangingTheStay() {
        LocalDate businessDate = LocalDate.of(2026, 11, 8);
        LocalDate plannedEnd = LocalDate.of(2026, 11, 7);
        occupancy.setStatus(OccupancyStatus.ACTIVE);
        occupancy.setExpectedExitDate(plannedEnd);
        stubManagers();
        when(occupancyRepository.findActiveStaysPastPlannedEnd(spaceId, OccupancyStatus.ACTIVE, businessDate))
                .thenReturn(List.of(occupancy));

        service.syncSpace(spaceId, businessDate, businessDate);

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(overdueStayAlertPublisher).publishOnce(captor.capture());
        PublishNotificationCommand command = captor.getValue();
        assertThat(command.getNotificationType()).isEqualTo(NotificationType.STAY_OVERDUE_ACTIVE);
        assertThat(command.getDedupeKey()).contains(plannedEnd.plusDays(1).toString()).contains(occupancyId.toString());
        assertThat(command.getTitle()).isEqualTo("Stay still active");
        assertThat(command.getActionLabel()).isEqualTo("View stay");
        assertThat(command.getMessage()).doesNotContain("Complete Vacate");
        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.ACTIVE);
        assertThat(occupancy.getExpectedExitDate()).isEqualTo(plannedEnd);
        assertThat(occupancy.getVacatedAt()).isNull();
        verify(occupancyRepository, never()).save(any());
        verify(notificationService, never()).publish(any());
    }

    @Test
    void plannedEndOnTheBusinessDate_keepsCheckoutTodayAndSkipsTheOverdueAlert() {
        LocalDate today = LocalDate.of(2026, 11, 8);
        occupancy.setStatus(OccupancyStatus.ACTIVE);
        occupancy.setExpectedExitDate(today);
        stubManagers();
        when(occupancyRepository.findBySpaceIdAndStatusAndExpectedExitDate(spaceId, OccupancyStatus.ACTIVE, today))
                .thenReturn(List.of(occupancy));

        service.syncSpace(spaceId, today, today);

        verify(overdueStayAlertPublisher, never()).publishOnce(any());
        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.MOVE_OUT_SCHEDULED_TODAY);
        assertThat(captor.getValue().getDedupeKey()).doesNotContain("STAY_OVERDUE_ACTIVE");
        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.ACTIVE);
    }

    @Test
    void futurePlannedEnd_doesNotAlert() {
        LocalDate businessDate = LocalDate.of(2026, 11, 8);
        stubManagers();

        service.syncSpace(spaceId, businessDate, businessDate);

        verify(overdueStayAlertPublisher, never()).publishOnce(any());
        verify(notificationService, never()).publish(any());
    }

    @Test
    void vacatedAndReservedRows_doNotAlertEvenIfTheQueryReturnsThem() {
        LocalDate businessDate = LocalDate.of(2026, 11, 8);
        occupancy.setStatus(OccupancyStatus.ACTIVE);
        occupancy.setExpectedExitDate(businessDate.minusDays(1));
        occupancy.setVacatedAt(LocalDateTime.of(2026, 11, 7, 9, 0));
        OccupancyEntity reserved = OccupancyEntity.builder().space(occupancy.getSpace()).member(occupancy.getMember()).build();
        reserved.setId(UUID.randomUUID());
        reserved.setStatus(OccupancyStatus.RESERVED);
        reserved.setExpectedExitDate(businessDate.minusDays(2));
        stubManagers();
        when(occupancyRepository.findActiveStaysPastPlannedEnd(spaceId, OccupancyStatus.ACTIVE, businessDate))
                .thenReturn(List.of(occupancy, reserved));

        service.syncSpace(spaceId, businessDate, businessDate);

        verify(overdueStayAlertPublisher, never()).publishOnce(any());
        verify(notificationService, never()).publish(argThat(command ->
                command.getNotificationType() == NotificationType.EXPIRED_RESERVATION
                        || command.getNotificationType() == NotificationType.VACANT_RESERVED_BED));
    }

    @Test
    void repeatedSync_usesTheSameDedupeKey_andTheNextBusinessDayUsesAnother() {
        LocalDate businessDate = LocalDate.of(2026, 11, 8);
        occupancy.setStatus(OccupancyStatus.ACTIVE);
        occupancy.setExpectedExitDate(businessDate.minusDays(1));
        stubManagers();
        when(occupancyRepository.findActiveStaysPastPlannedEnd(eq(spaceId), eq(OccupancyStatus.ACTIVE), any()))
                .thenReturn(List.of(occupancy));

        service.syncSpace(spaceId, businessDate, businessDate);
        service.syncSpace(spaceId, businessDate, businessDate);
        service.syncSpace(spaceId, businessDate.plusDays(1), businessDate.plusDays(1));

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(overdueStayAlertPublisher, times(3)).publishOnce(captor.capture());
        assertThat(captor.getAllValues().get(0).getDedupeKey()).isEqualTo(captor.getAllValues().get(1).getDedupeKey());
        assertThat(captor.getAllValues().get(2).getDedupeKey()).isNotEqualTo(captor.getAllValues().get(0).getDedupeKey());
        assertThat(captor.getAllValues().get(2).getDedupeKey()).contains("2026-11-09");
    }

    @Test
    void duplicateAlertInsert_doesNotChangeTheStay() {
        LocalDate businessDate = LocalDate.of(2026, 11, 8);
        occupancy.setStatus(OccupancyStatus.ACTIVE);
        occupancy.setExpectedExitDate(businessDate.minusDays(1));
        stubManagers();
        when(occupancyRepository.findActiveStaysPastPlannedEnd(spaceId, OccupancyStatus.ACTIVE, businessDate))
                .thenReturn(List.of(occupancy));

        service.syncSpace(spaceId, businessDate, businessDate);
        service.syncSpace(spaceId, businessDate, businessDate);

        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.ACTIVE);
        assertThat(occupancy.getVacatedAt()).isNull();
        verify(occupancyRepository, never()).save(any());
        verify(overdueStayAlertPublisher, times(2)).publishOnce(any());
    }

    private UUID managerUserId;

    private void stubManagers() {
        managerUserId = UUID.randomUUID();
        UserEntity manager = UserEntity.builder().fullName("Owner").mobileNumber("9222222222").build();
        manager.setId(managerUserId);
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .user(manager)
                .build();
        when(membershipRepository.findBySpaceIdAndStatus(spaceId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(membership));
        when(occupancyRepository.findBySpaceIdAndStatusAndMoveInDate(any(), any(), any())).thenReturn(List.of());
        when(occupancyRepository.findBySpaceIdAndStatusAndExpectedExitDate(any(), any(), any())).thenReturn(List.of());
        when(occupancyRepository.findActiveStaysPastPlannedEnd(any(), any(), any())).thenReturn(List.of());
        when(notificationService.listOpenActions(any(), any())).thenReturn(List.of());
    }
}
