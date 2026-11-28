package com.acomi.acomi_backend.admin.application.service;

import com.acomi.acomi_backend.admin.api.dto.request.AdminSendListingMarketingWhatsAppRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminListingMarketingResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminListingMarketingWhatsAppCapabilityResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminSendListingMarketingWhatsAppResponse;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingChannel;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingDeliveryMode;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingKind;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingOutcome;
import com.acomi.acomi_backend.admin.infrastructure.config.MarketingWhatsAppProperties;
import com.acomi.acomi_backend.admin.infrastructure.persistence.entity.AdminListingMarketingActivityEntity;
import com.acomi.acomi_backend.admin.infrastructure.persistence.repository.AdminListingMarketingActivityRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Capability checks and automatic Business Platform send foundation for listing marketing.
 *
 * <p>Live Meta sends are intentionally gated by {@code sender-ready}. Until media upload and an
 * approved promotional Meta template are wired and E2E-tested, automatic mode stays unavailable and
 * never reports a successful provider send.
 */
@Service
@RequiredArgsConstructor
public class AdminListingMarketingWhatsAppService {

    private final MarketingWhatsAppProperties properties;
    private final AdminListingMarketingService marketingService;
    private final AdminListingMarketingActivityRepository activityRepository;
    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final MessRegistrationRepository messRegistrationRepository;
    private final SpaceRepository spaceRepository;

    /** In-memory duplicate guard keyed by listing + normalized phone + clientRequestId. */
    private final ConcurrentHashMap<String, Long> recentSubmitKeys = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public AdminListingMarketingWhatsAppCapabilityResponse capability() {
        List<String> reasons = properties.unavailableReasons();
        boolean available = reasons.isEmpty();
        return AdminListingMarketingWhatsAppCapabilityResponse.builder()
                .available(available)
                .mode(properties.normalizedMode())
                .enabled(properties.isEnabled())
                .senderReady(properties.isSenderReady())
                .businessNumberHint(properties.maskedBusinessNumberHint())
                .reasons(reasons)
                .explanation(buildExplanation(available, reasons))
                .build();
    }

    @Transactional
    public AdminSendListingMarketingWhatsAppResponse sendForProperty(
            UUID registrationId, AdminSendListingMarketingWhatsAppRequest request) {
        if (!propertyRegistrationRepository.existsById(registrationId)) {
            throw new BusinessException("Property registration not found", HttpStatus.NOT_FOUND);
        }
        return sendAutomatic(
                ListingMarketingKind.PROPERTY,
                registrationId,
                null,
                propertyRegistrationRepository
                        .findById(registrationId)
                        .map(p -> p.getConvertedSpaceId())
                        .orElse(null),
                request,
                () -> marketingService.getPropertyRegistrationMarketing(registrationId));
    }

    @Transactional
    public AdminSendListingMarketingWhatsAppResponse sendForMess(
            UUID registrationId, AdminSendListingMarketingWhatsAppRequest request) {
        if (!messRegistrationRepository.existsById(registrationId)) {
            throw new BusinessException("Mess registration not found", HttpStatus.NOT_FOUND);
        }
        return sendAutomatic(
                ListingMarketingKind.MESS,
                null,
                registrationId,
                messRegistrationRepository
                        .findById(registrationId)
                        .map(m -> m.getConvertedSpaceId())
                        .orElse(null),
                request,
                () -> marketingService.getMessRegistrationMarketing(registrationId));
    }

