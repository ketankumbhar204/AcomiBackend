package com.acomi.acomi_backend.enquiry.api.dto.response;

import com.acomi.acomi_backend.enquiry.domain.model.EnquiryInboxMode;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EnquiryInboxResponse {
    private EnquiryInboxMode mode;
    private List<EnquiryInboxListingResponse> listings;

    public static EnquiryInboxResponse mine() {
        return EnquiryInboxResponse.builder().mode(EnquiryInboxMode.MINE).listings(List.of()).build();
    }
}
