package com.amico.amico_backend.common.security;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.config.security.UserPrincipal;
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
