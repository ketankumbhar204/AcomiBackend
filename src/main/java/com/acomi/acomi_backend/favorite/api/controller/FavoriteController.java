package com.acomi.acomi_backend.favorite.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.favorite.api.dto.response.FavoriteStateResponse;
import com.acomi.acomi_backend.favorite.application.service.FavoriteService;
import com.acomi.acomi_backend.space.api.dto.response.DiscoverSpaceCardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/favorites")
@RequiredArgsConstructor
@Tag(name = "Favorites", description = "Saved discovery listings for the signed-in user")
@SecurityRequirement(name = "bearerAuth")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    @Operation(summary = "List the current user's saved listings")
    public ResponseEntity<ApiResponse<List<DiscoverSpaceCardResponse>>> list() {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(favoriteService.list(callerId)));
    }

    @GetMapping("/ids")
    @Operation(summary = "Listing ids the current user has saved")
    public ResponseEntity<ApiResponse<List<UUID>>> listIds() {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(favoriteService.listIds(callerId)));
    }

    @PutMapping("/{spaceId}")
    @Operation(summary = "Save a listing. Repeating the request does not create a duplicate.")
    public ResponseEntity<ApiResponse<FavoriteStateResponse>> add(@PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(favoriteService.add(callerId, spaceId)));
    }

    @DeleteMapping("/{spaceId}")
    @Operation(summary = "Remove a saved listing. Missing favorites are treated as already removed.")
    public ResponseEntity<ApiResponse<FavoriteStateResponse>> remove(@PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(favoriteService.remove(callerId, spaceId)));
    }
}
