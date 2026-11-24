package com.acomi.acomi_backend.member.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.member.api.dto.response.MemberDirectoryAssociationResponse;
import com.acomi.acomi_backend.member.api.dto.response.MemberDirectoryPersonResponse;
import com.acomi.acomi_backend.member.application.service.MemberDirectoryService;
import com.acomi.acomi_backend.member.domain.model.MemberStatus;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.occupancy.domain.model.MemberOccupancyStatus;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@ExtendWith(MockitoExtension.class)
class MemberDirectoryControllerTest {

    @Mock
    private MemberDirectoryService memberDirectoryService;

    private MockMvc mockMvc;
    private UUID callerId;

    @BeforeEach
    void setUp() {
        callerId = UUID.randomUUID();
        MemberDirectoryController controller = new MemberDirectoryController(memberDirectoryService);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void directory_returnsPagedPeople() throws Exception {
        UUID spaceId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MemberDirectoryPersonResponse person = MemberDirectoryPersonResponse.builder()
                .directoryId("u:" + userId)
                .userId(userId)
                .displayName("Rahul")
                .mobileNumber("9876543210")
                .associations(List.of(MemberDirectoryAssociationResponse.builder()
                        .memberId(memberId)
                        .spaceId(spaceId)
                        .spaceName("Sunrise PG")
                        .spaceType(SpaceType.PG)
                        .role(MembershipRole.TENANT)
                        .membershipStatus(MemberStatus.ACTIVE)
                        .occupancyStatus(MemberOccupancyStatus.ALLOCATED)
                        .build()))
                .build();
        PagedResponse<MemberDirectoryPersonResponse> page = PagedResponse.<MemberDirectoryPersonResponse>builder()
                .content(List.of(person))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(memberDirectoryService.searchDirectory(
                        eq(callerId), eq("rahul"), isNull(), isNull(), isNull(), any()))
                .thenReturn(page);

        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(callerId);

            mockMvc.perform(get("/api/v1/members/directory")
                            .param("search", "rahul")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].displayName").value("Rahul"))
                    .andExpect(jsonPath("$.data.content[0].associations[0].spaceName")
                            .value("Sunrise PG"))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        verify(memberDirectoryService).searchDirectory(
                eq(callerId), eq("rahul"), isNull(), isNull(), isNull(), any());
    }

    @Test
    void directory_forwardsFilters() throws Exception {
        UUID spaceId = UUID.randomUUID();
        when(memberDirectoryService.searchDirectory(
                        eq(callerId),
                        isNull(),
                        eq(spaceId),
                        eq(SpaceType.MESS),
                        eq(MemberStatus.ACTIVE),
                        any()))
                .thenReturn(PagedResponse.from(
                        new org.springframework.data.domain.PageImpl<>(List.of(), PageRequest.of(0, 10), 0)));

        try (MockedStatic<SecurityUtils> security = Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(callerId);

            mockMvc.perform(get("/api/v1/members/directory")
                            .param("spaceId", spaceId.toString())
                            .param("spaceType", "MESS")
                            .param("status", "ACTIVE")
                            .param("size", "10"))
                    .andExpect(status().isOk());
        }
    }
}
