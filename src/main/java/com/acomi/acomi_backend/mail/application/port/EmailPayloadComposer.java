package com.acomi.acomi_backend.mail.application.port;

import com.acomi.acomi_backend.mail.application.dto.OutboundEmail;
import com.acomi.acomi_backend.mail.domain.model.EmailEventType;
import com.acomi.acomi_backend.mail.infrastructure.persistence.entity.EmailSendLogEntity;
import java.util.Optional;

/**
 * Rebuilds a send payload from persisted metadata. Used by the retry job when
 * the original body is no longer in memory. Must not persist the composed body.
 */
public interface EmailPayloadComposer {

    boolean supports(EmailEventType eventType);

    Optional<OutboundEmail> compose(EmailSendLogEntity log);
}
