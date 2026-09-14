package com.acomi.acomi_backend.mail.application.port;

import com.acomi.acomi_backend.mail.application.dto.MailSendResult;
import com.acomi.acomi_backend.mail.application.dto.OutboundEmail;

public interface MailTransport {

    MailSendResult send(OutboundEmail email);

    /** {@code true} when this transport performs real SMTP delivery. */
    boolean isLive();
}
