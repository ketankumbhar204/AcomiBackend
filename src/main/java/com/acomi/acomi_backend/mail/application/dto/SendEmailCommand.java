package com.acomi.acomi_backend.mail.application.dto;

import com.acomi.acomi_backend.mail.domain.model.EmailEventType;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SendEmailCommand {

    private final EmailEventType eventType;
    private final UUID recipientUserId;
    private final String recipientEmail;
    private final String subject;
    private final String plainBody;
    private final String htmlBody;
    private final String relatedEntityType;
    private final UUID relatedEntityId;
    private final String idempotencyKey;
}
