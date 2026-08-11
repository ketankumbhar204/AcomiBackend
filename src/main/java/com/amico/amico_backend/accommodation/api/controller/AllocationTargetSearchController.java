package com.amico.amico_backend.accommodation.api.controller;

import com.amico.amico_backend.accommodation.api.dto.response.AllocationTargetSearchResponse;
import com.amico.amico_backend.accommodation.application.service.AllocationTargetSearchService;
import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.common.security.SecurityUtils;
import com.amico.amico_backend.common.web.ApiResponse;
import com.amico.amico_backend.common.web.PagedResponse;
import com.amico.amico_backend.occupancy.domain.model.AllocationTargetType;
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
@RequestMapping("/api/v1/spaces/{spaceId}/accommodation")
@RequiredArgsConstructor
@Tag(name = "Accommodation - Allocation Search", description = "Unified accommodation search for occupancy workflows")
@SecurityRequirement(name = "bearerAuth")
public class AllocationTargetSearchController {

    private final AllocationTargetSearchService allocationTargetSearchService;

    @GetMapping("/allocation-targets")
    @Operation(
            summary = "Search allocation targets",
            description = "Returns paginated bed or unit targets with full display paths for dashboard occupancy flows. "
                    + "Default target type is BED for PG/HOSTEL/CO_LIVING and UNIT for RENTAL.")
    public ResponseEntity<ApiResponse<PagedResponse<AllocationTargetSearchResponse>>> searchAllocationTargets(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) AllocationTargetType targetType,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID floorId,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) AccommodationStatus status,
            @RequestParam(required = false) Boolean selectableOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        PagedResponse<AllocationTargetSearchResponse> results = allocationTargetSearchService.searchAllocationTargets(
                spaceId,
                callerId,
                query,
                targetType,
                buildingId,
                floorId,
                unitId,
                status,
                selectableOnly,
                pageable);
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
