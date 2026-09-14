package com.acomi.acomi_backend.storage.application.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.storage.application.service.StoredFileService;
import com.acomi.acomi_backend.storage.config.StorageProperties;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoredFileCleanupJobTest {

    @Mock
    private StoredFileService storedFileService;

    @Mock
    private StorageProperties storageProperties;

    @InjectMocks
    private StoredFileCleanupJob job;

    @Test
    void cleanupIsIdempotentAndUsesConfiguredGrace() {
        when(storageProperties.getUnassociatedGraceDays()).thenReturn(7);
        when(storedFileService.cleanupExpiredPending(any(LocalDateTime.class))).thenReturn(1, 0);
        when(storedFileService.markUnassociatedForDelete(any(LocalDateTime.class))).thenReturn(0);
        when(storedFileService.purgeDue(any(LocalDateTime.class))).thenReturn(2, 0);

        job.cleanup();
        job.cleanup();

        verify(storedFileService, times(2)).cleanupExpiredPending(any(LocalDateTime.class));
        verify(storedFileService, times(2)).purgeDue(any(LocalDateTime.class));
    }
}
