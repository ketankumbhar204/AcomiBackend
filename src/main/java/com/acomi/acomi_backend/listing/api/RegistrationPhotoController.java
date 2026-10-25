package com.acomi.acomi_backend.listing.api;

import com.acomi.acomi_backend.listing.application.RegistrationPhotoAccessService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/registration-photos")
@RequiredArgsConstructor
public class RegistrationPhotoController {

    private final RegistrationPhotoAccessService registrationPhotoAccessService;

    @GetMapping("/{photoId}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID photoId) {
        return registrationPhotoAccessService.content(photoId);
    }
}
