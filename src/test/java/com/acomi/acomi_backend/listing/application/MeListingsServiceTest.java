package com.acomi.acomi_backend.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.listing.api.dto.MeListingResponse;
import com.acomi.acomi_backend.listing.api.dto.MeListingResponse.Publication;
import com.acomi.acomi_backend.listing.api.dto.UpdateListingCatalogRequest;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.mess.application.service.MessRegistrationService;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.application.service.PropertyRegistrationService;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.registration.application.RegistrationPhotoService;
import com.acomi.acomi_backend.registration.infrastructure.persistence.repository.RegistrationPhotoRepository;
import com.acomi.acomi_backend.space.api.dto.request.UpdateSpaceRequest;
import com.acomi.acomi_backend.space.application.service.SpaceService;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeListingsServiceTest {

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;
    @Mock
    private PropertyRegistrationRepository propertyRegistrationRepository;
    @Mock
    private MessRegistrationRepository messRegistrationRepository;
    @Mock
    private RegistrationPhotoRepository registrationPhotoRepository;
    @Mock
    private RegistrationPhotoService registrationPhotoService;
    @Mock
    private PropertyRegistrationService propertyRegistrationService;
    @Mock
    private MessRegistrationService messRegistrationService;
    @Mock
    private SpaceService spaceService;

    @InjectMocks
    private MeListingsService service;

    private UUID callerId;

    @BeforeEach
    void setUp() {
        callerId = UUID.randomUUID();
        lenient().when(registrationPhotoRepository.findByPropertyRegistrationIdOrderByDisplayOrderAsc(any()))
                .thenReturn(List.of());
        lenient().when(registrationPhotoRepository.findByMessRegistrationIdOrderByDisplayOrderAsc(any()))
                .thenReturn(List.of());
        lenient().when(propertyRegistrationRepository.findByConvertedSpaceId(any())).thenReturn(Optional.empty());
        lenient().when(messRegistrationRepository.findByConvertedSpaceId(any())).thenReturn(Optional.empty());
    }

    @Test
    void listReturnsOwnedLiveSpacesAndLinkedPendingLeadsOnly() {
        SpaceMembershipEntity pgOne = membership(SpaceType.PG, "PG One", MembershipRole.OWNER);
        SpaceMembershipEntity pgTwo = membership(SpaceType.HOSTEL, "PG Two", MembershipRole.MANAGER);
        SpaceMembershipEntity mess = membership(SpaceType.MESS, "City Mess", MembershipRole.OWNER);
        SpaceMembershipEntity tenant = membership(SpaceType.PG, "Tenant Home", MembershipRole.TENANT);
        SpaceMembershipEntity customer = membership(SpaceType.MESS, "Customer Mess", MembershipRole.CUSTOMER);
        when(spaceMembershipRepository.findUserSpaces(callerId))
                .thenReturn(List.of(pgOne, pgTwo, mess, tenant, customer));

        PropertyRegistrationEntity pending = propertyLead("Pending PG", PropertyRegistrationStatus.PENDING, "PR-1");
        PropertyRegistrationEntity rejected = propertyLead("Rejected PG", PropertyRegistrationStatus.REJECTED, "PR-2");
        when(propertyRegistrationRepository.findByLinkedOwnerUserIdAndConvertedSpaceIdIsNull(callerId))
                .thenReturn(List.of(pending, rejected));
        MessRegistrationEntity pendingMess = messLead("Pending Mess");
        when(messRegistrationRepository.findByLinkedOwnerUserIdAndConvertedSpaceIdIsNull(callerId))
                .thenReturn(List.of(pendingMess));

        List<MeListingResponse> result = service.list(callerId, null, null, null, null);

        assertThat(result).extracting(MeListingResponse::getName)
                .containsExactlyInAnyOrder("PG One", "PG Two", "City Mess", "Pending PG", "Pending Mess");
        assertThat(result).filteredOn(item -> item.getPublication() == Publication.LIVE)
                .allMatch(item -> item.getSpaceId() != null && item.isOperationalManagementAvailable());
        assertThat(result).filteredOn(item -> item.getPublication() == Publication.PENDING)
                .allMatch(item -> item.getSpaceId() == null && item.getPublicPath() == null && !item.isOperationalManagementAvailable());
        assertThat(result).filteredOn(item -> "City Mess".equals(item.getName()))
                .allMatch(item -> item.getMealsPath() != null && item.getAccommodationPath() == null);
        assertThat(result).filteredOn(item -> "PG One".equals(item.getName()))
                .allMatch(item -> item.getAccommodationPath() != null && item.getMealsPath() == null);
    }

    @Test
    void filtersByKindStatusAndSearch() {
        when(spaceMembershipRepository.findUserSpaces(callerId)).thenReturn(List.of(
                membership(SpaceType.PG, "Lake PG", MembershipRole.OWNER),
                membership(SpaceType.MESS, "Lake Mess", MembershipRole.OWNER)));
        when(propertyRegistrationRepository.findByLinkedOwnerUserIdAndConvertedSpaceIdIsNull(callerId))
                .thenReturn(List.of(propertyLead("Hill PG", PropertyRegistrationStatus.PENDING, "PR-HILL")));
        when(messRegistrationRepository.findByLinkedOwnerUserIdAndConvertedSpaceIdIsNull(callerId))
                .thenReturn(List.of());

        assertThat(service.list(callerId, "MESS", null, null, null))
                .extracting(MeListingResponse::getName)
                .containsExactly("Lake Mess");
        assertThat(service.list(callerId, null, null, "PENDING", null))
                .extracting(MeListingResponse::getName)
                .containsExactly("Hill PG");
        assertThat(service.list(callerId, null, null, null, "hill"))
                .extracting(MeListingResponse::getRegistrationReference)
                .containsExactly("PR-HILL");
    }

    @Test
    void emptyCallerGetsNoRows() {
        when(spaceMembershipRepository.findUserSpaces(callerId)).thenReturn(List.of());
        when(propertyRegistrationRepository.findByLinkedOwnerUserIdAndConvertedSpaceIdIsNull(callerId))
                .thenReturn(List.of());
        when(messRegistrationRepository.findByLinkedOwnerUserIdAndConvertedSpaceIdIsNull(callerId))
                .thenReturn(List.of());

        assertThat(service.list(callerId, null, null, null, null)).isEmpty();
    }

    @Test
    void anotherCallerDoesNotReceiveTheseLeads() {
        UUID otherId = UUID.randomUUID();
        when(spaceMembershipRepository.findUserSpaces(otherId)).thenReturn(List.of());
        when(propertyRegistrationRepository.findByLinkedOwnerUserIdAndConvertedSpaceIdIsNull(otherId))
                .thenReturn(List.of());
        when(messRegistrationRepository.findByLinkedOwnerUserIdAndConvertedSpaceIdIsNull(otherId))
                .thenReturn(List.of());

        assertThat(service.list(otherId, null, null, null, null)).isEmpty();
        verify(propertyRegistrationRepository).findByLinkedOwnerUserIdAndConvertedSpaceIdIsNull(otherId);
        verify(propertyRegistrationRepository, never()).findByLinkedOwnerUserIdAndConvertedSpaceIdIsNull(callerId);
    }

    @Test
    void priceEditDoesNotUpdateTheSpaceOrBedRent() {
        UUID spaceId = UUID.randomUUID();
        SpaceMembershipEntity owner = membership(SpaceType.PG, "Lake PG", MembershipRole.OWNER);
        owner.getSpace().setId(spaceId);
        PropertyRegistrationEntity linked = propertyLead("Lake PG", PropertyRegistrationStatus.CONVERTED, "PR-LIVE");
        linked.setStartingPrice(new BigDecimal("4000"));
        when(spaceMembershipRepository.findMembershipByUserAndSpace(callerId, spaceId)).thenReturn(Optional.of(owner));
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(linked));
        when(propertyRegistrationRepository.findById(linked.getId())).thenReturn(Optional.of(linked));

        UpdateListingCatalogRequest request = new UpdateListingCatalogRequest();
        request.setStartingPrice(new BigDecimal("9000"));
        MeListingResponse updated = service.updateCatalog(callerId, spaceId, request);

        assertThat(linked.getStartingPrice()).isEqualByComparingTo("9000");
        assertThat(updated.getStartingPrice()).isEqualByComparingTo("9000");
        verify(spaceService, never()).updateSpace(any(), any(), any());
        verify(propertyRegistrationRepository).save(linked);
    }

    @Test
    void genderEditIsPassedToTheSpaceUpdate() {
        UUID spaceId = UUID.randomUUID();
        SpaceMembershipEntity owner = membership(SpaceType.PG, "Lake PG", MembershipRole.OWNER);
        owner.getSpace().setId(spaceId);
        owner.getSpace().setDefaultFoodCharge(new BigDecimal("250"));
        when(spaceMembershipRepository.findMembershipByUserAndSpace(callerId, spaceId)).thenReturn(Optional.of(owner));

        UpdateListingCatalogRequest request = new UpdateListingCatalogRequest();
        request.setGenderPolicy(GenderPolicy.FEMALE);
        service.updateCatalog(callerId, spaceId, request);

        ArgumentCaptor<UpdateSpaceRequest> captor = ArgumentCaptor.forClass(UpdateSpaceRequest.class);
        verify(spaceService).updateSpace(org.mockito.ArgumentMatchers.eq(spaceId), org.mockito.ArgumentMatchers.eq(callerId), captor.capture());
        assertThat(captor.getValue().getGenderPolicy()).isEqualTo(GenderPolicy.FEMALE);
        assertThat(captor.getValue().getName()).isEqualTo("Lake PG");
        assertThat(captor.getValue().getDefaultFoodCharge()).isEqualByComparingTo("250");
    }

    @Test
    void catalogFieldsWithoutARegistrationAreRejectedForTheOwner() {
        UUID spaceId = UUID.randomUUID();
        SpaceMembershipEntity owner = membership(SpaceType.PG, "Ops PG", MembershipRole.OWNER);
        owner.getSpace().setId(spaceId);
        when(spaceMembershipRepository.findMembershipByUserAndSpace(callerId, spaceId)).thenReturn(Optional.of(owner));

        UpdateListingCatalogRequest request = new UpdateListingCatalogRequest();
        request.setDescription("Only on a lead");

        assertThatThrownBy(() -> service.updateCatalog(callerId, spaceId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("LISTING_CATALOG_NOT_LINKED");
        verify(spaceService, never()).updateSpace(any(), any(), any());
    }

    @Test
    void tenantAndStrangerCannotEdit() {
        UUID spaceId = UUID.randomUUID();
        SpaceMembershipEntity tenant = membership(SpaceType.PG, "Hidden", MembershipRole.TENANT);
        tenant.getSpace().setId(spaceId);
        when(spaceMembershipRepository.findMembershipByUserAndSpace(callerId, spaceId)).thenReturn(Optional.of(tenant));
        UpdateListingCatalogRequest request = new UpdateListingCatalogRequest();
        request.setName("Stolen");
        request.setStartingPrice(new BigDecimal("1"));

        assertThatThrownBy(() -> service.updateCatalog(callerId, spaceId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus().value())
                .isEqualTo(404);

        UUID stranger = UUID.randomUUID();
        when(spaceMembershipRepository.findMembershipByUserAndSpace(stranger, spaceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateCatalog(stranger, spaceId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus().value())
                .isEqualTo(404);
        verify(propertyRegistrationRepository, never()).save(any());
    }

    private SpaceMembershipEntity membership(SpaceType type, String name, MembershipRole role) {
        SpaceEntity space = new SpaceEntity();
        space.setId(UUID.randomUUID());
        space.setName(name);
        space.setType(type);
        space.setAddress(name + " address");
        space.setUpdatedAt(LocalDateTime.now());
        SpaceMembershipEntity membership = new SpaceMembershipEntity();
        membership.setRole(role);
        membership.setSpace(space);
        return membership;
    }

    private PropertyRegistrationEntity propertyLead(String name, PropertyRegistrationStatus status, String reference) {
        PropertyRegistrationEntity lead = new PropertyRegistrationEntity();
        lead.setId(UUID.randomUUID());
        lead.setPropertyName(name);
        lead.setPropertyType(SpaceType.PG);
        lead.setStatus(status);
        lead.setReference(reference);
        lead.setUpdatedAt(LocalDateTime.now());
        return lead;
    }

    private MessRegistrationEntity messLead(String name) {
        MessRegistrationEntity lead = new MessRegistrationEntity();
        lead.setId(UUID.randomUUID());
        lead.setMessName(name);
        lead.setStatus(MessRegistrationStatus.PENDING);
        lead.setReference("MR-1");
        lead.setUpdatedAt(LocalDateTime.now());
        return lead;
    }
}
