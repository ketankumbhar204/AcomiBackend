package com.acomi.acomi_backend.common.reference;

import java.util.Locale;
import java.util.Optional;

/**
 * Canonical business-reference prefixes. Legacy {@code PR}, {@code MR}, and {@code PAY}
 * values stay on their existing columns and are not minted here.
 */
public enum BusinessReferencePrefix {
    USR("users", "id", "id", null),
    MEM("members", "id", "space_id", "user_id"),
    SPC("spaces", "id", "id", "owner_id"),
    BLD("buildings", "id", "space_id", null),
    FLR("floors", "id", "building_space", null),
    UNT("units", "id", "unit_space", null),
    ROM("rooms", "id", "room_space", null),
    BED("beds", "id", "bed_space", null),
    STY("occupancies", "id", "space_id", null),
    INV("invitations", "id", "space_id", null),
    ENQ("space_enquiries", "id", "space_id", "requester_user_id"),
    CMP("space_complaints", "id", "space_id", null),
    ICP("inquiry_credit_packages", "id", null, null),
    PCR("inquiry_credit_purchase_requests", "id", null, "user_id"),
    ICL("inquiry_credit_ledger", "id", null, "user_id"),
    SAR("subscription_activation_requests", "id", "space_id", null),
    MBR("meal_billing_change_requests", "id", "space_id", null),
    MPL("meal_participations", "id", "space_id", null),
    POL("meal_polls", "id", "space_id", null),
    FDC("food_categories", "id", "space_id", "owner_id"),
    FDI("food_items", "id", "space_id", "owner_id"),
    CMB("meal_combos", "id", "space_id", "owner_id"),
    DOC("member_documents", "id", "document_space", "document_user"),
    MLB("member_meal_balance_ledger", "id", "meal_ledger_space", null),
    STK("inventory_items", "id", "space_id", null),
    SUP("inventory_suppliers", "id", "space_id", null),
    STM("inventory_transactions", "id", "space_id", null);

    private final String table;
    private final String spaceColumn;
    private final String userColumn;

    BusinessReferencePrefix(String table, String idColumn, String spaceColumn, String userColumn) {
        this.table = table;
        this.spaceColumn = spaceColumn;
        this.userColumn = userColumn;
    }

    public String sequenceName() {
        return name().toLowerCase(Locale.ROOT) + "_reference_seq";
    }

    public String table() {
        return table;
    }

    public String spaceColumn() {
        return spaceColumn;
    }

    public String userColumn() {
        return userColumn;
    }

    public static Optional<BusinessReferencePrefix> fromCode(String referenceCode) {
        if (referenceCode == null || referenceCode.length() < 8) {
            return Optional.empty();
        }
        String prefix = referenceCode.substring(0, 3);
        for (BusinessReferencePrefix value : values()) {
            if (value.name().equals(prefix)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
