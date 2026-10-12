package com.acomi.acomi_backend.admin.application;

import java.util.UUID;

/**
 * Records administrative security actions. Implementations must not accept or store passwords.
 */
public interface AdminSecurityAudit {

    void record(UUID actorId, UUID targetUserId, String action, String result);
}
