package com.acomi.acomi_backend.inquirycredit.api.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RejectPurchaseRequest {

    private String reason;
}
