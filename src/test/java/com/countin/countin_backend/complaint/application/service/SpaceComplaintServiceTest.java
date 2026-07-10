package com.countin.countin_backend.complaint.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.countin.countin_backend.common.exception.BusinessException;
import com.countin.countin_backend.complaint.api.dto.request.ReopenComplaintRequest;
import com.countin.countin_backend.complaint.api.dto.request.UpdateComplaintStatusRequest;
import com.countin.countin_backend.complaint.domain.model.ComplaintCategory;
import com.countin.countin_backend.complaint.domain.model.ComplaintPriority;
import com.countin.countin_backend.complaint.domain.model.ComplaintStatus;
import com.countin.countin_backend.complaint.infrastructure.persistence.entity.SpaceComplaintEntity;
import com.countin.countin_backend.complaint.infrastructure.persistence.repository.SpaceComplaintAttachmentRepository;
import com.countin.countin_backend.complaint.infrastructure.persistence.repository.SpaceComplaintCommentRepository;
import com.countin.countin_backend.complaint.infrastructure.persistence.repository.SpaceComplaintRepository;
import com.countin.countin_backend.complaint.infrastructure.persistence.repository.SpaceComplaintTimelineEventRepository;
import com.countin.countin_backend.member.domain.model.MembershipRole;
import com.countin.countin_backend.member.domain.model.MembershipStatus;
import com.countin.countin_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.countin.countin_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.countin.countin_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.countin.countin_backend.space.domain.model.SpaceType;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.countin.countin_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.countin.countin_backend.user.infrastructure.persistence.entity.UserEntity;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpaceComplaintServiceTest {

    @Mock
    private SpaceComplaintRepository complaintRepository;

    @Mock
    private SpaceComplaintCommentRepository commentRepository;

    @Mock
    private SpaceComplaintAttachmentRepository attachmentRepository;

    @Mock
    private SpaceComplaintTimelineEventRepository timelineRepository;

    @Mock
    private SpaceComplaintAccessService accessService;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMembershipRepository membershipRepository;

    @Mock
    private ComplaintNotificationSyncService notificationSyncService;

    @InjectMocks
    private SpaceComplaintService complaintService;

    private UUID spaceId;
    private UUID callerId;
    private UUID complaintId;
    private SpaceEntity space;
    private SpaceMembershipEntity ownerMembership;
    private SpaceMembershipEntity tenantMembership;
    private SpaceComplaintEntity complaint;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        complaintId = UUID.randomUUID();

        UserEntity user = UserEntity.builder().fullName("Owner").mobileNumber("9000000000").build();
        user.setId(callerId);
        space = SpaceEntity.builder().owner(user).name("Test PG").type(SpaceType.PG).isActive(true).build();
        space.setId(spaceId);

        ownerMembership = SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();
        ownerMembership.setId(UUID.randomUUID());

        UserEntity tenantUser =
                UserEntity.builder().fullName("Tenant").mobileNumber("9000000001").build();
        tenantUser.setId(UUID.randomUUID());
        tenantMembership = SpaceMembershipEntity.builder()
                .user(tenantUser)
                .space(space)
                .role(MembershipRole.TENANT)
                .status(MembershipStatus.ACTIVE)
                .build();
        tenantMembership.setId(UUID.randomUUID());

        MemberEntity member = MemberEntity.builder().fullName("Tenant").space(space).build();
        member.setId(UUID.randomUUID());

        complaint = SpaceComplaintEntity.builder()
                .space(space)
                .createdByMember(member)
                .createdByUserId(tenantUser.getId())
                .category(ComplaintCategory.MAINTENANCE)
                .priority(ComplaintPriority.MEDIUM)
                .status(ComplaintStatus.OPEN)
                .title("Leaking tap")
                .description("Bathroom tap leaks")
                .build();
        complaint.setId(complaintId);
    }

    @Test
    void updateStatus_rejectsInvalidTransition() {
        when(accessService.requireManageComplaints(spaceId, callerId)).thenReturn(ownerMembership);
        when(complaintRepository.findByIdAndSpace_Id(complaintId, spaceId)).thenReturn(Optional.of(complaint));

        UpdateComplaintStatusRequest request = new UpdateComplaintStatusRequest();
        request.setStatus(ComplaintStatus.CLOSED);

        assertThatThrownBy(() -> complaintService.updateStatus(spaceId, complaintId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid status transition");
        verify(notificationSyncService, never()).onComplaintStatusChanged(any());
    }

    @Test
    void reopen_rejectsOutsideWindow() {
        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.setResolvedAt(LocalDateTime.now().minusDays(10));

        when(accessService.requireActiveMembership(spaceId, callerId)).thenReturn(ownerMembership);
        when(accessService.canManageComplaints(ownerMembership)).thenReturn(true);
        when(complaintRepository.findByIdAndSpace_Id(complaintId, spaceId)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() ->
                        complaintService.reopen(spaceId, complaintId, callerId, new ReopenComplaintRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be reopened");
    }

    @Test
    void reopen_rejectsStaffWithoutOwnership() {
        SpaceMembershipEntity staff = SpaceMembershipEntity.builder()
                .user(ownerMembership.getUser())
                .space(space)
                .role(MembershipRole.STAFF)
                .status(MembershipStatus.ACTIVE)
                .build();
        staff.setId(UUID.randomUUID());

        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.setResolvedAt(LocalDateTime.now().minusDays(1));

        when(accessService.requireActiveMembership(spaceId, callerId)).thenReturn(staff);
        when(accessService.canManageComplaints(staff)).thenReturn(false);
        when(accessService.isOwnScopeOnly(staff)).thenReturn(false);
        when(complaintRepository.findByIdAndSpace_Id(complaintId, spaceId)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() ->
                        complaintService.reopen(spaceId, complaintId, callerId, new ReopenComplaintRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot reopen");
    }

    @Test
    void list_forcesOwnScopeForTenant() {
        when(accessService.requireActiveMembership(spaceId, callerId)).thenReturn(tenantMembership);
        when(accessService.isOwnScopeOnly(tenantMembership)).thenReturn(true);
        when(accessService.resolveOwnMemberId(spaceId, callerId))
                .thenReturn(complaint.getCreatedByMember().getId());
        when(complaintRepository.findFiltered(
                        spaceId, null, null, null, null, complaint.getCreatedByMember().getId()))
                .thenReturn(Collections.emptyList());

        complaintService.list(spaceId, callerId, null, null, null, UUID.randomUUID(), false);

        verify(complaintRepository)
                .findFiltered(spaceId, null, null, null, null, complaint.getCreatedByMember().getId());
    }
}
