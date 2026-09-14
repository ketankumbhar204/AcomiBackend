package com.acomi.acomi_backend.member.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.member.domain.model.InvitationStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.InvitationEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.InvitationRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.notification.application.service.InvitationNotificationSyncService;
import com.acomi.acomi_backend.meal.application.service.MealParticipationService;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationServiceExpireDueTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MemberMasterService memberMasterService;

    @Mock
    private InvitationProvisioner invitationProvisioner;

    @Mock
    private MealParticipationService mealParticipationService;

    @Mock
    private InvitationNotificationSyncService invitationNotificationSyncService;

    @InjectMocks
    private InvitationService invitationService;

    @Test
    void expireDue_marksPendingInvitationsExpiredAndNotifiesOnce() {
        InvitationEntity invitation = InvitationEntity.builder()
                .status(InvitationStatus.PENDING)
                .build();
        invitation.setId(UUID.randomUUID());
        when(invitationRepository.findExpiredPending(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(invitation));
        when(invitationRepository.save(invitation)).thenReturn(invitation);

        int expired = invitationService.expireDue();

        assertThat(expired).isEqualTo(1);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
        verify(invitationNotificationSyncService).onInvitationCancelledOrExpired(invitation);
    }
}
