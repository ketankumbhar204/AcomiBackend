package com.acomi.acomi_backend.complaint.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.complaint.infrastructure.persistence.entity.SpaceComplaintEntity;
import com.acomi.acomi_backend.complaint.infrastructure.persistence.repository.SpaceComplaintRepository;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComplaintNotificationSyncServiceTest {

    @Mock
    private SpaceComplaintRepository complaintRepository;

    @Mock
    private SpaceMembershipRepository membershipRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ComplaintNotificationSyncService service;

    @Test
    void onComplaintCommented_doesNotNotifyTheCommentAuthor() {
        UUID spaceId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID otherManagerId = UUID.randomUUID();

        SpaceEntity space = SpaceEntity.builder().name("Sunrise PG").isActive(true).build();
        space.setId(spaceId);

        SpaceComplaintEntity complaint = SpaceComplaintEntity.builder()
                .space(space)
                .title("Leaky tap")
                .createdByUserId(creatorId)
                .build();
        complaint.setId(UUID.randomUUID());

        UserEntity creator = UserEntity.builder().fullName("Creator").build();
        creator.setId(creatorId);
        UserEntity other = UserEntity.builder().fullName("Manager").build();
        other.setId(otherManagerId);
        when(membershipRepository.findBySpaceIdAndStatus(spaceId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(
                        SpaceMembershipEntity.builder()
                                .user(creator)
                                .space(space)
                                .role(MembershipRole.OWNER)
                                .status(MembershipStatus.ACTIVE)
                                .build(),
                        SpaceMembershipEntity.builder()
                                .user(other)
                                .space(space)
                                .role(MembershipRole.MANAGER)
                                .status(MembershipStatus.ACTIVE)
                                .build()));

        service.onComplaintCommented(complaint, creatorId);

        ArgumentCaptor<PublishNotificationCommand> captor = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getUserId()).isEqualTo(otherManagerId);
        verify(notificationService, never()).publish(org.mockito.ArgumentMatchers.argThat(
                command -> creatorId.equals(command.getUserId())));
    }
}
