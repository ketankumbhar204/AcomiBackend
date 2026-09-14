package com.acomi.acomi_backend.notification.application.job;

import com.acomi.acomi_backend.member.application.service.InvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marks pending invitations EXPIRED after their expiry timestamp and notifies the invitee once.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvitationExpiryJob {

    private final InvitationService invitationService;

    @Value("${acomi.invitations.expiry-job.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${acomi.invitations.expiry-job-delay-ms:3600000}")
    public void expirePendingInvitations() {
        if (!enabled) {
            return;
        }
        try {
            int expired = invitationService.expireDue();
            if (expired > 0) {
                log.info("Expired {} invitation{}", expired, expired == 1 ? "" : "s");
            }
        } catch (Exception ex) {
            log.error("Invitation expiry job failed", ex);
        }
    }
}
