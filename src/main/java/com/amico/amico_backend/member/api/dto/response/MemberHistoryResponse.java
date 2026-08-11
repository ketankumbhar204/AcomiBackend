package com.amico.amico_backend.member.api.dto.response;

import com.amico.amico_backend.member.domain.model.MemberHistoryAction;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberHistoryEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Audit history entry for a member record")
public class MemberHistoryResponse {

    private UUID historyId;
    private MemberHistoryAction action;
    private String oldValue;
    private String newValue;
    private UUID changedBy;
    private String changedByName;
    private LocalDateTime changedAt;

    public static MemberHistoryResponse from(MemberHistoryEntity history) {
        return MemberHistoryResponse.builder()
                .historyId(history.getId())
                .action(history.getAction())
                .oldValue(history.getOldValue())
                .newValue(history.getNewValue())
                .changedBy(history.getChangedBy().getId())
                .changedByName(history.getChangedBy().getFullName())
                .changedAt(history.getCreatedAt())
                .build();
    }
}
