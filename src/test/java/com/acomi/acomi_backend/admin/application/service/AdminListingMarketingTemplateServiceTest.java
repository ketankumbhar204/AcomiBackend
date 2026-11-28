package com.acomi.acomi_backend.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.admin.api.dto.request.AdminUpsertListingMarketingTemplateRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminListingMarketingTemplateResponse;
import com.acomi.acomi_backend.admin.application.support.ListingMarketingPlaceholders;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingTemplateApplicability;
import com.acomi.acomi_backend.admin.infrastructure.persistence.entity.AdminListingMarketingTemplateEntity;
import com.acomi.acomi_backend.admin.infrastructure.persistence.repository.AdminListingMarketingTemplateRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AdminListingMarketingTemplateServiceTest {

    @Mock
    private AdminListingMarketingTemplateRepository repository;

    private AdminListingMarketingTemplateService service;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        service = new AdminListingMarketingTemplateService(repository);
        actorId = UUID.randomUUID();
        UserEntity admin = UserEntity.builder()
                .fullName("Admin")
                .mobileNumber("9000000000")
                .systemRole(SystemRole.ADMIN)
                .isActive(true)
                .build();
        admin.setId(actorId);
        UserPrincipal principal = new UserPrincipal(admin);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRejectsUnsupportedPlaceholders() {
        AdminUpsertListingMarketingTemplateRequest request =
                new AdminUpsertListingMarketingTemplateRequest(
                        "Promo",
                        null,
                        "Hello {{ownerName}} {{unknownField}}",
                        ListingMarketingTemplateApplicability.PROPERTY,
                        true,
                        0);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unknownField");
    }

    @Test
    void createPersistsCustomTemplate() {
        when(repository.save(any())).thenAnswer(invocation -> {
            AdminListingMarketingTemplateEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        AdminUpsertListingMarketingTemplateRequest request =
                new AdminUpsertListingMarketingTemplateRequest(
                        "PG short",
                        "Short promo",
                        "Hi {{ownerName}}, promoting {{propertyName}} in {{location}}.",
                        ListingMarketingTemplateApplicability.PROPERTY,
                        true,
                        1);

        AdminListingMarketingTemplateResponse response = service.create(request);
        assertThat(response.name()).isEqualTo("PG short");
        assertThat(response.builtin()).isFalse();
        assertThat(response.applicability()).isEqualTo(ListingMarketingTemplateApplicability.PROPERTY);

        ArgumentCaptor<AdminListingMarketingTemplateEntity> captor =
                ArgumentCaptor.forClass(AdminListingMarketingTemplateEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(actorId);
    }

    @Test
    void duplicateCopiesBodyAndSuffixesName() {
        UUID id = UUID.randomUUID();
        AdminListingMarketingTemplateEntity source = AdminListingMarketingTemplateEntity.builder()
                .id(id)
                .name("Original")
                .description("Desc")
                .body("Hi {{ownerName}}")
                .applicability(ListingMarketingTemplateApplicability.BOTH)
                .active(true)
                .displayOrder(2)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(repository.findById(id)).thenReturn(Optional.of(source));
        when(repository.save(any())).thenAnswer(invocation -> {
            AdminListingMarketingTemplateEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        AdminListingMarketingTemplateResponse copy = service.duplicate(id);
        assertThat(copy.name()).isEqualTo("Original (copy)");
        assertThat(copy.body()).isEqualTo("Hi {{ownerName}}");
    }

    @Test
    void deleteRemovesCustomTemplate() {
        UUID id = UUID.randomUUID();
        AdminListingMarketingTemplateEntity entity = AdminListingMarketingTemplateEntity.builder()
                .id(id)
                .name("To delete")
                .body("Hi {{name}}")
                .applicability(ListingMarketingTemplateApplicability.MESS)
                .active(true)
                .displayOrder(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        service.delete(id);
        verify(repository).delete(entity);
    }

    @Test
    void listActiveOnlyFilters() {
        when(repository.findByActiveTrueOrderByDisplayOrderAscNameAsc()).thenReturn(List.of());
        assertThat(service.list(true)).isEmpty();
        verify(repository).findByActiveTrueOrderByDisplayOrderAscNameAsc();
    }

    @Test
    void placeholderAllowlistContainsCoreTokens() {
        assertThat(ListingMarketingPlaceholders.ALLOWED)
                .contains("ownerName", "propertyName", "messName", "location", "rent", "amenities");
        assertThat(ListingMarketingPlaceholders.unsupported("{{ownerName}} {{bad}}"))
                .containsExactly("bad");
    }

    @Test
    void appliesToRespectsBothPropertyAndMess() {
        assertThat(AdminListingMarketingTemplateService.appliesTo(
                        ListingMarketingTemplateApplicability.BOTH, "PROPERTY"))
                .isTrue();
        assertThat(AdminListingMarketingTemplateService.appliesTo(
                        ListingMarketingTemplateApplicability.PROPERTY, "MESS"))
                .isFalse();
        assertThat(AdminListingMarketingTemplateService.appliesTo(
                        ListingMarketingTemplateApplicability.MESS, "MESS"))
                .isTrue();
    }
}
