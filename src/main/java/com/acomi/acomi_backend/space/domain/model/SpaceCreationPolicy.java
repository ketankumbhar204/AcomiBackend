package com.acomi.acomi_backend.space.domain.model;

/**
 * A space can be created by an account that already owns one, or by an account
 * with no membership yet (first space). Tenant, customer, staff, and manager
 * memberships do not grant creation.
 */
public final class SpaceCreationPolicy {

    private SpaceCreationPolicy() {}

    public static boolean mayCreateSpace(boolean hasActiveMembership, boolean hasOwnerMembership) {
        if (!hasActiveMembership) {
            return true;
        }
        return hasOwnerMembership;
    }
}
