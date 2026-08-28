package com.acomi.acomi_backend.admin.api.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acomi.acomi_backend.admin.application.service.AdminMessRegistrationService;
import com.acomi.acomi_backend.admin.application.service.AdminPropertyRegistrationService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminRegistrationDeleteControllerTest {

    @Mock
    private AdminPropertyRegistrationService adminPropertyRegistrationService;

    @Mock
    private AdminMessRegistrationService adminMessRegistrationService;

    @InjectMocks
    private AdminPropertyRegistrationController propertyController;

    @InjectMocks
    private AdminMessRegistrationController messController;

    private MockMvc propertyMockMvc;
    private MockMvc messMockMvc;

    @BeforeEach
    void setUp() {
        propertyMockMvc = MockMvcBuilders.standaloneSetup(propertyController).build();
        messMockMvc = MockMvcBuilders.standaloneSetup(messController).build();
    }

    @Test
    void deletePropertyRegistration_returnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        propertyMockMvc.perform(delete("/api/v1/admin/property-registrations/{id}", id))
                .andExpect(status().isNoContent());

        verify(adminPropertyRegistrationService).delete(id);
    }

    @Test
    void deleteMessRegistration_returnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        messMockMvc.perform(delete("/api/v1/admin/mess-registrations/{id}", id))
                .andExpect(status().isNoContent());

        verify(adminMessRegistrationService).delete(id);
    }
}
