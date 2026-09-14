package com.acomi.acomi_backend.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.acomi.acomi_backend.meal.domain.model.MealParticipationStatus;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SubscriptionActivationRequestEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SubscriptionPlanEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
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
class MealLifecycleNotificationSyncServiceTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private MealLifecycleNotificationSyncService service;

    private UUID actorId;
    private UUID customerId;
    private SpaceEntity space;
    private MemberEntity member;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        space = SpaceEntity.builder().name("Tiffin Mess").isActive(true).build();
        space.setId(UUID.randomUUID());
        UserEntity user = UserEntity.builder().fullName("Customer").mobileNumber("9222222222").build();
        user.setId(customerId);
        member = MemberEntity.builder().space(space).user(user).fullName("Customer").build();
        member.setId(UUID.randomUUID());
    }

    @Test
    void onSubscriptionApproved_notifiesLinkedCustomer() {
        SubscriptionPlanEntity plan = SubscriptionPlanEntity.builder().name("Monthly 60").build();
        SubscriptionActivationRequestEntity request = SubscriptionActivationRequestEntity.builder()
                .space(space)
                .member(member)
                .plan(plan)
                .build();
        request.setId(UUID.randomUUID());

        service.onSubscriptionApproved(request, actorId);

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        assertThat(captor.getValue().getNotificationType())
                .isEqualTo(NotificationType.SUBSCRIPTION_ACTIVATION_APPROVED);
        assertThat(captor.getValue().getUserId()).isEqualTo(customerId);
        assertThat(captor.getValue().getActionRoute()).isEqualTo("Meals");
    }

    @Test
    void onSubscriptionRejected_usesDistinctType() {
        SubscriptionActivationRequestEntity request = SubscriptionActivationRequestEntity.builder()
                .space(space)
                .member(member)
                .build();
        request.setId(UUID.randomUUID());

        service.onSubscriptionRejected(request, actorId);

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        assertThat(captor.getValue().getNotificationType())
                .isEqualTo(NotificationType.SUBSCRIPTION_ACTIVATION_REJECTED);
    }

    @Test
    void onParticipationChanged_notifiesLinkedUser() {
        MealParticipationEntity participation = MealParticipationEntity.builder()
                .space(space)
                .member(member)
                .status(MealParticipationStatus.PAUSED)
                .build();
        participation.setId(UUID.randomUUID());

        service.onParticipationChanged(participation, MealParticipationStatus.PAUSED);

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        assertThat(captor.getValue().getNotificationType())
                .isEqualTo(NotificationType.MEAL_PARTICIPATION_CHANGED);
        assertThat(captor.getValue().getMessage()).contains("paused");
    }

    @Test
    void onSubscriptionApproved_skipsWhenNoLinkedUser() {
        member.setUser(null);
        SubscriptionActivationRequestEntity request = SubscriptionActivationRequestEntity.builder()
                .space(space)
                .member(member)
                .build();
        request.setId(UUID.randomUUID());

        service.onSubscriptionApproved(request, actorId);

        verify(notificationService, never()).publish(any());
    }
}
