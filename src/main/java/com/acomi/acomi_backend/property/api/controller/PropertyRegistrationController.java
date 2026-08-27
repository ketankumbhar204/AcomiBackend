package com.acomi.acomi_backend.property.api.controller;

import com.acomi.acomi_backend.auth.application.otp.ClientIpResolver;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.property.api.dto.request.CreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationResponse;
import com.acomi.acomi_backend.property.application.service.PropertyRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/property-registrations")
@RequiredArgsConstructor
@Tag(name = "Property Registrations", description = "Public website property lead capture")
public class PropertyRegistrationController {

    private final PropertyRegistrationService propertyRegistrationService;

    @PostMapping
    @Operation(
            summary = "Submit a property registration",
            description =
                    "Public endpoint. Authorization comes from a PROPERTY_REGISTRATION verification"
                            + " token rather than a JWT. Does not create a space or a user account.")
    public ResponseEntity<ApiResponse<PropertyRegistrationResponse>> register(
            @RequestBody @Valid CreatePropertyRegistrationRequest request,
            HttpServletRequest httpRequest) {
        PropertyRegistrationResponse response =
                propertyRegistrationService.register(request, ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Property registration received", response));
    }
}
