package com.acomi.acomi_backend.inquirycredit.application.service;

import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryCreditPurchaseRequestEntity;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository.InquiryCreditPurchaseRequestRepository;
import com.acomi.acomi_backend.mail.application.dto.OutboundEmail;
import com.acomi.acomi_backend.mail.application.port.EmailPayloadComposer;
import com.acomi.acomi_backend.mail.config.MailProperties;
import com.acomi.acomi_backend.mail.domain.model.EmailEventType;
import com.acomi.acomi_backend.mail.infrastructure.persistence.entity.EmailSendLogEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InquiryCreditEmailComposer implements EmailPayloadComposer {

    private final InquiryCreditPurchaseRequestRepository purchaseRepository;
    private final UserRepository userRepository;
    private final MailProperties mailProperties;

    @Override
    public boolean supports(EmailEventType eventType) {
        return eventType == EmailEventType.INQUIRY_CREDIT_PAYMENT_PENDING
                || eventType == EmailEventType.INQUIRY_CREDIT_PAYMENT_APPROVED
                || eventType == EmailEventType.INQUIRY_CREDIT_PAYMENT_REJECTED;
    }

    @Override
    public Optional<OutboundEmail> compose(EmailSendLogEntity sendLog) {
        if (sendLog.getRelatedEntityId() == null || sendLog.getEventType() == null) {
            return Optional.empty();
        }
        InquiryCreditPurchaseRequestEntity request =
                purchaseRepository.findById(sendLog.getRelatedEntityId()).orElse(null);
        if (request == null) {
            log.warn(
                    "Inquiry credit email retry skipped missing purchase requestId={}",
                    sendLog.getRelatedEntityId());
            return Optional.empty();
        }
        UserEntity seeker = userRepository.findById(request.getUserId()).orElse(null);
        String amount = InquiryCreditEmailCopy.amountLabel(request.getAmount(), request.getCurrency());
        String to = firstNonBlank(sendLog.getRecipientEmail(), seeker == null ? null : seeker.getEmail());
        if (to == null || to.isBlank()) {
            return Optional.empty();
        }
        String name = seeker == null ? null : seeker.getFullName();
        return switch (sendLog.getEventType()) {
            case INQUIRY_CREDIT_PAYMENT_PENDING -> Optional.of(outbound(
                    to,
                    InquiryCreditEmailCopy.pendingAdminSubject(request.getCredits()),
                    InquiryCreditEmailCopy.pendingAdminBody(request.getCredits(), amount)));
            case INQUIRY_CREDIT_PAYMENT_APPROVED -> Optional.of(outbound(
                    to,
                    InquiryCreditEmailCopy.approvedSubject(request.getCredits()),
                    InquiryCreditEmailCopy.approvedBody(name, request.getCredits())));
            case INQUIRY_CREDIT_PAYMENT_REJECTED -> Optional.of(outbound(
                    to,
                    InquiryCreditEmailCopy.rejectedSubject(),
                    InquiryCreditEmailCopy.rejectedBody(name, request.getRejectionReason())));
            default -> Optional.empty();
        };
    }

    private OutboundEmail outbound(String to, String subject, String body) {
        String from = mailProperties.getFrom() == null ? "" : mailProperties.getFrom().trim();
        return new OutboundEmail(
                to, subject, body, from, mailProperties.getFromName(), mailProperties.resolvedReplyTo());
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return fallback;
    }
}
