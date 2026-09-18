package com.acomi.acomi_backend.enquiry.application.service;

import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.entity.SpaceEnquiryEntity;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository.SpaceEnquiryRepository;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryClientChannel;
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
 * Rebuilds the authorised enquiry-share email for retry. Does not log owner contact.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnquirySharedEmailComposer implements EmailPayloadComposer {

    private final SpaceEnquiryRepository enquiryRepository;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;
    private final OwnerContactResolver ownerContactResolver;
    private final EnquiryListingDetailsResolver listingDetailsResolver;
    private final MailProperties mailProperties;

    @Override
    public boolean supports(EmailEventType eventType) {
        return eventType == EmailEventType.ENQUIRY_SHARED;
    }

    @Override
    public Optional<OutboundEmail> compose(EmailSendLogEntity sendLog) {
        if (sendLog.getRelatedEntityId() == null) {
            return Optional.empty();
        }
        SpaceEnquiryEntity enquiry = enquiryRepository.findById(sendLog.getRelatedEntityId()).orElse(null);
        if (enquiry == null) {
            log.warn("Enquiry share email retry skipped enquiry missing enquiryId={}", sendLog.getRelatedEntityId());
            return Optional.empty();
        }
        if (enquiry.getClientChannel() == InquiryClientChannel.ANDROID) {
            log.info(
                    "Enquiry share email retry skipped ANDROID in-app delivery enquiryId={}",
                    enquiry.getId());
            return Optional.empty();
        }
        UserEntity requester = userRepository.findByIdAndIsActiveTrue(enquiry.getRequesterUserId()).orElse(null);
        SpaceEntity space = spaceRepository.findActiveWithOwnerById(enquiry.getSpaceId()).orElse(null);
        if (space == null) {
            log.warn("Enquiry share email retry skipped space missing enquiryId={}", enquiry.getId());
            return Optional.empty();
        }
        OwnerContactResponse contact = ownerContactResolver.resolve(space);
        if (!ownerContactResolver.hasShareableContact(contact)) {
            log.warn("Enquiry share email retry skipped owner contact unavailable enquiryId={}", enquiry.getId());
            return Optional.empty();
        }
        String requesterName = requester != null ? requester.getFullName() : enquiry.getRequesterNameSnapshot();
        EnquiryMailMessage message = new EnquiryMailMessage(
                sendLog.getRecipientEmail() != null ? sendLog.getRecipientEmail() : enquiry.getRequesterEmail(),
                requesterName,
                space.getName(),
                space.getType(),
                space.getAddress(),
                contact,
                listingDetailsResolver.resolve(space));
        String from = mailProperties.getFrom() == null ? "" : mailProperties.getFrom().trim();
        String fromName = mailProperties.getFromName();
        String replyTo = mailProperties.resolvedReplyTo();
        return Optional.of(new OutboundEmail(
                message.to(),
                EnquiryContactEmailComposer.subject(message),
                EnquiryContactEmailComposer.body(message),
                from,
                fromName,
                replyTo,
                EnquiryContactEmailComposer.htmlBody(message)));
    }
}
