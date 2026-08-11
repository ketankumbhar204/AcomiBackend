package com.amico.amico_backend.accommodation.api.controller;

import com.amico.amico_backend.accommodation.api.dto.request.BulkCreateRoomsRequest;
import com.amico.amico_backend.accommodation.api.dto.request.CreateRoomRequest;
import com.amico.amico_backend.accommodation.api.dto.request.DuplicateRoomRequest;
import com.amico.amico_backend.accommodation.api.dto.request.UpdateRoomRequest;
import com.amico.amico_backend.accommodation.api.dto.response.BulkCreateRoomsResponse;
import com.amico.amico_backend.accommodation.api.dto.response.DuplicateRoomResponse;
import com.amico.amico_backend.accommodation.api.dto.response.RoomListItemResponse;
import com.amico.amico_backend.accommodation.api.dto.response.RoomResponse;
import com.amico.amico_backend.accommodation.application.service.AccommodationBulkService;
import com.amico.amico_backend.accommodation.application.service.AccommodationLazyListService;
import com.amico.amico_backend.accommodation.application.service.AccommodationDeletionService;
import com.amico.amico_backend.accommodation.application.service.AccommodationDuplicateService;
import com.amico.amico_backend.accommodation.application.service.AccommodationRestoreService;
import com.amico.amico_backend.accommodation.application.service.RoomService;
import com.amico.amico_backend.common.security.SecurityUtils;
import com.amico.amico_backend.common.web.ApiResponse;
import com.amico.amico_backend.common.web.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}")
@RequiredArgsConstructor
@Tag(name = "Accommodation - Rooms", description = "Room structure APIs")
@SecurityRequirement(name = "bearerAuth")
public class RoomController {

    private final RoomService roomService;
    private final AccommodationLazyListService lazyListService;
    private final AccommodationDuplicateService duplicateService;
    private final AccommodationBulkService bulkService;
    private final AccommodationDeletionService deletionService;
    private final AccommodationRestoreService restoreService;

