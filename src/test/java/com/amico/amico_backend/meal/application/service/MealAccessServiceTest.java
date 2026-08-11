package com.amico.amico_backend.meal.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.member.application.service.SpaceMembershipResolver;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.domain.model.MembershipStatus;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.amico.amico_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.amico.amico_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MealAccessServiceTest {

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private MemberRepository memberRepository;

    private MealAccessService accessService;

    private UUID spaceId;
    private UUID callerId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        accessService = new MealAccessService(new SpaceMembershipResolver(spaceMembershipRepository), memberRepository);
        spaceId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
    }

    @ParameterizedTest
    @EnumSource(
            value = MembershipRole.class,
            names = {"OWNER", "MANAGER"})
    void requireManageMeals_allowsManagers(MembershipRole role) {
        stubMembership(role);
        assertThatCode(() -> accessService.requireManageMeals(spaceId, callerId)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(
            value = MembershipRole.class,
            names = {"STAFF", "TENANT", "CUSTOMER"})
    void requireManageMeals_deniesOthers(MembershipRole role) {
        stubMembership(role);
        assertThatThrownBy(() -> accessService.requireManageMeals(spaceId, callerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only OWNER or MANAGER can manage meals");
    }

    @Test
    void requireViewParticipation_tenantCanViewOwnMember() {
        stubMembership(MembershipRole.TENANT);
        MemberEntity member = memberLinkedToCaller();
        assertThatCode(() -> accessService.requireViewParticipation(spaceId, memberId, callerId, member))
                .doesNotThrowAnyException();
    }

    @Test
    void requireViewParticipation_tenantCannotViewOtherMember() {
        stubMembership(MembershipRole.TENANT);
        MemberEntity otherMember = MemberEntity.builder().fullName("Other").mobileNumber("9000000001").build();
        otherMember.setId(memberId);
        UserEntity otherUser = UserEntity.builder().fullName("Other User").mobileNumber("9000000002").build();
        otherUser.setId(UUID.randomUUID());
        otherMember.setUser(otherUser);

        assertThatThrownBy(() -> accessService.requireViewParticipation(spaceId, memberId, callerId, otherMember))
                .isInstanceOf(BusinessException.class)
                .hasMessage("You can only view your own meal participation");
    }

    @Test
    void requireViewParticipation_staffCanViewAnyMember() {
        stubMembership(MembershipRole.STAFF);
        MemberEntity member = MemberEntity.builder().fullName("Tenant").mobileNumber("9000000001").build();
        member.setId(memberId);
        assertThatCode(() -> accessService.requireViewParticipation(spaceId, memberId, callerId, member))
                .doesNotThrowAnyException();
    }

    private MemberEntity memberLinkedToCaller() {
        MemberEntity member = MemberEntity.builder().fullName("Tenant").mobileNumber("9000000001").build();
        member.setId(memberId);
        UserEntity user = UserEntity.builder().fullName("Tenant User").mobileNumber("9000000001").build();
        user.setId(callerId);
        member.setUser(user);
        return member;
    }

    private void stubMembership(MembershipRole role) {
        UserEntity user = UserEntity.builder().fullName("User").mobileNumber("9000000000").build();
        user.setId(callerId);
        SpaceEntity space = SpaceEntity.builder()
                .owner(user)
                .name("Space")
                .type(SpaceType.PG)
                .isActive(true)
                .build();
        space.setId(spaceId);
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(role)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(spaceMembershipRepository.findMembershipByUserAndSpace(callerId, spaceId))
                .thenReturn(Optional.of(membership));
    }
}
