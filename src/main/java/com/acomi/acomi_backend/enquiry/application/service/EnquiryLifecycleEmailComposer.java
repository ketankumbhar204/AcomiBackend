package com.acomi.acomi_backend.enquiry.application.service;

import com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.entity.SpaceEnquiryEntity;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository.SpaceEnquiryRepository;
import com.acomi.acomi_backend.mail.application.dto.OutboundEmail;
import com.acomi.acomi_backend.mail.application.port.EmailPayloadComposer;
import com.acomi.acomi_backend.mail.config.MailProperties;
import com.acomi.acomi_backend.mail.domain.model.EmailEventType;
import com.acomi.acomi_backend.mail.infrastructure.persistence.entity.EmailSendLogEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Rebuilds enquiry submitted / support / rejected emails for retry.
 * Does not resolve or include owner contact.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnquiryLifecycleEmailComposer implements EmailPayloadComposer {

    private final SpaceEnquiryRepository enquiryRepository;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;
    private final MailProperties mailProperties;

    @Override
    public boolean supports(EmailEventType eventType) {
        return eventType == EmailEventType.ENQUIRY_SUBMITTED
                || eventType == EmailEventType.ENQUIRY_SUBMITTED_SUPPORT
                || eventType == EmailEventType.ENQUIRY_REJECTED;
    }

    @Override
    public Optional<OutboundEmail> compose(EmailSendLogEntity sendLog) {
        if (sendLog.getRelatedEntityId() == null || sendLog.getEventType() == null) {
            return Optional.empty();
        }
        SpaceEnquiryEntity enquiry = enquiryRepository.findById(sendLog.getRelatedEntityId()).orElse(null);
        if (enquiry == null) {
            log.warn(
                    "Enquiry lifecycle email retry skipped enquiry missing eventType={} enquiryId={}",
                    sendLog.getEventType(),
                    sendLog.getRelatedEntityId());
            return Optional.empty();
        }
        return switch (sendLog.getEventType()) {
            case ENQUIRY_SUBMITTED -> Optional.of(submitted(sendLog, enquiry));
            case ENQUIRY_REJECTED -> Optional.of(rejected(sendLog, enquiry));
            case ENQUIRY_SUBMITTED_SUPPORT -> support(sendLog, enquiry);
            default -> Optional.empty();
        };
    }

    private OutboundEmail submitted(EmailSendLogEntity sendLog, SpaceEnquiryEntity enquiry) {
        String to = firstNonBlank(sendLog.getRecipientEmail(), enquiry.getRequesterEmail());
        String name = enquiry.getRequesterNameSnapshot();
        String spaceName = enquiry.getSpaceNameSnapshot();
        return outbound(to, EnquiryLifecycleEmailCopy.submittedSubject(spaceName), EnquiryLifecycleEmailCopy.submittedBody(name, spaceName));
    }

    private OutboundEmail rejected(EmailSendLogEntity sendLog, SpaceEnquiryEntity enquiry) {
        String to = firstNonBlank(sendLog.getRecipientEmail(), enquiry.getRequesterEmail());
        String name = enquiry.getRequesterNameSnapshot();
        String spaceName = enquiry.getSpaceNameSnapshot();
        return outbound(to, EnquiryLifecycleEmailCopy.rejectedSubject(spaceName), EnquiryLifecycleEmailCopy.rejectedBody(name, spaceName));
    }

    private Optional<OutboundEmail> support(EmailSendLogEntity sendLog, SpaceEnquiryEntity enquiry) {
        String to = firstNonBlank(sendLog.getRecipientEmail(), mailProperties.resolvedSupportAddress());
        if (to == null || to.isBlank()) {
            log.warn("Enquiry support email retry skipped missing support address enquiryId={}", enquiry.getId());
            return Optional.empty();
        }
        SpaceEntity space = spaceRepository.findById(enquiry.getSpaceId()).orElse(null);
        UserEntity requester = userRepository.findById(enquiry.getRequesterUserId()).orElse(null);
        String mobile = requester != null ? requester.getMobileNumber() : null;
        String body = EnquiryLifecycleEmailCopy.supportBody(
                enquiry.getSpaceNameSnapshot(),
                space != null ? space.getType() : null,
                enquiry.getRequesterNameSnapshot(),
                enquiry.getRequesterEmail(),
                mobile,
                enquiry.getId(),
                enquiry.getRequestedAt(),
                enquiry.getStatus() == SpaceEnquiryStatus.SHARED && enquiry.getSharedByAdminId() == null);
        return Optional.of(outbound(to, EnquiryLifecycleEmailCopy.supportSubject(enquiry.getSpaceNameSnapshot()), body));
    }

    private OutboundEmail outbound(String to, String subject, String body) {
        String from = mailProperties.getFrom() == null ? "" : mailProperties.getFrom().trim();
        return new OutboundEmail(
                to,
                subject,
                body,
                from,
                mailProperties.getFromName(),
                mailProperties.resolvedReplyTo());
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
