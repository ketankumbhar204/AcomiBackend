package com.acomi.acomi_backend.enquiry.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.config.discovery.DiscoveryProperties;
import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.enquiry.application.service.OwnerContactResolver;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.List;
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
    private DiscoveryProperties discoveryProperties;
    private UserEntity owner;
    private SpaceEntity space;
    private OwnerContactResponse shareableContact;

    @BeforeEach
    void setUp() {
        discoveryProperties = new DiscoveryProperties();
        policy = new EnquiryAutoSharePolicy(
                ownerContactResolver,
                propertyRegistrationRepository,
                messRegistrationRepository,
                true,
                discoveryProperties);
        UUID spaceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
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
    void allowsActiveDiscoverableListingWithShareableContact() {
        when(ownerContactResolver.resolve(space)).thenReturn(shareableContact);

        EnquiryAutoShareDecision decision = policy.evaluate(space, "ketan@example.com");

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.reason()).isEqualTo(EnquiryAutoShareReason.ALLOWED);
        assertThat(decision.contact().getMobileNumber()).isEqualTo("9991110001");
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
    void deniesInactiveOwner() {
        owner.setActive(false);
        assertThat(policy.evaluate(space, "ketan@example.com").reason())
                .isEqualTo(EnquiryAutoShareReason.OWNER_INACTIVE);
    }

    @Test
    void allowsInactivePlatformAdminOwnerWhenContactIsShareable() {
        owner.setSystemRole(SystemRole.ADMIN);
        owner.setActive(false);
        when(ownerContactResolver.resolve(space)).thenReturn(shareableContact);

        assertThat(policy.evaluate(space, "ketan@example.com").isAllowed()).isTrue();
    }

    @Test
    void allowsMissingOwnerWhenContactIsShareable() {
        space.setOwner(null);
        when(ownerContactResolver.resolve(space)).thenReturn(shareableContact);

        assertThat(policy.evaluate(space, "ketan@example.com").isAllowed()).isTrue();
    }

    @Test
    void allowsWhenOwnerContactIsNotShareable() {
        OwnerContactResponse empty = OwnerContactResponse.builder().available(false).build();
        when(ownerContactResolver.resolve(space)).thenReturn(empty);

        EnquiryAutoShareDecision decision = policy.evaluate(space, "ketan@example.com");

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.contact().isAvailable()).isFalse();
    }

    @Test
    void allowsMissingRequesterEmail() {
        when(ownerContactResolver.resolve(space)).thenReturn(shareableContact);

        assertThat(policy.evaluate(space, null).isAllowed()).isTrue();
        assertThat(policy.evaluate(space, "  ").isAllowed()).isTrue();
    }

    @Test
    void deniesWhenAutoShareDisabled() {
        policy = new EnquiryAutoSharePolicy(
                ownerContactResolver,
                propertyRegistrationRepository,
                messRegistrationRepository,
                false,
                discoveryProperties);
        assertThat(policy.evaluate(space, "ketan@example.com").reason())
                .isEqualTo(EnquiryAutoShareReason.AUTO_SHARE_DISABLED);
    }

    @Test
    void evaluateListing_matchesEvaluateWithoutEmail() {
        when(ownerContactResolver.resolve(space)).thenReturn(shareableContact);

        assertThat(policy.evaluateListing(space).isAllowed()).isTrue();
        assertThat(policy.evaluate(space, null).isAllowed()).isTrue();
    }

    @Test
    void allowsAdminHeldListingWhenListingContactIsShareable() {
        owner.setSystemRole(SystemRole.ADMIN);
        when(ownerContactResolver.resolve(space)).thenReturn(shareableContact);

        EnquiryAutoShareDecision decision = policy.evaluate(space, "ketan@example.com");

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.contact().getMobileNumber()).isEqualTo("9991110001");
    }

    @Test
    void allowsAdminHeldListingWithoutListingContact() {
        owner.setSystemRole(SystemRole.ADMIN);
        OwnerContactResponse empty = OwnerContactResponse.builder().available(false).build();
        when(ownerContactResolver.resolve(space)).thenReturn(empty);

        assertThat(policy.evaluate(space, "ketan@example.com").isAllowed()).isTrue();
    }

    @Test
    void allowsNonDiscoverableTestListingWhenIncludeTestSpaces() {
        discoveryProperties.setIncludeTestSpaces(true);
        space.setDiscoverable(false);
        when(propertyRegistrationRepository.findTestLeadConvertedSpaceIds(any()))
                .thenReturn(List.of(space.getId()));
        when(ownerContactResolver.resolve(space)).thenReturn(shareableContact);

        EnquiryAutoShareDecision decision = policy.evaluate(space, "ketan@example.com");

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.reason()).isEqualTo(EnquiryAutoShareReason.ALLOWED);
    }

    @Test
    void deniesNonDiscoverableNonTestListingEvenWhenIncludeTestSpaces() {
        discoveryProperties.setIncludeTestSpaces(true);
        space.setDiscoverable(false);
        when(propertyRegistrationRepository.findTestLeadConvertedSpaceIds(any())).thenReturn(List.of());
        when(messRegistrationRepository.findTestLeadConvertedSpaceIds(any())).thenReturn(List.of());

        assertThat(policy.evaluate(space, "ketan@example.com").reason())
                .isEqualTo(EnquiryAutoShareReason.SPACE_NOT_DISCOVERABLE);
    }

    @Test
    void deniesNonDiscoverableTestListingWhenIncludeTestSpacesIsOff() {
        space.setDiscoverable(false);

        assertThat(policy.evaluate(space, "ketan@example.com").reason())
                .isEqualTo(EnquiryAutoShareReason.SPACE_NOT_DISCOVERABLE);
    }
}
