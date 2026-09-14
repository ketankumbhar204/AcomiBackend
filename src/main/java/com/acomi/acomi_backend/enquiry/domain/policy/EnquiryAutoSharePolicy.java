package com.acomi.acomi_backend.enquiry.domain.policy;

import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.enquiry.application.service.OwnerContactResolver;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides whether a new enquiry may automatically share owner contact.
 * Manual Admin Share remains available as fallback and is not gated by this policy.
 */
@Component
public class EnquiryAutoSharePolicy {

    private final OwnerContactResolver ownerContactResolver;
    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final MessRegistrationRepository messRegistrationRepository;
    private final boolean autoShareEnabled;

    public EnquiryAutoSharePolicy(
            OwnerContactResolver ownerContactResolver,
            PropertyRegistrationRepository propertyRegistrationRepository,
            MessRegistrationRepository messRegistrationRepository,
            @Value("${acomi.enquiry.auto-share-enabled:true}") boolean autoShareEnabled) {
        this.ownerContactResolver = ownerContactResolver;
        this.propertyRegistrationRepository = propertyRegistrationRepository;
        this.messRegistrationRepository = messRegistrationRepository;
        this.autoShareEnabled = autoShareEnabled;
    }

    public EnquiryAutoShareDecision evaluate(SpaceEntity space, String requesterEmail) {
        if (!autoShareEnabled) {
            return EnquiryAutoShareDecision.deny(EnquiryAutoShareReason.AUTO_SHARE_DISABLED);
        }
        if (space == null || !space.isActive()) {
            return EnquiryAutoShareDecision.deny(EnquiryAutoShareReason.SPACE_INACTIVE);
        }
        if (!space.isDiscoverable()) {
            return EnquiryAutoShareDecision.deny(EnquiryAutoShareReason.SPACE_NOT_DISCOVERABLE);
        }

        PropertyRegistrationEntity property =
                propertyRegistrationRepository.findByConvertedSpaceId(space.getId()).orElse(null);
        MessRegistrationEntity mess =
                messRegistrationRepository.findByConvertedSpaceId(space.getId()).orElse(null);
        if (isTestLead(property, mess)) {
            return EnquiryAutoShareDecision.deny(EnquiryAutoShareReason.TEST_LISTING);
        }

        UserEntity owner = space.getOwner();
        if (owner != null && !owner.isPlatformAdmin() && !owner.isActive()) {
            return EnquiryAutoShareDecision.deny(EnquiryAutoShareReason.OWNER_INACTIVE);
        }
        if (!canAutoShareOwnership(space, owner, property, mess)) {
            return EnquiryAutoShareDecision.deny(EnquiryAutoShareReason.OWNER_NOT_LINKED);
        }
        if (requesterEmail == null || requesterEmail.isBlank()) {
            return EnquiryAutoShareDecision.deny(EnquiryAutoShareReason.REQUESTER_EMAIL_UNAVAILABLE);
        }

        OwnerContactResponse contact = ownerContactResolver.resolve(space);
        if (!ownerContactResolver.hasShareableContact(contact)) {
            return EnquiryAutoShareDecision.deny(EnquiryAutoShareReason.OWNER_CONTACT_UNAVAILABLE);
        }
        return EnquiryAutoShareDecision.allow(contact);
    }

    /**
     * Listing-side eligibility for Admin diagnostics. Does not represent a specific enquiry
     * requester; a later enquiry still needs a valid requester email.
     */
    public EnquiryAutoShareDecision evaluateListing(SpaceEntity space) {
        return evaluate(space, "listing-eligibility");
    }

    /**
     * Direct owner-created Spaces have no converted registration; {@code spaces.owner_id} is the
     * associated owner. Converted listings auto-share when a real ACOMI owner is linked, or when
     * Admin published the listing and the lead has a shareable listing contact (never the admin
     * account itself).
     */
    private static boolean canAutoShareOwnership(
            SpaceEntity space,
            UserEntity owner,
            PropertyRegistrationEntity property,
            MessRegistrationEntity mess) {
        if (owner == null) {
            return false;
        }
        if (owner.isPlatformAdmin()) {
            return property != null || mess != null;
        }
        if (!owner.isLinkableOwner()) {
            return false;
        }
        UUID linkedOwnerUserId = linkedOwnerUserId(space.getType(), property, mess);
        if (linkedOwnerUserId == null && property == null && mess == null) {
            return true;
        }
        return linkedOwnerUserId != null && linkedOwnerUserId.equals(owner.getId());
    }

    private static UUID linkedOwnerUserId(
            SpaceType type, PropertyRegistrationEntity property, MessRegistrationEntity mess) {
        if (type == SpaceType.MESS) {
            return mess != null ? mess.getLinkedOwnerUserId() : null;
        }
        return property != null ? property.getLinkedOwnerUserId() : null;
    }

    private static boolean isTestLead(PropertyRegistrationEntity property, MessRegistrationEntity mess) {
        return (property != null && property.isTestLead()) || (mess != null && mess.isTestLead());
    }
}
