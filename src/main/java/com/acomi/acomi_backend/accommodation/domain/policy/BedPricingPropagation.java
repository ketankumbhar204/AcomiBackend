package com.acomi.acomi_backend.accommodation.domain.policy;

import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Mirrors Quick Setup preview autofill:
 * APARTMENT_PG / CO_LIVING — same room number + same bed number across units.
 * CORRIDOR_PG — same bed number across rooms.
 * Copies a field only when the target field is empty. Never overwrites.
 */
public final class BedPricingPropagation {

    private BedPricingPropagation() {}

    public static boolean isEmpty(BigDecimal value) {
        return value == null;
    }

    public static String normalizeBedNumber(String raw) {
        return stripPrefix(raw, "bed");
    }

    public static String normalizeRoomNumber(String raw) {
        return stripPrefix(raw, "room");
    }

    public static BuildingEntity resolveBuilding(BedEntity bed) {
        if (bed == null || bed.getRoom() == null) {
            return null;
        }
        RoomEntity room = bed.getRoom();
        if (room.getFloor() != null && room.getFloor().getBuilding() != null) {
            return room.getFloor().getBuilding();
        }
        if (room.getUnit() != null) {
            return room.getUnit().getBuilding();
        }
        return null;
    }

    public static boolean isEquivalent(PropertyLayoutMode layoutMode, BedEntity source, BedEntity candidate) {
        if (source == null || candidate == null) {
            return false;
        }
        UUID sourceId = source.getId();
        UUID candidateId = candidate.getId();
        if (sourceId != null && sourceId.equals(candidateId)) {
            return false;
        }
        if (!Objects.equals(normalizeBedNumber(source.getBedNumber()), normalizeBedNumber(candidate.getBedNumber()))) {
            return false;
        }
        if (layoutMode == PropertyLayoutMode.APARTMENT_PG || layoutMode == PropertyLayoutMode.CO_LIVING) {
            String sourceRoom = source.getRoom() == null ? "" : source.getRoom().getRoomNumber();
            String candidateRoom = candidate.getRoom() == null ? "" : candidate.getRoom().getRoomNumber();
            return Objects.equals(normalizeRoomNumber(sourceRoom), normalizeRoomNumber(candidateRoom));
        }
        return true;
    }

    /**
     * Copies non-empty source rent/deposit into equivalent beds whose matching field is empty.
     *
     * @return beds that were changed (caller should persist them)
     */
    public static List<BedEntity> apply(PropertyLayoutMode layoutMode, BedEntity source, List<BedEntity> candidates) {
        List<BedEntity> changed = new ArrayList<>();
        if (source == null || candidates == null || candidates.isEmpty()) {
            return changed;
        }
        BigDecimal rent = source.getDefaultRent();
        BigDecimal deposit = source.getDefaultDeposit();
        if (isEmpty(rent) && isEmpty(deposit)) {
            return changed;
        }
        for (BedEntity candidate : candidates) {
            if (!isEquivalent(layoutMode, source, candidate)) {
                continue;
            }
            boolean updated = false;
            if (!isEmpty(rent) && isEmpty(candidate.getDefaultRent())) {
                candidate.setDefaultRent(rent);
                updated = true;
            }
            if (!isEmpty(deposit) && isEmpty(candidate.getDefaultDeposit())) {
                candidate.setDefaultDeposit(deposit);
                updated = true;
            }
            if (updated) {
                changed.add(candidate);
            }
        }
        return changed;
    }

    private static String stripPrefix(String raw, String prefix) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        String token = prefix + " ";
        if (value.startsWith(token)) {
            value = value.substring(token.length()).trim();
        }
        return value;
    }
}
