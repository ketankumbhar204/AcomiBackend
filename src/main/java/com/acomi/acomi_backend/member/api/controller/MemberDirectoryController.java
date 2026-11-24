package com.acomi.acomi_backend.member.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.member.api.dto.response.MemberDirectoryPersonResponse;
import com.acomi.acomi_backend.member.application.service.MemberDirectoryService;
import com.acomi.acomi_backend.member.domain.model.MemberStatus;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Tag(name = "Member Directory", description = "Owner-level unique people directory across managed spaces")
@SecurityRequirement(name = "bearerAuth")
public class MemberDirectoryController {

    private final MemberDirectoryService memberDirectoryService;

    @GetMapping("/directory")
    @Operation(
            summary = "Owner member directory",
            description = "Returns unique people (TENANT / CUSTOMER / STAFF) associated with spaces "
                    + "the caller owns or manages. Deduplicates by userId; unambiguous mobile is a "
                    + "fallback for unlinked members. Distinct userIds are never merged. "
                    + "Page size is capped at 100. Discovery only — does not create associations. "
                    + "Optional filters: search, spaceId, spaceType, status.")
    public ResponseEntity<ApiResponse<PagedResponse<MemberDirectoryPersonResponse>>> searchDirectory(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID spaceId,
            @RequestParam(required = false) SpaceType spaceType,
            @RequestParam(required = false) MemberStatus status,
            @PageableDefault(size = 20, sort = "displayName", direction = Sort.Direction.ASC)
                    Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        PagedResponse<MemberDirectoryPersonResponse> page = memberDirectoryService.searchDirectory(
                callerId, search, spaceId, spaceType, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }
}
