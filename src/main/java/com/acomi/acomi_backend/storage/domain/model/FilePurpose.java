package com.acomi.acomi_backend.storage.domain.model;

public enum FilePurpose {
    PROFILE_PHOTO,
    IDENTITY_DOCUMENT,
    ADDRESS_PROOF,
    MEMBER_DOCUMENT,
    PAYMENT_PROOF,
    MEAL_PAYMENT_PROOF,
    SUBSCRIPTION_PAYMENT_PROOF,
    COMPLAINT_ATTACHMENT;

    public String objectKeySegment() {
        return name().toLowerCase().replace('_', '-');
    }

    public boolean requiresSpace() {
        return this != PROFILE_PHOTO;
    }

    public long maxBytes() {
        return switch (this) {
            case PROFILE_PHOTO -> 2L * 1024 * 1024;
            case IDENTITY_DOCUMENT, ADDRESS_PROOF, MEMBER_DOCUMENT -> 8L * 1024 * 1024;
            case PAYMENT_PROOF, MEAL_PAYMENT_PROOF, SUBSCRIPTION_PAYMENT_PROOF, COMPLAINT_ATTACHMENT ->
                    4L * 1024 * 1024;
        };
    }
}
