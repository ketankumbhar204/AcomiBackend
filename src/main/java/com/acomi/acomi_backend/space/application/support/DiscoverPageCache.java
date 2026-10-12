package com.acomi.acomi_backend.space.application.support;

import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.space.api.dto.response.DiscoverSpaceCardResponse;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived discover pages. Same filters and caller reuse one response. */
public final class DiscoverPageCache {

    public static final long TTL_MILLIS = 30_000;
    private static final int MAX_ENTRIES = 200;

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public PagedResponse<DiscoverSpaceCardResponse> get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt <= System.currentTimeMillis()) {
            entries.remove(key, entry);
            return null;
        }
        return entry.response;
    }

    public void put(String key, PagedResponse<DiscoverSpaceCardResponse> response) {
        long now = System.currentTimeMillis();
        if (entries.size() >= MAX_ENTRIES) {
            entries.entrySet().removeIf(item -> item.getValue().expiresAt <= now);
        }
        if (entries.size() >= MAX_ENTRIES) {
            entries.clear();
        }
        entries.put(key, new Entry(response, now + TTL_MILLIS));
    }

    private record Entry(PagedResponse<DiscoverSpaceCardResponse> response, long expiresAt) {}
}