    @PostMapping("/floors/{floorId}/rooms")
    @Operation(summary = "Create room under floor", description = "Creates a room under a floor. PG and Hostel spaces only.")
    public ResponseEntity<ApiResponse<RoomResponse>> createRoomUnderFloor(
            @PathVariable UUID spaceId,
            @PathVariable UUID floorId,
            @RequestBody @Valid CreateRoomRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        RoomResponse response = roomService.createRoomUnderFloor(spaceId, floorId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Room created successfully", response));
    }

    @PostMapping("/units/{unitId}/rooms")
    @Operation(summary = "Create room under unit", description = "Creates a room under a unit. Co-Living spaces only.")
    public ResponseEntity<ApiResponse<RoomResponse>> createRoomUnderUnit(
            @PathVariable UUID spaceId,
            @PathVariable UUID unitId,
            @RequestBody @Valid CreateRoomRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        RoomResponse response = roomService.createRoomUnderUnit(spaceId, unitId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Room created successfully", response));
    }

    @GetMapping("/floors/{floorId}/rooms")
    @Operation(
            summary = "List rooms by floor",
            description = "Returns lightweight room summaries with bed counts. "
                    + "Use view=full for legacy full RoomResponse list. Supports query and pagination.")
    public ResponseEntity<?> getRoomsByFloor(
            @PathVariable UUID spaceId,
            @PathVariable UUID floorId,
            @RequestParam(defaultValue = "summary") String view,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "roomNumber") Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        if ("full".equalsIgnoreCase(view)) {
            List<RoomResponse> rooms = roomService.getRoomsByFloor(spaceId, floorId, callerId);
            return ResponseEntity.ok(ApiResponse.success(rooms));
        }
        PagedResponse<RoomListItemResponse> rooms =
                lazyListService.listRoomsByFloor(spaceId, floorId, callerId, query, pageable);
        return ResponseEntity.ok(ApiResponse.success(rooms));
    }

    @GetMapping("/units/{unitId}/rooms")
    @Operation(
            summary = "List rooms by unit",
            description = "Returns lightweight room summaries with bed counts. "
                    + "Use view=full for legacy full RoomResponse list. Supports query and pagination.")
    public ResponseEntity<?> getRoomsByUnit(
            @PathVariable UUID spaceId,
            @PathVariable UUID unitId,
            @RequestParam(defaultValue = "summary") String view,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "roomNumber") Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        if ("full".equalsIgnoreCase(view)) {
            List<RoomResponse> rooms = roomService.getRoomsByUnit(spaceId, unitId, callerId);
            return ResponseEntity.ok(ApiResponse.success(rooms));
        }
        PagedResponse<RoomListItemResponse> rooms =
                lazyListService.listRoomsByUnit(spaceId, unitId, callerId, query, pageable);
        return ResponseEntity.ok(ApiResponse.success(rooms));
    }

    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "Get room", description = "Returns room details.")
    public ResponseEntity<ApiResponse<RoomResponse>> getRoom(
            @PathVariable UUID spaceId, @PathVariable UUID roomId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        RoomResponse response = roomService.getRoom(spaceId, roomId, callerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/rooms/{roomId}")
    @Operation(summary = "Update room", description = "Updates room details. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<RoomResponse>> updateRoom(
            @PathVariable UUID spaceId,
            @PathVariable UUID roomId,
            @RequestBody @Valid UpdateRoomRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        RoomResponse response = roomService.updateRoom(spaceId, roomId, callerId, request);
        return ResponseEntity.ok(ApiResponse.success("Room updated successfully", response));
    }

    @PostMapping("/rooms/{roomId}/duplicate")
    @Operation(summary = "Duplicate room", description = "Clones a room with its beds. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<DuplicateRoomResponse>> duplicateRoom(
            @PathVariable UUID spaceId,
            @PathVariable UUID roomId,
            @RequestBody @Valid DuplicateRoomRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        DuplicateRoomResponse response = duplicateService.duplicateRoom(spaceId, roomId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Room duplicated successfully", response));
    }

    @PostMapping("/floors/{floorId}/rooms/bulk")
    @Operation(summary = "Bulk create rooms under floor", description = "Creates multiple rooms on a floor. PG/Hostel only.")
    public ResponseEntity<ApiResponse<BulkCreateRoomsResponse>> bulkCreateRoomsUnderFloor(
            @PathVariable UUID spaceId,
            @PathVariable UUID floorId,
            @RequestBody @Valid BulkCreateRoomsRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        BulkCreateRoomsResponse response =
                bulkService.bulkCreateRoomsUnderFloor(spaceId, floorId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Rooms created successfully", response));
    }

    @PostMapping("/units/{unitId}/rooms/bulk")
    @Operation(summary = "Bulk create rooms under unit", description = "Creates multiple rooms in a unit. Co-Living only.")
    public ResponseEntity<ApiResponse<BulkCreateRoomsResponse>> bulkCreateRoomsUnderUnit(
            @PathVariable UUID spaceId,
            @PathVariable UUID unitId,
            @RequestBody @Valid BulkCreateRoomsRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        BulkCreateRoomsResponse response =
                bulkService.bulkCreateRoomsUnderUnit(spaceId, unitId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Rooms created successfully", response));
    }

    @PostMapping("/rooms/{roomId}/deactivate")
    @Operation(
            summary = "Deactivate room",
            description = "Soft-deletes a room. OWNER only. Blocked if active beds exist.")
    public ResponseEntity<Void> deactivateRoom(@PathVariable UUID spaceId, @PathVariable UUID roomId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        roomService.deactivateRoom(spaceId, roomId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/restore")
    @Operation(summary = "Restore room", description = "Reactivates a deactivated room. OWNER only.")
    public ResponseEntity<Void> restoreRoom(@PathVariable UUID spaceId, @PathVariable UUID roomId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        restoreService.restoreRoom(spaceId, roomId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/delete")
    @Operation(
            summary = "Permanently delete room",
            description = "Hard-deletes a room. OWNER only. For setup corrections only.")
    public ResponseEntity<Void> deleteRoom(@PathVariable UUID spaceId, @PathVariable UUID roomId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        deletionService.deleteRoom(spaceId, roomId, callerId);
        return ResponseEntity.noContent().build();
    }
}
