package com.acomi.acomi_backend.common.security;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.config.security.UserPrincipal;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UUID getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        throw new BusinessException("Invalid authentication context");
    }
}
