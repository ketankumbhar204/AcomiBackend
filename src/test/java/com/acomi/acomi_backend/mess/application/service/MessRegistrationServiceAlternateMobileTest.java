package com.acomi.acomi_backend.mess.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.auth.application.service.OtpService;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.mess.api.dto.request.AdminCreateMessRegistrationRequest;
import com.acomi.acomi_backend.mess.api.dto.request.CreateMessRegistrationRequest;
import com.acomi.acomi_backend.mess.application.mapper.MessRegistrationMapper;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessRegistrationServiceAlternateMobileTest {

    @Mock
    private MessRegistrationRepository messRegistrationRepository;

    @Mock
    private OtpService otpService;

    private MessRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new MessRegistrationService(messRegistrationRepository, otpService);
    }

    @Test
    void publicRegister_persistsPrimaryOnly() {
        CreateMessRegistrationRequest request = publicRequest("Sunrise Mess", "9876543210");

        when(messRegistrationRepository.findUnclaimedAdminLeads("9876543210", "Sunrise Mess"))
                .thenReturn(Collections.emptyList());
        when(messRegistrationRepository.existsLikelyDuplicate("9876543210", "411001", "Sunrise Mess"))
                .thenReturn(false);
        when(messRegistrationRepository.nextReferenceNumber()).thenReturn(1L);
        when(messRegistrationRepository.save(any(MessRegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registerPublic(request, "127.0.0.1");

        ArgumentCaptor<MessRegistrationEntity> captor = ArgumentCaptor.forClass(MessRegistrationEntity.class);
        verify(messRegistrationRepository).save(captor.capture());
        assertThat(captor.getValue().getMobileNumber()).isEqualTo("9876543210");
        assertThat(captor.getValue().getAlternateMobileNumber()).isNull();
    }

    @Test
    void publicRegister_persistsOptionalAlternateMobile() {
        CreateMessRegistrationRequest request = publicRequest("Sunrise Mess", "9876543210");
        request.setAlternateMobileNumber("9123456780");

        when(messRegistrationRepository.findUnclaimedAdminLeads("9876543210", "Sunrise Mess"))
                .thenReturn(Collections.emptyList());
        when(messRegistrationRepository.existsLikelyDuplicate("9876543210", "411001", "Sunrise Mess"))
                .thenReturn(false);
        when(messRegistrationRepository.nextReferenceNumber()).thenReturn(2L);
        when(messRegistrationRepository.save(any(MessRegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registerPublic(request, "127.0.0.1");

        ArgumentCaptor<MessRegistrationEntity> captor = ArgumentCaptor.forClass(MessRegistrationEntity.class);
        verify(messRegistrationRepository).save(captor.capture());
        assertThat(captor.getValue().getAlternateMobileNumber()).isEqualTo("9123456780");
    }

    @Test
    void publicRegister_rejectsInvalidAlternateMobile() {
        CreateMessRegistrationRequest request = publicRequest("Sunrise Mess", "9876543210");
        request.setAlternateMobileNumber("12345");

        when(messRegistrationRepository.findUnclaimedAdminLeads("9876543210", "Sunrise Mess"))
                .thenReturn(Collections.emptyList());
        when(messRegistrationRepository.existsLikelyDuplicate("9876543210", "411001", "Sunrise Mess"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.registerPublic(request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Alternate mobile number");
        verify(messRegistrationRepository, never()).save(any());
    }

    @Test
    void publicRegister_rejectsAlternateEqualToPrimary() {
        CreateMessRegistrationRequest request = publicRequest("Sunrise Mess", "9876543210");
        request.setAlternateMobileNumber("9876543210");

        when(messRegistrationRepository.findUnclaimedAdminLeads("9876543210", "Sunrise Mess"))
                .thenReturn(Collections.emptyList());
        when(messRegistrationRepository.existsLikelyDuplicate("9876543210", "411001", "Sunrise Mess"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.registerPublic(request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(messRegistrationRepository, never()).save(any());
    }

    @Test
    void mapper_detailLoadsWhenAlternateIsMissing() {
        MessRegistrationEntity existing = adminLead("Sunrise Mess", "9876543210");
        var detail = MessRegistrationMapper.toDetail(existing);
        assertThat(detail.getMobileNumber()).isEqualTo("9876543210");
        assertThat(detail.getAlternateMobileNumber()).isNull();
    }

    @Test
    void adminRegister_persistsAlternateMobile() {
        AdminCreateMessRegistrationRequest request = new AdminCreateMessRegistrationRequest();
        request.setMobileNumber("9876543210");
        request.setAlternateMobileNumber("9123456780");

        when(messRegistrationRepository.existsLikelyDuplicate("9876543210", "110001", "Untitled mess"))
                .thenReturn(false);
        when(messRegistrationRepository.nextReferenceNumber()).thenReturn(3L);
        when(messRegistrationRepository.save(any(MessRegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registerAdmin(request);

        ArgumentCaptor<MessRegistrationEntity> captor = ArgumentCaptor.forClass(MessRegistrationEntity.class);
        verify(messRegistrationRepository).save(captor.capture());
        assertThat(captor.getValue().getAlternateMobileNumber()).isEqualTo("9123456780");
    }

    private static CreateMessRegistrationRequest publicRequest(String name, String mobile) {
        CreateMessRegistrationRequest request = new CreateMessRegistrationRequest();
        request.setMessName(name);
        request.setOwnerName("Ketan");
        request.setMobileNumber(mobile);
        request.setVerificationToken("token");
        request.setAddressLine("Main Road");
        request.setCity("Pune");
        request.setState("Maharashtra");
        request.setPincode("411001");
        request.setMonthlyPrice(new BigDecimal("3000"));
        request.setMealPrice(new BigDecimal("80"));
        return request;
    }

    private static MessRegistrationEntity adminLead(String name, String mobile) {
        MessRegistrationEntity entity = MessRegistrationEntity.builder()
                .reference("MR-2026-000001")
                .messName(name)
                .ownerName("Admin Entry")
                .mobileNumber(mobile)
                .addressLine("Old Address")
                .city("Pune")
                .state("Maharashtra")
                .pincode("411001")
                .monthlyPrice(new BigDecimal("3000"))
                .mealPrice(new BigDecimal("80"))
                .status(MessRegistrationStatus.PENDING)
                .source(MessRegistrationSource.ADMIN)
                .build();
        entity.setId(UUID.randomUUID());
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }
}
