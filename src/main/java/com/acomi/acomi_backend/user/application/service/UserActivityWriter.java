package com.acomi.acomi_backend.user.application.service;

import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Own transaction so activity writes do not change the caller's transaction or {@code updated_at}. */
@Service
@RequiredArgsConstructor
class UserActivityWriter {

    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSignIn(UUID userId, LocalDateTime now) {
        userRepository.recordSignIn(userId, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touch(UUID userId, LocalDateTime now, LocalDateTime threshold) {
        userRepository.touchActivity(userId, now, threshold);
    }
}
