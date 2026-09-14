package com.acomi.acomi_backend.mail.infrastructure.smtp;

import com.acomi.acomi_backend.mail.application.dto.MailSendResult;
import com.acomi.acomi_backend.mail.application.dto.OutboundEmail;
import com.acomi.acomi_backend.mail.application.port.MailTransport;
import com.acomi.acomi_backend.mail.application.support.EmailErrorSanitizer;
import com.acomi.acomi_backend.mail.application.support.EmailTextSanitizer;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "acomi.mail.enabled", havingValue = "true")
public class SmtpMailTransport implements MailTransport {

    private final JavaMailSender javaMailSender;

    @Override
    public boolean isLive() {
        return true;
    }

    @Override
    public MailSendResult send(OutboundEmail email) {
        try {
            MimeMessage mime = javaMailSender.createMimeMessage();
            boolean multipart = email.htmlBody() != null && !email.htmlBody().isBlank();
            MimeMessageHelper helper = new MimeMessageHelper(mime, multipart, StandardCharsets.UTF_8.name());
            String from = EmailTextSanitizer.header(email.from());
            String fromName = EmailTextSanitizer.header(email.fromName());
            if (fromName.isBlank()) {
                helper.setFrom(from);
            } else {
                helper.setFrom(new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()));
            }
            helper.setTo(EmailTextSanitizer.header(email.to()));
            helper.setSubject(EmailTextSanitizer.subject(email.subject()));
            String replyTo = EmailTextSanitizer.header(email.replyTo());
            if (!replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            String plain = email.plainBody() == null ? "" : email.plainBody();
            String html = email.htmlBody();
            if (html != null && !html.isBlank()) {
                helper.setText(plain, html);
            } else {
                helper.setText(plain, false);
            }
            javaMailSender.send(mime);
            String messageId = mime.getMessageID();
            log.info(
                    "SMTP accepted email to={} messageId={}",
                    EmailTextSanitizer.header(email.to()),
                    messageId);
            return MailSendResult.sent(messageId);
        } catch (Exception ex) {
            String sanitized = EmailErrorSanitizer.sanitize(ex);
            log.warn("SMTP send failed to={} reason={}", EmailTextSanitizer.header(email.to()), sanitized);
            return MailSendResult.failed(sanitized);
        }
    }
}
