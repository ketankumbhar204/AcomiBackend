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
        InquiryDailyUsageEntity todayUsage = dailyUsageRepository
                .findOneByUserIdAndUsageDateAndChannel(userId, today, safe)
                .orElse(null);
        int freeUsed = todayUsage != null ? todayUsage.getFreeUsed() : 0;
        int viewHereUsed = todayUsage != null ? todayUsage.getViewHereUsed() : 0;

        if (safe == InquiryClientChannel.ANDROID) {
            AndroidInquiryBillingMode mode =
                    AndroidInquiryBillingMode.fromDb(config.getAndroidBillingMode());
            boolean unlimited = !androidCreditsEnforced(config);
            int limit = unlimited ? 0 : Math.max(0, config.getAndroidFreeDailyLimit());
            int viewHereLimit = viewHereLimit(config, safe);
            return InquiryQuotaResponse.builder()
                    .channel(safe.name())
                    .dailyFreeLimit(limit)
                    .freeUsedToday(freeUsed)
                    .freeRemainingToday(unlimited ? 0 : Math.max(0, limit - freeUsed))
                    .availableCredits(walletService.getBalance(userId))
                    .androidBillingMode(mode.name())
                    .purchasesEnabled(config.isEnabled())
                    .unlimited(unlimited)
                    .viewHereDailyLimit(viewHereLimit)
                    .viewHereUsedToday(viewHereUsed)
                    .viewHereRemainingToday(Math.max(0, viewHereLimit - viewHereUsed))
                    .build();
        }

        boolean unlimited = !webLimitsEnforced(config);
        int limit = unlimited ? 0 : Math.max(0, config.getWebFreeDailyLimit());
        int freeRemaining = unlimited ? 0 : Math.max(0, limit - freeUsed);
        int viewHereLimit = viewHereLimit(config, safe);
        int viewHereRemaining = Math.max(0, viewHereLimit - viewHereUsed);
        return InquiryQuotaResponse.builder()
                .channel(safe.name())
                .dailyFreeLimit(limit)
                .freeUsedToday(freeUsed)
                .freeRemainingToday(freeRemaining)
                .availableCredits(walletService.getBalance(userId))
                .purchasesEnabled(config.isEnabled())
                .unlimited(unlimited)
                .viewHereDailyLimit(viewHereLimit)
                .viewHereUsedToday(viewHereUsed)
                .viewHereRemainingToday(viewHereRemaining)
                .build();
    }

    /**
     * Local testing only: set today's email enquiry usage back to zero.
     * Does not change the view-here counter.
     */
    @Transactional
    public InquiryQuotaResponse resetTodayQuota(UUID userId, InquiryClientChannel channel) {
        return resetTodayQuota(userId, channel, false);
    }

    /**
     * Local testing only. {@code viewHereOnly} resets the website view counter;
     * otherwise only the email enquiry counter is reset.
     */
    @Transactional
    public InquiryQuotaResponse resetTodayQuota(UUID userId, InquiryClientChannel channel, boolean viewHereOnly) {
        InquiryClientChannel safe = channel != null ? channel : InquiryClientChannel.WEB;
        LocalDate today = LocalDate.now(clock);
        dailyUsageRepository
                .findOneByUserIdAndUsageDateAndChannel(userId, today, safe)
                .ifPresent(usage -> {
                    if (viewHereOnly) {
                        usage.setViewHereUsed(0);
                    } else {
                        usage.setFreeUsed(0);
                    }
                    dailyUsageRepository.save(usage);
                });
        return getQuota(userId, safe);
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
                if (!webLimitsEnforced(paymentConfigService.requireConfig())) {
                    return;
                }
                if (!tryIncrementFreeUsed(userId, InquiryClientChannel.WEB)) {
                    fallbackToCreditOrFail(userId, enquiryId, webFreeLimit());
                }
            }
            case ANDROID_FREE -> {
                InquiryPaymentConfigEntity config = paymentConfigService.requireConfig();
                if (androidCreditsEnforced(config)) {
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

    /**
     * Checks the instant-view allowance for the caller's channel.
     * This counter is independent of email and in-app enquiry allowances.
     */
    @Transactional
    public void assertViewHereAvailable(UUID userId, InquiryClientChannel channel) {
        viewHereDecision(userId, channel, false);
    }

    /** @deprecated Use {@link #assertViewHereAvailable(UUID, InquiryClientChannel)}. */
    @Transactional
    public void assertViewHereAvailable(UUID userId) {
        assertViewHereAvailable(userId, InquiryClientChannel.WEB);
    }

    /**
     * Atomically consumes one view-here slot for the channel.
     * Does not change the email or in-app enquiry counter.
     */
    @Transactional
    public void consumeViewHere(UUID userId, InquiryClientChannel channel) {
        viewHereDecision(userId, channel, true);
    }

    /** @deprecated Use {@link #consumeViewHere(UUID, InquiryClientChannel)}. */
    @Transactional
    public void consumeViewHere(UUID userId) {
        consumeViewHere(userId, InquiryClientChannel.WEB);
    }

    /** Effective instant-view cap. 0 hides the option and rejects the API. */
    static int viewHereLimit(InquiryPaymentConfigEntity config, InquiryClientChannel channel) {
        if (config == null) {
            return 0;
        }
        if (channel == InquiryClientChannel.ANDROID) {
            if (!config.isAndroidViewHereEnabled()) {
                return 0;
            }
            return Math.max(0, config.getAndroidViewHereDailyLimit());
        }
        if (!config.isWebViewHereEnabled()) {
            return 0;
        }
        return Math.max(0, config.getViewHereDailyLimit());
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

        if (!androidCreditsEnforced(config)) {
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
        if (!webLimitsEnforced(paymentConfigService.requireConfig())) {
            return InquiryAccessGrant.FREE_WEB;
        }
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

    private void viewHereDecision(UUID userId, InquiryClientChannel channel, boolean consume) {
        InquiryClientChannel safe = channel != null ? channel : InquiryClientChannel.WEB;
        InquiryPaymentConfigEntity config = paymentConfigService.requireConfig();
        int viewLimit = viewHereLimit(config, safe);
        if (viewLimit <= 0) {
            throw new BusinessException(
                    "VIEW_HERE_DISABLED",
                    "Instant contact viewing is not available.",
                    HttpStatus.CONFLICT);
        }
        LocalDate today = LocalDate.now(clock);
        InquiryDailyUsageEntity usage = getOrCreateUsage(userId, today, safe);
        if (usage.getViewHereUsed() >= viewLimit) {
            throw new BusinessException(
                    "VIEW_HERE_LIMIT_REACHED",
                    "You have used all instant contact views for today.",
                    HttpStatus.CONFLICT);
        }
        if (!consume) {
            return;
        }
        usage.setViewHereUsed(usage.getViewHereUsed() + 1);
        dailyUsageRepository.save(usage);
    }

    /**
     * Credit purchases off → unlimited (no daily cap, no paywall).
     * Purchases on with free limit 0 → no free enquiries; paid credits or purchase required.
     */
    private static boolean webLimitsEnforced(InquiryPaymentConfigEntity config) {
        return config != null && config.isEnabled();
    }

    /** Credit purchases on and Android billing = CREDITS. */
    private static boolean androidCreditsEnforced(InquiryPaymentConfigEntity config) {
        if (config == null || !config.isEnabled()) {
            return false;
        }
        return AndroidInquiryBillingMode.fromDb(config.getAndroidBillingMode())
                == AndroidInquiryBillingMode.CREDITS;
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
