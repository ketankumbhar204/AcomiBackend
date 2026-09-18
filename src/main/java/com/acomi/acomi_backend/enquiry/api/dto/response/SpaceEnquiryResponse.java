package com.acomi.acomi_backend.enquiry.api.dto.response;

import com.acomi.acomi_backend.enquiry.domain.model.EnquiryDeliveryChannel;
import com.acomi.acomi_backend.enquiry.domain.model.EnquiryRequesterType;
import com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.entity.SpaceEnquiryEntity;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryClientChannel;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Member-facing enquiry record.
 * Owner contact is included only when status is SHARED and delivery is ANDROID (in-app).
 * WEB shared enquiries continue to deliver contact by email and do not expose owner contact here.
 */
@Getter
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpaceEnquiryResponse {

    private UUID enquiryId;
    private UUID spaceId;
    private String spaceName;
    private SpaceType spaceType;
    private String locationLabel;
    private String sharingNotes;
    private List<String> amenityLabels;
    private Boolean foodIncludedInRent;
    private EnquiryRequesterType requesterType;
    private SpaceEnquiryStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime sharedAt;
    private boolean detailsShared;
    private String requesterEmail;
    /** Channel captured at create; drives contact delivery. */
    private InquiryClientChannel clientChannel;
    /**
     * EMAIL when owner-contact was emailed; IN_APP for ANDROID delivery.
     * Null while not yet SHARED.
     */
    private String contactDelivery;
    /** When owner-contact email was enqueued; null if not emailed. */
    private LocalDateTime contactEmailSentAt;
    /** True when owner-contact email was (or for legacy WEB SHARED, should be treated as) sent. */
    private boolean contactEmailSent;
    /**
     * Owner contact for ANDROID SHARED enquiries only. Always null for WEB.
     */
    private OwnerContactResponse ownerContact;
    /** True when create reused an open enquiry and did not perform a new share delivery. */
    @Builder.Default
    private boolean reusedExisting = false;
    /** True when the requested channel delivery was already active (not a new send). */
    @Builder.Default
    private boolean alreadyDelivered = false;
    /** Channel that was already delivered / just delivered in this response context. */
    private EnquiryDeliveryChannel deliveryChannel;
    /** Timestamp of the channel delivery referenced by {@link #deliveryChannel}. */
    private LocalDateTime deliveredAt;
    /** Active APP delivery timestamp when present. */
    private LocalDateTime appDeliveredAt;
    /** Active EMAIL delivery timestamp for {@link #requesterEmail} when present. */
    private LocalDateTime emailDeliveredAt;

    public static SpaceEnquiryResponse from(SpaceEnquiryEntity entity) {
        return from(entity, null, null, null, null, null, null);
    }

    public static SpaceEnquiryResponse from(
            SpaceEnquiryEntity entity,
            SpaceType spaceType,
            String locationLabel,
            String sharingNotes,
            List<String> amenityLabels,
            Boolean foodIncludedInRent) {
        return from(entity, spaceType, locationLabel, sharingNotes, amenityLabels, foodIncludedInRent, null);
    }

    public static SpaceEnquiryResponse from(
            SpaceEnquiryEntity entity,
            SpaceType spaceType,
            String locationLabel,
            String sharingNotes,
            List<String> amenityLabels,
            Boolean foodIncludedInRent,
            OwnerContactResponse ownerContact) {
        InquiryClientChannel channel =
                entity.getClientChannel() != null ? entity.getClientChannel() : InquiryClientChannel.WEB;
        boolean shared = entity.getStatus() == SpaceEnquiryStatus.SHARED;
        boolean emailSent = entity.getContactEmailSentAt() != null;
        String delivery = null;
        if (shared) {
            if (emailSent) {
                delivery = "EMAIL";
            } else if (channel == InquiryClientChannel.ANDROID) {
                delivery = "IN_APP";
            } else {
                // WEB SHARED, email not yet requested by the user
                delivery = null;
            }
        }
        OwnerContactResponse memberContact = null;
        if (shared
                && channel == InquiryClientChannel.ANDROID
                && ownerContact != null
                && ownerContact.isAvailable()) {
            memberContact = ownerContact;
        }
        return SpaceEnquiryResponse.builder()
                .enquiryId(entity.getId())
                .spaceId(entity.getSpaceId())
                .spaceName(entity.getSpaceNameSnapshot())
                .spaceType(spaceType)
                .locationLabel(locationLabel)
                .sharingNotes(sharingNotes)
                .amenityLabels(amenityLabels == null ? List.of() : amenityLabels)
                .foodIncludedInRent(foodIncludedInRent)
                .requesterType(entity.getRequesterType())
                .status(entity.getStatus())
                .requestedAt(entity.getRequestedAt())
                .expiresAt(entity.getExpiresAt())
                .sharedAt(entity.getSharedAt())
                .detailsShared(shared)
                .requesterEmail(entity.getRequesterEmail())
                .clientChannel(channel)
                .contactDelivery(delivery)
                .contactEmailSentAt(entity.getContactEmailSentAt())
                .contactEmailSent(emailSent)
                .ownerContact(memberContact)
                .reusedExisting(false)
                .build();
    }
}
