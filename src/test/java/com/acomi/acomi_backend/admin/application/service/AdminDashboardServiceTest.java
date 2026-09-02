package com.acomi.acomi_backend.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.admin.api.dto.response.AdminDashboardSummaryResponse;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
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

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(
                propertyRegistrationRepository,
                messRegistrationRepository,
                spaceRepository,
                adminRegisteredUsersService);
    }

    @Test
    void getSummary_keepsExistingLeadAndSpaceMetricsAndAddsRegisteredUsersCount() {
        when(propertyRegistrationRepository.count()).thenReturn(12L);
        when(messRegistrationRepository.count()).thenReturn(1L);
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

        AdminDashboardSummaryResponse summary = service.getSummary();

        assertThat(summary.getPropertyRegistrationCount()).isEqualTo(12L);
        assertThat(summary.getMessRegistrationCount()).isEqualTo(1L);
        assertThat(summary.getWebsitePropertyLeads()).isEqualTo(12L);
        assertThat(summary.getWebsiteMessLeads()).isEqualTo(1L);
        assertThat(summary.getAdminPropertyLeads()).isZero();
        assertThat(summary.getAdminMessLeads()).isZero();
        assertThat(summary.getActivePropertySpaces()).isEqualTo(4L);
        assertThat(summary.getActiveMessSpaces()).isEqualTo(2L);
        assertThat(summary.getRegisteredUsersCount()).isEqualTo(12L);
    }
}
