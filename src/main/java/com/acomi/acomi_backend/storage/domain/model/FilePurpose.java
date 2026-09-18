package com.acomi.acomi_backend.storage.domain.model;

public enum FilePurpose {
    PROFILE_PHOTO,
    IDENTITY_DOCUMENT,
    ADDRESS_PROOF,
    MEMBER_DOCUMENT,
    PAYMENT_PROOF,
    MEAL_PAYMENT_PROOF,
    SUBSCRIPTION_PAYMENT_PROOF,
    COMPLAINT_ATTACHMENT,
    BUILDING_PHOTO,
    FLOOR_PHOTO,
    UNIT_PHOTO,
    ROOM_PHOTO,
    BED_PHOTO,
    MENU_ITEM_PHOTO,
    COMBO_PHOTO,
    SPACE_PHOTO,
    INQUIRY_PAYMENT_QR;

    public String objectKeySegment() {
        return name().toLowerCase().replace('_', '-');
    }

    public static final long ABSOLUTE_MAX_BYTES = 5L * 1024 * 1024;

    public boolean requiresSpace() {
        return this != PROFILE_PHOTO && this != INQUIRY_PAYMENT_QR;
    }

    public long maxBytes() {
        long purposeMax =
                switch (this) {
                    case PROFILE_PHOTO -> 2L * 1024 * 1024;
                    case IDENTITY_DOCUMENT, ADDRESS_PROOF, MEMBER_DOCUMENT -> ABSOLUTE_MAX_BYTES;
                    case PAYMENT_PROOF, MEAL_PAYMENT_PROOF, SUBSCRIPTION_PAYMENT_PROOF, COMPLAINT_ATTACHMENT ->
                            4L * 1024 * 1024;
                    case BUILDING_PHOTO,
                            FLOOR_PHOTO,
                            UNIT_PHOTO,
                            ROOM_PHOTO,
                            BED_PHOTO,
                            MENU_ITEM_PHOTO,
                            COMBO_PHOTO,
                            SPACE_PHOTO -> ABSOLUTE_MAX_BYTES;
                    case INQUIRY_PAYMENT_QR -> 2L * 1024 * 1024;
                };
        return Math.min(purposeMax, ABSOLUTE_MAX_BYTES);
    }

    public boolean isSingularEntityPhoto() {
        return switch (this) {
            case BUILDING_PHOTO,
                    FLOOR_PHOTO,
                    UNIT_PHOTO,
                    ROOM_PHOTO,
                    BED_PHOTO,
                    MENU_ITEM_PHOTO,
                    COMBO_PHOTO,
                    SPACE_PHOTO -> true;
            default -> false;
        };
    }
}
