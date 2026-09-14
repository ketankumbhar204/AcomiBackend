package com.acomi.acomi_backend.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.admin.domain.model.AdminActivityType;
import org.junit.jupiter.api.Test;

class AdminActivityServiceMappingTest {

    @Test
    void titlesAndDescriptions_areStableForV1Types() {
        assertThat(AdminActivityService.titleFor(AdminActivityType.NEW_ENQUIRY)).isEqualTo("New Enquiry");
        assertThat(AdminActivityService.descriptionFor(
                        AdminActivityType.NEW_ENQUIRY, "Sunrise PG", null, null))
                .isEqualTo("For Sunrise PG");
        assertThat(AdminActivityService.descriptionFor(
                        AdminActivityType.NEW_PROPERTY_LISTED, "Green View PG", "Hinjewadi", null))
                .isEqualTo("Green View PG, Hinjewadi");
        assertThat(AdminActivityService.descriptionFor(
                        AdminActivityType.NEW_USER_REGISTRATION, "user", null, "9876543210"))
                .isEqualTo("9876543210");
        assertThat(AdminActivityService.descriptionFor(
                        AdminActivityType.ADDRESS_SAVED, "12 MG Road", "Pune", null))
                .isEqualTo("12 MG Road, Pune");
    }
}
