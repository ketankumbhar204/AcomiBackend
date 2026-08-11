package com.amico.amico_backend.complaint.api.dto.response;

import com.amico.amico_backend.complaint.domain.model.ComplaintCategory;
import com.amico.amico_backend.complaint.domain.model.ComplaintPriority;
import com.amico.amico_backend.complaint.domain.model.ComplaintStatus;
import com.amico.amico_backend.complaint.infrastructure.persistence.entity.SpaceComplaintEntity;
import com.amico.amico_backend.meal.domain.model.MealType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ComplaintResponse {

    private UUID complaintId;
    private UUID spaceId;
    private UUID createdByMemberId;
    private String createdByMemberName;
    private UUID createdByUserId;
    private ComplaintCategory category;
    private ComplaintPriority priority;
    private ComplaintStatus status;
    private String title;
    private String description;
    private UUID assignedToMembershipId;
    private String assignedToName;
    private String resolutionSummary;
    private LocalDateTime resolvedAt;
    private UUID resolvedByUserId;
    private LocalDateTime reopenedAt;
    private LocalDateTime closedAt;
    private LocalDateTime cancelledAt;
    private LocalDate mealDate;
    private MealType mealType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean canReopen;
    private List<ComplaintCommentResponse> comments;
    private List<ComplaintAttachmentResponse> attachments;
    private List<ComplaintTimelineEventResponse> timeline;

    public static ComplaintResponse summary(SpaceComplaintEntity entity, boolean canReopen) {
        return base(entity, canReopen)
                .comments(null)
                .attachments(null)
                .timeline(null)
                .build();
    }

    public static ComplaintResponse detail(
            SpaceComplaintEntity entity,
            boolean canReopen,
            List<ComplaintCommentResponse> comments,
            List<ComplaintAttachmentResponse> attachments,
            List<ComplaintTimelineEventResponse> timeline) {
        return base(entity, canReopen)
                .comments(comments != null ? comments : Collections.emptyList())
                .attachments(attachments != null ? attachments : Collections.emptyList())
                .timeline(timeline != null ? timeline : Collections.emptyList())
                .build();
    }

    private static ComplaintResponseBuilder base(SpaceComplaintEntity entity, boolean canReopen) {
        return ComplaintResponse.builder()
                .complaintId(entity.getId())
                .spaceId(entity.getSpace().getId())
                .createdByMemberId(entity.getCreatedByMember().getId())
                .createdByMemberName(entity.getCreatedByMember().getFullName())
                .createdByUserId(entity.getCreatedByUserId())
                .category(entity.getCategory())
                .priority(entity.getPriority())
                .status(entity.getStatus())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .assignedToMembershipId(
                        entity.getAssignedToMembership() != null
                                ? entity.getAssignedToMembership().getId()
                                : null)
                .assignedToName(
                        entity.getAssignedToMembership() != null
                                        && entity.getAssignedToMembership().getUser() != null
                                ? entity.getAssignedToMembership().getUser().getFullName()
                                : null)
                .resolutionSummary(entity.getResolutionSummary())
                .resolvedAt(entity.getResolvedAt())
                .resolvedByUserId(entity.getResolvedByUserId())
                .reopenedAt(entity.getReopenedAt())
                .closedAt(entity.getClosedAt())
                .cancelledAt(entity.getCancelledAt())
                .mealDate(entity.getMealDate())
                .mealType(entity.getMealType())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .canReopen(canReopen);
    }
}
