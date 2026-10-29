package com.acomi.acomi_backend.servicecontact.application;

import java.util.Set;
import java.util.UUID;

/**
 * Trusted view of what the authenticated user may do with service contacts.
 * Built from active space memberships. Unknown roles are not included.
 */
public record CallerScope(
        UUID callerId,
        boolean ownsAccountDirectory,
        Set<UUID> manageableSpaceIds,
        Set<UUID> visibleSpaceIds) {}
