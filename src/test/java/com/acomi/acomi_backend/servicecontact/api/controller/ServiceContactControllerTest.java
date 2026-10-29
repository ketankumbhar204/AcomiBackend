package com.acomi.acomi_backend.servicecontact.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acomi.acomi_backend.common.web.GlobalExceptionHandler;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.config.security.UserPrincipal;
import com.acomi.acomi_backend.servicecontact.api.ServiceContactExceptionHandler;
import com.acomi.acomi_backend.servicecontact.api.dto.response.ServiceContactResponse;
import com.acomi.acomi_backend.servicecontact.application.service.ServiceContactService;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class ServiceContactControllerTest {

    @Mock
    private ServiceContactService serviceContactService;

    private MockMvc mockMvc;
    private UUID callerId;

    @BeforeEach
    void setUp() {
        callerId = UUID.randomUUID();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ServiceContactController controller = new ServiceContactController(serviceContactService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new ServiceContactExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blankNameIsRejected() throws Exception {
        authenticate();
        mockMvc.perform(post("/api/v1/service-contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessName":"  ","category":"ELECTRICAL","primaryPhone":"9876543210"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.businessName").exists());
    }

    @Test
    void missingAuthenticationIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/service-contacts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid authentication context"));
    }

    @Test
    void createReturnsSavedContact() throws Exception {
        authenticate();
        UUID contactId = UUID.randomUUID();
        when(serviceContactService.create(eq(callerId), any())).thenReturn(ServiceContactResponse.builder()
                .id(contactId)
                .businessName("Ramesh Electricals")
                .category("ELECTRICAL")
                .categoryLabel("Electrical")
                .primaryPhone("+919876543210")
                .active(true)
                .canEdit(true)
                .canArchive(true)
                .spaces(List.of())
                .build());

        mockMvc.perform(post("/api/v1/service-contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessName":"Ramesh Electricals","category":"ELECTRICAL","primaryPhone":"9876543210"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.primaryPhone").value("+919876543210"))
                .andExpect(jsonPath("$.data.businessName").value("Ramesh Electricals"));
    }

    @Test
    void listPassesFiltersToTheService() throws Exception {
        authenticate();
        when(serviceContactService.list(
                        eq(callerId), eq("ramesh"), eq("ELECTRICAL"), any(), eq("name"), eq(false), eq(0), eq(20)))
                .thenReturn(PagedResponse.<ServiceContactResponse>builder()
                        .content(List.of())
                        .page(0)
                        .size(20)
                        .totalElements(0)
                        .totalPages(0)
                        .first(true)
                        .last(true)
                        .build());

        mockMvc.perform(get("/api/v1/service-contacts")
                        .param("q", "ramesh")
                        .param("category", "ELECTRICAL")
                        .param("sort", "name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        verify(serviceContactService)
                .list(eq(callerId), eq("ramesh"), eq("ELECTRICAL"), isNull(), eq("name"), anyBoolean(), anyInt(), anyInt());
    }

    private void authenticate() {
        UserEntity user = new UserEntity();
        user.setId(callerId);
        user.setMobileNumber("9876543210");
        user.setFullName("Owner");
        user.setActive(true);
        user.setSystemRole(SystemRole.USER);
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }
}
