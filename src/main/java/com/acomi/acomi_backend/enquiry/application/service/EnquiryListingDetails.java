package com.acomi.acomi_backend.enquiry.application.service;

/**
 * Customer-facing listing fields for the authorised enquiry-share email.
 * Null means the value is missing or a stored placeholder.
 */
public record EnquiryListingDetails(
        String description,
        String addressLine,
        String city,
        String state,
        String pincode,
        String location,
        String mapUrl,
        String coordinates,
        String genderPolicy,
        String foodIncluded,
        String startingPrice,
        String priceBasis,
        String monthlyPrice,
        String mealPrice,
        String capacity,
        String sharingNotes,
        String amenities) {

    public static EnquiryListingDetails empty() {
        return new EnquiryListingDetails(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
