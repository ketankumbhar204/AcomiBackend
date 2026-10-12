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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Free daily quota is one row per user, channel, and Asia/Kolkata calendar date.
 * A new IST date is a new row. Yesterday's row is never rewritten, and there is no midnight job.
 */
@ExtendWith(MockitoExtension.class)
class InquiryDailyQuotaIstCalendarTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate OCT_2 = LocalDate.of(2026, 10, 2);
    private static final LocalDate OCT_3 = LocalDate.of(2026, 10, 3);

    @Mock
    private InquiryDailyUsageRepository dailyUsageRepository;

    @Mock
    private InquiryCreditWalletService walletService;

    @Mock
    private SpaceEnquiryRepository spaceEnquiryRepository;

    @Mock
    private InquiryPaymentConfigService paymentConfigService;

    private UUID userId;
    private InquiryPaymentConfigEntity config;

    @BeforeEach
    void setUp() {
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
        lenient()
                .when(dailyUsageRepository.findByUserIdAndUsageDateAndChannel(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(dailyUsageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sameDay_oct2At10amIst_used2OfConfiguredLimit_remaining3() {
        InquiryDailyUsageEntity today = usage(OCT_2, InquiryClientChannel.WEB, 2);
        when(dailyUsageRepository.findOneByUserIdAndUsageDateAndChannel(
                        userId, OCT_2, InquiryClientChannel.WEB))
                .thenReturn(Optional.of(today));
        when(walletService.getBalance(userId)).thenReturn(0);

        var quota = serviceAt(ist(2026, 10, 2, 10, 0, 0)).getQuota(userId, InquiryClientChannel.WEB);

        assertThat(quota.getFreeUsedToday()).isEqualTo(2);
        assertThat(quota.getDailyFreeLimit()).isEqualTo(5);
        assertThat(quota.getFreeRemainingToday()).isEqualTo(3);
        verify(dailyUsageRepository)
                .findOneByUserIdAndUsageDateAndChannel(userId, OCT_2, InquiryClientChannel.WEB);
    }

    @Test
    void sameDay_oct2LimitReached_nextFreeEnquiryRejected() {
        InquiryDailyUsageEntity today = usage(OCT_2, InquiryClientChannel.WEB, 5);
        when(dailyUsageRepository.findByUserIdAndUsageDateAndChannel(
                        userId, OCT_2, InquiryClientChannel.WEB))
                .thenReturn(Optional.of(today));
        when(walletService.getBalance(userId)).thenReturn(0);

        assertThatThrownBy(() -> serviceAt(ist(2026, 10, 2, 10, 0, 0))
                        .authorizeNewEnquiry(userId, InquiryClientChannel.WEB))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException error = (BusinessException) ex;
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
                    assertThat(error.getErrorCode()).isEqualTo("WEB_FREE_LIMIT_REACHED");
                });
        assertThat(today.getFreeUsed()).isEqualTo(5);
    }

    @Test
    void nextIstDay_oct3AtMidnight_reportsUsed0Remaining5_withoutReadingYesterday() {
        InquiryDailyUsageEntity yesterday = usage(OCT_2, InquiryClientChannel.WEB, 5);
        when(walletService.getBalance(userId)).thenReturn(0);

        var quota = serviceAt(ist(2026, 10, 3, 0, 0, 0)).getQuota(userId, InquiryClientChannel.WEB);

        assertThat(quota.getFreeUsedToday()).isEqualTo(0);
        assertThat(quota.getFreeRemainingToday()).isEqualTo(5);
        assertThat(quota.getDailyFreeLimit()).isEqualTo(5);
        assertThat(yesterday.getFreeUsed()).isEqualTo(5);
        verify(dailyUsageRepository)
                .findOneByUserIdAndUsageDateAndChannel(userId, OCT_3, InquiryClientChannel.WEB);
        verify(dailyUsageRepository, never())
                .findOneByUserIdAndUsageDateAndChannel(userId, OCT_2, InquiryClientChannel.WEB);
    }

    @Test
    void firstEnquiryNextDay_missingOct3Row_startsAt0_thenBecomes1_andLeavesOct2Untouched() {
        InquiryDailyUsageEntity yesterday = usage(OCT_2, InquiryClientChannel.WEB, 5);
        InquiryDailyUsageEntity today = usage(OCT_3, InquiryClientChannel.WEB, 0);
        when(dailyUsageRepository.findByUserIdAndUsageDateAndChannel(
                        userId, OCT_3, InquiryClientChannel.WEB))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(today));

        InquiryAccessService service = serviceAt(ist(2026, 10, 3, 0, 0, 0));
        InquiryAccessGrant grant = service.authorizeNewEnquiry(userId, InquiryClientChannel.WEB);

        assertThat(grant).isEqualTo(InquiryAccessGrant.FREE_WEB);
        ArgumentCaptor<InquiryDailyUsageEntity> created = ArgumentCaptor.forClass(InquiryDailyUsageEntity.class);
        verify(dailyUsageRepository).save(created.capture());
        assertThat(created.getValue().getUsageDate()).isEqualTo(OCT_3);
        assertThat(created.getValue().getChannel()).isEqualTo(InquiryClientChannel.WEB);
        assertThat(created.getValue().getFreeUsed()).isEqualTo(0);

        service.consumeAfterSuccessfulCreate(
                userId, InquiryClientChannel.WEB, InquiryAccessGrant.FREE_WEB, UUID.randomUUID());

        assertThat(today.getFreeUsed()).isEqualTo(1);
        assertThat(today.getUsageDate()).isEqualTo(OCT_3);
        assertThat(yesterday.getFreeUsed()).isEqualTo(5);
        assertThat(yesterday.getUsageDate()).isEqualTo(OCT_2);
        verify(dailyUsageRepository, never())
                .findByUserIdAndUsageDateAndChannel(userId, OCT_2, InquiryClientChannel.WEB);
        verify(walletService, never()).consumeUsage(any(), any());
    }

    @Test
    void istBoundary_oct2_235959_staysOct2_and_oct3_000000_isOct3() {
        InquiryDailyUsageEntity oct2 = usage(OCT_2, InquiryClientChannel.WEB, 5);
        when(dailyUsageRepository.findOneByUserIdAndUsageDateAndChannel(
                        userId, OCT_2, InquiryClientChannel.WEB))
                .thenReturn(Optional.of(oct2));
        when(walletService.getBalance(userId)).thenReturn(0);

        var stillOct2 = serviceAt(ist(2026, 10, 2, 23, 59, 59)).getQuota(userId, InquiryClientChannel.WEB);
        var oct3 = serviceAt(ist(2026, 10, 3, 0, 0, 0)).getQuota(userId, InquiryClientChannel.WEB);

        assertThat(stillOct2.getFreeUsedToday()).isEqualTo(5);
        assertThat(stillOct2.getFreeRemainingToday()).isEqualTo(0);
        assertThat(oct3.getFreeUsedToday()).isEqualTo(0);
        assertThat(oct3.getFreeRemainingToday()).isEqualTo(5);
        assertThat(oct2.getFreeUsed()).isEqualTo(5);
        verify(dailyUsageRepository)
                .findOneByUserIdAndUsageDateAndChannel(userId, OCT_3, InquiryClientChannel.WEB);
    }

    @Test
    void utcMidnight_doesNotDefineTheQuotaDay() {
        // 2026-10-02 18:30:00Z is 2026-10-03 00:00 IST, while the UTC calendar date is still 2 Oct.
        Instant istMidnightWhileUtcStillOct2 = Instant.parse("2026-10-02T18:30:00Z");
        Instant oneSecondBeforeIstMidnight = Instant.parse("2026-10-02T18:29:59Z");
        Instant utcMidnightOct3 = Instant.parse("2026-10-03T00:00:00Z");

        InquiryDailyUsageEntity oct2 = usage(OCT_2, InquiryClientChannel.WEB, 5);
        when(dailyUsageRepository.findOneByUserIdAndUsageDateAndChannel(
                        userId, OCT_2, InquiryClientChannel.WEB))
                .thenReturn(Optional.of(oct2));
        when(walletService.getBalance(userId)).thenReturn(0);

        var before = serviceAt(oneSecondBeforeIstMidnight).getQuota(userId, InquiryClientChannel.WEB);
        var atIstMidnight = serviceAt(istMidnightWhileUtcStillOct2).getQuota(userId, InquiryClientChannel.WEB);
        var atUtcMidnight = serviceAt(utcMidnightOct3).getQuota(userId, InquiryClientChannel.WEB);

        assertThat(LocalDate.now(Clock.fixed(oneSecondBeforeIstMidnight, IST))).isEqualTo(OCT_2);
        assertThat(LocalDate.now(Clock.fixed(istMidnightWhileUtcStillOct2, ZoneId.of("UTC"))))
                .isEqualTo(OCT_2);
        assertThat(LocalDate.now(Clock.fixed(istMidnightWhileUtcStillOct2, IST))).isEqualTo(OCT_3);
        assertThat(before.getFreeUsedToday()).isEqualTo(5);
        assertThat(atIstMidnight.getFreeUsedToday()).isEqualTo(0);
        assertThat(atIstMidnight.getFreeRemainingToday()).isEqualTo(5);
        assertThat(atUtcMidnight.getFreeUsedToday()).isEqualTo(0);
        assertThat(atUtcMidnight.getFreeRemainingToday()).isEqualTo(5);
        assertThat(oct2.getFreeUsed()).isEqualTo(5);
        verify(dailyUsageRepository, never())
                .findOneByUserIdAndUsageDateAndChannel(
                        eq(userId), eq(LocalDate.of(2026, 10, 3).plusDays(1)), any());
    }

    @Test
    void webAndAndroidQuotasStayOnSeparateRows() {
        config.setAndroidBillingMode("CREDITS");
        InquiryDailyUsageEntity web = usage(OCT_2, InquiryClientChannel.WEB, 5);
        InquiryDailyUsageEntity android = usage(OCT_2, InquiryClientChannel.ANDROID, 1);
        when(dailyUsageRepository.findOneByUserIdAndUsageDateAndChannel(
                        userId, OCT_2, InquiryClientChannel.WEB))
                .thenReturn(Optional.of(web));
        when(dailyUsageRepository.findOneByUserIdAndUsageDateAndChannel(
                        userId, OCT_2, InquiryClientChannel.ANDROID))
                .thenReturn(Optional.of(android));
        when(walletService.getBalance(userId)).thenReturn(0);

        InquiryAccessService service = serviceAt(ist(2026, 10, 2, 10, 0, 0));
        var webQuota = service.getQuota(userId, InquiryClientChannel.WEB);
        var androidQuota = service.getQuota(userId, InquiryClientChannel.ANDROID);

        assertThat(webQuota.getFreeUsedToday()).isEqualTo(5);
        assertThat(webQuota.getFreeRemainingToday()).isEqualTo(0);
        assertThat(androidQuota.getFreeUsedToday()).isEqualTo(1);
        assertThat(androidQuota.getFreeRemainingToday()).isEqualTo(4);
        assertThat(androidQuota.getDailyFreeLimit()).isEqualTo(5);
        assertThat(web.getFreeUsed()).isEqualTo(5);
        assertThat(android.getFreeUsed()).isEqualTo(1);
    }

    @Test
    void paidCredits_areNotResetWhenTheIstDayChanges() {
        InquiryDailyUsageEntity yesterday = usage(OCT_2, InquiryClientChannel.WEB, 5);
        when(walletService.getBalance(userId)).thenReturn(4);

        var oct2 = serviceAt(ist(2026, 10, 2, 23, 59, 59)).getQuota(userId, InquiryClientChannel.WEB);
        var oct3 = serviceAt(ist(2026, 10, 3, 0, 0, 0)).getQuota(userId, InquiryClientChannel.WEB);

        assertThat(oct2.getAvailableCredits()).isEqualTo(4);
        assertThat(oct3.getAvailableCredits()).isEqualTo(4);
        assertThat(oct3.getFreeUsedToday()).isEqualTo(0);
        assertThat(yesterday.getFreeUsed()).isEqualTo(5);
        verify(walletService, never()).consumeUsage(any(), any());
    }

    @Test
    void androidHourlyCap_staysARollingHour_andDoesNotUseTheIstCalendarDay() {
        when(spaceEnquiryRepository.countByRequesterUserIdAndRequestedAtGreaterThanEqual(eq(userId), any()))
                .thenReturn(3L);

        InquiryAccessGrant grant = serviceAt(ist(2026, 10, 3, 0, 0, 0))
                .authorizeNewEnquiry(userId, InquiryClientChannel.ANDROID);

        assertThat(grant).isEqualTo(InquiryAccessGrant.ANDROID_FREE);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(spaceEnquiryRepository)
                .countByRequesterUserIdAndRequestedAtGreaterThanEqual(eq(userId), cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(LocalDateTime.of(2026, 10, 2, 23, 0, 0));
        assertThat(cutoff.getValue()).isNotEqualTo(OCT_3.atStartOfDay());

        when(spaceEnquiryRepository.countByRequesterUserIdAndRequestedAtGreaterThanEqual(eq(userId), any()))
                .thenReturn(20L);
        assertThatThrownBy(() -> serviceAt(ist(2026, 10, 3, 0, 30, 0))
                        .authorizeNewEnquiry(userId, InquiryClientChannel.ANDROID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException error = (BusinessException) ex;
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(error.getErrorCode()).isEqualTo("RATE_LIMITED");
                });
        verify(dailyUsageRepository, never())
                .findByUserIdAndUsageDateAndChannel(any(), any(), eq(InquiryClientChannel.ANDROID));
    }

    @Test
    void dailyLimit_comesFromAdminConfig_notAHardCodedFive() {
        config.setWebFreeDailyLimit(8);
        InquiryDailyUsageEntity today = usage(OCT_2, InquiryClientChannel.WEB, 2);
        when(dailyUsageRepository.findOneByUserIdAndUsageDateAndChannel(
                        userId, OCT_2, InquiryClientChannel.WEB))
                .thenReturn(Optional.of(today));
        when(walletService.getBalance(userId)).thenReturn(0);

        var quota = serviceAt(ist(2026, 10, 2, 10, 0, 0)).getQuota(userId, InquiryClientChannel.WEB);

        assertThat(quota.getDailyFreeLimit()).isEqualTo(8);
        assertThat(quota.getFreeUsedToday()).isEqualTo(2);
        assertThat(quota.getFreeRemainingToday()).isEqualTo(6);
    }

    private InquiryAccessService serviceAt(ZonedDateTime istDateTime) {
        return serviceAt(istDateTime.toInstant());
    }

    private InquiryAccessService serviceAt(Instant instant) {
        return new InquiryAccessService(
                dailyUsageRepository,
                walletService,
                spaceEnquiryRepository,
                paymentConfigService,
                Clock.fixed(instant, IST));
    }

    private static ZonedDateTime ist(int year, int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, IST);
    }

    private InquiryDailyUsageEntity usage(LocalDate date, InquiryClientChannel channel, int freeUsed) {
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
