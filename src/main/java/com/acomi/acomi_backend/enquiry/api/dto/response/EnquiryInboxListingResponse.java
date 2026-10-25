package com.acomi.acomi_backend.enquiry.api.dto.response;

import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EnquiryInboxListingResponse {
    private UUID spaceId;
    private String name;
    private SpaceType spaceType;
    private String locationLabel;
}
