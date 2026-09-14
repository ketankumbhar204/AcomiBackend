package com.acomi.acomi_backend.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.admin.api.dto.request.AdminLinkOwnerRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminRegistrationConvertResponse;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.enquiry.domain.policy.EnquiryAutoShareDecision;
import com.acomi.acomi_backend.enquiry.domain.policy.EnquiryAutoSharePolicy;
import com.acomi.acomi_backend.enquiry.domain.policy.EnquiryAutoShareReason;
import com.acomi.acomi_backend.mess.application.service.MessRegistrationService;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationDetailResponse;
import com.acomi.acomi_backend.property.application.service.PropertyRegistrationService;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.api.dto.request.CreateSpaceRequest;
import com.acomi.acomi_backend.space.api.dto.response.SpaceResponse;
import com.acomi.acomi_backend.space.application.service.SpaceService;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AdminRegistrationConversionServiceTest {

    @Mock
    private PropertyRegistrationRepository propertyRegistrationRepository;

    @Mock
    private MessRegistrationRepository messRegistrationRepository;

    @Mock
    private PropertyRegistrationService propertyRegistrationService;

    @Mock
    private MessRegistrationService messRegistrationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SpaceService spaceService;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private EnquiryAutoSharePolicy enquiryAutoSharePolicy;

    @InjectMocks
    private AdminRegistrationConversionService conversionService;

    private UUID registrationId;
    private UUID ownerId;
    private UUID adminId;
    private UUID spaceId;
    private UserEntity owner;
    private UserEntity admin;
    private PropertyRegistrationEntity registration;

    @BeforeEach
    void setUp() {
        registrationId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        spaceId = UUID.randomUUID();

        owner = UserEntity.builder()
                .mobileNumber("9876543210")
                .fullName("Owner")
                .isActive(true)
                .systemRole(SystemRole.USER)
                .build();
        owner.setId(ownerId);
        admin = UserEntity.builder()
                .mobileNumber("9000000001")
                .fullName("Admin")
                .isActive(true)
                .systemRole(SystemRole.ADMIN)
                .build();
        admin.setId(adminId);

        registration = PropertyRegistrationEntity.builder()
                .reference("PR-2026-000001")
                .propertyType(SpaceType.PG)
                .propertyName("Sunrise PG")
                .ownerName("Unknown")
                .mobileNumber("9876543210")
                .additionalMobileNumber("9123456780")
                .addressLine("12 MG Road")
                .city("Bengaluru")
                .state("Karnataka")
                .pincode("560001")
                .latitude(new BigDecimal("12.9700000"))
                .longitude(new BigDecimal("77.5900000"))
                .genderPolicy(GenderPolicy.MIXED)
                .foodIncludedListing(true)
                .linkedOwnerUserId(ownerId)
                .status(PropertyRegistrationStatus.PENDING)
                .testLead(false)
                .build();
        registration.setId(registrationId);
    }

    @Test
    void convertProperty_createsDiscoverableSpaceForAdminVerifiedLead() {
        when(propertyRegistrationService.requireEntity(registrationId)).thenReturn(registration);
        when(userRepository.findByIdAndIsActiveTrue(ownerId)).thenReturn(Optional.of(owner));
        when(spaceService.createSpace(any(CreateSpaceRequest.class)))
                .thenReturn(SpaceResponse.builder()
                        .id(spaceId)
                        .name("Sunrise PG")
                        .discoverable(true)
                        .build());
        when(propertyRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminRegistrationConvertResponse response =
                conversionService.convertProperty(registrationId, adminId);

        ArgumentCaptor<CreateSpaceRequest> captor = ArgumentCaptor.forClass(CreateSpaceRequest.class);
        verify(spaceService).createSpace(captor.capture());
        CreateSpaceRequest req = captor.getValue();
        assertThat(req.getOwnerId()).isEqualTo(ownerId);
        assertThat(req.getDiscoverable()).isTrue();
        assertThat(req.getGenderPolicy()).isEqualTo(GenderPolicy.MIXED);
        assertThat(req.getFoodIncludedInRent()).isTrue();
        assertThat(req.getLatitude()).isEqualByComparingTo("12.9700000");
        assertThat(req.getContactNumber()).isEqualTo("9876543210");

        assertThat(response.getSpaceId()).isEqualTo(spaceId);
        assertThat(response.isDiscoverable()).isTrue();
        assertThat(registration.getStatus()).isEqualTo(PropertyRegistrationStatus.CONVERTED);
        assertThat(registration.getConvertedSpaceId()).isEqualTo(spaceId);
    }

    @Test
    void convertProperty_fallsBackToAdminWhenNoLinkedOwner() {
        registration.setLinkedOwnerUserId(null);
        when(propertyRegistrationService.requireEntity(registrationId)).thenReturn(registration);
        when(userRepository.findByIdAndIsActiveTrue(adminId)).thenReturn(Optional.of(admin));
        when(spaceService.createSpace(any(CreateSpaceRequest.class)))
                .thenReturn(SpaceResponse.builder()
                        .id(spaceId)
                        .name("Sunrise PG")
                        .discoverable(true)
                        .build());
        when(propertyRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminRegistrationConvertResponse response =
                conversionService.convertProperty(registrationId, adminId);

        assertThat(response.isDiscoverable()).isTrue();
        assertThat(registration.getLinkedOwnerUserId()).isNull();
        ArgumentCaptor<CreateSpaceRequest> captor = ArgumentCaptor.forClass(CreateSpaceRequest.class);
        verify(spaceService).createSpace(captor.capture());
        assertThat(captor.getValue().getOwnerId()).isEqualTo(adminId);
        verify(userRepository, never()).findByMobileNumberAndIsActiveTrue(any());
    }

    @Test
    void convertProperty_keepsTestLeadHiddenFromDiscovery() {
        registration.setTestLead(true);
        when(propertyRegistrationService.requireEntity(registrationId)).thenReturn(registration);
        when(userRepository.findByIdAndIsActiveTrue(ownerId)).thenReturn(Optional.of(owner));
        when(spaceService.createSpace(any(CreateSpaceRequest.class)))
                .thenReturn(SpaceResponse.builder()
                        .id(spaceId)
                        .name("Sunrise PG")
                        .discoverable(false)
                        .build());
        when(propertyRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminRegistrationConvertResponse response =
                conversionService.convertProperty(registrationId, adminId);

        assertThat(response.isDiscoverable()).isFalse();
        ArgumentCaptor<CreateSpaceRequest> captor = ArgumentCaptor.forClass(CreateSpaceRequest.class);
        verify(spaceService).createSpace(captor.capture());
        assertThat(captor.getValue().getDiscoverable()).isFalse();
    }

    @Test
    void convertProperty_rejectsDuplicateConvert() {
        registration.setStatus(PropertyRegistrationStatus.CONVERTED);
        registration.setConvertedSpaceId(spaceId);
        when(propertyRegistrationService.requireEntity(registrationId)).thenReturn(registration);

        assertThatThrownBy(() -> conversionService.convertProperty(registrationId, adminId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(spaceService, never()).createSpace(any());
    }

    @Test
    void enableDiscovery_rejectsTestLeadConvertedSpace() {
        SpaceEntity space = SpaceEntity.builder()
                .owner(owner)
                .name("Test PG")
                .type(SpaceType.PG)
                .isActive(true)
                .discoverable(false)
                .build();
        space.setId(spaceId);

        PropertyRegistrationEntity testLead = PropertyRegistrationEntity.builder()
                .testLead(true)
                .convertedSpaceId(spaceId)
                .build();

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(testLead));

        assertThatThrownBy(() -> conversionService.enableDiscovery(spaceId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("test leads");
        verify(spaceRepository, never()).save(any());
    }

    @Test
    void enableDiscovery_setsDiscoverableTrue() {
        SpaceEntity space = SpaceEntity.builder()
                .owner(owner)
                .name("Live PG")
                .type(SpaceType.PG)
                .isActive(true)
                .discoverable(false)
                .build();
        space.setId(spaceId);

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());
        when(messRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());
        when(spaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SpaceEntity saved = conversionService.enableDiscovery(spaceId);
        assertThat(saved.isDiscoverable()).isTrue();
    }

    @Test
    void linkPropertyOwner_linksActiveUserAndTransfersConvertedSpace() {
        registration.setLinkedOwnerUserId(null);
        registration.setStatus(PropertyRegistrationStatus.CONVERTED);
        registration.setConvertedSpaceId(spaceId);
        SpaceEntity space = convertedSpace(admin);
        stubPropertyLink(space, EnquiryAutoShareDecision.allow(shareableContact()));

        PropertyRegistrationDetailResponse response =
                conversionService.linkPropertyOwner(registrationId, linkRequest(ownerId));

        verify(spaceService).transferOwnership(spaceId, ownerId);
        assertThat(registration.getLinkedOwnerUserId()).isEqualTo(ownerId);
        assertThat(registration.getConvertedSpaceId()).isEqualTo(spaceId);
        assertThat(registration.getReference()).isEqualTo("PR-2026-000001");
        assertThat(response.getOwnershipStatus()).isEqualTo("LINKED");
        assertThat(response.getLinkedOwnerName()).isEqualTo("Owner");
        assertThat(response.getAutoShareEligible()).isTrue();
        assertThat(response.getAutoShareReason()).isEqualTo("ALLOWED");
        verify(enquiryAutoSharePolicy, times(1)).evaluateListing(space);
        verify(enquiryAutoSharePolicy, never()).evaluate(any(), any());
        assertThat(space.isDiscoverable()).isTrue();
    }

    @Test
    void linkPropertyOwner_sameUserIsIdempotent() {
        registration.setLinkedOwnerUserId(ownerId);
        registration.setStatus(PropertyRegistrationStatus.CONVERTED);
        registration.setConvertedSpaceId(spaceId);
        stubPropertyLink(convertedSpace(owner), EnquiryAutoShareDecision.allow(shareableContact()));

        PropertyRegistrationDetailResponse response =
                conversionService.linkPropertyOwner(registrationId, linkRequest(ownerId));

        verify(spaceService).transferOwnership(spaceId, ownerId);
        assertThat(registration.getLinkedOwnerUserId()).isEqualTo(ownerId);
        assertThat(response.getOwnershipStatus()).isEqualTo("LINKED");
    }

    @Test
    void linkPropertyOwner_rejectsSilentReplaceOfDifferentLinkedOwner() {
        UUID otherOwnerId = UUID.randomUUID();
        registration.setLinkedOwnerUserId(otherOwnerId);
        registration.setStatus(PropertyRegistrationStatus.CONVERTED);
        registration.setConvertedSpaceId(spaceId);
        when(propertyRegistrationService.requireEntity(registrationId)).thenReturn(registration);
        when(propertyRegistrationRepository.lockById(registrationId)).thenReturn(Optional.of(registration));
        when(userRepository.findByIdAndIsActiveTrue(ownerId)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> conversionService.linkPropertyOwner(registrationId, linkRequest(ownerId)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("OWNER_ALREADY_LINKED");
        verify(spaceService, never()).transferOwnership(any(), any());
        assertThat(registration.getLinkedOwnerUserId()).isEqualTo(otherOwnerId);
    }

    @Test
    void linkPropertyOwner_rejectsRealUserOwnerTakeoverWhenRegistrationUnlinked() {
        UserEntity existingOwner = UserEntity.builder()
                .fullName("Existing Owner")
                .mobileNumber("9111111111")
                .isActive(true)
                .systemRole(SystemRole.USER)
                .build();
        existingOwner.setId(UUID.randomUUID());
        registration.setLinkedOwnerUserId(null);
        registration.setStatus(PropertyRegistrationStatus.CONVERTED);
        registration.setConvertedSpaceId(spaceId);
        when(propertyRegistrationService.requireEntity(registrationId)).thenReturn(registration);
        when(propertyRegistrationRepository.lockById(registrationId)).thenReturn(Optional.of(registration));
        when(userRepository.findByIdAndIsActiveTrue(ownerId)).thenReturn(Optional.of(owner));
        when(spaceRepository.lockById(spaceId)).thenReturn(Optional.of(convertedSpace(existingOwner)));

        assertThatThrownBy(() -> conversionService.linkPropertyOwner(registrationId, linkRequest(ownerId)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("OWNER_ALREADY_LINKED");
        verify(spaceService, never()).transferOwnership(any(), any());
    }

    @Test
    void linkPropertyOwner_rejectsInactiveUser() {
        registration.setLinkedOwnerUserId(null);
        when(propertyRegistrationService.requireEntity(registrationId)).thenReturn(registration);
        when(propertyRegistrationRepository.lockById(registrationId)).thenReturn(Optional.of(registration));
        when(userRepository.findByIdAndIsActiveTrue(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversionService.linkPropertyOwner(registrationId, linkRequest(ownerId)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(spaceService, never()).transferOwnership(any(), any());
    }

    @Test
    void linkPropertyOwner_rejectsAdminAccount() {
        admin.setSystemRole(SystemRole.ADMIN);
        registration.setLinkedOwnerUserId(null);
        when(propertyRegistrationService.requireEntity(registrationId)).thenReturn(registration);
        when(propertyRegistrationRepository.lockById(registrationId)).thenReturn(Optional.of(registration));
        when(userRepository.findByIdAndIsActiveTrue(adminId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> conversionService.linkPropertyOwner(registrationId, linkRequest(adminId)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("OWNER_NOT_LINKABLE");
        verify(spaceService, never()).transferOwnership(any(), any());
    }

    @Test
    void linkPropertyOwner_canLinkSameOwnerOntoSecondSpace() {
        UUID secondSpaceId = UUID.randomUUID();
        registration.setLinkedOwnerUserId(null);
        registration.setStatus(PropertyRegistrationStatus.CONVERTED);
        registration.setConvertedSpaceId(secondSpaceId);
        SpaceEntity secondSpace = convertedSpace(admin);
        secondSpace.setId(secondSpaceId);
        stubPropertyLink(secondSpace, EnquiryAutoShareDecision.deny(EnquiryAutoShareReason.SPACE_NOT_DISCOVERABLE));

        conversionService.linkPropertyOwner(registrationId, linkRequest(ownerId));

        verify(spaceService).transferOwnership(secondSpaceId, ownerId);
        verify(spaceService, never()).transferOwnership(spaceId, ownerId);
        assertThat(registration.getLinkedOwnerUserId()).isEqualTo(ownerId);
    }

    @Test
    void linkMessOwner_linksConvertedMessWithoutChangingRegistrationHistory() {
        UUID messId = UUID.randomUUID();
        MessRegistrationEntity mess = MessRegistrationEntity.builder()
                .reference("MR-2026-000001")
                .messName("Sunrise Mess")
                .ownerName("Unknown")
                .mobileNumber("9876543210")
                .status(MessRegistrationStatus.CONVERTED)
                .convertedSpaceId(spaceId)
                .testLead(false)
                .build();
        mess.setId(messId);
        admin.setSystemRole(SystemRole.ADMIN);
        SpaceEntity space = convertedSpace(admin);
        space.setType(SpaceType.MESS);
        when(messRegistrationService.requireEntity(messId)).thenReturn(mess);
        when(messRegistrationRepository.lockById(messId)).thenReturn(Optional.of(mess));
        when(userRepository.findByIdAndIsActiveTrue(ownerId)).thenReturn(Optional.of(owner));
        when(spaceRepository.lockById(spaceId)).thenReturn(Optional.of(space));
        when(messRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(enquiryAutoSharePolicy.evaluateListing(space))
                .thenReturn(EnquiryAutoShareDecision.deny(EnquiryAutoShareReason.TEST_LISTING));

        var response = conversionService.linkMessOwner(messId, linkRequest(ownerId));

        verify(spaceService).transferOwnership(spaceId, ownerId);
        assertThat(mess.getLinkedOwnerUserId()).isEqualTo(ownerId);
        assertThat(mess.getReference()).isEqualTo("MR-2026-000001");
        assertThat(response.getOwnershipStatus()).isEqualTo("LINKED");
        assertThat(response.getAutoShareEligible()).isFalse();
        assertThat(response.getAutoShareReason()).isEqualTo("TEST_LISTING");
    }

    private void stubPropertyLink(SpaceEntity space, EnquiryAutoShareDecision decision) {
        when(propertyRegistrationService.requireEntity(registrationId)).thenReturn(registration);
        when(propertyRegistrationRepository.lockById(registrationId)).thenReturn(Optional.of(registration));
        when(userRepository.findByIdAndIsActiveTrue(ownerId)).thenReturn(Optional.of(owner));
        if (registration.getConvertedSpaceId() != null) {
            when(spaceRepository.lockById(registration.getConvertedSpaceId())).thenReturn(Optional.of(space));
            when(spaceRepository.findById(registration.getConvertedSpaceId())).thenReturn(Optional.of(space));
            when(enquiryAutoSharePolicy.evaluateListing(space)).thenReturn(decision);
        }
        when(propertyRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
    }

    private SpaceEntity convertedSpace(UserEntity currentOwner) {
        SpaceEntity space = SpaceEntity.builder()
                .owner(currentOwner)
                .name("Sunrise PG")
                .type(SpaceType.PG)
                .isActive(true)
                .discoverable(true)
                .build();
        space.setId(spaceId);
        return space;
    }

    private static AdminLinkOwnerRequest linkRequest(UUID userId) {
        AdminLinkOwnerRequest request = new AdminLinkOwnerRequest();
        request.setUserId(userId);
        return request;
    }

    private static OwnerContactResponse shareableContact() {
        return OwnerContactResponse.builder()
                .ownerName("Owner")
                .mobileNumber("9876543210")
                .available(true)
                .build();
    }
}
