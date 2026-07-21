package com.countin.countin_backend.member.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.countin.countin_backend.member.domain.model.InvitationStatus;
import com.countin.countin_backend.member.domain.model.MembershipRole;
import com.countin.countin_backend.member.domain.model.MembershipStatus;
import com.countin.countin_backend.member.infrastructure.persistence.entity.InvitationEntity;
import com.countin.countin_backend.member.infrastructure.persistence.repository.InvitationRepository;
import com.countin.countin_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.countin.countin_backend.space.domain.model.SpaceType;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.countin.countin_backend.user.infrastructure.persistence.entity.UserEntity;
import com.countin.countin_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationProvisionerTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    private InvitationProvisioner invitationProvisioner;

    private SpaceEntity space;
    private UserEntity invitedBy;

    @BeforeEach
    void setUp() {
        invitationProvisioner = new InvitationProvisioner(
                invitationRepository, userRepository, spaceMembershipRepository);

        UserEntity owner = UserEntity.builder().fullName("Owner").mobileNumber("9000000001").build();
        owner.setId(UUID.randomUUID());
        space = SpaceEntity.builder()
                .owner(owner)
                .name("Test Mess")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        space.setId(UUID.randomUUID());

        invitedBy = UserEntity.builder().fullName("Manager").mobileNumber("9000000002").build();
        invitedBy.setId(UUID.randomUUID());
    }

    @Test
    void ensurePendingInvitation_createsInvitationForNewMobile() {
        when(userRepository.findByMobileNumber("9876543210")).thenReturn(Optional.empty());
        when(invitationRepository.existsBySpaceIdAndMobileNumberAndStatus(
                        space.getId(), "9876543210", InvitationStatus.PENDING))
                .thenReturn(false);
        when(invitationRepository.save(any(InvitationEntity.class)))
                .thenAnswer(invocation -> {
                    InvitationEntity entity = invocation.getArgument(0);
                    entity.setId(UUID.randomUUID());
                    return entity;
                });

        Optional<InvitationEntity> created = invitationProvisioner.ensurePendingInvitation(
                space, invitedBy, "+91 98765 43210", MembershipRole.CUSTOMER);

        assertThat(created).isPresent();
        ArgumentCaptor<InvitationEntity> captor = ArgumentCaptor.forClass(InvitationEntity.class);
        verify(invitationRepository).save(captor.capture());
        assertThat(captor.getValue().getMobileNumber()).isEqualTo("9876543210");
        assertThat(captor.getValue().getRole()).isEqualTo(MembershipRole.CUSTOMER);
        assertThat(captor.getValue().getExpiresAt())
                .isAfter(LocalDateTime.now().plusDays(364))
                .isBefore(LocalDateTime.now().plusDays(366));
    }

    @Test
    void ensurePendingInvitation_skipsWhenUserAlreadyMember() {
        UserEntity existing = UserEntity.builder().mobileNumber("9876543210").build();
        existing.setId(UUID.randomUUID());
        when(userRepository.findByMobileNumber("9876543210")).thenReturn(Optional.of(existing));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                        existing.getId(), space.getId(), MembershipStatus.ACTIVE))
                .thenReturn(true);

        Optional<InvitationEntity> created = invitationProvisioner.ensurePendingInvitation(
                space, invitedBy, "9876543210", MembershipRole.CUSTOMER);

        assertThat(created).isEmpty();
        verify(invitationRepository, org.mockito.Mockito.never()).save(any());
    }
}
