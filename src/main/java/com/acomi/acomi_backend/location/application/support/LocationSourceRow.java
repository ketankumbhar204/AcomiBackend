package com.acomi.acomi_backend.location.application.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationSourceRow {

    public String officeName;
    public Object pincode;
    public String taluk;
    public String districtName;
    public String stateName;
}
