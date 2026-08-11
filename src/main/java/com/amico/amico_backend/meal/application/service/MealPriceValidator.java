package com.amico.amico_backend.meal.application.service;

import com.amico.amico_backend.common.exception.BusinessException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public final class MealPriceValidator {

    private MealPriceValidator() {}

    public static void validateOptionalPrice(BigDecimal price) {
        if (price == null) {
            return;
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Price must be greater than 0", HttpStatus.BAD_REQUEST);
        }
    }

    public static String resolveCurrencyCode(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return "INR";
        }
        return currencyCode.trim().toUpperCase();
    }
}
