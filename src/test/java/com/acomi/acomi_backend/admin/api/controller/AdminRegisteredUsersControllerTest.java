package com.acomi.acomi_backend.admin.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acomi.acomi_backend.admin.api.dto.request.AdminCreateRegisteredUserRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminRegisteredUserResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminRegisteredUsersSummaryResponse;
import com.acomi.acomi_backend.admin.application.service.AdminRegisteredUsersService;
import com.acomi.acomi_backend.common.web.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminRegisteredUsersControllerTest {

    @Mock
    private AdminRegisteredUsersService adminRegisteredUsersService;

    @InjectMocks
    private AdminRegisteredUsersController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(converter)
                .build();
    }

    @Test
    void list_returnsOk() throws Exception {
        when(adminRegisteredUsersService.list(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/registered-users")).andExpect(status().isOk());

        verify(adminRegisteredUsersService)
                .list(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        any(Pageable.class));
    }

    @Test
    void summary_returnsOk() throws Exception {
        when(adminRegisteredUsersService.summary())
                .thenReturn(AdminRegisteredUsersSummaryResponse.builder()
                        .totalUsers(1)
                        .verifiedUsers(1)
                        .newUsersLast30Days(0)
                        .withSpaceAssociation(0)
                        .build());

        mockMvc.perform(get("/api/v1/admin/registered-users/summary")).andExpect(status().isOk());

        verify(adminRegisteredUsersService).summary();
    }

    @Test
    void create_returnsCreatedWithoutPassword() throws Exception {
        UUID id = UUID.randomUUID();
        when(adminRegisteredUsersService.createTestUser(any(AdminCreateRegisteredUserRequest.class)))
                .thenReturn(AdminRegisteredUserResponse.builder()
                        .id(id)
                        .fullName("QA Tester")
                        .mobileNumber("9876500001")
                        .email("qa@example.com")
                        .mobileVerified(true)
                        .selectedRole("NOT_SELECTED")
                        .onboardingStatus("INCOMPLETE")
                        .profileCompleted(false)
                        .systemRole("USER")
                        .active(true)
                        .testUser(true)
                        .spaces(java.util.List.of())
                        .build());

        mockMvc.perform(post("/api/v1/admin/registered-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "fullName": "QA Tester",
                                  "mobileNumber": "9876500001",
                                  "email": "qa@example.com",
                                  "password": "Secret12",
                                  "confirmPassword": "Secret12",
                                  "spaceRole": "OWNER",
                                  "spaceType": "PG"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.testUser").value(true))
                .andExpect(jsonPath("$.data.systemRole").value("USER"))
                .andExpect(jsonPath("$.data.mobileNumber").value("9876500001"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        verify(adminRegisteredUsersService).createTestUser(any(AdminCreateRegisteredUserRequest.class));
    }

    @Test
    void create_validationError_forShortPassword() throws Exception {
        mockMvc.perform(post("/api/v1/admin/registered-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "fullName": "QA Tester",
                                  "mobileNumber": "9876500001",
                                  "password": "short",
                                  "confirmPassword": "short",
                                  "spaceRole": "OWNER",
                                  "spaceType": "PG"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
