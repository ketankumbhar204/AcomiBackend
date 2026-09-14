package com.acomi.acomi_backend.storage.infrastructure.provider;

public record ObjectMetadata(boolean exists, long byteSize, String contentType, String checksumSha256) {

    public static ObjectMetadata missing() {
        return new ObjectMetadata(false, 0, null, null);
    }
}
