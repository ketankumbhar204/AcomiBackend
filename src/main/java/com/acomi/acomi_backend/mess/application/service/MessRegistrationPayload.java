package com.acomi.acomi_backend.mess.application.service;

import com.acomi.acomi_backend.mess.api.dto.request.AdminCreateMessRegistrationRequest;
import com.acomi.acomi_backend.mess.api.dto.request.CreateMessRegistrationRequest;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MessRegistrationPayload {

    private String messName;
    private String ownerName;
    private String description;
    private String mobileNumber;
    private String alternateMobileNumber;
    private String addressLine;
    private String city;
    private String state;
    private String pincode;
    private String mapUrl;
    private BigDecimal monthlyPrice;
    private BigDecimal mealPrice;
    private Integer capacityEstimate;
    private Boolean testLead;

    public static MessRegistrationPayload fromPublic(CreateMessRegistrationRequest request) {
        return MessRegistrationPayload.builder()
                .messName(request.getMessName())
                .ownerName(request.getOwnerName())
                .description(request.getDescription())
                .mobileNumber(request.getMobileNumber())
                .alternateMobileNumber(request.getAlternateMobileNumber())
                .addressLine(request.getAddressLine())
                .city(request.getCity())
                .state(request.getState())
                .pincode(request.getPincode())
                .mapUrl(request.getMapUrl())
                .monthlyPrice(request.getMonthlyPrice())
                .mealPrice(request.getMealPrice())
                .capacityEstimate(request.getCapacityEstimate())
                .build();
    }

    public static MessRegistrationPayload fromAdmin(AdminCreateMessRegistrationRequest request) {
        return MessRegistrationPayload.builder()
                .messName(request.getMessName())
                .ownerName(request.getOwnerName())
                .description(request.getDescription())
                .mobileNumber(request.getMobileNumber())
                .alternateMobileNumber(request.getAlternateMobileNumber())
                .addressLine(request.getAddressLine())
                .city(request.getCity())
                .state(request.getState())
                .pincode(request.getPincode())
                .mapUrl(request.getMapUrl())
                .monthlyPrice(request.getMonthlyPrice())
                .mealPrice(request.getMealPrice())
                .capacityEstimate(request.getCapacityEstimate())
                .testLead(request.getTestLead())
                .build();
    }
}
