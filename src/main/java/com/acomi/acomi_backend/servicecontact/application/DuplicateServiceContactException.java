package com.acomi.acomi_backend.servicecontact.application;

import com.acomi.acomi_backend.servicecontact.api.dto.response.DuplicateServiceContactResponse;
import lombok.Getter;

@Getter
public class DuplicateServiceContactException extends RuntimeException {

    private final DuplicateServiceContactResponse duplicate;

    public DuplicateServiceContactException(DuplicateServiceContactResponse duplicate) {
        super(duplicate.isStrongMatch()
                ? "A service contact with this name and phone number already exists"
                : "A service contact with this phone number already exists");
        this.duplicate = duplicate;
    }
}
