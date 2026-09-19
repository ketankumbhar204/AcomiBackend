package com.acomi.acomi_backend.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.address.infrastructure.persistence.repository.SavedAddressRepository;
import com.acomi.acomi_backend.admin.api.dto.response.AdminDashboardSummaryResponse;
import com.acomi.acomi_backend.admin.domain.model.AdminDashboardTrendMetric;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository.SpaceEnquiryRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private PropertyRegistrationRepository propertyRegistrationRepository;

    @Mock
    private MessRegistrationRepository messRegistrationRepository;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private AdminRegisteredUsersService adminRegisteredUsersService;

    @Mock
    private SpaceEnquiryRepository spaceEnquiryRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private SavedAddressRepository savedAddressRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository
                    .InquiryCreditPurchaseRequestRepository
            inquiryCreditPurchaseRequestRepository;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(
                propertyRegistrationRepository,
                messRegistrationRepository,
                spaceRepository,
                adminRegisteredUsersService,
                spaceEnquiryRepository,
                spaceMembershipRepository,
                savedAddressRepository,
                userRepository,
                inquiryCreditPurchaseRequestRepository);
    }

    @Test
    void getSummary_keepsExistingMetricsAndAddsPrimaryDashboardCounts() {
        List<PropertyRegistrationStatus> closedProperty =
                List.of(PropertyRegistrationStatus.CONVERTED, PropertyRegistrationStatus.REJECTED);
        List<MessRegistrationStatus> closedMess =
                List.of(MessRegistrationStatus.CONVERTED, MessRegistrationStatus.REJECTED);
        // Open leads only (excludes converted/rejected leftover rows).
        when(propertyRegistrationRepository.countByStatusNotIn(closedProperty)).thenReturn(8L);
        when(messRegistrationRepository.countByStatusNotIn(closedMess)).thenReturn(1L);
        when(propertyRegistrationRepository.countBySource(PropertyRegistrationSource.ADMIN)).thenReturn(0L);
        when(messRegistrationRepository.countBySource(MessRegistrationSource.ADMIN)).thenReturn(0L);
        when(propertyRegistrationRepository.countBySource(PropertyRegistrationSource.PUBLIC_WEBSITE)).thenReturn(12L);
        when(messRegistrationRepository.countBySource(MessRegistrationSource.PUBLIC_WEBSITE)).thenReturn(1L);
        when(propertyRegistrationRepository.countByClaimedAtIsNullAndSource(PropertyRegistrationSource.ADMIN))
                .thenReturn(0L);
        when(messRegistrationRepository.countByClaimedAtIsNullAndSource(MessRegistrationSource.ADMIN)).thenReturn(0L);
        when(propertyRegistrationRepository.countByClaimedAtIsNotNull()).thenReturn(0L);
        when(messRegistrationRepository.countByClaimedAtIsNotNull()).thenReturn(0L);
        when(spaceRepository.countByTypeAndIsActiveTrue(SpaceType.PG)).thenReturn(2L);
        when(spaceRepository.countByTypeAndIsActiveTrue(SpaceType.HOSTEL)).thenReturn(1L);
        when(spaceRepository.countByTypeAndIsActiveTrue(SpaceType.CO_LIVING)).thenReturn(1L);
        when(spaceRepository.countByTypeAndIsActiveTrue(SpaceType.RENTAL)).thenReturn(0L);
        when(spaceRepository.countByTypeAndIsActiveTrue(SpaceType.MESS)).thenReturn(2L);
        when(adminRegisteredUsersService.countRegisteredUsers()).thenReturn(12L);
        when(spaceEnquiryRepository.count()).thenReturn(18L);
        when(spaceMembershipRepository.countDistinctActiveOwners()).thenReturn(6L);
        when(savedAddressRepository.countByIsActiveTrue()).thenReturn(4L);

        AdminDashboardSummaryResponse summary = service.getSummary();

        // Total = open leads + active spaces (8+4 properties, 1+2 mess).
        assertThat(summary.getPropertyRegistrationCount()).isEqualTo(12L);
        assertThat(summary.getMessRegistrationCount()).isEqualTo(3L);
        assertThat(summary.getWebsitePropertyLeads()).isEqualTo(12L);
        assertThat(summary.getWebsiteMessLeads()).isEqualTo(1L);
        assertThat(summary.getAdminPropertyLeads()).isZero();
        assertThat(summary.getAdminMessLeads()).isZero();
        assertThat(summary.getActivePropertySpaces()).isEqualTo(4L);
        assertThat(summary.getActiveMessSpaces()).isEqualTo(2L);
        assertThat(summary.getRegisteredUsersCount()).isEqualTo(12L);
        assertThat(summary.getTotalEnquiriesCount()).isEqualTo(18L);
        assertThat(summary.getOwnersCount()).isEqualTo(6L);
        assertThat(summary.getSavedAddressesCount()).isEqualTo(4L);
        assertThat(summary.getRegisteredUsersDeltaPercent()).isNull();
    }

    @Test
    void dashboardTrend_fillsMissingDaysAndUsesSelectedMetric() {
        LocalDate from = LocalDate.of(2026, 9, 12);
        LocalDate to = LocalDate.of(2026, 9, 14);
        LocalDateTime fromAt = from.atStartOfDay();
        LocalDateTime toAt = to.plusDays(1).atStartOfDay();

        when(spaceEnquiryRepository.countDailyRequestedBetween(fromAt, toAt))
                .thenReturn(List.<Object[]>of(new Object[] {Date.valueOf(from), 2L}));
        when(userRepository.countDailyVerifiedRegistrationsBetween(fromAt, toAt))
                .thenReturn(List.<Object[]>of(new Object[] {Date.valueOf(from.plusDays(1)), 5L}));

        var enquiries = service.dashboardTrend(AdminDashboardTrendMetric.ENQUIRIES, from, to);
        assertThat(enquiries.getMetric()).isEqualTo("ENQUIRIES");
        assertThat(enquiries.getTotal()).isEqualTo(2L);
        assertThat(enquiries.getPoints()).hasSize(3);
        assertThat(enquiries.getPoints().get(0).getCount()).isEqualTo(2L);
        assertThat(enquiries.getPoints().get(1).getCount()).isZero();
        assertThat(enquiries.getPoints().get(2).getCount()).isZero();

        var users = service.dashboardTrend(AdminDashboardTrendMetric.REGISTERED_USERS, from, to);
        assertThat(users.getMetric()).isEqualTo("REGISTERED_USERS");
        assertThat(users.getTotal()).isEqualTo(5L);
        assertThat(users.getPoints().get(1).getCount()).isEqualTo(5L);
    }
}
