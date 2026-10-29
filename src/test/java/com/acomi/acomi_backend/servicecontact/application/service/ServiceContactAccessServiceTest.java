package com.acomi.acomi_backend.servicecontact.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.servicecontact.application.CallerScope;
import com.acomi.acomi_backend.servicecontact.domain.model.ServiceContactCategory;
import com.acomi.acomi_backend.servicecontact.infrastructure.persistence.entity.ServiceContactEntity;
import com.acomi.acomi_backend.servicecontact.infrastructure.persistence.entity.ServiceContactSpaceEntity;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceContactAccessServiceTest {

    @Mock
    private SpaceMembershipRepository membershipRepository;

    private ServiceContactAccessService accessService;
    private UUID ownerId;
    private UUID managerId;
    private UUID staffId;
    private UUID tenantId;
    private UUID spaceA;
    private UUID spaceB;

    @BeforeEach
    void setUp() {
        accessService = new ServiceContactAccessService(membershipRepository);
        ownerId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        spaceA = UUID.randomUUID();
        spaceB = UUID.randomUUID();
    }

    @Test
    void ownerSeesOwnDirectoryAndCanManageOwnedSpaces() {
        when(membershipRepository.findUserSpaces(ownerId))
                .thenReturn(List.of(membership(ownerId, spaceA, MembershipRole.OWNER)));

        CallerScope scope = accessService.requireDirectoryAccess(ownerId);

        assertTrue(scope.ownsAccountDirectory());
        assertTrue(scope.manageableSpaceIds().contains(spaceA));
        assertTrue(scope.visibleSpaceIds().contains(spaceA));
    }

    @Test
    void managerDoesNotOwnTheAccountDirectory() {
        when(membershipRepository.findUserSpaces(managerId))
                .thenReturn(List.of(membership(managerId, spaceA, MembershipRole.MANAGER)));

        CallerScope scope = accessService.requireDirectoryAccess(managerId);

        assertFalse(scope.ownsAccountDirectory());
        assertTrue(scope.manageableSpaceIds().contains(spaceA));
        assertFalse(scope.visibleSpaceIds().contains(spaceB));
    }

    @Test
    void staffCanViewButNotManageOrArchive() {
        when(membershipRepository.findUserSpaces(staffId))
                .thenReturn(List.of(membership(staffId, spaceA, MembershipRole.STAFF)));
        CallerScope scope = accessService.requireDirectoryAccess(staffId);
        ServiceContactEntity contact = linkedContact(ownerId, spaceA);

        assertFalse(scope.ownsAccountDirectory());
        assertTrue(scope.manageableSpaceIds().isEmpty());
        assertTrue(accessService.canRead(scope, contact));
        assertFalse(accessService.canEdit(scope, contact));
        assertFalse(accessService.canArchive(scope, contact));
        assertThrows(BusinessException.class, () -> accessService.requireManageCapability(scope));
    }

    @Test
    void managerCannotReadUnassociatedOwnerContact() {
        when(membershipRepository.findUserSpaces(managerId))
                .thenReturn(List.of(membership(managerId, spaceA, MembershipRole.MANAGER)));
        CallerScope scope = accessService.requireDirectoryAccess(managerId);
        ServiceContactEntity unlinked = ServiceContactEntity.builder()
                .ownerUserId(ownerId)
                .businessName("Hidden")
                .category(ServiceContactCategory.ELECTRICAL)
                .active(true)
                .build();

        assertFalse(accessService.canRead(scope, unlinked));
        BusinessException ex = assertThrows(BusinessException.class, () -> accessService.requireRead(scope, unlinked));
        assertEquals("SERVICE_CONTACT_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void managerCanEditContactLinkedToAssignedSpaceButCannotArchiveIt() {
        when(membershipRepository.findUserSpaces(managerId))
                .thenReturn(List.of(membership(managerId, spaceA, MembershipRole.MANAGER)));
        CallerScope scope = accessService.requireDirectoryAccess(managerId);
        ServiceContactEntity contact = linkedContact(ownerId, spaceA);

        assertTrue(accessService.canEdit(scope, contact));
        assertFalse(accessService.canArchive(scope, contact));
    }

    @Test
    void tenantIsDenied() {
        when(membershipRepository.findUserSpaces(tenantId))
                .thenReturn(List.of(membership(tenantId, spaceA, MembershipRole.TENANT)));

        BusinessException ex = assertThrows(
                BusinessException.class, () -> accessService.requireDirectoryAccess(tenantId));
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void customerIsDenied() {
        when(membershipRepository.findUserSpaces(tenantId))
                .thenReturn(List.of(membership(tenantId, spaceA, MembershipRole.CUSTOMER)));

        assertThrows(BusinessException.class, () -> accessService.requireDirectoryAccess(tenantId));
    }

    @Test
    void userWithNoMembershipCanOwnAnEmptyDirectory() {
        when(membershipRepository.findUserSpaces(ownerId)).thenReturn(List.of());

        CallerScope scope = accessService.requireDirectoryAccess(ownerId);

        assertTrue(scope.ownsAccountDirectory());
        assertTrue(scope.manageableSpaceIds().isEmpty());
    }

    private ServiceContactEntity linkedContact(UUID accountOwnerId, UUID linkedSpaceId) {
        SpaceEntity space = SpaceEntity.builder()
                .name("PG A")
                .type(SpaceType.PG)
                .isActive(true)
                .build();
        space.setId(linkedSpaceId);
        ServiceContactEntity contact = ServiceContactEntity.builder()
                .ownerUserId(accountOwnerId)
                .businessName("Ramesh Electricals")
                .category(ServiceContactCategory.ELECTRICAL)
                .primaryPhone("9876543210")
                .active(true)
                .build();
        contact.setId(UUID.randomUUID());
        ServiceContactSpaceEntity link = ServiceContactSpaceEntity.builder()
                .contact(contact)
                .space(space)
                .build();
        contact.getSpaceLinks().add(link);
        return contact;
    }

    private SpaceMembershipEntity membership(UUID userId, UUID spaceId, MembershipRole role) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setFullName("User");
        user.setMobileNumber("9000000000");
        SpaceEntity space = SpaceEntity.builder().name("Space").type(SpaceType.PG).isActive(true).build();
        space.setId(spaceId);
        return SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(role)
                .status(MembershipStatus.ACTIVE)
                .build();
    }
}