    @Transactional
    public AdminSendListingMarketingWhatsAppResponse sendForSpace(
            UUID spaceId, AdminSendListingMarketingWhatsAppRequest request) {
        var space = spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new BusinessException("Space not found", HttpStatus.NOT_FOUND));
        var property = propertyRegistrationRepository.findByConvertedSpaceId(spaceId);
        if (property.isPresent()) {
            return sendForProperty(property.get().getId(), request);
        }
        var mess = messRegistrationRepository.findByConvertedSpaceId(spaceId);
        if (mess.isPresent()) {
            return sendForMess(mess.get().getId(), request);
        }
        ListingMarketingKind kind =
                space.getType() != null && "MESS".equalsIgnoreCase(space.getType().name())
                        ? ListingMarketingKind.MESS
                        : ListingMarketingKind.PROPERTY;
        return sendAutomatic(
                kind, null, null, space.getId(), request, () -> marketingService.getSpaceMarketing(spaceId));
    }

    private AdminSendListingMarketingWhatsAppResponse sendAutomatic(
            ListingMarketingKind kind,
            UUID propertyRegistrationId,
            UUID messRegistrationId,
            UUID spaceId,
            AdminSendListingMarketingWhatsAppRequest request,
            java.util.function.Supplier<AdminListingMarketingResponse> reload) {
        UUID actorId = SecurityUtils.getCurrentUserId();
        String digits = normalizeDigits(request.getRecipientPhone());
        if (digits == null || digits.length() < 10) {
            throw new BusinessException("Enter a valid recipient mobile number", HttpStatus.BAD_REQUEST);
        }

        String dedupeKey = buildDedupeKey(
                kind, propertyRegistrationId, messRegistrationId, spaceId, digits, request.getClientRequestId());
        guardDuplicate(dedupeKey);

        AdminListingMarketingWhatsAppCapabilityResponse capability = capability();
        if (!capability.isAvailable()) {
            AdminListingMarketingActivityEntity blocked = persistActivity(
                    kind,
                    propertyRegistrationId,
                    messRegistrationId,
                    spaceId,
                    actorId,
                    ListingMarketingOutcome.WHATSAPP_API_BLOCKED,
                    request,
                    maskPhone(digits),
                    null,
                    "Automatic WhatsApp send blocked: " + capability.getExplanation());
            return AdminSendListingMarketingWhatsAppResponse.builder()
                    .accepted(false)
                    .outcome(ListingMarketingOutcome.WHATSAPP_API_BLOCKED)
                    .activityId(blocked.getId())
                    .explanation(capability.getExplanation())
                    .marketing(reload.get())
                    .build();
        }

        // Live path reserved until Meta promotional pipeline is implemented and E2E-tested.
        AdminListingMarketingActivityEntity failed = persistActivity(
                kind,
                propertyRegistrationId,
                messRegistrationId,
                spaceId,
                actorId,
                ListingMarketingOutcome.WHATSAPP_API_FAILED,
                request,
                maskPhone(digits),
                null,
                "Automatic sender flagged ready in config, but the Meta promotional send pipeline "
                        + "for listing marketing is not implemented yet. Use Manual mode.");
        return AdminSendListingMarketingWhatsAppResponse.builder()
                .accepted(false)
                .outcome(ListingMarketingOutcome.WHATSAPP_API_FAILED)
                .activityId(failed.getId())
                .explanation(
                        "Automatic WhatsApp Business send is not operational yet. "
                                + "Switch to Manual — Open owner’s WhatsApp.")
                .marketing(reload.get())
                .build();
    }

    private AdminListingMarketingActivityEntity persistActivity(
            ListingMarketingKind kind,
            UUID propertyRegistrationId,
            UUID messRegistrationId,
            UUID spaceId,
            UUID actorId,
            ListingMarketingOutcome outcome,
            AdminSendListingMarketingWhatsAppRequest request,
            String maskedPhone,
            String providerMessageId,
            String note) {
        String mediaNote = "";
        if (request.getMediaFileNames() != null && !request.getMediaFileNames().isEmpty()) {
            mediaNote = " Media referenced: " + String.join(", ", request.getMediaFileNames());
        }
        return activityRepository.save(AdminListingMarketingActivityEntity.builder()
                .listingKind(kind)
                .propertyRegistrationId(propertyRegistrationId)
                .messRegistrationId(messRegistrationId)
                .spaceId(spaceId)
                .channel(ListingMarketingChannel.WHATSAPP)
                .outcome(outcome)
                .templateKey(trimToNull(request.getTemplateKey()))
                .note(trimToNull((note == null ? "" : note) + mediaNote))
                .messageSnapshot(trimToNull(request.getMessageSnapshot()))
                .deliveryMode(ListingMarketingDeliveryMode.AUTOMATIC)
                .providerMessageId(providerMessageId)
                .recipientPhoneMasked(maskedPhone)
                .actorUserId(actorId)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void guardDuplicate(String key) {
        long now = System.currentTimeMillis();
        int windowMs = Math.max(5, properties.getDuplicateWindowSeconds()) * 1000;
        recentSubmitKeys.entrySet().removeIf(e -> now - e.getValue() > windowMs);
        Long previous = recentSubmitKeys.putIfAbsent(key, now);
        if (previous != null && now - previous < windowMs) {
            throw new BusinessException(
                    "An automatic WhatsApp send was already submitted for this listing. Wait a moment before retrying.",
                    HttpStatus.CONFLICT);
        }
    }

    private static String buildDedupeKey(
            ListingMarketingKind kind,
            UUID propertyRegistrationId,
            UUID messRegistrationId,
            UUID spaceId,
            String digits,
            String clientRequestId) {
        String listing = propertyRegistrationId != null
                ? "p:" + propertyRegistrationId
                : messRegistrationId != null
                        ? "m:" + messRegistrationId
                        : "s:" + spaceId;
        String client = StringUtils.hasText(clientRequestId) ? clientRequestId.trim() : "";
        return kind.name() + "|" + listing + "|" + digits + "|" + client;
    }

    private static String buildExplanation(boolean available, List<String> reasons) {
        if (available) {
            return "Automatic send through ACOMI’s configured WhatsApp Business number is available.";
        }
        if (reasons.contains("SENDER_NOT_READY")) {
            return "Automatic Business WhatsApp send is not ready yet. ACOMI still needs an approved "
                    + "promotional Meta template, media upload to WhatsApp, and end-to-end verification. "
                    + "Use Manual — Open owner’s WhatsApp for now.";
        }
        if (reasons.contains("INTEGRATION_DISABLED") || reasons.contains("MODE_DISABLED")) {
            return "Automatic Business WhatsApp send is turned off in server configuration. "
                    + "Use Manual — Open owner’s WhatsApp, or ask an operator to configure ACOMI_MARKETING_WHATSAPP_*.";
        }
        if (reasons.contains("CREDENTIALS_MISSING") || reasons.contains("TEMPLATE_MISSING")) {
            return "WhatsApp Business Platform credentials or an approved promotional template are not configured. "
                    + "Automatic send stays unavailable. Use Manual mode.";
        }
        return "Automatic Business WhatsApp send is unavailable. Use Manual — Open owner’s WhatsApp.";
    }

    private static String normalizeDigits(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String digits = raw.replaceAll("\\D+", "");
        if (digits.startsWith("91") && digits.length() == 12) {
            return digits;
        }
        if (digits.length() == 10) {
            return "91" + digits;
        }
        return digits.isEmpty() ? null : digits;
    }

    private static String maskPhone(String digits) {
        if (digits == null || digits.length() < 4) {
            return "••••";
        }
        return "••••" + digits.substring(digits.length() - 4);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
