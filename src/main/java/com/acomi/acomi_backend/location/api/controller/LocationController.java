package com.acomi.acomi_backend.location.api.controller;

import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.location.api.dto.response.LocationAutocompleteSuggestion;
import com.acomi.acomi_backend.location.api.dto.response.LocationRecordResponse;
import com.acomi.acomi_backend.location.application.service.LocationAutocompleteService;
import com.acomi.acomi_backend.location.application.service.LocationReferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@Tag(name = "Locations", description = "India location reference data for public discovery")
public class LocationController {

    private final LocationReferenceService locationReferenceService;
    private final LocationAutocompleteService locationAutocompleteService;

    @GetMapping("/states")
    @Operation(summary = "List unique states")
    public ResponseEntity<ApiResponse<List<String>>> states() {
        return ResponseEntity.ok(ApiResponse.success(locationReferenceService.listStates()));
    }

    @GetMapping("/districts")
    @Operation(summary = "List districts for a state")
    public ResponseEntity<ApiResponse<List<String>>> districts(@RequestParam String state) {
        return ResponseEntity.ok(ApiResponse.success(locationReferenceService.listDistricts(state)));
    }

    @GetMapping("/talukas")
    @Operation(summary = "List city/taluka values for a state and district")
    public ResponseEntity<ApiResponse<List<String>>> talukas(
            @RequestParam String state, @RequestParam String district) {
        return ResponseEntity.ok(ApiResponse.success(locationReferenceService.listTalukas(state, district)));
    }

    @GetMapping("/areas")
    @Operation(summary = "List locations and pincodes for a city/taluka")
    public ResponseEntity<ApiResponse<List<LocationRecordResponse>>> areas(
            @RequestParam String state, @RequestParam String district, @RequestParam String taluk) {
        return ResponseEntity.ok(ApiResponse.success(locationReferenceService.listAreas(state, district, taluk)));
    }

    @GetMapping("/autocomplete")
    @Operation(summary = "Suggest areas, cities, or pincodes. Does not create ACOMI locations or listings.")
    public ResponseEntity<ApiResponse<List<LocationAutocompleteSuggestion>>> autocomplete(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String district) {
        return ResponseEntity.ok(ApiResponse.success(
                locationAutocompleteService.autocomplete(q, state, district)));
    }

    @GetMapping("/search")
    @Operation(summary = "Search locations by name, taluk, district, pincode, or multi-keyword query")
    public ResponseEntity<ApiResponse<List<LocationRecordResponse>>> search(
            @RequestParam String q,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String taluk) {
        return ResponseEntity.ok(
                ApiResponse.success(locationReferenceService.search(q, limit, state, district, taluk)));
    }
}
