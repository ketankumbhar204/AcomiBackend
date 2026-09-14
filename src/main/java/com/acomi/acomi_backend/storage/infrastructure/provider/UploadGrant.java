package com.acomi.acomi_backend.storage.infrastructure.provider;

import java.time.LocalDateTime;
import java.util.Map;

public record UploadGrant(String url, String method, Map<String, String> headers, LocalDateTime expiresAt) {}
