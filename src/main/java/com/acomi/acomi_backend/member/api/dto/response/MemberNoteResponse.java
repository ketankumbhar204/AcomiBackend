package com.acomi.acomi_backend.member.api.dto.response;

import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberNoteEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Note attached to a member record")
public class MemberNoteResponse {

    private UUID noteId;
    private String note;
    private UUID createdBy;
    private String createdByName;
    private LocalDateTime createdAt;

    public static MemberNoteResponse from(MemberNoteEntity note) {
        return MemberNoteResponse.builder()
                .noteId(note.getId())
                .note(note.getNote())
                .createdBy(note.getCreatedBy().getId())
                .createdByName(note.getCreatedBy().getFullName())
                .createdAt(note.getCreatedAt())
                .build();
    }
}
