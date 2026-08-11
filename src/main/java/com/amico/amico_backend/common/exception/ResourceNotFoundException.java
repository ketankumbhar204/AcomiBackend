package com.amico.amico_backend.common.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, String field, Object value) {
        super(String.format("%s not found with %s: '%s'", resource, field, value));
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException notInSpace(String resource, Object id) {
        return new ResourceNotFoundException(
                String.format("%s not found in this space with id: '%s'", resource, id));
    }
}
