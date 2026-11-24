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
        Set<UUID> visibleSpaceIds,
        /**
         * Account owners whose global directory this caller may search.
         * Includes the caller when they own a directory, and the owner of each
         * space they operate. This is visibility, not a space association.
         */
        Set<UUID> directoryOwnerIds) {}
