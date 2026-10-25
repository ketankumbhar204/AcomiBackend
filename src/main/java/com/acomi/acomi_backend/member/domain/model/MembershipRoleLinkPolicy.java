package com.acomi.acomi_backend.member.domain.model;

import com.acomi.acomi_backend.space.domain.model.SpaceType;

/**
 * Decides the role stored on a member row when an invitation is linked.
 * Each space keeps its own member row. A mess acceptance must not replace a
 * lodging tenant role, and a PG acceptance must not replace a mess customer role.
 * OWNER, MANAGER, and STAFF are not rewritten by a consumer invitation.
 */
public final class MembershipRoleLinkPolicy {

    private MembershipRoleLinkPolicy() {}

    public static MembershipRole roleAfterLink(
            MembershipRole existingRole, MembershipRole incomingRole, SpaceType spaceType) {
        if (existingRole == null) {
            return incomingRole;
        }
        if (existingRole == MembershipRole.OWNER) {
            return MembershipRole.OWNER;
        }
        if (isPrivileged(existingRole) && isConsumer(incomingRole)) {
            return existingRole;
        }
        MembershipRole canonical = canonicalConsumerRole(spaceType);
        if (canonical != null
                && existingRole == canonical
                && isConsumer(incomingRole)
                && incomingRole != canonical) {
            return existingRole;
        }
        if (canonical == null && isConsumer(existingRole) && isConsumer(incomingRole)) {
            return existingRole;
        }
        return incomingRole;
    }

    public static MembershipRole canonicalConsumerRole(SpaceType spaceType) {
        if (spaceType == null) {
            return null;
        }
        if (spaceType == SpaceType.MESS) {
            return MembershipRole.CUSTOMER;
        }
        if (spaceType == SpaceType.PG
                || spaceType == SpaceType.HOSTEL
                || spaceType == SpaceType.CO_LIVING
                || spaceType == SpaceType.RENTAL
                || spaceType == SpaceType.CORPORATE_STAY) {
            return MembershipRole.TENANT;
        }
        return null;
    }

    private static boolean isConsumer(MembershipRole role) {
        return role == MembershipRole.TENANT || role == MembershipRole.CUSTOMER;
    }

    private static boolean isPrivileged(MembershipRole role) {
        return role == MembershipRole.OWNER
                || role == MembershipRole.MANAGER
                || role == MembershipRole.STAFF;
    }
}
