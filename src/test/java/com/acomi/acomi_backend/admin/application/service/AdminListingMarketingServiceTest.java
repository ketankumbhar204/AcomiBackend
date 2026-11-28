package com.acomi.acomi_backend.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.admin.api.dto.request.AdminUpdateListingMarketingRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminListingMarketingResponse;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingChannel;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingOutcome;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingStatus;
import com.acomi.acomi_backend.admin.domain.model.WhatsAppAvailability;
import com.acomi.acomi_backend.admin.infrastructure.persistence.entity.AdminListingMarketingActivityEntity;
import com.acomi.acomi_backend.admin.infrastructure.persistence.repository.AdminListingMarketingActivityRepository;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.config.security.UserPrincipal;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AdminListingMarketingServiceTest {

    @Mock
    private PropertyRegistrationRepository propertyRegistrationRepository;

    @Mock
    private MessRegistrationRepository messRegistrationRepository;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private AdminListingMarketingActivityRepository activityRepository;

    private AdminListingMarketingService service;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        service = new AdminListingMarketingService(
                propertyRegistrationRepository,
                messRegistrationRepository,
                spaceRepository,
                activityRepository);
        adminId = UUID.randomUUID();
        UserEntity admin = UserEntity.builder()
                .fullName("Admin")
                .mobileNumber("9000000000")
                .systemRole(SystemRole.ADMIN)
                .isActive(true)
                .build();
        admin.setId(adminId);
        UserPrincipal principal = new UserPrincipal(admin);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void defaultsToNotSharedAndUnknown() {
        UUID id = UUID.randomUUID();
        PropertyRegistrationEntity entity = property(id);
        entity.setMarketingStatus(null);
        entity.setWhatsappAvailability(null);
        when(propertyRegistrationRepository.findById(id)).thenReturn(Optional.of(entity));
        when(activityRepository.findByPropertyRegistrationIdOrderByCreatedAtDesc(id)).thenReturn(List.of());

        AdminListingMarketingResponse response = service.getPropertyRegistrationMarketing(id);

        assertThat(response.getMarketingStatus()).isEqualTo(ListingMarketingStatus.NOT_SHARED);
        assertThat(response.getWhatsappAvailability()).isEqualTo(WhatsAppAvailability.UNKNOWN);
    }

    @Test
    void whatsappOpenedDoesNotMarkShared() {
        UUID id = UUID.randomUUID();
        PropertyRegistrationEntity entity = property(id);
        when(propertyRegistrationRepository.findById(id)).thenReturn(Optional.of(entity));
        when(propertyRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(activityRepository.findByPropertyRegistrationIdOrderByCreatedAtDesc(id)).thenReturn(List.of());
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminUpdateListingMarketingRequest request = new AdminUpdateListingMarketingRequest();
        AdminUpdateListingMarketingRequest.Activity activity = new AdminUpdateListingMarketingRequest.Activity();
        activity.setChannel(ListingMarketingChannel.WHATSAPP);
        activity.setOutcome(ListingMarketingOutcome.WHATSAPP_OPENED);
        activity.setTemplateKey("general_property");
        request.setActivity(activity);

        AdminListingMarketingResponse response = service.updatePropertyRegistrationMarketing(id, request);

        assertThat(response.getMarketingStatus()).isEqualTo(ListingMarketingStatus.NOT_SHARED);
        ArgumentCaptor<AdminListingMarketingActivityEntity> captor =
                ArgumentCaptor.forClass(AdminListingMarketingActivityEntity.class);
        verify(activityRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo(ListingMarketingOutcome.WHATSAPP_OPENED);
    }

    @Test
    void sharedConfirmedMarksSharedAndRecordsHistory() {
        UUID id = UUID.randomUUID();
        PropertyRegistrationEntity entity = property(id);
        when(propertyRegistrationRepository.findById(id)).thenReturn(Optional.of(entity));
        when(propertyRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(activityRepository.findByPropertyRegistrationIdOrderByCreatedAtDesc(id)).thenReturn(List.of());
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminUpdateListingMarketingRequest request = new AdminUpdateListingMarketingRequest();
        request.setMarketingStatus(ListingMarketingStatus.SHARED);
        AdminUpdateListingMarketingRequest.Activity activity = new AdminUpdateListingMarketingRequest.Activity();
        activity.setChannel(ListingMarketingChannel.WHATSAPP);
        activity.setOutcome(ListingMarketingOutcome.SHARED_CONFIRMED);
        activity.setTemplateKey("pg_promotion");
        activity.setMessageSnapshot("Hello owner");
        request.setActivity(activity);

        AdminListingMarketingResponse response = service.updatePropertyRegistrationMarketing(id, request);

        assertThat(response.getMarketingStatus()).isEqualTo(ListingMarketingStatus.SHARED);
        assertThat(entity.getLastSharedBy()).isEqualTo(adminId);
        assertThat(entity.getLastSharedAt()).isNotNull();
        verify(activityRepository).save(any(AdminListingMarketingActivityEntity.class));
    }

    @Test
    void whatsappUnavailableDoesNotMarkShared() {
        UUID id = UUID.randomUUID();
        PropertyRegistrationEntity entity = property(id);
        when(propertyRegistrationRepository.findById(id)).thenReturn(Optional.of(entity));
        when(propertyRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(activityRepository.findByPropertyRegistrationIdOrderByCreatedAtDesc(id)).thenReturn(List.of());
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminUpdateListingMarketingRequest request = new AdminUpdateListingMarketingRequest();
        AdminUpdateListingMarketingRequest.Activity activity = new AdminUpdateListingMarketingRequest.Activity();
        activity.setChannel(ListingMarketingChannel.WHATSAPP);
        activity.setOutcome(ListingMarketingOutcome.WHATSAPP_UNAVAILABLE);
        request.setActivity(activity);

        AdminListingMarketingResponse response = service.updatePropertyRegistrationMarketing(id, request);

        assertThat(response.getMarketingStatus()).isEqualTo(ListingMarketingStatus.NOT_SHARED);
        assertThat(response.getWhatsappAvailability()).isEqualTo(WhatsAppAvailability.UNAVAILABLE);
    }

    @Test
    void marketingSurvivesConversionLookupViaRegistration() {
        UUID registrationId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        PropertyRegistrationEntity entity = property(registrationId);
        entity.setConvertedSpaceId(spaceId);
        entity.setMarketingStatus(ListingMarketingStatus.SHARED);
        entity.setWhatsappAvailability(WhatsAppAvailability.AVAILABLE);
        SpaceEntity space = SpaceEntity.builder()
                .name("Sunrise PG")
                .type(SpaceType.PG)
                .owner(UserEntity.builder().fullName("Owner").mobileNumber("9999999999").build())
                .build();
        space.setId(spaceId);
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(entity));
        when(propertyRegistrationRepository.findById(registrationId)).thenReturn(Optional.of(entity));
        when(activityRepository.findByPropertyRegistrationIdOrderByCreatedAtDesc(registrationId))
                .thenReturn(List.of());
        when(activityRepository.findBySpaceIdOrderByCreatedAtDesc(spaceId)).thenReturn(List.of());

        AdminListingMarketingResponse response = service.getSpaceMarketing(spaceId);

        assertThat(response.getMarketingStatus()).isEqualTo(ListingMarketingStatus.SHARED);
        assertThat(response.getPropertyRegistrationId()).isEqualTo(registrationId);
        assertThat(response.getSpaceId()).isEqualTo(spaceId);
    }

    @Test
    void nativeSpaceWithoutRegistrationCanBeMarketed() {
        UUID spaceId = UUID.randomUUID();
        SpaceEntity space = SpaceEntity.builder()
                .name("Native PG")
                .type(SpaceType.PG)
                .owner(UserEntity.builder().fullName("Owner").mobileNumber("9999999999").build())
                .marketingStatus(ListingMarketingStatus.NOT_SHARED)
                .whatsappAvailability(WhatsAppAvailability.UNKNOWN)
                .build();
        space.setId(spaceId);
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());
        when(messRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());
        when(spaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(activityRepository.findBySpaceIdOrderByCreatedAtDesc(spaceId)).thenReturn(List.of());
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminUpdateListingMarketingRequest request = new AdminUpdateListingMarketingRequest();
        request.setMarketingStatus(ListingMarketingStatus.SHARED);
        AdminUpdateListingMarketingRequest.Activity activity = new AdminUpdateListingMarketingRequest.Activity();
        activity.setOutcome(ListingMarketingOutcome.SHARED_CONFIRMED);
        activity.setChannel(ListingMarketingChannel.WHATSAPP);
        request.setActivity(activity);

        AdminListingMarketingResponse response = service.updateSpaceMarketing(spaceId, request);

        assertThat(response.getMarketingStatus()).isEqualTo(ListingMarketingStatus.SHARED);
        assertThat(space.getMarketingStatus()).isEqualTo(ListingMarketingStatus.SHARED);
        verify(propertyRegistrationRepository, never()).save(any());
    }

    @Test
    void missingRegistrationThrows() {
        UUID id = UUID.randomUUID();
        when(propertyRegistrationRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPropertyRegistrationMarketing(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static PropertyRegistrationEntity property(UUID id) {
        PropertyRegistrationEntity entity = PropertyRegistrationEntity.builder()
                .reference("PR-1")
                .propertyType(SpaceType.PG)
                .propertyName("Sunrise PG")
                .ownerName("Owner")
                .mobileNumber("9876543210")
                .mobileVerifiedAt(LocalDateTime.now())
                .status(PropertyRegistrationStatus.PENDING)
                .source(PropertyRegistrationSource.ADMIN)
                .marketingStatus(ListingMarketingStatus.NOT_SHARED)
                .whatsappAvailability(WhatsAppAvailability.UNKNOWN)
                .build();
        entity.setId(id);
        return entity;
    }
}
