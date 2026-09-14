package com.acomi.acomi_backend.enquiry.application.job;

import com.acomi.acomi_backend.enquiry.application.service.SpaceEnquiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marks pending enquiries EXPIRED after the retention window.
 * History rows are kept. Share is blocked for expired enquiries at action time as well.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpaceEnquiryExpiryJob {

    private final SpaceEnquiryService spaceEnquiryService;

    @Scheduled(fixedDelayString = "${acomi.enquiry.expiry-job-delay-ms:3600000}")
    public void expirePendingEnquiries() {
        try {
            int expired = spaceEnquiryService.expireDue();
            if (expired > 0) {
                log.info("Expired {} contact enquir{}", expired, expired == 1 ? "y" : "ies");
            }
        } catch (Exception ex) {
            log.error("Enquiry expiry job failed", ex);
        }
    }
}
