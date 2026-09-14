package com.acomi.acomi_backend.common.security;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.config.security.UserPrincipal;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UUID getCurrentUserId() {
        UUID userId = getCurrentUserIdOrNull();
        if (userId == null) {
            throw new BusinessException("Invalid authentication context");
        }
        return userId;
    }

    /**
     * Authenticated caller id, or {@code null} for anonymous requests (public discovery).
     * AnonymousAuthenticationToken is treated as unsigned-in.
     */
    public static UUID getCurrentUserIdOrNull() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        return null;
    }
}
