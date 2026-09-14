package com.acomi.acomi_backend.admin.bulkimport.property;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Mappable Excel columns for admin property lead bulk import. */
public enum PropertyBulkImportField {
    PROPERTY_TYPE(
            "Property Type",
            "PROPERTY_TYPE",
            "PropertyType",
            "Type",
            "Space Type",
            "SpaceType"),
    PROPERTY_NAME(
            "Property Name",
            "PROPERTY_NAME",
            "PropertyName",
            "property_name",
            "PG Name",
            "Pg Name",
            "Name",
            "Mess Name"),
    OWNER_NAME(
            "Owner Name",
            "OWNER_NAME",
            "OwnerName",
            "owner_name",
            "Owner",
            "Contact Name"),
    MOBILE_NUMBER(
            "Mobile Number",
            "MOBILE_NUMBER",
            "MobileNumber",
            "mobile_number",
            "Mobile",
            "Phone",
            "Phone Number",
            "Contact Number",
            "Contact",
            "Contact 1",
            "Contact1"),
    ALTERNATE_MOBILE_NUMBER(
            "Alternate Mobile Number",
            "ALTERNATE_MOBILE_NUMBER",
            "AlternateMobileNumber",
            "alternate_mobile_number",
            "Alternate Mobile",
            "Alt Mobile",
            "Secondary Mobile",
            "Alt Phone",
            "Contact 2",
            "Contact2"),
    CONTACT_3(
            "Contact 3",
            "CONTACT_3",
            "Contact3",
            "contact_3",
            "Additional Mobile",
            "Additional Mobile Number",
            "Third Mobile",
            "Phone 3"),
    ADDRESS_LINE(
            "Address Line",
            "ADDRESS_LINE",
            "AddressLine",
            "address_line",
            "Address",
            "Street Address",
            "Street",
            "Address Given"),
    CITY("City", "CITY"),
    STATE("State", "STATE"),
    PINCODE(
            "Pincode",
            "PINCODE",
            "Pin Code",
            "Pin",
            "ZIP",
            "Zip Code",
            "Postal Code"),
    MAP_URL(
            "Map URL",
            "MAP_URL",
            "MapUrl",
            "map_url",
            "Map Link",
            "Google Maps",
            "Maps URL",
            "Google Map Link"),
    STARTING_PRICE(
            "Starting Price",
            "STARTING_PRICE",
            "StartingPrice",
            "starting_price",
            "Price",
            "Rent",
            "Starting Rent",
            "Rent starts from",
            "Rent Starts From",
            "Monthly Price"),
    GENDER(
            "Gender",
            "GENDER",
            "Gender Policy",
            "GenderPolicy",
            "gender_policy",
            "For",
            "Gents / Ladies / Both",
            "Gents/Ladies/Both",
            "Gents Ladies Both"),
    SHARING(
            "Sharing",
            "SHARING",
            "Sharing Notes",
            "sharing_notes",
            "Sharing Type",
            "Occupancy"),
    AMENITIES(
            "Amenities",
            "AMENITIES",
            "Facility",
            "Facilities",
            "Amenity"),
    FOOD_INCLUDED(
            "Food Included",
            "FOOD_INCLUDED",
            "FoodIncluded",
            "food_included",
            "Food",
            "Food Included Listing"),
    LATITUDE(
            "Latitude",
            "LATITUDE",
            "Lat",
            "lat"),
    LONGITUDE(
            "Longitude",
            "LONGITUDE",
            "Lng",
            "Lon",
            "Long",
            "lng"),
    TEST_LEAD(
            "Test Lead",
            "TEST_LEAD",
            "TestLead",
            "test_lead",
            "Is Test",
            "Test");

    private final String templateHeader;
    private final Set<String> normalizedAliases;

    PropertyBulkImportField(String templateHeader, String... aliases) {
        this.templateHeader = templateHeader;
        this.normalizedAliases =
                Stream.concat(Stream.of(templateHeader, name()), Arrays.stream(aliases))
                        .map(PropertyBulkImportField::normalizeHeader)
                        .collect(Collectors.toUnmodifiableSet());
    }

    public String getTemplateHeader() {
        return templateHeader;
    }

    public boolean matchesHeader(String header) {
        if (header == null || header.isBlank()) {
            return false;
        }
        return normalizedAliases.contains(normalizeHeader(header));
    }

    public static List<PropertyBulkImportField> all() {
        return List.of(values());
    }

    public static String normalizeHeader(String header) {
        return header.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ");
    }
}
