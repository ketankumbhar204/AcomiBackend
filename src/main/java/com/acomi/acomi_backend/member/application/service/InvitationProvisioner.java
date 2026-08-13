package com.acomi.acomi_backend.member.application.service;

import com.acomi.acomi_backend.common.util.MobileNumberNormalizer;
import com.acomi.acomi_backend.member.domain.model.InvitationStatus;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.InvitationEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.InvitationRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InvitationProvisioner {

    private static final int INVITATION_EXPIRY_DAYS = 365;

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final SpaceMembershipRepository spaceMembershipRepository;

    /**
     * Creates a pending invitation when absent. Idempotent — skips when the invitee already has
     * active membership. If a pending invitation already exists, refreshes its expiry.
     */
    @Transactional
    public Optional<InvitationEntity> ensurePendingInvitation(
            SpaceEntity space, UserEntity invitedBy, String mobileNumber, MembershipRole role) {
        String normalizedMobile = MobileNumberNormalizer.normalize(mobileNumber);

        Optional<UserEntity> existingUser = userRepository.findByMobileNumber(normalizedMobile);
        if (existingUser.isPresent()
                && spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                        existingUser.get().getId(), space.getId(), MembershipStatus.ACTIVE)) {
            return Optional.empty();
        }

        if (invitationRepository.existsBySpaceIdAndMobileNumberAndStatus(
                space.getId(), normalizedMobile, InvitationStatus.PENDING)) {
            return invitationRepository
                    .findBySpaceIdAndMobileNumberAndStatus(space.getId(), normalizedMobile, InvitationStatus.PENDING)
                    .map(existing -> {
                        existing.setExpiresAt(LocalDateTime.now().plusDays(INVITATION_EXPIRY_DAYS));
                        return invitationRepository.save(existing);
                    });
        }

        InvitationEntity invitation = InvitationEntity.builder()
                .space(space)
                .invitedBy(invitedBy)
                .mobileNumber(normalizedMobile)
                .role(role)
                .status(InvitationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusDays(INVITATION_EXPIRY_DAYS))
                .build();

        return Optional.of(invitationRepository.save(invitation));
    }
}
