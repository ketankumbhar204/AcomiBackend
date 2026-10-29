package com.acomi.acomi_backend.servicecontact.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.servicecontact.domain.model.ServiceContactCategory;
import org.junit.jupiter.api.Test;

class ServiceContactRulesTest {

    @Test
    void requiresNameAndNormalizesPhonesToNationalDigits() {
        assertEquals("Ramesh Electricals", ServiceContactRules.requireBusinessName("  Ramesh   Electricals  "));
        assertEquals("9876543210", ServiceContactRules.optionalPhone("+91 98765 43210", "Primary phone"));
        assertEquals("9876543210", ServiceContactRules.optionalPhone("09876543210", "Primary phone"));
        assertNull(ServiceContactRules.optionalPhone("  ", "Primary phone"));
        assertEquals("+919876543210", ServiceContactRules.displayPhone("9876543210"));
    }

    @Test
    void rejectsBlankNameInvalidPhoneAndMissingContactMethod() {
        BusinessException name = assertThrows(BusinessException.class, () -> ServiceContactRules.requireBusinessName("  "));
        assertEquals("INVALID_NAME", name.getErrorCode());

        BusinessException phone = assertThrows(
                BusinessException.class, () -> ServiceContactRules.optionalPhone("12345", "Primary phone"));
        assertEquals("INVALID_PHONE", phone.getErrorCode());

        BusinessException method = assertThrows(
                BusinessException.class, () -> ServiceContactRules.requireContactMethod(null, null, null, null));
        assertEquals("CONTACT_METHOD_REQUIRED", method.getErrorCode());
    }

    @Test
    void customCategoryIsRequiredOnlyForOther() {
        assertEquals("Gas agency", ServiceContactRules.customCategoryName(ServiceContactCategory.OTHER, " Gas  agency "));
        assertNull(ServiceContactRules.customCategoryName(ServiceContactCategory.ELECTRICAL, null));

        BusinessException missing = assertThrows(
                BusinessException.class,
                () -> ServiceContactRules.customCategoryName(ServiceContactCategory.OTHER, " "));
        assertEquals("INVALID_CATEGORY", missing.getErrorCode());

        BusinessException unexpected = assertThrows(
                BusinessException.class,
                () -> ServiceContactRules.customCategoryName(ServiceContactCategory.ELECTRICAL, "Wiring"));
        assertEquals("INVALID_CATEGORY", unexpected.getErrorCode());
    }

    @Test
    void rejectsUnknownCategoryInvalidEmailAndOverlongNotes() {
        BusinessException category = assertThrows(
                BusinessException.class, () -> ServiceContactRules.requireCategory("NOT_A_CATEGORY"));
        assertEquals("INVALID_CATEGORY", category.getErrorCode());

        BusinessException email = assertThrows(
                BusinessException.class, () -> ServiceContactRules.optionalEmail("not-an-email"));
        assertEquals("INVALID_EMAIL", email.getErrorCode());

        String notes = "n".repeat(ServiceContactRules.NOTES_MAX + 1);
        BusinessException tooLong = assertThrows(
                BusinessException.class,
                () -> ServiceContactRules.optionalText(notes, ServiceContactRules.NOTES_MAX, "INVALID_NOTES", "Notes"));
        assertEquals("INVALID_NOTES", tooLong.getErrorCode());
        assertTrue(ServiceContactRules.phoneSet("9876543210", null, "9876543210").size() == 1);
    }

    @Test
    void comparableNamesIgnoreCaseAndSpacing() {
        assertEquals(
                "ramesh electricals",
                ServiceContactRules.normalizeComparableName("  Ramesh   Electricals "));
    }
}
