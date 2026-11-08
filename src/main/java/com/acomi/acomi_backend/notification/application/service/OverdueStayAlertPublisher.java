package com.acomi.acomi_backend.notification.application.service;

import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves one overdue-stay alert in its own transaction.
 * A duplicate open row hits {@code uq_space_notifications_open_dedupe} and is ignored.
 * That failure stays outside the occupancy transaction and does not vacate a stay.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueStayAlertPublisher {

    private final NotificationService notificationService;
    private final ObjectProvider<OverdueStayAlertPublisher> self;

    public void publishOnce(PublishNotificationCommand command) {
        try {
            self.getObject().insert(command);
        } catch (DataIntegrityViolationException | UnexpectedRollbackException ex) {
            log.info("Overdue stay alert already open dedupeKey={}", command.getDedupeKey());
        } catch (RuntimeException ex) {
            log.error("Overdue stay alert was not saved dedupeKey={}", command.getDedupeKey(), ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(PublishNotificationCommand command) {
        notificationService.publish(command);
    }
}
