package com.acomi.acomi_backend.member.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpaceMembershipResolver {

    private final SpaceMembershipRepository spaceMembershipRepository;

    public SpaceMembershipEntity requireActive(UUID spaceId, UUID userId) {
        return spaceMembershipRepository
                .findMembershipByUserAndSpace(userId, spaceId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_A_MEMBER", "You are not a member of this space", HttpStatus.FORBIDDEN));
    }

    /** Account holder of the space — OWNER membership. Not MANAGER/STAFF/TENANT. */
    public SpaceMembershipEntity requireAccountHolder(UUID spaceId, UUID userId) {
        SpaceMembershipEntity membership = requireActive(spaceId, userId);
        if (membership.getRole() != MembershipRole.OWNER) {
            throw new BusinessException(
                    "ACCOUNT_HOLDER_REQUIRED",
                    "Only the account holder can edit this photo",
                    HttpStatus.FORBIDDEN);
        }
        return membership;
    }
}
