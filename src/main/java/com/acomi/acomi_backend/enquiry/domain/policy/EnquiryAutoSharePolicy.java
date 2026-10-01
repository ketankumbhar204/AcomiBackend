package com.acomi.acomi_backend.enquiry.domain.policy;

import com.acomi.acomi_backend.config.discovery.DiscoveryProperties;
import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.enquiry.application.service.OwnerContactResolver;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides whether a new enquiry may automatically share owner contact.
 * Auto-share listing details for active discoverable Spaces. Locally, test-lead
 * listings are also auto-shared when {@code acomi.discovery.include-test-spaces}
 * is true. Production keeps that flag false, so test listings stay unshared.
 */
@Component
public class EnquiryAutoSharePolicy {

    private final OwnerContactResolver ownerContactResolver;
    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final MessRegistrationRepository messRegistrationRepository;
    private final boolean autoShareEnabled;
    private final DiscoveryProperties discoveryProperties;

    public EnquiryAutoSharePolicy(
            OwnerContactResolver ownerContactResolver,
            PropertyRegistrationRepository propertyRegistrationRepository,
            MessRegistrationRepository messRegistrationRepository,
            @Value("${acomi.enquiry.auto-share-enabled:true}") boolean autoShareEnabled,
            DiscoveryProperties discoveryProperties) {
        this.ownerContactResolver = ownerContactResolver;
        this.propertyRegistrationRepository = propertyRegistrationRepository;
        this.messRegistrationRepository = messRegistrationRepository;
        this.autoShareEnabled = autoShareEnabled;
        this.discoveryProperties = discoveryProperties;
    }

    public EnquiryAutoShareDecision evaluate(SpaceEntity space, String requesterEmail) {
        if (!autoShareEnabled) {
            return EnquiryAutoShareDecision.deny(EnquiryAutoShareReason.AUTO_SHARE_DISABLED);
        }
        if (space == null || !space.isActive()) {
            return EnquiryAutoShareDecision.deny(EnquiryAutoShareReason.SPACE_INACTIVE);
        }
        if (!space.isDiscoverable() && !allowLocalTestListing(space)) {
            return EnquiryAutoShareDecision.deny(EnquiryAutoShareReason.SPACE_NOT_DISCOVERABLE);
        }

        UserEntity owner = space.getOwner();
        if (owner != null && !owner.isPlatformAdmin() && !owner.isActive()) {
            return EnquiryAutoShareDecision.deny(EnquiryAutoShareReason.OWNER_INACTIVE);
        }

        OwnerContactResponse contact = ownerContactResolver.resolve(space);
        return EnquiryAutoShareDecision.allow(contact != null ? contact : ownerContactResolver.empty());
    }

    /**
     * Listing-side eligibility for Admin diagnostics. Email is not required.
     */
    public EnquiryAutoShareDecision evaluateListing(SpaceEntity space) {
        return evaluate(space, null);
    }

    private boolean allowLocalTestListing(SpaceEntity space) {
        return discoveryProperties != null
                && discoveryProperties.isIncludeTestSpaces()
                && isTestListing(space.getId());
    }

    private boolean isTestListing(UUID spaceId) {
        if (spaceId == null) {
            return false;
        }
        Set<UUID> ids = Set.of(spaceId);
        List<UUID> fromProperty = propertyRegistrationRepository.findTestLeadConvertedSpaceIds(ids);
        if (fromProperty != null && !fromProperty.isEmpty()) {
            return true;
        }
        List<UUID> fromMess = messRegistrationRepository.findTestLeadConvertedSpaceIds(ids);
        return fromMess != null && !fromMess.isEmpty();
    }
}
