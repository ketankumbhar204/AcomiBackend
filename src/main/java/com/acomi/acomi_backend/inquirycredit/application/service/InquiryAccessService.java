package com.acomi.acomi_backend.inquirycredit.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository.SpaceEnquiryRepository;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryQuotaResponse;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryAccessGrant;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryClientChannel;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryDailyUsageEntity;
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

    /** Max free enquiries per day per user on WEB channel. */
    private static final int WEB_FREE_DAILY_LIMIT = 5;

    /** ANDROID hourly rate limit to prevent scraping abuse. */
    private static final int ANDROID_HOURLY_RATE_LIMIT = 20;

    private final InquiryDailyUsageRepository dailyUsageRepository;
    private final InquiryCreditWalletService walletService;
    private final SpaceEnquiryRepository spaceEnquiryRepository;
    private final Clock clock;

    /**
     * Snapshot of today's WEB free-quota for the seeker UI.
     * Does not create a usage row when none exists yet (remaining = full limit).
     */
    @Transactional(readOnly = true)
    public InquiryQuotaResponse getWebQuota(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        int freeUsed = dailyUsageRepository
                .findOneByUserIdAndUsageDateAndChannel(userId, today, InquiryClientChannel.WEB)
                .map(InquiryDailyUsageEntity::getFreeUsed)
                .orElse(0);
        int freeRemaining = Math.max(0, WEB_FREE_DAILY_LIMIT - freeUsed);
        return InquiryQuotaResponse.builder()
                .dailyFreeLimit(WEB_FREE_DAILY_LIMIT)
                .freeUsedToday(freeUsed)
                .freeRemainingToday(freeRemaining)
                .availableCredits(walletService.getBalance(userId))
                .build();
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
        switch (grant) {
            case FREE_WEB -> {
                // Under concurrent load another TX may have taken the last free slot after authorize.
                // Fail closed (rollback enquiry) unless paid credits can cover.
                if (!tryIncrementWebFreeUsed(userId)) {
                    if (walletService.getBalance(userId) > 0) {
                        walletService.consumeUsage(userId, enquiryId);
                        log.info(
                                "inquiry_free_race_fallback_to_credit userId={} enquiryId={}",
                                userId,
                                enquiryId);
                    } else {
                        throw new BusinessException(
                                "WEB_FREE_LIMIT_REACHED",
                                "You have used all " + WEB_FREE_DAILY_LIMIT
                                        + " free enquiries for today. Purchase inquiry credits to continue.",
                                HttpStatus.PAYMENT_REQUIRED);
                    }
                }
            }
            case PAID_CREDIT -> walletService.consumeUsage(userId, enquiryId);
            case ANDROID_FREE -> incrementAndroidUsageForMetrics(userId);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private InquiryAccessGrant authorizeAndroid(UUID userId) {
        LocalDateTime oneHourAgo = LocalDateTime.now(clock).minusHours(1);
        long recentCount = spaceEnquiryRepository
                .countByRequesterUserIdAndRequestedAtGreaterThanEqual(userId, oneHourAgo);
        if (recentCount >= ANDROID_HOURLY_RATE_LIMIT) {
            throw new BusinessException(
                    "RATE_LIMITED",
                    "You have submitted too many enquiries recently. Please try again later.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        return InquiryAccessGrant.ANDROID_FREE;
    }

    private InquiryAccessGrant authorizeWeb(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        InquiryDailyUsageEntity usage = getOrCreateWebUsage(userId, today);

        if (usage.getFreeUsed() < WEB_FREE_DAILY_LIMIT) {
            return InquiryAccessGrant.FREE_WEB;
        }

        // Free quota exhausted — check wallet
        int balance = walletService.getBalance(userId);
        if (balance > 0) {
            return InquiryAccessGrant.PAID_CREDIT;
        }

        throw new BusinessException(
                "WEB_FREE_LIMIT_REACHED",
                "You have used all " + WEB_FREE_DAILY_LIMIT + " free enquiries for today. "
                        + "Purchase inquiry credits to continue.",
                HttpStatus.PAYMENT_REQUIRED);
    }

    /** @return true if a free slot was reserved; false if the daily free limit is already exhausted */
    private boolean tryIncrementWebFreeUsed(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        InquiryDailyUsageEntity usage = getOrCreateWebUsage(userId, today);
        if (usage.getFreeUsed() >= WEB_FREE_DAILY_LIMIT) {
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

    private InquiryDailyUsageEntity getOrCreateWebUsage(UUID userId, LocalDate date) {
        return getOrCreateUsage(userId, date, InquiryClientChannel.WEB);
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
            // Race condition: row was created concurrently — re-fetch.
            return dailyUsageRepository
                    .findByUserIdAndUsageDateAndChannel(userId, date, channel)
                    .orElseThrow(() -> new BusinessException(
                            "INQUIRY_USAGE_CREATE_ERROR",
                            "Failed to initialize daily usage. Please try again.",
                            HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }
}
