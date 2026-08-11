package com.amico.amico_backend.occupancy.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.member.application.service.SpaceMembershipResolver;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.domain.model.MembershipStatus;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
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
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OccupancyAccessServiceTest {

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    private OccupancyAccessService accessService;

    private UUID spaceId;
    private UUID callerId;
    private UUID memberId;
    private SpaceEntity space;

    @BeforeEach
    void setUp() {
        accessService = new OccupancyAccessService(new SpaceMembershipResolver(spaceMembershipRepository));
        spaceId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        UserEntity owner = UserEntity.builder().fullName("Owner").mobileNumber("9000000003").build();
        owner.setId(UUID.randomUUID());
        space = SpaceEntity.builder()
                .owner(owner)
                .name("PG")
                .type(SpaceType.PG)
                .isActive(true)
                .build();
        space.setId(spaceId);
    }

    @ParameterizedTest
    @EnumSource(
            value = MembershipRole.class,
            names = {"OWNER", "MANAGER"})
    void assertCanManageOccupancy_allowsManagers(MembershipRole role) {
        stubMembership(role);

        assertThatCode(() -> accessService.assertCanManageOccupancy(spaceId, callerId))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(
            value = MembershipRole.class,
            names = {"STAFF", "TENANT", "CUSTOMER"})
    void assertCanManageOccupancy_deniesOthers(MembershipRole role) {
        stubMembership(role);

        assertThatThrownBy(() -> accessService.assertCanManageOccupancy(spaceId, callerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only OWNER or MANAGER can perform this action");
    }

    @ParameterizedTest
    @EnumSource(
            value = MembershipRole.class,
            names = {"OWNER", "MANAGER", "STAFF"})
    void assertCanViewSpaceOccupancies_allowsOperationalRoles(MembershipRole role) {
        stubMembership(role);

        assertThatCode(() -> accessService.assertCanViewSpaceOccupancies(spaceId, callerId))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(
            value = MembershipRole.class,
            names = {"TENANT", "CUSTOMER"})
    void assertCanViewSpaceOccupancies_deniesResidents(MembershipRole role) {
        stubMembership(role);

        assertThatThrownBy(() -> accessService.assertCanViewSpaceOccupancies(spaceId, callerId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void assertCanViewMemberOccupancy_tenantOwnMember_allowed() {
        stubMembership(MembershipRole.TENANT);
        MemberEntity member = linkedMember(callerId);

        assertThatCode(() -> accessService.assertCanViewMemberOccupancy(spaceId, memberId, callerId, member))
                .doesNotThrowAnyException();
    }

    @Test
    void assertCanViewMemberOccupancy_tenantOtherMember_forbidden() {
        stubMembership(MembershipRole.TENANT);
        MemberEntity member = linkedMember(UUID.randomUUID());

        assertThatThrownBy(() -> accessService.assertCanViewMemberOccupancy(spaceId, memberId, callerId, member))
                .isInstanceOf(BusinessException.class)
                .hasMessage("You can only view your own occupancy")
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo("OWN_SCOPE_ONLY"));
    }

    @ParameterizedTest
    @EnumSource(
            value = MembershipRole.class,
            names = {"OWNER", "MANAGER"})
    void assertSubjectIsResident_rejectsManagementMembers(MembershipRole role) {
        MemberEntity member = MemberEntity.builder().role(role).build();

        assertThatThrownBy(() -> accessService.assertSubjectIsResident(member))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be allocated");
    }

    @ParameterizedTest
    @EnumSource(
            value = MembershipRole.class,
            names = {"TENANT", "CUSTOMER", "STAFF"})
    void assertSubjectIsResident_allowsResidents(MembershipRole role) {
        MemberEntity member = MemberEntity.builder().role(role).build();

        assertThatCode(() -> accessService.assertSubjectIsResident(member)).doesNotThrowAnyException();
    }

    private void stubMembership(MembershipRole role) {
        UserEntity user = UserEntity.builder().fullName("Caller").mobileNumber("9000000004").build();
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

    private MemberEntity linkedMember(UUID userId) {
        UserEntity user = UserEntity.builder().fullName("Tenant").mobileNumber("9000000005").build();
        user.setId(userId);
        return MemberEntity.builder().user(user).role(MembershipRole.TENANT).build();
    }
}
