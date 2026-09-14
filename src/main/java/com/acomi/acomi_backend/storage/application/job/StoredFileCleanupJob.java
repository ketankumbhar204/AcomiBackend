package com.acomi.acomi_backend.storage.application.job;

import com.acomi.acomi_backend.storage.application.service.StoredFileService;
import com.acomi.acomi_backend.storage.config.StorageProperties;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoredFileCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(StoredFileCleanupJob.class);

    private final StoredFileService storedFileService;
    private final StorageProperties storageProperties;

    @Scheduled(fixedDelayString = "${acomi.storage.cleanup-job-delay-ms:900000}")
    public void cleanup() {
        try {
            LocalDateTime now = LocalDateTime.now();
            int pending = storedFileService.cleanupExpiredPending(now);
            int unassociated = storedFileService.markUnassociatedForDelete(
                    now.minusDays(Math.max(1, storageProperties.getUnassociatedGraceDays())));
            int purged = storedFileService.purgeDue(now);
            if (pending > 0 || unassociated > 0 || purged > 0) {
                log.info(
                        "file_cleanup_job pendingExpired={} unassociated={} purged={}",
                        pending,
                        unassociated,
                        purged);
            }
        } catch (Exception ex) {
            log.error("file_cleanup_job_failed", ex);
        }
    }
}
