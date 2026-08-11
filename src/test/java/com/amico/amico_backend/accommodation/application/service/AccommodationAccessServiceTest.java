package com.amico.amico_backend.accommodation.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.accommodation.domain.policy.AccommodationProfileResolver;
import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.member.application.service.SpaceMembershipResolver;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.domain.model.MembershipStatus;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.amico.amico_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.amico.amico_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AccommodationAccessServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    private AccommodationAccessService accessService;

    private UUID spaceId;
    private UUID callerId;
    private SpaceEntity pgSpace;
    private SpaceEntity messSpace;

    @BeforeEach
    void setUp() {
        SpaceMembershipResolver membershipResolver = new SpaceMembershipResolver(spaceMembershipRepository);
        accessService = new AccommodationAccessService(
                spaceRepository, membershipResolver, new AccommodationProfileResolver());
        spaceId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        UserEntity owner = UserEntity.builder().fullName("Owner").mobileNumber("9000000001").build();
        owner.setId(callerId);
        pgSpace = SpaceEntity.builder()
                .owner(owner)
                .name("PG")
                .type(SpaceType.PG)
                .isActive(true)
                .build();
        pgSpace.setId(spaceId);
        messSpace = SpaceEntity.builder()
                .owner(owner)
                .name("Mess")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        messSpace.setId(spaceId);
    }

    @ParameterizedTest
    @EnumSource(
            value = MembershipRole.class,
            names = {"OWNER", "MANAGER", "STAFF"})
    void assertCanViewStructure_allowsOperationalRoles(MembershipRole role) {
        stubMembership(role, pgSpace);
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));

        assertThatCode(() -> accessService.assertCanViewStructure(spaceId, callerId))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(
            value = MembershipRole.class,
            names = {"TENANT", "CUSTOMER"})
    void assertCanViewStructure_deniesResidents(MembershipRole role) {
        stubMembership(role, pgSpace);
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));

        assertThatThrownBy(() -> accessService.assertCanViewStructure(spaceId, callerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("permission to view accommodation structure")
                .satisfies(ex -> assertThatStatus((BusinessException) ex, HttpStatus.FORBIDDEN));
    }

    @ParameterizedTest
    @EnumSource(
            value = MembershipRole.class,
            names = {"OWNER", "MANAGER"})
    void assertCanManageStructure_allowsOwnerAndManager(MembershipRole role) {
        stubMembership(role, pgSpace);
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));

        assertThatCode(() -> accessService.assertCanManageStructure(spaceId, callerId))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(
            value = MembershipRole.class,
            names = {"STAFF", "TENANT", "CUSTOMER"})
    void assertCanManageStructure_deniesOthers(MembershipRole role) {
        stubMembership(role, pgSpace);
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));

        assertThatThrownBy(() -> accessService.assertCanManageStructure(spaceId, callerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only OWNER or MANAGER can perform this action");
    }

    @ParameterizedTest
    @EnumSource(
            value = MembershipRole.class,
            names = {"MANAGER", "STAFF", "TENANT", "CUSTOMER"})
    void assertCanDeactivateStructure_ownerOnly(MembershipRole role) {
        stubMembership(role, pgSpace);
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(pgSpace));

        assertThatThrownBy(() -> accessService.assertCanDeactivateStructure(spaceId, callerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only the space owner can perform this action");
    }

    @ParameterizedTest
    @EnumSource(value = MembershipRole.class)
    void loadAccommodationSpace_messSpace_forbidden(MembershipRole role) {
        stubMembership(role, messSpace);
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(messSpace));

        assertThatThrownBy(() -> accessService.assertCanViewStructure(spaceId, callerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Accommodation is not applicable for Mess spaces")
                .satisfies(ex -> assertThatStatus((BusinessException) ex, HttpStatus.FORBIDDEN));
    }

    private void stubMembership(MembershipRole role, SpaceEntity space) {
        UserEntity user = UserEntity.builder().fullName("Caller").mobileNumber("9000000002").build();
        user.setId(callerId);
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(role)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(spaceMembershipRepository.findMembershipByUserAndSpace(callerId, spaceId))
                .thenReturn(Optional.of(membership));
    }

    private static void assertThatStatus(BusinessException ex, HttpStatus status) {
        org.assertj.core.api.Assertions.assertThat(ex.getStatus()).isEqualTo(status);
    }
}
