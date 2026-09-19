package com.acomi.acomi_backend.enquiry.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.enquiry.application.service.OwnerContactResolver;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnquiryAutoSharePolicyTest {

    @Mock
    private OwnerContactResolver ownerContactResolver;

    @Mock
    private PropertyRegistrationRepository propertyRegistrationRepository;

    @Mock
    private MessRegistrationRepository messRegistrationRepository;

    private EnquiryAutoSharePolicy policy;
    private UUID spaceId;
    private UUID ownerId;
    private UserEntity owner;
    private SpaceEntity space;
    private OwnerContactResponse shareableContact;

    @BeforeEach
    void setUp() {
        policy = new EnquiryAutoSharePolicy(
                ownerContactResolver, propertyRegistrationRepository, messRegistrationRepository, true);
        spaceId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        owner = UserEntity.builder().fullName("Rahul").isActive(true).systemRole(SystemRole.USER).build();
        owner.setId(ownerId);
        space = SpaceEntity.builder()
                .name("Sunrise PG")
                .type(SpaceType.PG)
                .isActive(true)
                .discoverable(true)
                .owner(owner)
                .build();
        space.setId(spaceId);
        shareableContact = OwnerContactResponse.builder()
                .ownerName("Rahul")
                .mobileNumber("9991110001")
                .available(true)
                .build();
    }

    @Test
    void allowsActiveDiscoverableOwnerCreatedSpaceWithShareableContact() {
        stubNoRegistrations();
        when(ownerContactResolver.resolve(space)).thenReturn(shareableContact);
        when(ownerContactResolver.hasShareableContact(shareableContact)).thenReturn(true);

        EnquiryAutoShareDecision decision = policy.evaluate(space, "ketan@example.com");

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.reason()).isEqualTo(EnquiryAutoShareReason.ALLOWED);
        assertThat(decision.contact().getMobileNumber()).isEqualTo("9991110001");
    }

    @Test
    void allowsConvertedListingWhenLinkedOwnerMatchesSpaceOwner() {
        PropertyRegistrationEntity registration = PropertyRegistrationEntity.builder()
                .testLead(false)
                .linkedOwnerUserId(ownerId)
                .build();
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));
        when(messRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());
        when(ownerContactResolver.resolve(space)).thenReturn(shareableContact);
        when(ownerContactResolver.hasShareableContact(shareableContact)).thenReturn(true);

        assertThat(policy.evaluate(space, "ketan@example.com").isAllowed()).isTrue();
    }

    @Test
    void deniesInactiveSpace() {
        space.setActive(false);
        assertThat(policy.evaluate(space, "ketan@example.com").reason())
                .isEqualTo(EnquiryAutoShareReason.SPACE_INACTIVE);
    }

    @Test
    void deniesNonDiscoverableSpace() {
        space.setDiscoverable(false);
        assertThat(policy.evaluate(space, "ketan@example.com").reason())
                .isEqualTo(EnquiryAutoShareReason.SPACE_NOT_DISCOVERABLE);
    }

    @Test
    void deniesTestLeadEvenIfDiscoverable() {
        PropertyRegistrationEntity registration = PropertyRegistrationEntity.builder()
                .testLead(true)
                .linkedOwnerUserId(ownerId)
                .build();
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));
        when(messRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());

        assertThat(policy.evaluate(space, "ketan@example.com").reason())
                .isEqualTo(EnquiryAutoShareReason.TEST_LISTING);
    }

    @Test
    void deniesConvertedListingWithoutLinkedOwner() {
        PropertyRegistrationEntity registration = PropertyRegistrationEntity.builder()
                .testLead(false)
                .linkedOwnerUserId(null)
                .build();
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));
        when(messRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());

        assertThat(policy.evaluate(space, "ketan@example.com").reason())
                .isEqualTo(EnquiryAutoShareReason.OWNER_NOT_LINKED);
    }

    @Test
    void deniesWhenLinkedOwnerDoesNotMatchSpaceOwner() {
        PropertyRegistrationEntity registration = PropertyRegistrationEntity.builder()
                .testLead(false)
                .linkedOwnerUserId(UUID.randomUUID())
                .build();
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));
        when(messRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());

        assertThat(policy.evaluate(space, "ketan@example.com").reason())
                .isEqualTo(EnquiryAutoShareReason.OWNER_NOT_LINKED);
    }

    @Test
    void deniesInactiveOwner() {
        owner.setActive(false);
        stubNoRegistrations();
        assertThat(policy.evaluate(space, "ketan@example.com").reason())
                .isEqualTo(EnquiryAutoShareReason.OWNER_INACTIVE);
    }

    @Test
    void deniesMissingOwnerAssociation() {
        space.setOwner(null);
        stubNoRegistrations();
        assertThat(policy.evaluate(space, "ketan@example.com").reason())
                .isEqualTo(EnquiryAutoShareReason.OWNER_NOT_LINKED);
    }

    @Test
    void deniesWhenOwnerContactIsNotShareable() {
        stubNoRegistrations();
        OwnerContactResponse empty = OwnerContactResponse.builder().available(false).build();
        when(ownerContactResolver.resolve(space)).thenReturn(empty);
        when(ownerContactResolver.hasShareableContact(empty)).thenReturn(false);

        assertThat(policy.evaluate(space, "ketan@example.com").reason())
                .isEqualTo(EnquiryAutoShareReason.OWNER_CONTACT_UNAVAILABLE);
    }

    @Test
    void allowsMissingRequesterEmail() {
        stubNoRegistrations();
        when(ownerContactResolver.resolve(space)).thenReturn(shareableContact);
        when(ownerContactResolver.hasShareableContact(shareableContact)).thenReturn(true);

        assertThat(policy.evaluate(space, null).isAllowed()).isTrue();
        assertThat(policy.evaluate(space, "  ").isAllowed()).isTrue();
    }

    @Test
    void deniesWhenAutoShareDisabled() {
        policy = new EnquiryAutoSharePolicy(
                ownerContactResolver, propertyRegistrationRepository, messRegistrationRepository, false);
        assertThat(policy.evaluate(space, "ketan@example.com").reason())
                .isEqualTo(EnquiryAutoShareReason.AUTO_SHARE_DISABLED);
    }

    @Test
    void deniesMessTestLead() {
        space.setType(SpaceType.MESS);
        MessRegistrationEntity registration = MessRegistrationEntity.builder()
                .testLead(true)
                .linkedOwnerUserId(ownerId)
                .build();
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());
        when(messRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));

        assertThat(policy.evaluate(space, "ketan@example.com").reason())
                .isEqualTo(EnquiryAutoShareReason.TEST_LISTING);
    }

    @Test
    void evaluateListing_usesListingPlaceholderEmailAndStillDeniesUnlinkedConvertedListing() {
        PropertyRegistrationEntity registration = PropertyRegistrationEntity.builder()
                .testLead(false)
                .linkedOwnerUserId(null)
                .build();
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));
        when(messRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());

        assertThat(policy.evaluateListing(space).reason()).isEqualTo(EnquiryAutoShareReason.OWNER_NOT_LINKED);
    }

    @Test
    void deniesPlatformAdminOwnerEvenWithoutConvertedRegistration() {
        owner.setSystemRole(SystemRole.ADMIN);
        stubNoRegistrations();

        assertThat(policy.evaluate(space, "ketan@example.com").reason())
                .isEqualTo(EnquiryAutoShareReason.OWNER_NOT_LINKED);
    }

    @Test
    void allowsAdminHeldConvertedListingWhenListingContactIsShareable() {
        owner.setSystemRole(SystemRole.ADMIN);
        PropertyRegistrationEntity registration = PropertyRegistrationEntity.builder()
                .testLead(false)
                .linkedOwnerUserId(null)
                .build();
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));
        when(messRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());
        when(ownerContactResolver.resolve(space)).thenReturn(shareableContact);
        when(ownerContactResolver.hasShareableContact(shareableContact)).thenReturn(true);

        EnquiryAutoShareDecision decision = policy.evaluate(space, "ketan@example.com");

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.contact().getMobileNumber()).isEqualTo("9991110001");
    }

    @Test
    void deniesConvertedListingLinkedToAdminAccountUntilListingContactExists() {
        owner.setSystemRole(SystemRole.ADMIN);
        PropertyRegistrationEntity registration = PropertyRegistrationEntity.builder()
                .testLead(false)
                .linkedOwnerUserId(ownerId)
                .build();
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));
        when(messRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());
        OwnerContactResponse empty = OwnerContactResponse.builder().available(false).build();
        when(ownerContactResolver.resolve(space)).thenReturn(empty);
        when(ownerContactResolver.hasShareableContact(empty)).thenReturn(false);

        assertThat(policy.evaluate(space, "ketan@example.com").reason())
                .isEqualTo(EnquiryAutoShareReason.OWNER_CONTACT_UNAVAILABLE);
    }

    private void stubNoRegistrations() {
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());
        when(messRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());
    }
}
