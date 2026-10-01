package com.acomi.acomi_backend.inquirycredit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository.SpaceEnquiryRepository;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryAccessGrant;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryClientChannel;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryDailyUsageEntity;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryPaymentConfigEntity;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository.InquiryDailyUsageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class InquiryAccessServiceTest {

    @Mock
    private InquiryDailyUsageRepository dailyUsageRepository;

    @Mock
    private InquiryCreditWalletService walletService;

    @Mock
    private SpaceEnquiryRepository spaceEnquiryRepository;

    @Mock
    private InquiryPaymentConfigService paymentConfigService;

    private InquiryAccessService accessService;

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-09-14T06:30:00Z"), ZoneId.of("Asia/Kolkata"));

    private UUID userId;
    private InquiryPaymentConfigEntity config;

    @BeforeEach
    void setUp() {
        accessService = new InquiryAccessService(
                dailyUsageRepository,
                walletService,
                spaceEnquiryRepository,
                paymentConfigService,
                fixedClock);
        userId = UUID.randomUUID();
        config = InquiryPaymentConfigEntity.builder()
                .enabled(true)
                .webFreeDailyLimit(5)
                .androidBillingMode("FREE")
                .androidFreeDailyLimit(5)
                .androidHourlyRateLimit(20)
                .build();
        lenient().when(paymentConfigService.requireConfig()).thenReturn(config);
        lenient()
                .when(dailyUsageRepository.findOneByUserIdAndUsageDateAndChannel(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void authorizeNewEnquiry_webWithFreeQuotaAvailable_returnsFreeWeb() {
        LocalDate today = LocalDate.now(fixedClock);
        InquiryDailyUsageEntity usage = buildUsage(userId, today, InquiryClientChannel.WEB, 2);
        when(dailyUsageRepository.findByUserIdAndUsageDateAndChannel(userId, today, InquiryClientChannel.WEB))
                .thenReturn(Optional.of(usage));

        InquiryAccessGrant grant = accessService.authorizeNewEnquiry(userId, InquiryClientChannel.WEB);

        assertThat(grant).isEqualTo(InquiryAccessGrant.FREE_WEB);
    }

    @Test
    void authorizeNewEnquiry_webWithFreeQuotaExhaustedAndCreditsAvailable_returnsPaidCredit() {
        LocalDate today = LocalDate.now(fixedClock);
        InquiryDailyUsageEntity usage = buildUsage(userId, today, InquiryClientChannel.WEB, 5);
        when(dailyUsageRepository.findByUserIdAndUsageDateAndChannel(userId, today, InquiryClientChannel.WEB))
                .thenReturn(Optional.of(usage));
        when(walletService.getBalance(userId)).thenReturn(3);

        InquiryAccessGrant grant = accessService.authorizeNewEnquiry(userId, InquiryClientChannel.WEB);

        assertThat(grant).isEqualTo(InquiryAccessGrant.PAID_CREDIT);
    }

    @Test
    void authorizeNewEnquiry_webWithFreeQuotaExhaustedAndNoCredits_throwsPaymentRequired() {
        LocalDate today = LocalDate.now(fixedClock);
        InquiryDailyUsageEntity usage = buildUsage(userId, today, InquiryClientChannel.WEB, 5);
        when(dailyUsageRepository.findByUserIdAndUsageDateAndChannel(userId, today, InquiryClientChannel.WEB))
                .thenReturn(Optional.of(usage));
        when(walletService.getBalance(userId)).thenReturn(0);

        assertThatThrownBy(() -> accessService.authorizeNewEnquiry(userId, InquiryClientChannel.WEB))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
                    assertThat(be.getErrorCode()).isEqualTo("WEB_FREE_LIMIT_REACHED");
                });
    }

    @Test
    void authorizeNewEnquiry_webNoUsageRow_createsFreshAndReturnsFreeWeb() {
        LocalDate today = LocalDate.now(fixedClock);
        when(dailyUsageRepository.findByUserIdAndUsageDateAndChannel(userId, today, InquiryClientChannel.WEB))
                .thenReturn(Optional.empty());
        InquiryDailyUsageEntity fresh = buildUsage(userId, today, InquiryClientChannel.WEB, 0);
        when(dailyUsageRepository.save(any())).thenReturn(fresh);

        InquiryAccessGrant grant = accessService.authorizeNewEnquiry(userId, InquiryClientChannel.WEB);

        assertThat(grant).isEqualTo(InquiryAccessGrant.FREE_WEB);
    }

    @Test
    void authorizeNewEnquiry_androidUnderRateLimit_returnsAndroidFree() {
        when(spaceEnquiryRepository.countByRequesterUserIdAndRequestedAtGreaterThanEqual(eq(userId), any()))
                .thenReturn(5L);

        InquiryAccessGrant grant = accessService.authorizeNewEnquiry(userId, InquiryClientChannel.ANDROID);

        assertThat(grant).isEqualTo(InquiryAccessGrant.ANDROID_FREE);
        verify(walletService, never()).getBalance(any());
    }

    @Test
    void authorizeNewEnquiry_androidAtRateLimit_throwsRateLimited() {
        when(spaceEnquiryRepository.countByRequesterUserIdAndRequestedAtGreaterThanEqual(eq(userId), any()))
                .thenReturn(20L);

        assertThatThrownBy(() -> accessService.authorizeNewEnquiry(userId, InquiryClientChannel.ANDROID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(be.getErrorCode()).isEqualTo("RATE_LIMITED");
                });
    }

    @Test
    void authorizeNewEnquiry_androidCreditsModeFreeExhausted_returnsPaidCredit() {
        config.setAndroidBillingMode("CREDITS");
        LocalDate today = LocalDate.now(fixedClock);
        InquiryDailyUsageEntity usage = buildUsage(userId, today, InquiryClientChannel.ANDROID, 5);
        when(spaceEnquiryRepository.countByRequesterUserIdAndRequestedAtGreaterThanEqual(eq(userId), any()))
                .thenReturn(1L);
        when(dailyUsageRepository.findByUserIdAndUsageDateAndChannel(
                        userId, today, InquiryClientChannel.ANDROID))
                .thenReturn(Optional.of(usage));
        when(walletService.getBalance(userId)).thenReturn(2);

        InquiryAccessGrant grant = accessService.authorizeNewEnquiry(userId, InquiryClientChannel.ANDROID);

        assertThat(grant).isEqualTo(InquiryAccessGrant.PAID_CREDIT);
    }

    @Test
    void consumeAfterSuccessfulCreate_freeWeb_incrementsUsage() {
        LocalDate today = LocalDate.now(fixedClock);
        InquiryDailyUsageEntity usage = buildUsage(userId, today, InquiryClientChannel.WEB, 2);
        when(dailyUsageRepository.findByUserIdAndUsageDateAndChannel(userId, today, InquiryClientChannel.WEB))
                .thenReturn(Optional.of(usage));
        when(dailyUsageRepository.save(any())).thenReturn(usage);

        UUID enquiryId = UUID.randomUUID();
        accessService.consumeAfterSuccessfulCreate(
                userId, InquiryClientChannel.WEB, InquiryAccessGrant.FREE_WEB, enquiryId);

        verify(dailyUsageRepository).save(any());
        verify(walletService, never()).consumeUsage(any(), any());
        assertThat(usage.getFreeUsed()).isEqualTo(3);
    }

    @Test
    void consumeAfterSuccessfulCreate_freeWebRaceAtLimit_fallsBackToCredit() {
        LocalDate today = LocalDate.now(fixedClock);
        InquiryDailyUsageEntity usage = buildUsage(userId, today, InquiryClientChannel.WEB, 5);
        when(dailyUsageRepository.findByUserIdAndUsageDateAndChannel(userId, today, InquiryClientChannel.WEB))
                .thenReturn(Optional.of(usage));
        when(walletService.getBalance(userId)).thenReturn(1);

        UUID enquiryId = UUID.randomUUID();
        accessService.consumeAfterSuccessfulCreate(
                userId, InquiryClientChannel.WEB, InquiryAccessGrant.FREE_WEB, enquiryId);

        verify(walletService).consumeUsage(userId, enquiryId);
        verify(dailyUsageRepository, never()).save(any());
    }

    @Test
    void consumeAfterSuccessfulCreate_freeWebRaceAtLimitNoCredits_throwsAndBlocks() {
        LocalDate today = LocalDate.now(fixedClock);
        InquiryDailyUsageEntity usage = buildUsage(userId, today, InquiryClientChannel.WEB, 5);
        when(dailyUsageRepository.findByUserIdAndUsageDateAndChannel(userId, today, InquiryClientChannel.WEB))
                .thenReturn(Optional.of(usage));
        when(walletService.getBalance(userId)).thenReturn(0);

        UUID enquiryId = UUID.randomUUID();
        assertThatThrownBy(() -> accessService.consumeAfterSuccessfulCreate(
                        userId, InquiryClientChannel.WEB, InquiryAccessGrant.FREE_WEB, enquiryId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("WEB_FREE_LIMIT_REACHED");
                });
        verify(walletService, never()).consumeUsage(any(), any());
    }

    @Test
    void consumeAfterSuccessfulCreate_paidCredit_debitsWallet() {
        UUID enquiryId = UUID.randomUUID();
        accessService.consumeAfterSuccessfulCreate(
                userId, InquiryClientChannel.WEB, InquiryAccessGrant.PAID_CREDIT, enquiryId);

        verify(walletService).consumeUsage(userId, enquiryId);
        verify(dailyUsageRepository, never()).save(any());
    }

    @Test
    void consumeAfterSuccessfulCreate_androidFree_tracksMetricsOnly() {
        LocalDate today = LocalDate.now(fixedClock);
        InquiryDailyUsageEntity usage = buildUsage(userId, today, InquiryClientChannel.ANDROID, 10);
        when(dailyUsageRepository.findByUserIdAndUsageDateAndChannel(
                        userId, today, InquiryClientChannel.ANDROID))
                .thenReturn(Optional.of(usage));
        when(dailyUsageRepository.save(any())).thenReturn(usage);

        UUID enquiryId = UUID.randomUUID();
        accessService.consumeAfterSuccessfulCreate(
                userId, InquiryClientChannel.ANDROID, InquiryAccessGrant.ANDROID_FREE, enquiryId);

        verify(dailyUsageRepository).save(any());
        verify(walletService, never()).consumeUsage(any(), any());
    }

    @Test
    void resetTodayQuota_setsFreeUsedToZero() {
        LocalDate today = LocalDate.now(fixedClock);
        InquiryDailyUsageEntity usage = buildUsage(userId, today, InquiryClientChannel.WEB, 5);
        when(dailyUsageRepository.findOneByUserIdAndUsageDateAndChannel(
                        userId, today, InquiryClientChannel.WEB))
                .thenReturn(Optional.of(usage));
        when(dailyUsageRepository.save(usage)).thenReturn(usage);
        when(walletService.getBalance(userId)).thenReturn(0);

        var quota = accessService.resetTodayQuota(userId, InquiryClientChannel.WEB);

        assertThat(usage.getFreeUsed()).isEqualTo(0);
        assertThat(quota.getFreeRemainingToday()).isEqualTo(5);
        verify(dailyUsageRepository).save(usage);
    }

    @Test
    void authorizeNewEnquiry_webPurchasesDisabled_isUnlimitedEvenWhenUsedUp() {
        config.setEnabled(false);

        InquiryAccessGrant grant = accessService.authorizeNewEnquiry(userId, InquiryClientChannel.WEB);

        assertThat(grant).isEqualTo(InquiryAccessGrant.FREE_WEB);
        verify(dailyUsageRepository, never())
                .findByUserIdAndUsageDateAndChannel(any(), any(), any());
    }

    @Test
    void getQuota_webPurchasesDisabled_marksUnlimited() {
        config.setEnabled(false);
        when(walletService.getBalance(userId)).thenReturn(19);

        var quota = accessService.getQuota(userId, InquiryClientChannel.WEB);

        assertThat(quota.getUnlimited()).isTrue();
        assertThat(quota.getPurchasesEnabled()).isFalse();
        assertThat(quota.getAvailableCredits()).isEqualTo(19);
        assertThat(quota.getDailyFreeLimit()).isEqualTo(0);
    }

    @Test
    void getQuota_webWithPaidCredits_includesWalletBalance() {
        when(walletService.getBalance(userId)).thenReturn(30);

        var quota = accessService.getQuota(userId, InquiryClientChannel.WEB);

        assertThat(quota.getAvailableCredits()).isEqualTo(30);
        assertThat(quota.getPurchasesEnabled()).isTrue();
        assertThat(quota.getUnlimited()).isFalse();
        assertThat(quota.getDailyFreeLimit()).isEqualTo(5);
    }

    @Test
    void getQuota_webFreeLimitZero_isNotUnlimitedAndRequiresCreditsOrPay() {
        config.setWebFreeDailyLimit(0);
        when(walletService.getBalance(userId)).thenReturn(0);

        var quota = accessService.getQuota(userId, InquiryClientChannel.WEB);

        assertThat(quota.getUnlimited()).isFalse();
        assertThat(quota.getPurchasesEnabled()).isTrue();
        assertThat(quota.getDailyFreeLimit()).isEqualTo(0);
        assertThat(quota.getFreeRemainingToday()).isEqualTo(0);
    }

    @Test
    void authorizeNewEnquiry_webFreeLimitZeroWithCredits_returnsPaidCredit() {
        config.setWebFreeDailyLimit(0);
        LocalDate today = LocalDate.now(fixedClock);
        InquiryDailyUsageEntity usage = buildUsage(userId, today, InquiryClientChannel.WEB, 0);
        when(dailyUsageRepository.findByUserIdAndUsageDateAndChannel(userId, today, InquiryClientChannel.WEB))
                .thenReturn(Optional.of(usage));
        when(walletService.getBalance(userId)).thenReturn(5);

        InquiryAccessGrant grant = accessService.authorizeNewEnquiry(userId, InquiryClientChannel.WEB);

        assertThat(grant).isEqualTo(InquiryAccessGrant.PAID_CREDIT);
    }

    @Test
    void authorizeNewEnquiry_webFreeLimitZeroNoCredits_throwsPaymentRequired() {
        config.setWebFreeDailyLimit(0);
        LocalDate today = LocalDate.now(fixedClock);
        InquiryDailyUsageEntity usage = buildUsage(userId, today, InquiryClientChannel.WEB, 0);
        when(dailyUsageRepository.findByUserIdAndUsageDateAndChannel(userId, today, InquiryClientChannel.WEB))
                .thenReturn(Optional.of(usage));
        when(walletService.getBalance(userId)).thenReturn(0);

        assertThatThrownBy(() -> accessService.authorizeNewEnquiry(userId, InquiryClientChannel.WEB))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
                    assertThat(be.getErrorCode()).isEqualTo("WEB_FREE_LIMIT_REACHED");
                });
    }

    private InquiryDailyUsageEntity buildUsage(
            UUID userId, LocalDate date, InquiryClientChannel channel, int freeUsed) {
        InquiryDailyUsageEntity entity = InquiryDailyUsageEntity.builder()
                .userId(userId)
                .usageDate(date)
                .channel(channel)
                .freeUsed(freeUsed)
                .build();
        entity.setId(UUID.randomUUID());
        return entity;
    }
}
