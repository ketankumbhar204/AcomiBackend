package com.acomi.acomi_backend.inquirycredit.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository.SpaceEnquiryRepository;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryQuotaResponse;
import com.acomi.acomi_backend.inquirycredit.domain.model.AndroidInquiryBillingMode;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryAccessGrant;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryClientChannel;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryDailyUsageEntity;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryPaymentConfigEntity;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository.InquiryDailyUsageRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryAccessService {

    private final InquiryDailyUsageRepository dailyUsageRepository;
    private final InquiryCreditWalletService walletService;
    private final SpaceEnquiryRepository spaceEnquiryRepository;
    private final InquiryPaymentConfigService paymentConfigService;
    private final Clock clock;

    /**
     * Snapshot of today's free-quota for the seeker UI for the given channel.
     * Does not create a usage row when none exists yet (remaining = full limit).
     */
    @Transactional(readOnly = true)
    public InquiryQuotaResponse getQuota(UUID userId, InquiryClientChannel channel) {
        InquiryClientChannel safe = channel != null ? channel : InquiryClientChannel.WEB;
        InquiryPaymentConfigEntity config = paymentConfigService.requireConfig();
        LocalDate today = LocalDate.now(clock);
        int freeUsed = dailyUsageRepository
                .findOneByUserIdAndUsageDateAndChannel(userId, today, safe)
                .map(InquiryDailyUsageEntity::getFreeUsed)
                .orElse(0);

        if (safe == InquiryClientChannel.ANDROID) {
            AndroidInquiryBillingMode mode =
                    AndroidInquiryBillingMode.fromDb(config.getAndroidBillingMode());
            if (mode == AndroidInquiryBillingMode.FREE) {
                return InquiryQuotaResponse.builder()
                        .channel(safe.name())
                        .dailyFreeLimit(0)
                        .freeUsedToday(freeUsed)
                        .freeRemainingToday(0)
                        .availableCredits(walletService.getBalance(userId))
                        .androidBillingMode(mode.name())
                        .build();
            }
            int limit = Math.max(0, config.getAndroidFreeDailyLimit());
            return InquiryQuotaResponse.builder()
                    .channel(safe.name())
                    .dailyFreeLimit(limit)
                    .freeUsedToday(freeUsed)
                    .freeRemainingToday(Math.max(0, limit - freeUsed))
                    .availableCredits(walletService.getBalance(userId))
                    .androidBillingMode(mode.name())
                    .build();
        }

        int limit = Math.max(0, config.getWebFreeDailyLimit());
        return InquiryQuotaResponse.builder()
                .channel(safe.name())
                .dailyFreeLimit(limit)
                .freeUsedToday(freeUsed)
                .freeRemainingToday(Math.max(0, limit - freeUsed))
                .availableCredits(walletService.getBalance(userId))
                .build();
    }

    /** @deprecated Prefer {@link #getQuota(UUID, InquiryClientChannel)}. */
    @Transactional(readOnly = true)
    public InquiryQuotaResponse getWebQuota(UUID userId) {
        return getQuota(userId, InquiryClientChannel.WEB);
    }

    /**
     * Authorize a new enquiry creation. Returns the grant type.
     * The caller MUST invoke {@link #consumeAfterSuccessfulCreate} after the enquiry is persisted.
     *
     * @throws BusinessException RATE_LIMITED (429) for Android rate limiting
     * @throws BusinessException WEB_FREE_LIMIT_REACHED (402) when free quota exhausted and wallet empty
     */
    @Transactional
    public InquiryAccessGrant authorizeNewEnquiry(UUID userId, InquiryClientChannel channel) {
        if (channel == InquiryClientChannel.ANDROID) {
            return authorizeAndroid(userId);
        }
        return authorizeWeb(userId);
    }

    /**
     * Called after the enquiry entity has been successfully persisted.
     * Consumes quota/credits according to the grant type.
     */
    @Transactional
    public void consumeAfterSuccessfulCreate(
            UUID userId, InquiryClientChannel channel, InquiryAccessGrant grant, UUID enquiryId) {
        InquiryClientChannel safe = channel != null ? channel : InquiryClientChannel.WEB;
        switch (grant) {
            case FREE_WEB -> {
                if (!tryIncrementFreeUsed(userId, InquiryClientChannel.WEB)) {
                    fallbackToCreditOrFail(userId, enquiryId, webFreeLimit());
                }
            }
            case ANDROID_FREE -> {
                InquiryPaymentConfigEntity config = paymentConfigService.requireConfig();
                AndroidInquiryBillingMode mode =
                        AndroidInquiryBillingMode.fromDb(config.getAndroidBillingMode());
                if (mode == AndroidInquiryBillingMode.CREDITS) {
                    if (!tryIncrementFreeUsed(userId, InquiryClientChannel.ANDROID)) {
                        fallbackToCreditOrFail(userId, enquiryId, config.getAndroidFreeDailyLimit());
                    }
                } else {
                    incrementAndroidUsageForMetrics(userId);
                }
            }
            case PAID_CREDIT -> walletService.consumeUsage(userId, enquiryId);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private InquiryAccessGrant authorizeAndroid(UUID userId) {
        InquiryPaymentConfigEntity config = paymentConfigService.requireConfig();
        int hourlyLimit = Math.max(1, config.getAndroidHourlyRateLimit());
        LocalDateTime oneHourAgo = LocalDateTime.now(clock).minusHours(1);
        long recentCount = spaceEnquiryRepository
                .countByRequesterUserIdAndRequestedAtGreaterThanEqual(userId, oneHourAgo);
        if (recentCount >= hourlyLimit) {
            throw new BusinessException(
                    "RATE_LIMITED",
                    "You have submitted too many enquiries recently. Please try again later.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        AndroidInquiryBillingMode mode =
                AndroidInquiryBillingMode.fromDb(config.getAndroidBillingMode());
        if (mode == AndroidInquiryBillingMode.FREE) {
            return InquiryAccessGrant.ANDROID_FREE;
        }

        int freeLimit = Math.max(0, config.getAndroidFreeDailyLimit());
        LocalDate today = LocalDate.now(clock);
        InquiryDailyUsageEntity usage = getOrCreateUsage(userId, today, InquiryClientChannel.ANDROID);
        if (usage.getFreeUsed() < freeLimit) {
            return InquiryAccessGrant.ANDROID_FREE;
        }
        if (walletService.getBalance(userId) > 0) {
            return InquiryAccessGrant.PAID_CREDIT;
        }
        throw new BusinessException(
                "WEB_FREE_LIMIT_REACHED",
                "You have used all " + freeLimit
                        + " free mobile enquiries for today. Purchase inquiry credits to continue.",
                HttpStatus.PAYMENT_REQUIRED);
    }

    private InquiryAccessGrant authorizeWeb(UUID userId) {
        int freeLimit = webFreeLimit();
        LocalDate today = LocalDate.now(clock);
        InquiryDailyUsageEntity usage = getOrCreateUsage(userId, today, InquiryClientChannel.WEB);

        if (usage.getFreeUsed() < freeLimit) {
            return InquiryAccessGrant.FREE_WEB;
        }

        int balance = walletService.getBalance(userId);
        if (balance > 0) {
            return InquiryAccessGrant.PAID_CREDIT;
        }

        throw new BusinessException(
                "WEB_FREE_LIMIT_REACHED",
                "You have used all " + freeLimit + " free email enquiries for today. "
                        + "Purchase inquiry credits to continue.",
                HttpStatus.PAYMENT_REQUIRED);
    }

    private void fallbackToCreditOrFail(UUID userId, UUID enquiryId, int freeLimit) {
        if (walletService.getBalance(userId) > 0) {
            walletService.consumeUsage(userId, enquiryId);
            log.info(
                    "inquiry_free_race_fallback_to_credit userId={} enquiryId={}",
                    userId,
                    enquiryId);
            return;
        }
        throw new BusinessException(
                "WEB_FREE_LIMIT_REACHED",
                "You have used all " + freeLimit
                        + " free enquiries for today. Purchase inquiry credits to continue.",
                HttpStatus.PAYMENT_REQUIRED);
    }

    private boolean tryIncrementFreeUsed(UUID userId, InquiryClientChannel channel) {
        int freeLimit = channel == InquiryClientChannel.ANDROID
                ? Math.max(0, paymentConfigService.requireConfig().getAndroidFreeDailyLimit())
                : webFreeLimit();
        LocalDate today = LocalDate.now(clock);
        InquiryDailyUsageEntity usage = getOrCreateUsage(userId, today, channel);
        if (usage.getFreeUsed() >= freeLimit) {
            return false;
        }
        usage.setFreeUsed(usage.getFreeUsed() + 1);
        dailyUsageRepository.save(usage);
        return true;
    }

    private void incrementAndroidUsageForMetrics(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        InquiryDailyUsageEntity usage = getOrCreateUsage(userId, today, InquiryClientChannel.ANDROID);
        usage.setFreeUsed(usage.getFreeUsed() + 1);
        dailyUsageRepository.save(usage);
    }

    private int webFreeLimit() {
        return Math.max(0, paymentConfigService.requireConfig().getWebFreeDailyLimit());
    }

    private InquiryDailyUsageEntity getOrCreateUsage(
            UUID userId, LocalDate date, InquiryClientChannel channel) {
        return dailyUsageRepository
                .findByUserIdAndUsageDateAndChannel(userId, date, channel)
                .orElseGet(() -> createUsageRow(userId, date, channel));
    }

    private InquiryDailyUsageEntity createUsageRow(
            UUID userId, LocalDate date, InquiryClientChannel channel) {
        try {
            return dailyUsageRepository.save(InquiryDailyUsageEntity.builder()
                    .userId(userId)
                    .usageDate(date)
                    .channel(channel)
                    .freeUsed(0)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            return dailyUsageRepository
                    .findByUserIdAndUsageDateAndChannel(userId, date, channel)
                    .orElseThrow(() -> new BusinessException(
                            "INQUIRY_USAGE_CREATE_ERROR",
                            "Failed to initialize daily usage. Please try again.",
                            HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
