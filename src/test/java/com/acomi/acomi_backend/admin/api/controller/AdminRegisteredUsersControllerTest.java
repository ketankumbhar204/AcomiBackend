package com.acomi.acomi_backend.admin.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acomi.acomi_backend.admin.application.service.AdminRegisteredUsersService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
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
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void list_returnsOk() throws Exception {
        when(adminRegisteredUsersService.list(any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/registered-users")).andExpect(status().isOk());

        verify(adminRegisteredUsersService).list(any(Pageable.class));
    }
}
