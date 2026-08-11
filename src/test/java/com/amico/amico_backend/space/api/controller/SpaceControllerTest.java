package com.amico.amico_backend.space.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amico.amico_backend.space.api.dto.response.SpaceResponse;
import com.amico.amico_backend.space.application.service.SpaceService;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SpaceControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private SpaceService spaceService;

    @InjectMocks
    private SpaceController spaceController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(spaceController).build();
    }

    @Test
    void createSpace_withRentalType_returnsCreatedSpace() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();

        SpaceResponse response = SpaceResponse.builder()
                .id(spaceId)
                .name("Sunrise Apartments")
                .type(SpaceType.RENTAL)
                .ownerId(ownerId)
                .ownerName("Ketan")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(spaceService.createSpace(any())).thenReturn(response);

        String requestBody = objectMapper.writeValueAsString(
                new CreateSpacePayload("Sunrise Apartments", "RENTAL", ownerId));

        mockMvc.perform(post("/api/v1/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Sunrise Apartments"))
                .andExpect(jsonPath("$.data.type").value("RENTAL"));
    }

    private record CreateSpacePayload(String name, String type, UUID ownerId) {}
}
