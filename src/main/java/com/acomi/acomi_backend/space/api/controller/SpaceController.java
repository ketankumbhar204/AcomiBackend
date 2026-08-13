package com.acomi.acomi_backend.space.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.space.api.dto.request.CreateSpaceRequest;
import com.acomi.acomi_backend.space.api.dto.request.UpdateSpaceRequest;
import com.acomi.acomi_backend.space.api.dto.response.DefaultSpaceResponse;
import com.acomi.acomi_backend.space.api.dto.response.MySpaceResponse;
import com.acomi.acomi_backend.space.api.dto.response.SetDefaultSpaceResponse;
import com.acomi.acomi_backend.space.api.dto.response.SpaceDetailsResponse;
import com.acomi.acomi_backend.space.api.dto.response.SpaceResponse;
import com.acomi.acomi_backend.space.api.dto.response.UserSpaceResponse;
import com.acomi.acomi_backend.space.application.service.SpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces")
@RequiredArgsConstructor
@Tag(name = "Spaces", description = "Space management APIs")
@SecurityRequirement(name = "bearerAuth")
public class SpaceController {

    private final SpaceService spaceService;

    @GetMapping("/my")
    @Operation(
            summary = "List my spaces",
            description = "Returns all active spaces for the logged-in user. "
                    + "Optional case-insensitive search by space name. "
                    + "Sorted: default first, then most recently joined.")
    public ResponseEntity<ApiResponse<List<MySpaceResponse>>> getMySpaces(
            @RequestParam(required = false) String search) {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<MySpaceResponse> spaces = (search != null && !search.isBlank())
                ? spaceService.searchMySpaces(userId, search)
                : spaceService.getMySpaces(userId);
        return ResponseEntity.ok(ApiResponse.success(spaces));
    }

    @GetMapping("/default")
    @Operation(
            summary = "Get default space",
            description = "Returns the logged-in user's default space for the space switcher.")
    public ResponseEntity<ApiResponse<DefaultSpaceResponse>> getDefaultSpace() {
        UUID userId = SecurityUtils.getCurrentUserId();
        DefaultSpaceResponse response = spaceService.getDefaultSpace(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @Operation(
            summary = "Create a space",
            description = "Creates a new space and an OWNER membership for the owner. "
                    + "Supported types: PG, MESS, HOSTEL, CO_LIVING, RENTAL.")
    public ResponseEntity<ApiResponse<SpaceResponse>> createSpace(
            @RequestBody @Valid CreateSpaceRequest request) {
        SpaceResponse response = spaceService.createSpace(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Space created successfully", response));
    }

    @GetMapping("/user/{userId}")
    @Operation(
            summary = "Get spaces for a user",
            description = "Returns all active spaces linked to the user via active memberships.")
    public ResponseEntity<ApiResponse<List<UserSpaceResponse>>> getUserSpaces(
            @PathVariable UUID userId) {
        List<UserSpaceResponse> spaces = spaceService.getUserSpaces(userId);
        return ResponseEntity.ok(ApiResponse.success(spaces));
    }

    @GetMapping("/{spaceId}")
    @Operation(summary = "Get space details", description = "Returns complete details of a single active space.")
    public ResponseEntity<ApiResponse<SpaceDetailsResponse>> getSpaceById(
            @PathVariable UUID spaceId) {
        SpaceDetailsResponse response = spaceService.getSpaceById(spaceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{spaceId}")
    @Operation(
            summary = "Update a space",
            description = "Updates name, address, contact, and food pricing policy. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<SpaceDetailsResponse>> updateSpace(
            @PathVariable UUID spaceId,
            @RequestBody @Valid UpdateSpaceRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        SpaceDetailsResponse response = spaceService.updateSpace(spaceId, callerId, request);
        return ResponseEntity.ok(ApiResponse.success("Space updated successfully", response));
    }

    @PutMapping("/{spaceId}/default")
    @Operation(
            summary = "Set default space",
            description = "Sets the given space as the logged-in user's default. "
                    + "Clears any previous default automatically.")
    public ResponseEntity<ApiResponse<SetDefaultSpaceResponse>> setDefaultSpace(
            @PathVariable UUID spaceId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        SetDefaultSpaceResponse response = spaceService.setDefaultSpace(userId, spaceId);
        return ResponseEntity.ok(ApiResponse.success("Default space updated", response));
    }

    @DeleteMapping("/{spaceId}")
    @Operation(
            summary = "Deactivate a space",
            description = "Soft-deletes a space by setting active to false. Only the space owner may deactivate.")
    public ResponseEntity<Void> deactivateSpace(@PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        spaceService.deactivateSpace(spaceId, callerId);
        return ResponseEntity.noContent().build();
    }
}
