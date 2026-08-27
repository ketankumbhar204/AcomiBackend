package com.acomi.acomi_backend.mess.api.controller;

import com.acomi.acomi_backend.auth.application.otp.ClientIpResolver;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.mess.api.dto.request.CreateMessRegistrationRequest;
import com.acomi.acomi_backend.mess.api.dto.response.MessRegistrationResponse;
import com.acomi.acomi_backend.mess.application.service.MessRegistrationService;
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
@RequestMapping("/api/v1/mess-registrations")
@RequiredArgsConstructor
@Tag(name = "Mess Registrations", description = "Public website mess lead capture")
public class MessRegistrationController {

    private final MessRegistrationService messRegistrationService;

    @PostMapping
    @Operation(
            summary = "Submit a mess registration",
            description =
                    "Public endpoint. Authorization comes from a MESS_REGISTRATION verification"
                            + " token rather than a JWT. Does not create a space or a user account.")
    public ResponseEntity<ApiResponse<MessRegistrationResponse>> register(
            @RequestBody @Valid CreateMessRegistrationRequest request,
            HttpServletRequest httpRequest) {
        MessRegistrationResponse response =
                messRegistrationService.register(request, ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Mess registration received", response));
    }
}
