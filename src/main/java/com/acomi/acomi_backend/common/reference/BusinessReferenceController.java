package com.acomi.acomi_backend.common.reference;

import com.acomi.acomi_backend.common.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business-references")
@RequiredArgsConstructor
public class BusinessReferenceController {

    private final BusinessReferenceLookupService lookupService;

    @GetMapping("/{referenceCode}")
    public ApiResponse<BusinessReferenceLookupResponse> lookup(@PathVariable String referenceCode) {
        return ApiResponse.success(lookupService.lookup(referenceCode));
    }
}
