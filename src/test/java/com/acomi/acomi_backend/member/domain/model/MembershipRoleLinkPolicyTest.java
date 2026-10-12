package com.acomi.acomi_backend.member.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.acomi.acomi_backend.space.domain.model.SpaceType;
import org.junit.jupiter.api.Test;

class MembershipRoleLinkPolicyTest {

    @Test
    void pgAcceptanceStoresTenant() {
        assertEquals(
                MembershipRole.TENANT,
                MembershipRoleLinkPolicy.roleAfterLink(null, MembershipRole.TENANT, SpaceType.PG));
    }

    @Test
    void messAcceptanceStoresCustomer() {
        assertEquals(
                MembershipRole.CUSTOMER,
                MembershipRoleLinkPolicy.roleAfterLink(null, MembershipRole.CUSTOMER, SpaceType.MESS));
    }

    @Test
    void messAcceptanceDoesNotOverwritePgTenant() {
        assertEquals(
                MembershipRole.TENANT,
                MembershipRoleLinkPolicy.roleAfterLink(
                        MembershipRole.TENANT, MembershipRole.CUSTOMER, SpaceType.PG));
        assertEquals(
                MembershipRole.TENANT,
                MembershipRoleLinkPolicy.roleAfterLink(
                        MembershipRole.TENANT, MembershipRole.CUSTOMER, SpaceType.HOSTEL));
    }

    @Test
    void pgAcceptanceDoesNotOverwriteMessCustomer() {
        assertEquals(
                MembershipRole.CUSTOMER,
                MembershipRoleLinkPolicy.roleAfterLink(
                        MembershipRole.CUSTOMER, MembershipRole.TENANT, SpaceType.MESS));
    }

    @Test
    void bothRelationshipsStayIndependent() {
        assertEquals(
                MembershipRole.TENANT,
                MembershipRoleLinkPolicy.roleAfterLink(
                        MembershipRole.TENANT, MembershipRole.TENANT, SpaceType.CO_LIVING));
        assertEquals(
                MembershipRole.CUSTOMER,
                MembershipRoleLinkPolicy.roleAfterLink(
                        MembershipRole.CUSTOMER, MembershipRole.CUSTOMER, SpaceType.MESS));
    }

    @Test
    void ownerManagerAndStaffKeepTheirRoles() {
        assertEquals(
                MembershipRole.OWNER,
                MembershipRoleLinkPolicy.roleAfterLink(
                        MembershipRole.OWNER, MembershipRole.CUSTOMER, SpaceType.PG));
        assertEquals(
                MembershipRole.MANAGER,
                MembershipRoleLinkPolicy.roleAfterLink(
                        MembershipRole.MANAGER, MembershipRole.TENANT, SpaceType.MESS));
        assertEquals(
                MembershipRole.STAFF,
                MembershipRoleLinkPolicy.roleAfterLink(
                        MembershipRole.STAFF, MembershipRole.CUSTOMER, SpaceType.RENTAL));
    }

    @Test
    void unknownSpaceDoesNotInventAServiceRole() {
        assertEquals(
                MembershipRole.TENANT,
                MembershipRoleLinkPolicy.roleAfterLink(null, MembershipRole.TENANT, null));
        assertEquals(
                MembershipRole.CUSTOMER,
                MembershipRoleLinkPolicy.roleAfterLink(
                        MembershipRole.CUSTOMER, MembershipRole.TENANT, null));
    }
}
