package com.acomi.acomi_backend.enquiry.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerContactResolverTest {

    @Mock
    private PropertyRegistrationRepository propertyRegistrationRepository;

    @Mock
    private MessRegistrationRepository messRegistrationRepository;

    @InjectMocks
    private OwnerContactResolver resolver;

    private UUID spaceId;
    private SpaceEntity space;
    private UserEntity owner;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        owner = UserEntity.builder()
                .fullName("Account Owner")
                .mobileNumber("9000000008")
                .email("owner-account@example.com")
                .build();
        space = SpaceEntity.builder()
                .name("Sunrise PG")
                .type(SpaceType.PG)
                .contactNumber("9000000009")
                .owner(owner)
                .build();
        space.setId(spaceId);
    }

    @Test
    void usesBulkImportedRegistrationContactsIncludingContact3() {
        PropertyRegistrationEntity registration = PropertyRegistrationEntity.builder()
                .ownerName("Imported Owner")
                .mobileNumber("9991110001")
                .alternateMobileNumber("9991110002")
                .additionalMobileNumber("9991110003")
                .testLead(true)
                .build();
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));

        OwnerContactResponse contact = resolver.resolve(space);

        assertThat(contact.getOwnerName()).isEqualTo("Imported Owner");
        assertThat(contact.getMobileNumber()).isEqualTo("9991110001");
        assertThat(contact.getAlternateMobileNumber()).isEqualTo("9991110002");
        assertThat(contact.getAdditionalMobileNumber()).isEqualTo("9991110003");
        assertThat(contact.getEmail()).isNull();
        assertThat(contact.isAvailable()).isTrue();
    }

    @Test
    void fallsBackToOwnerCreatedSpaceContact() {
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());

        OwnerContactResponse contact = resolver.resolve(space);

        assertThat(contact.getOwnerName()).isEqualTo("Account Owner");
        assertThat(contact.getMobileNumber()).isEqualTo("9000000009");
        assertThat(contact.getEmail()).isEqualTo("owner-account@example.com");
        assertThat(resolver.hasShareableContact(contact)).isTrue();
    }

    @Test
    void messRegistrationContact3IsUsed() {
        space.setType(SpaceType.MESS);
        MessRegistrationEntity registration = MessRegistrationEntity.builder()
                .ownerName("Mess Owner")
                .mobileNumber("8881110001")
                .additionalMobileNumber("8881110003")
                .build();
        when(messRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));

        OwnerContactResponse contact = resolver.resolve(space);

        assertThat(contact.getAdditionalMobileNumber()).isEqualTo("8881110003");
        assertThat(contact.getMobileNumber()).isEqualTo("8881110001");
        assertThat(contact.getEmail()).isNull();
    }

    @Test
    void unlinkedConvertedListingDoesNotExposeProvisionalOwnerEmail() {
        owner.setEmail("admin@acomi.in");
        PropertyRegistrationEntity registration = PropertyRegistrationEntity.builder()
                .ownerName("Lead Owner")
                .mobileNumber("9991110001")
                .linkedOwnerUserId(null)
                .build();
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));

        OwnerContactResponse contact = resolver.resolve(space);

        assertThat(contact.getMobileNumber()).isEqualTo("9991110001");
        assertThat(contact.getEmail()).isNull();
    }

    @Test
    void prefersLinkedAcomiOwnerMobileOverRegistrationContact() {
        UUID ownerId = UUID.randomUUID();
        owner.setId(ownerId);
        PropertyRegistrationEntity registration = PropertyRegistrationEntity.builder()
                .ownerName("Imported Owner")
                .mobileNumber("9991110001")
                .alternateMobileNumber("9991110002")
                .additionalMobileNumber("9991110003")
                .linkedOwnerUserId(ownerId)
                .build();
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));

        OwnerContactResponse contact = resolver.resolve(space);

        assertThat(contact.getOwnerName()).isEqualTo("Account Owner");
        assertThat(contact.getMobileNumber()).isEqualTo("9000000008");
        assertThat(contact.getAlternateMobileNumber()).isEqualTo("9991110002");
        assertThat(contact.getAdditionalMobileNumber()).isEqualTo("9991110003");
        assertThat(contact.getEmail()).isEqualTo("owner-account@example.com");
    }

    @Test
    void unlinkedAdminHeldListingUsesLeadAlternateAndHidesAdminIdentity() {
        owner.setFullName("ACOMI Admin");
        owner.setMobileNumber("9000000001");
        owner.setEmail("admin@acomi.in");
        owner.setSystemRole(SystemRole.ADMIN);
        space.setContactNumber("9000000001");
        PropertyRegistrationEntity registration = PropertyRegistrationEntity.builder()
                .ownerName("Unknown")
                .mobileNumber("6000000000")
                .alternateMobileNumber("7722085599")
                .linkedOwnerUserId(null)
                .build();
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));

        OwnerContactResponse contact = resolver.resolve(space);

        assertThat(contact.getOwnerName()).isNull();
        assertThat(contact.getMobileNumber()).isEqualTo("7722085599");
        assertThat(contact.getAlternateMobileNumber()).isNull();
        assertThat(contact.getEmail()).isNull();
        assertThat(contact.isAvailable()).isTrue();
        assertThat(resolver.hasShareableContact(contact)).isTrue();
    }

    @Test
    void adminHeldSpaceWithoutLeadDoesNotExposeAdminContact() {
        owner.setFullName("ACOMI Admin");
        owner.setMobileNumber("9000000001");
        owner.setEmail("admin@acomi.in");
        owner.setSystemRole(SystemRole.ADMIN);
        space.setContactNumber("9000000001");
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());

        OwnerContactResponse contact = resolver.resolve(space);

        assertThat(contact.getOwnerName()).isNull();
        assertThat(contact.getMobileNumber()).isNull();
        assertThat(contact.getEmail()).isNull();
        assertThat(contact.isAvailable()).isFalse();
        assertThat(resolver.hasShareableContact(contact)).isFalse();
    }

    @Test
    void placeholderOnlyLeadIsNotShareable() {
        owner.setSystemRole(SystemRole.ADMIN);
        PropertyRegistrationEntity registration = PropertyRegistrationEntity.builder()
                .ownerName("Unknown")
                .mobileNumber("6000000000")
                .linkedOwnerUserId(null)
                .build();
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));

        OwnerContactResponse contact = resolver.resolve(space);

        assertThat(contact.getMobileNumber()).isNull();
        assertThat(resolver.hasShareableContact(contact)).isFalse();
    }
}
