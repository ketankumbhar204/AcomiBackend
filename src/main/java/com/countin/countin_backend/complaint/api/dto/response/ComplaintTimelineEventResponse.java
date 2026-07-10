package com.countin.countin_backend.complaint.api.dto.response;

import com.countin.countin_backend.complaint.domain.model.ComplaintTimelineEventType;
import com.countin.countin_backend.complaint.infrastructure.persistence.entity.SpaceComplaintTimelineEventEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ComplaintTimelineEventResponse {

    private UUID eventId;
    private ComplaintTimelineEventType eventType;
    private LocalDateTime performedAt;
    private String remarks;
    private UUID performedBy;

    public static ComplaintTimelineEventResponse from(SpaceComplaintTimelineEventEntity entity) {
        return ComplaintTimelineEventResponse.builder()
                .eventId(entity.getId())
                .eventType(entity.getEventType())
                .performedAt(entity.getPerformedAt())
                .remarks(entity.getRemarks())
                .performedBy(entity.getPerformedBy())
                .build();
    }
}
