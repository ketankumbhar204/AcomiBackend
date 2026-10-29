package com.acomi.acomi_backend.servicecontact.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.servicecontact.api.dto.request.SaveServiceContactRequest;
import com.acomi.acomi_backend.servicecontact.api.dto.response.ServiceContactResponse;
import com.acomi.acomi_backend.servicecontact.application.DuplicateServiceContactException;
import com.acomi.acomi_backend.servicecontact.domain.model.ServiceContactCategory;
import com.acomi.acomi_backend.servicecontact.infrastructure.persistence.entity.ServiceContactEntity;
import com.acomi.acomi_backend.servicecontact.infrastructure.persistence.entity.ServiceContactSpaceEntity;
import com.acomi.acomi_backend.servicecontact.infrastructure.persistence.repository.ServiceContactRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ServiceContactServiceTest {

    @Mock
    private ServiceContactRepository contactRepository;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMembershipRepository membershipRepository;

    private ServiceContactService service;
    private UUID ownerId;
    private UUID otherOwnerId;
    private UUID spaceA;
    private UUID spaceB;
    private UUID foreignSpace;

    @BeforeEach
    void setUp() {
        service = new ServiceContactService(
                contactRepository, spaceRepository, new ServiceContactAccessService(membershipRepository));
        ownerId = UUID.randomUUID();
        otherOwnerId = UUID.randomUUID();
        spaceA = UUID.randomUUID();
        spaceB = UUID.randomUUID();
        foreignSpace = UUID.randomUUID();
    }

    @Test
    void ownerCreatesContactWithTwoSpacesAndDisplayPhones() {
        ownerMemberships();
        when(spaceRepository.findActiveWithOwnerById(spaceA)).thenReturn(Optional.of(space(spaceA, ownerId, "PG A")));
        when(spaceRepository.findActiveWithOwnerById(spaceB)).thenReturn(Optional.of(space(spaceB, ownerId, "PG B")));
        when(contactRepository.findActiveByOwnerAndAnyPhone(eq(ownerId), anyCollection())).thenReturn(List.of());
        when(contactRepository.save(any())).thenAnswer(invocation -> {
            ServiceContactEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        SaveServiceContactRequest request = request();
        request.setSpaceIds(List.of(spaceA, spaceB, spaceA));
        ServiceContactResponse response = service.create(ownerId, request);

        assertEquals("Ramesh Electricals", response.getBusinessName());
        assertEquals("+919876543210", response.getPrimaryPhone());
        assertEquals(2, response.getSpaces().size());
        assertTrue(response.isCanEdit());
        assertTrue(response.isCanArchive());
    }

    @Test
    void duplicatePhoneBlocksCreateUntilAcknowledged() {
        ownerMemberships();
        ServiceContactEntity existing = existingContact();
        when(contactRepository.findActiveByOwnerAndAnyPhone(eq(ownerId), anyCollection())).thenReturn(List.of(existing));

        SaveServiceContactRequest request = request();
        DuplicateServiceContactException duplicate = assertThrows(
                DuplicateServiceContactException.class, () -> service.create(ownerId, request));
        assertTrue(duplicate.getDuplicate().isStrongMatch());
        verify(contactRepository, never()).save(any());

        request.setAcknowledgeDuplicate(true);
        when(contactRepository.save(any())).thenAnswer(invocation -> {
            ServiceContactEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
        ServiceContactResponse saved = service.create(ownerId, request);
        assertEquals("Ramesh Electricals", saved.getBusinessName());
    }

    @Test
    void phoneOnlyMatchIsNotAStrongDuplicate() {
        ownerMemberships();
        ServiceContactEntity existing = existingContact();
        existing.setBusinessName("Other Electrician");
        when(contactRepository.findActiveByOwnerAndAnyPhone(eq(ownerId), anyCollection())).thenReturn(List.of(existing));

        DuplicateServiceContactException duplicate = assertThrows(
                DuplicateServiceContactException.class, () -> service.create(ownerId, request()));
        assertFalse(duplicate.getDuplicate().isStrongMatch());
    }

    @Test
    void cannotAssociateAnotherAccountsSpace() {
        ownerMemberships();
        SaveServiceContactRequest request = request();
        request.setSpaceIds(List.of(foreignSpace));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(ownerId, request));
        assertEquals("SPACE_NOT_ALLOWED", ex.getErrorCode());
    }

    @Test
    void repeatedAssociationDoesNotAddAnotherLink() {
        ownerMemberships();
        ServiceContactEntity contact = existingContact();
        SpaceEntity space = space(spaceA, ownerId, "PG A");
        ServiceContactSpaceEntity link = ServiceContactSpaceEntity.builder().contact(contact).space(space).build();
        contact.setSpaceLinks(new ArrayList<>(List.of(link)));
        when(contactRepository.findById(contact.getId())).thenReturn(Optional.of(contact));

        ServiceContactResponse response = service.addSpace(ownerId, contact.getId(), spaceA);

        assertEquals(1, response.getSpaces().size());
        verify(contactRepository, never()).saveAndFlush(any());
    }

    @Test
    void removingOneAssociationKeepsTheContact() {
        ownerMemberships();
        ServiceContactEntity contact = existingContact();
        SpaceEntity first = space(spaceA, ownerId, "PG A");
        SpaceEntity second = space(spaceB, ownerId, "PG B");
        contact.setSpaceLinks(new ArrayList<>(List.of(
                ServiceContactSpaceEntity.builder().contact(contact).space(first).build(),
                ServiceContactSpaceEntity.builder().contact(contact).space(second).build())));
        when(contactRepository.findById(contact.getId())).thenReturn(Optional.of(contact));
        when(contactRepository.save(contact)).thenReturn(contact);

        ServiceContactResponse response = service.removeSpace(ownerId, contact.getId(), spaceA);

        assertEquals(contact.getId(), response.getId());
        assertEquals(1, response.getSpaces().size());
        assertEquals(spaceB, response.getSpaces().get(0).getSpaceId());
        assertTrue(contact.isActive());
    }

    @Test
    void ownerCanArchiveAndStaffCannot() {
        ownerMemberships();
        ServiceContactEntity contact = existingContact();
        when(contactRepository.findById(contact.getId())).thenReturn(Optional.of(contact));
        when(contactRepository.save(contact)).thenReturn(contact);

        service.archive(ownerId, contact.getId());
        assertFalse(contact.isActive());

        UUID staffId = UUID.randomUUID();
        when(membershipRepository.findUserSpaces(staffId)).thenReturn(List.of(
                membership(staffId, spaceA, MembershipRole.STAFF)));
        contact.setActive(true);
        contact.setSpaceLinks(new ArrayList<>(List.of(ServiceContactSpaceEntity.builder()
                .contact(contact)
                .space(space(spaceA, ownerId, "PG A"))
                .build())));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.archive(staffId, contact.getId()));
        assertEquals("SERVICE_CONTACT_FORBIDDEN", ex.getErrorCode());
    }

    @Test
    void unrelatedOwnerCannotReadTheContact() {
        UUID stranger = UUID.randomUUID();
        when(membershipRepository.findUserSpaces(stranger)).thenReturn(List.of(
                membership(stranger, foreignSpace, MembershipRole.OWNER)));
        ServiceContactEntity contact = existingContact();
        when(contactRepository.findById(contact.getId())).thenReturn(Optional.of(contact));

        BusinessException ex = assertThrows(
                BusinessException.class, () -> service.get(stranger, contact.getId()));
        assertEquals("SERVICE_CONTACT_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void tenantCannotList() {
        UUID tenantId = UUID.randomUUID();
        when(membershipRepository.findUserSpaces(tenantId)).thenReturn(List.of(
                membership(tenantId, spaceA, MembershipRole.TENANT)));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.list(tenantId, null, null, null, "updated", false, 0, 20));
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void listUsesDatabasePageAndRejectsUnknownCategory() {
        ownerMemberships();
        when(contactRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertEquals(0, service.list(ownerId, "ramesh", null, null, "name", false, 0, 500).getSize());

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.list(ownerId, null, "NOPE", null, "updated", false, 0, 20));
        assertEquals("INVALID_CATEGORY", ex.getErrorCode());
    }

    @Test
    void staleVersionIsRejected() {
        ownerMemberships();
        ServiceContactEntity contact = existingContact();
        contact.setVersion(3);
        when(contactRepository.findById(contact.getId())).thenReturn(Optional.of(contact));
        SaveServiceContactRequest request = request();
        request.setVersion(2L);

        BusinessException ex = assertThrows(
                BusinessException.class, () -> service.update(ownerId, contact.getId(), request));
        assertEquals("SERVICE_CONTACT_CONFLICT", ex.getErrorCode());
    }

    @Test
    void customCategoryRoundTrip() {
        ownerMemberships();
        when(contactRepository.findActiveByOwnerAndAnyPhone(eq(ownerId), anyCollection())).thenReturn(List.of());
        when(contactRepository.save(any())).thenAnswer(invocation -> {
            ServiceContactEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
        SaveServiceContactRequest request = request();
        request.setCategory("OTHER");
        request.setCustomCategoryName("Lift AMC");

        ServiceContactResponse response = service.create(ownerId, request);

        assertEquals("OTHER", response.getCategory());
        assertEquals("Lift AMC", response.getCategoryLabel());
    }

    private void ownerMemberships() {
        when(membershipRepository.findUserSpaces(ownerId)).thenReturn(List.of(
                membership(ownerId, spaceA, MembershipRole.OWNER),
                membership(ownerId, spaceB, MembershipRole.OWNER)));
    }

    private SaveServiceContactRequest request() {
        SaveServiceContactRequest request = new SaveServiceContactRequest();
        request.setBusinessName("Ramesh Electricals");
        request.setCategory("ELECTRICAL");
        request.setContactPersonName("Ramesh Kumar");
        request.setPrimaryPhone("+91 98765 43210");
        request.setVersion(0L);
        return request;
    }

    private ServiceContactEntity existingContact() {
        ServiceContactEntity contact = ServiceContactEntity.builder()
                .ownerUserId(ownerId)
                .businessName("Ramesh Electricals")
                .category(ServiceContactCategory.ELECTRICAL)
                .primaryPhone("9876543210")
                .active(true)
                .build();
        contact.setId(UUID.randomUUID());
        contact.setVersion(0);
        return contact;
    }

    private SpaceEntity space(UUID id, UUID owner, String name) {
        UserEntity user = new UserEntity();
        user.setId(owner);
        user.setFullName("Owner");
        user.setMobileNumber("9000000001");
        SpaceEntity space = SpaceEntity.builder().name(name).type(SpaceType.PG).isActive(true).owner(user).build();
        space.setId(id);
        return space;
    }

    private SpaceMembershipEntity membership(UUID userId, UUID spaceId, MembershipRole role) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setFullName("User");
        user.setMobileNumber("9000000002");
        return SpaceMembershipEntity.builder()
                .user(user)
                .space(space(spaceId, ownerId, "Space"))
                .role(role)
                .status(MembershipStatus.ACTIVE)
                .build();
    }
}
