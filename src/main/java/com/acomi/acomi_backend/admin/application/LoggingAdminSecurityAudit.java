package com.acomi.acomi_backend.admin.application;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingAdminSecurityAudit implements AdminSecurityAudit {

    public static final String PASSWORD_RESET = "ADMIN_PASSWORD_RESET";

    private static final Logger log = LoggerFactory.getLogger("acomi.audit");

    @Override
    public void record(UUID actorId, UUID targetUserId, String action, String result) {
        log.info(
                "audit action={} result={} actorId={} targetUserId={}",
                action,
                result,
                actorId,
                targetUserId);
    }
}
