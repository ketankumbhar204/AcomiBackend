package com.acomi.acomi_backend.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.property.application.service.PropertyRegistrationPayload;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AdminLeadDefaultsTest {

    @Test
    void normalizeProperty_stillFillsPlaceholdersForManualAdminRegistration() {
        PropertyRegistrationPayload normalized = AdminLeadDefaults.normalizeProperty(
                PropertyRegistrationPayload.builder().build());
        assertThat(normalized.getPropertyType()).isEqualTo(SpaceType.PG);
        assertThat(normalized.getPropertyName()).isEqualTo("Untitled property");
        assertThat(normalized.getMobileNumber()).isEqualTo(AdminLeadDefaults.PLACEHOLDER_MOBILE);
        assertThat(normalized.getStartingPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(normalized.getPincode()).isEqualTo(AdminLeadDefaults.PLACEHOLDER_PINCODE);
    }

    @Test
    void normalizeImportedProperty_requiresTypeAndNameOnly() {
        assertThatThrownBy(() -> AdminLeadDefaults.normalizeImportedProperty(
                        PropertyRegistrationPayload.builder().propertyName("Sunrise PG").build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Space type");

        PropertyRegistrationPayload normalized = AdminLeadDefaults.normalizeImportedProperty(
                PropertyRegistrationPayload.builder()
                        .propertyType(SpaceType.PG)
                        .propertyName("Sunrise PG")
                        .build());
        assertThat(normalized.getMobileNumber()).isNull();
        assertThat(normalized.getAddressLine()).isNull();
        assertThat(normalized.getStartingPrice()).isNull();
        assertThat(normalized.getOwnerName()).isEqualTo(AdminLeadDefaults.UNKNOWN_OWNER);
    }

    @Test
    void normalizeImportedProperty_keepsSuppliedValuesAndLeavesCityEmpty() {
        PropertyRegistrationPayload normalized = AdminLeadDefaults.normalizeImportedProperty(
                PropertyRegistrationPayload.builder()
                        .propertyType(SpaceType.HOSTEL)
                        .propertyName("Sunrise Hostel")
                        .mobileNumber("9876543210")
                        .addressLine("12 Lake Road")
                        .startingPrice(new BigDecimal("8000"))
                        .build());
        assertThat(normalized.getPropertyType()).isEqualTo(SpaceType.HOSTEL);
        assertThat(normalized.getPropertyName()).isEqualTo("Sunrise Hostel");
        assertThat(normalized.getMobileNumber()).isEqualTo("9876543210");
        assertThat(normalized.getAddressLine()).isEqualTo("12 Lake Road");
        assertThat(normalized.getStartingPrice()).isEqualByComparingTo("8000");
        assertThat(normalized.getCity()).isNull();
        assertThat(normalized.getPincode()).isNull();
        assertThat(normalized.getOwnerName()).isEqualTo(AdminLeadDefaults.UNKNOWN_OWNER);
    }
}
