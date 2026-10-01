package com.acomi.acomi_backend.space.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.space.api.dto.response.DiscoverSpaceCardResponse;
import com.acomi.acomi_backend.space.api.dto.response.DiscoverSpaceDetailResponse;
import com.acomi.acomi_backend.space.application.service.SpaceDiscoverService;
import com.acomi.acomi_backend.space.application.support.SpaceDiscoverQuery;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
                    + "Filters (location address-only, search, types, rent, amenities) are applied first. "
                    + "Matching rows are then ranked by information completeness "
                    + "(usable mobile, address, map URL, amenities) and the existing newest order, "
                    + "and only then paged. Does not expose owner or contact details. "
                    + "Anonymous callers are allowed; alreadyMember is false until signed in.")
    public ResponseEntity<ApiResponse<PagedResponse<DiscoverSpaceCardResponse>>> discover(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) SpaceType type,
            @RequestParam(required = false) List<SpaceType> types,
            @RequestParam(required = false) BigDecimal minRent,
            @RequestParam(required = false) BigDecimal maxRent,
            @RequestParam(required = false) List<String> amenities,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserIdOrNull();
        List<SpaceType> resolvedTypes = new ArrayList<>();
        if (types != null) {
            resolvedTypes.addAll(types);
        }
        if (type != null && !resolvedTypes.contains(type)) {
            resolvedTypes.add(type);
        }
        PagedResponse<DiscoverSpaceCardResponse> response = spaceDiscoverService.discover(
                callerId,
                new SpaceDiscoverQuery(search, location, resolvedTypes, minRent, maxRent, amenities),
                sort,
                pageable);
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
