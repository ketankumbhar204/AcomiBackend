package com.acomi.acomi_backend.location.api.dto.response;

import com.acomi.acomi_backend.location.domain.model.LocationRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Normalized India location row from the reference dataset")
public class LocationRecordResponse {

    private String state;
    private String district;
    private String cityTaluka;
    private String location;
    private String pincode;

    public static LocationRecordResponse from(LocationRecord record) {
        return LocationRecordResponse.builder()
                .state(record.state())
                .district(record.district())
                .cityTaluka(record.cityTaluka())
                .location(record.location())
                .pincode(record.pincode())
                .build();
    }
}
