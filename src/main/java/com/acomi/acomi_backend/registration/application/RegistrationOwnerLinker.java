package com.acomi.acomi_backend.registration.application;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.config.security.UserPrincipal;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Associates a newly verified public registration with an existing ACOMI account only when the
 * verified mobile matches exactly one active, linkable user. A signed-in account whose mobile
 * differs from the OTP mobile is never used as the owner.
 */
@Component
@RequiredArgsConstructor
public class RegistrationOwnerLinker {

    private final UserRepository userRepository;

    public UUID linkForVerifiedMobile(String verifiedMobile) {
        String national = nationalDigits(verifiedMobile);
        if (national == null) {
            return null;
        }
        List<UserEntity> eligible = userRepository.findByNationalMobile(national).stream()
                .filter(UserEntity::isLinkableOwner)
                .toList();
        if (eligible.size() != 1) {
            return null;
        }
        UserEntity match = eligible.get(0);
        UserPrincipal caller = SecurityUtils.getCurrentPrincipalOrNull();
        if (caller != null && !national.equals(nationalDigits(caller.getMobileNumber()))) {
            if (caller.getId().equals(match.getId())) {
                return null;
            }
        }
        return match.getId();
    }

    static String nationalDigits(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 10) {
            return null;
        }
        return digits.substring(digits.length() - 10);
    }
}
