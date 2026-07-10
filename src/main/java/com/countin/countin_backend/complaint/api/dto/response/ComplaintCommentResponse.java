package com.countin.countin_backend.complaint.api.dto.response;

import com.countin.countin_backend.complaint.infrastructure.persistence.entity.SpaceComplaintCommentEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ComplaintCommentResponse {

    private UUID commentId;
    private UUID authorMemberId;
    private String authorName;
    private UUID authorUserId;
    private String body;
    private boolean internal;
    private LocalDateTime createdAt;

    public static ComplaintCommentResponse from(SpaceComplaintCommentEntity entity) {
        return ComplaintCommentResponse.builder()
                .commentId(entity.getId())
                .authorMemberId(
                        entity.getAuthorMember() != null ? entity.getAuthorMember().getId() : null)
                .authorName(
                        entity.getAuthorMember() != null ? entity.getAuthorMember().getFullName() : null)
                .authorUserId(entity.getAuthorUserId())
                .body(entity.getBody())
                .internal(entity.isInternal())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
