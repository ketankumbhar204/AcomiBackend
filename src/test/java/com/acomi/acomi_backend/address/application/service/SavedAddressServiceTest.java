package com.acomi.acomi_backend.address.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.address.api.dto.request.SavedAddressRequest;
import com.acomi.acomi_backend.address.api.dto.response.SavedAddressResponse;
import com.acomi.acomi_backend.address.infrastructure.persistence.entity.SavedAddressEntity;
import com.acomi.acomi_backend.address.infrastructure.persistence.repository.SavedAddressRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.config.security.UserPrincipal;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class SavedAddressServiceTest {

    private static final UUID ADMIN_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_ADMIN_ID = UUID.fromString("a0000000-0000-4000-8000-000000000002");

    @Mock
    private SavedAddressRepository savedAddressRepository;

    private SavedAddressService service;

    @BeforeEach
    void setUp() {
        service = new SavedAddressService(savedAddressRepository);
        authenticate(adminPrincipal());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_persistsNormalizedAddressForCurrentAdmin() {
        when(savedAddressRepository.findFirstByCreatedByUserIdAndFingerprintAndIsActiveTrue(eq(ADMIN_ID), any()))
                .thenReturn(Optional.empty());
        when(savedAddressRepository.findFirstByCreatedByUserIdAndFingerprintOrderByUpdatedAtDesc(eq(ADMIN_ID), any()))
                .thenReturn(Optional.empty());
        when(savedAddressRepository.saveAndFlush(any(SavedAddressEntity.class))).thenAnswer(invocation -> {
            SavedAddressEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(LocalDateTime.now());
            return entity;
        });

        SavedAddressResponse response = service.create(request(
                "  Hinjewadi   Phase 1  ", "Pune", "Maharashtra", "411057", "https://maps.google.com/hinjewadi"));

        ArgumentCaptor<SavedAddressEntity> captor = ArgumentCaptor.forClass(SavedAddressEntity.class);
        verify(savedAddressRepository).saveAndFlush(captor.capture());
        SavedAddressEntity saved = captor.getValue();
        assertThat(saved.getCreatedByUserId()).isEqualTo(ADMIN_ID);
        assertThat(saved.getAddressLine()).isEqualTo("Hinjewadi Phase 1");
        assertThat(saved.getCity()).isEqualTo("Pune");
        assertThat(saved.getPincode()).isEqualTo("411057");
        assertThat(saved.getUsageCount()).isZero();
        assertThat(saved.getLastUsedAt()).isNull();
        assertThat(response.getAddressLine()).isEqualTo("Hinjewadi Phase 1");
    }

    @Test
    void create_reusesEquivalentAddressInsteadOfInsertingDuplicate() {
        SavedAddressEntity existing = address("Hinjewadi Phase 1", "Pune", "411057", LocalDateTime.now().minusDays(1));
        when(savedAddressRepository.findFirstByCreatedByUserIdAndFingerprintAndIsActiveTrue(eq(ADMIN_ID), any()))
                .thenReturn(Optional.of(existing));
        when(savedAddressRepository.save(existing)).thenReturn(existing);

        SavedAddressResponse response = service.create(request(
                "hinjewadi phase 1", "PUNE", "Maharashtra", "411057", "https://maps.google.com/hinjewadi"));

        verify(savedAddressRepository, never()).saveAndFlush(any());
        assertThat(response.getId()).isEqualTo(existing.getId());
        assertThat(existing.getUsageCount()).isEqualTo(2);
        assertThat(existing.getLastUsedAt()).isBefore(LocalDateTime.now().minusHours(1));
    }

    @Test
    void rememberFromLead_incrementsUsageAndSetsLastUsedAt() {
        SavedAddressEntity existing = address("Hinjewadi Phase 1", "Pune", "411057", LocalDateTime.now().minusDays(3));
        int previousUsage = existing.getUsageCount();
        when(savedAddressRepository.findFirstByCreatedByUserIdAndFingerprintAndIsActiveTrue(eq(ADMIN_ID), any()))
                .thenReturn(Optional.of(existing));
        when(savedAddressRepository.save(existing)).thenReturn(existing);

        service.rememberFromLead(
                "Hinjewadi Phase 1", "Pune", "Maharashtra", "411057", "https://maps.google.com/hinjewadi");

        assertThat(existing.getUsageCount()).isEqualTo(previousUsage + 1);
        assertThat(existing.getLastUsedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void rememberFromLead_skipsPlaceholderAdminLeads() {
        service.rememberFromLead("—", "—", "—", "110001", null);
        verify(savedAddressRepository, never()).findFirstByCreatedByUserIdAndFingerprintAndIsActiveTrue(any(), any());
        verify(savedAddressRepository, never()).save(any());
    }

    @Test
    void rememberFromLead_keepsPhase1AndPhase2Separate() {
        when(savedAddressRepository.findFirstByCreatedByUserIdAndFingerprintAndIsActiveTrue(eq(ADMIN_ID), any()))
                .thenReturn(Optional.empty());
        when(savedAddressRepository.findFirstByCreatedByUserIdAndFingerprintOrderByUpdatedAtDesc(eq(ADMIN_ID), any()))
                .thenReturn(Optional.empty());
        when(savedAddressRepository.saveAndFlush(any(SavedAddressEntity.class))).thenAnswer(invocation -> {
            SavedAddressEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        service.rememberFromLead("Hinjewadi Phase 1", "Pune", "Maharashtra", "411057", null);
        service.rememberFromLead("Hinjewadi Phase 2", "Pune", "Maharashtra", "411057", null);

        ArgumentCaptor<SavedAddressEntity> captor = ArgumentCaptor.forClass(SavedAddressEntity.class);
        verify(savedAddressRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(SavedAddressEntity::getFingerprint)
                .doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @CsvSource({
        "'  HINJEWADI  ', hinjewadi",
        "Pune, pune",
        "Maharashtra, maharashtra",
        "411057, 411057",
        "maps.google, maps.google"
    })
    void list_searchesCaseInsensitivelyAndPassesSanitizedTerm(String raw, String expected) {
        assertSearchTerm(raw, expected);
    }

    @Test
    void list_stripsLikeWildcardsFromSearch() {
        assertSearchTerm("hinj%ewadi_phase\\1", "hinjewadiphase1");
    }

    @Test
    void getById_notFoundWhenMissing() {
        when(savedAddressRepository.findByIdAndCreatedByUserIdAndIsActiveTrue(any(), eq(ADMIN_ID)))
                .thenReturn(Optional.empty());

        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_notFoundWhenDeactivated() {
        SavedAddressEntity inactive = address("Wakad", "Pune", "411033", LocalDateTime.now());
        inactive.setActive(false);
        when(savedAddressRepository.findByIdAndCreatedByUserIdAndIsActiveTrue(inactive.getId(), ADMIN_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(inactive.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void fingerprintsKeepDifferentAddressLinesSeparate() {
        var phase1 = SavedAddressService.AddressFields.from(
                "Hinjewadi Phase 1", "Pune", "Maharashtra", "411057", null);
        var phase2 = SavedAddressService.AddressFields.from(
                "Hinjewadi Phase 2", "Pune", "Maharashtra", "411057", null);
        assertThat(SavedAddressService.fingerprint(phase1)).isNotEqualTo(SavedAddressService.fingerprint(phase2));
    }

    @Test
    void create_reactivatesMatchingInactiveAddress() {
        SavedAddressEntity inactive = address("Wakad", "Pune", "411033", LocalDateTime.now().minusDays(2));
        inactive.setActive(false);
        when(savedAddressRepository.findFirstByCreatedByUserIdAndFingerprintAndIsActiveTrue(eq(ADMIN_ID), any()))
                .thenReturn(Optional.empty());
        when(savedAddressRepository.findFirstByCreatedByUserIdAndFingerprintOrderByUpdatedAtDesc(eq(ADMIN_ID), any()))
                .thenReturn(Optional.of(inactive));
        when(savedAddressRepository.save(inactive)).thenReturn(inactive);

        SavedAddressResponse response = service.create(request("Wakad", "Pune", "Maharashtra", "411033", null));

        assertThat(inactive.isActive()).isTrue();
        assertThat(response.getId()).isEqualTo(inactive.getId());
        verify(savedAddressRepository, never()).saveAndFlush(any());
    }

    @Test
    void getById_rejectsAddressOwnedByAnotherAdmin() {
        when(savedAddressRepository.findByIdAndCreatedByUserIdAndIsActiveTrue(any(), eq(ADMIN_ID)))
                .thenReturn(Optional.empty());

        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivate_softDeletesOwnedAddress() {
        SavedAddressEntity existing = address("Wakad", "Pune", "411033", LocalDateTime.now());
        when(savedAddressRepository.findByIdAndCreatedByUserIdAndIsActiveTrue(existing.getId(), ADMIN_ID))
                .thenReturn(Optional.of(existing));
        when(savedAddressRepository.save(existing)).thenReturn(existing);

        service.deactivate(existing.getId());

        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void regularUserCannotManageSavedAddresses() {
        authenticate(userPrincipal());
        assertThatThrownBy(() -> service.list(null, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void fingerprintsIgnoreWhitespaceAndCase() {
        var left = SavedAddressService.AddressFields.from(
                "Hinjewadi   Phase 1", "Pune", "Maharashtra", "411057", "https://maps.google.com/a");
        var right = SavedAddressService.AddressFields.from(
                "hinjewadi phase 1", "pune", "maharashtra", "411057", "https://maps.google.com/a");
        assertThat(SavedAddressService.fingerprint(left)).isEqualTo(SavedAddressService.fingerprint(right));
    }

    private void assertSearchTerm(String raw, String expected) {
        SavedAddressEntity match = address("Hinjewadi Phase 1", "Pune", "411057", LocalDateTime.now());
        when(savedAddressRepository.searchActiveByOwner(eq(ADMIN_ID), eq(expected), any()))
                .thenReturn(new PageImpl<>(List.of(match), PageRequest.of(0, 10), 1));

        Page<SavedAddressResponse> page = service.list(raw, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        verify(savedAddressRepository).searchActiveByOwner(eq(ADMIN_ID), eq(expected), any());
    }

    private static SavedAddressRequest request(
            String address, String city, String state, String pincode, String mapUrl) {
        SavedAddressRequest request = new SavedAddressRequest();
        request.setAddressLine(address);
        request.setCity(city);
        request.setState(state);
        request.setPincode(pincode);
        request.setMapUrl(mapUrl);
        return request;
    }

    private static SavedAddressEntity address(String line, String city, String pincode, LocalDateTime lastUsedAt) {
        SavedAddressEntity entity = SavedAddressEntity.builder()
                .createdByUserId(ADMIN_ID)
                .addressLine(line)
                .city(city)
                .state("Maharashtra")
                .pincode(pincode)
                .mapUrl("https://maps.google.com/hinjewadi")
                .fingerprint("abc")
                .usageCount(2)
                .lastUsedAt(lastUsedAt)
                .isActive(true)
                .build();
        entity.setId(UUID.randomUUID());
        entity.setCreatedAt(LocalDateTime.now().minusDays(5));
        return entity;
    }

    private static void authenticate(UserPrincipal principal) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static UserPrincipal adminPrincipal() {
        UserEntity user = UserEntity.builder()
                .fullName("ACOMI Admin")
                .mobileNumber("9000000001")
                .systemRole(SystemRole.ADMIN)
                .isActive(true)
                .build();
        user.setId(ADMIN_ID);
        return new UserPrincipal(user);
    }

    private static UserPrincipal userPrincipal() {
        UserEntity user = UserEntity.builder()
                .fullName("Owner")
                .mobileNumber("9876543210")
                .systemRole(SystemRole.USER)
                .isActive(true)
                .build();
        user.setId(OTHER_ADMIN_ID);
        return new UserPrincipal(user);
    }
}
