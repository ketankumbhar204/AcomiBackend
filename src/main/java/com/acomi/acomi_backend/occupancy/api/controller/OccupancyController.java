package com.acomi.acomi_backend.occupancy.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.occupancy.api.dto.request.AllocateOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.CancelReservationRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.MoveInOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.ReserveOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.TransferOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.VacateOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.response.MemberOccupancyListResponse;
import com.acomi.acomi_backend.occupancy.api.dto.response.OccupancyResponse;
import com.acomi.acomi_backend.occupancy.application.service.OccupancyService;
import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}")
@RequiredArgsConstructor
@Tag(name = "Occupancy", description = "Member accommodation allocation, transfer, and vacate")
@SecurityRequirement(name = "bearerAuth")
public class OccupancyController {

    private final OccupancyService occupancyService;

    @PostMapping("/occupancies/reserve")
    @Operation(summary = "Reserve member", description = "Creates a RESERVED occupancy for a future move-in. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<OccupancyResponse>> reserve(
            @PathVariable UUID spaceId, @RequestBody @Valid ReserveOccupancyRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        OccupancyResponse response = occupancyService.reserve(spaceId, callerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Reservation created successfully", response));
    }

    @PostMapping("/occupancies/{occupancyId}/move-in")
    @Operation(summary = "Move in member", description = "Promotes a RESERVED occupancy to ACTIVE. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<OccupancyResponse>> moveIn(
            @PathVariable UUID spaceId,
            @PathVariable UUID occupancyId,
            @RequestBody(required = false) @Valid MoveInOccupancyRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        MoveInOccupancyRequest body = request != null ? request : new MoveInOccupancyRequest();
        OccupancyResponse response = occupancyService.moveIn(spaceId, occupancyId, callerId, body);
        return ResponseEntity.ok(ApiResponse.success("Move-in completed successfully", response));
    }

    @PostMapping("/occupancies/{occupancyId}/cancel-reservation")
    @Operation(summary = "Cancel reservation", description = "Cancels a RESERVED occupancy and releases inventory. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<OccupancyResponse>> cancelReservation(
            @PathVariable UUID spaceId,
            @PathVariable UUID occupancyId,
            @RequestBody(required = false) @Valid CancelReservationRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        CancelReservationRequest body = request != null ? request : new CancelReservationRequest();
        OccupancyResponse response = occupancyService.cancelReservation(spaceId, occupancyId, callerId, body);
        return ResponseEntity.ok(ApiResponse.success("Reservation cancelled successfully", response));
    }

    @PostMapping("/occupancies")
    @Operation(summary = "Allocate member (walk-in)", description = "Immediate ACTIVE allocation (move-in today). OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<OccupancyResponse>> allocate(
            @PathVariable UUID spaceId, @RequestBody @Valid AllocateOccupancyRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        OccupancyResponse response = occupancyService.allocate(spaceId, callerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Member allocated successfully", response));
    }

    @PostMapping("/occupancies/{occupancyId}/transfer")
    @Operation(summary = "Transfer member", description = "Closes the active occupancy and creates a new one on the target.")
    public ResponseEntity<ApiResponse<OccupancyResponse>> transfer(
            @PathVariable UUID spaceId,
            @PathVariable UUID occupancyId,
            @RequestBody @Valid TransferOccupancyRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        OccupancyResponse response = occupancyService.transfer(spaceId, occupancyId, callerId, request);
        return ResponseEntity.ok(ApiResponse.success("Member transferred successfully", response));
    }

    @PostMapping("/occupancies/{occupancyId}/vacate")
    @Operation(summary = "Vacate member", description = "Ends an active occupancy and releases the accommodation target.")
    public ResponseEntity<ApiResponse<OccupancyResponse>> vacate(
            @PathVariable UUID spaceId,
            @PathVariable UUID occupancyId,
            @RequestBody(required = false) @Valid VacateOccupancyRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        VacateOccupancyRequest body = request != null ? request : new VacateOccupancyRequest();
        OccupancyResponse response = occupancyService.vacate(spaceId, occupancyId, callerId, body);
        return ResponseEntity.ok(ApiResponse.success("Member vacated successfully", response));
    }

    @GetMapping("/occupancies/{occupancyId}")
    @Operation(summary = "Get occupancy", description = "Returns occupancy details.")
    public ResponseEntity<ApiResponse<OccupancyResponse>> getOccupancy(
            @PathVariable UUID spaceId, @PathVariable UUID occupancyId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        OccupancyResponse response = occupancyService.getOccupancy(spaceId, occupancyId, callerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/occupancies")
    @Operation(summary = "List occupancies", description = "Search and filter occupancies in a space.")
    public ResponseEntity<ApiResponse<PagedResponse<OccupancyResponse>>> listOccupancies(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) OccupancyStatus status,
            @RequestParam(required = false) UUID memberId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID floorId,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) UUID bedId,
            @RequestParam(required = false) AllocationTargetType targetType,
            @PageableDefault(size = 20, sort = "allocatedAt") Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        PagedResponse<OccupancyResponse> response = occupancyService.listSpaceOccupancies(
                spaceId,
                callerId,
                status,
                memberId,
                buildingId,
                floorId,
                unitId,
                roomId,
                bedId,
                targetType,
                pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/members/{memberId}/occupancies")
    @Operation(summary = "Member occupancy history", description = "Returns current and historical occupancies for a member.")
    public ResponseEntity<ApiResponse<MemberOccupancyListResponse>> getMemberOccupancies(
            @PathVariable UUID spaceId, @PathVariable UUID memberId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        MemberOccupancyListResponse response = occupancyService.getMemberOccupancies(spaceId, memberId, callerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
