package com.acomi.acomi_backend.notification.application.job;

import com.acomi.acomi_backend.notification.application.service.OccupancyNotificationSyncService;
import com.acomi.acomi_backend.notification.application.service.PendingActionService;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily occupancy and meal Action Center sync so time-based conditions
 * (move-in today, menu not planned) do not depend on opening the dashboard.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OperationalNotificationScheduler {

    private final SpaceRepository spaceRepository;
    private final OccupancyNotificationSyncService occupancyNotificationSyncService;
    private final PendingActionService pendingActionService;

    @Value("${acomi.notifications.ops-scheduler.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${acomi.notifications.ops-scheduler.cron:0 15 8 * * *}")
    public void syncOperationalActions() {
        if (!enabled) {
            return;
        }
        List<SpaceEntity> spaces = spaceRepository.findByIsActiveTrue();
        log.info("Operational notification scheduler started activeSpaces={}", spaces.size());
        int occupancyOk = 0;
        int mealOk = 0;
        for (SpaceEntity space : spaces) {
            try {
                occupancyNotificationSyncService.syncSpace(space.getId());
                occupancyOk++;
            } catch (Exception ex) {
                log.error("Occupancy action sync failed for space={}", space.getId(), ex);
            }
            try {
                pendingActionService.syncMealOperations(space.getId());
                mealOk++;
            } catch (Exception ex) {
                log.error("Meal action sync failed for space={}", space.getId(), ex);
            }
        }
        log.info(
                "Operational notification scheduler finished spaces={} occupancySynced={} mealSynced={}",
                spaces.size(),
                occupancyOk,
                mealOk);
    }
}
