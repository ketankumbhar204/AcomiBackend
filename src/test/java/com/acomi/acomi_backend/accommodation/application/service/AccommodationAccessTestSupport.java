package com.acomi.acomi_backend.accommodation.application.service;

import com.acomi.acomi_backend.accommodation.domain.policy.AccommodationProfileResolver;
import com.acomi.acomi_backend.member.application.service.SpaceMembershipResolver;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.mockito.Mockito;

final class AccommodationAccessTestSupport {

    private AccommodationAccessTestSupport() {}

    static AccommodationAccessService accessService(
            SpaceRepository spaceRepository,
            SpaceMembershipRepository spaceMembershipRepository,
            AccommodationProfileResolver profileResolver) {
        return new AccommodationAccessService(
                spaceRepository,
                new SpaceMembershipResolver(spaceMembershipRepository),
                profileResolver);
    }

    static void stubMembership(
            SpaceMembershipRepository spaceMembershipRepository,
            UUID userId,
            UUID spaceId,
            SpaceEntity space,
            MembershipRole role) {
        UserEntity user = UserEntity.builder().fullName("Caller").mobileNumber("9000000099").build();
        user.setId(userId);
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(role)
                .status(MembershipStatus.ACTIVE)
                .build();
        Mockito.when(spaceMembershipRepository.findMembershipByUserAndSpace(userId, spaceId))
                .thenReturn(Optional.of(membership));
    }
}
