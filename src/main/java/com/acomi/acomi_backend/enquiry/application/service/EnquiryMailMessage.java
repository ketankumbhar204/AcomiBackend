package com.acomi.acomi_backend.enquiry.application.service;

import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.space.domain.model.SpaceType;

public record EnquiryMailMessage(
        String to,
        String requesterName,
        String spaceName,
        SpaceType spaceType,
        String spaceAddress,
        OwnerContactResponse ownerContact,
        EnquiryListingDetails listing) {

    public EnquiryMailMessage(
            String to,
            String requesterName,
            String spaceName,
            SpaceType spaceType,
            String spaceAddress,
            OwnerContactResponse ownerContact) {
        this(
                to,
                requesterName,
                spaceName,
                spaceType,
                spaceAddress,
                ownerContact,
                EnquiryListingDetails.empty());
    }

    public EnquiryListingDetails listingOrEmpty() {
        return listing != null ? listing : EnquiryListingDetails.empty();
    }
}
