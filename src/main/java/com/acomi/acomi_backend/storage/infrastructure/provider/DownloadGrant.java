package com.acomi.acomi_backend.storage.infrastructure.provider;

import java.time.LocalDateTime;

public record DownloadGrant(String url, LocalDateTime expiresAt) {}
