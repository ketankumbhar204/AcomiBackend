package com.acomi.acomi_backend.address.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminSavedAddressesSummaryResponse {

    private long totalAddresses;
    private long usedForProperties;
    private long usedForMesses;
    private long sharedAddresses;
    private List<String> cities;
    private List<String> states;
}
