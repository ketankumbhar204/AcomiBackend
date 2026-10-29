package com.acomi.acomi_backend.servicecontact.api.dto.response;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class DuplicateServiceContactResponse {

    private final boolean strongMatch;
    private final List<ServiceContactDuplicateMatchResponse> matches;
}
