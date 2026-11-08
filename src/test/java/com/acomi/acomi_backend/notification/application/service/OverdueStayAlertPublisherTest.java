package com.acomi.acomi_backend.notification.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.UnexpectedRollbackException;

@ExtendWith(MockitoExtension.class)
class OverdueStayAlertPublisherTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ObjectProvider<OverdueStayAlertPublisher> self;

    @InjectMocks
    private OverdueStayAlertPublisher publisher;

    @Test
    void uniqueIndexConflict_doesNotEscapeTheScheduler() {
        when(self.getObject()).thenReturn(publisher);
        doThrow(new DataIntegrityViolationException("uq_space_notifications_open_dedupe"))
                .when(notificationService).publish(any());

        assertThatCode(() -> publisher.publishOnce(command())).doesNotThrowAnyException();
    }

    @Test
    void rolledBackDuplicate_doesNotEscapeTheScheduler() {
        when(self.getObject()).thenReturn(publisher);
        doThrow(new UnexpectedRollbackException("duplicate"))
                .when(notificationService).publish(any());

        assertThatCode(() -> publisher.publishOnce(command())).doesNotThrowAnyException();
    }

    private static PublishNotificationCommand command() {
        return PublishNotificationCommand.builder()
                .notificationType(NotificationType.STAY_OVERDUE_ACTIVE)
                .dedupeKey("STAY_OVERDUE_ACTIVE:stay:2026-11-08:owner")
                .title("Stay still active")
                .build();
    }
}
