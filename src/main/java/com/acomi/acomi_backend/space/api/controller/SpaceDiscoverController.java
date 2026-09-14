package com.acomi.acomi_backend.space.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.space.api.dto.response.DiscoverSpaceCardResponse;
import com.acomi.acomi_backend.space.api.dto.response.DiscoverSpaceDetailResponse;
import com.acomi.acomi_backend.space.application.service.SpaceDiscoverService;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/discover")
@RequiredArgsConstructor
@Tag(name = "Space Discovery", description = "Public browse of discoverable spaces; membership flags require sign-in")
@SecurityRequirement(name = "bearerAuth")
public class SpaceDiscoverController {

    private final SpaceDiscoverService spaceDiscoverService;

    @GetMapping
    @Operation(
            summary = "Discover active spaces",
            description = "Returns a paginated list of active discoverable spaces. "
                    + "Optional case-insensitive search on name or address, and optional type filter. "
                    + "Does not expose owner or contact details. Join remains invitation-only. "
                    + "Anonymous callers are allowed; alreadyMember is false until signed in.")
    public ResponseEntity<ApiResponse<PagedResponse<DiscoverSpaceCardResponse>>> discover(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SpaceType type,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserIdOrNull();
        PagedResponse<DiscoverSpaceCardResponse> response =
                spaceDiscoverService.discover(callerId, search, type, sort, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{spaceId}")
    @Operation(
            summary = "Discover space detail",
            description = "Returns public discovery details for a single active space. "
                    + "Returns 404 when the space is missing or inactive. Anonymous callers are allowed.")
    public ResponseEntity<ApiResponse<DiscoverSpaceDetailResponse>> getDetail(
            @PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserIdOrNull();
        DiscoverSpaceDetailResponse response = spaceDiscoverService.getDetail(callerId, spaceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
