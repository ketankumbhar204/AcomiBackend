package com.acomi.acomi_backend.admin.bulkimport.property;

import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;

/** Display labels for canonical bulk-import values. Domain enums are unchanged. */
public final class BulkImportLabels {

    private BulkImportLabels() {}

    public static String spaceType(SpaceType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case PG -> "PG";
            case HOSTEL -> "Hostel";
            case CO_LIVING -> "Co-living";
            case RENTAL -> "Rental";
            case MESS -> "Mess";
            case CORPORATE_STAY -> "Corporate Stay";
        };
    }

    public static String gender(GenderPolicy policy) {
        if (policy == null) {
            return "";
        }
        return switch (policy) {
            case MALE -> "Gents";
            case FEMALE -> "Ladies";
            case MIXED -> "Both";
        };
    }

    public static String food(Boolean included) {
        if (included == null) {
            return "";
        }
        return included ? "Yes" : "No";
    }
}
