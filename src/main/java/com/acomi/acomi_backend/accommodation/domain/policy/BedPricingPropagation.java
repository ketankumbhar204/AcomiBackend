package com.acomi.acomi_backend.accommodation.domain.policy;

import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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

    public static boolean wouldReceivePricing(BedEntity source, BedEntity candidate) {
        if (source == null || candidate == null) {
            return false;
        }
        return (!isEmpty(source.getDefaultRent()) && isEmpty(candidate.getDefaultRent()))
                || (!isEmpty(source.getDefaultDeposit()) && isEmpty(candidate.getDefaultDeposit()));
    }

    /**
     * Equivalent beds that would receive a fill-empty copy. Does not mutate candidates.
     */
    public static List<BedEntity> matchingTargets(
            PropertyLayoutMode layoutMode, BedEntity source, List<BedEntity> candidates) {
        List<BedEntity> matches = new ArrayList<>();
        if (source == null || candidates == null || candidates.isEmpty()) {
            return matches;
        }
        if (isEmpty(source.getDefaultRent()) && isEmpty(source.getDefaultDeposit())) {
            return matches;
        }
        for (BedEntity candidate : candidates) {
            if (!isEquivalent(layoutMode, source, candidate)) {
                continue;
            }
            if (wouldReceivePricing(source, candidate)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    public static String floorLabel(BedEntity bed) {
        if (bed == null || bed.getRoom() == null) {
            return "";
        }
        RoomEntity room = bed.getRoom();
        if (room.getFloor() != null && hasText(room.getFloor().getName())) {
            return room.getFloor().getName().trim();
        }
        if (room.getUnit() != null) {
            if (room.getUnit().getFloor() != null && hasText(room.getUnit().getFloor().getName())) {
                return room.getUnit().getFloor().getName().trim();
            }
            if (hasText(room.getUnit().getName())) {
                return room.getUnit().getName().trim();
            }
        }
        return "";
    }

    public static List<String> locationLabels(BuildingEntity building, List<BedEntity> beds) {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        if (building != null && hasText(building.getName())) {
            labels.add(building.getName().trim());
        }
        if (beds != null) {
            for (BedEntity bed : beds) {
                String floor = floorLabel(bed);
                if (hasText(floor)) {
                    labels.add(floor);
                }
            }
        }
        return List.copyOf(labels);
    }

    /**
     * Copies non-empty source rent/deposit into equivalent beds whose matching field is empty.
     *
     * @return beds that were changed (caller should persist them)
     */
    public static List<BedEntity> apply(PropertyLayoutMode layoutMode, BedEntity source, List<BedEntity> candidates) {
        List<BedEntity> changed = matchingTargets(layoutMode, source, candidates);
        if (changed.isEmpty()) {
            return changed;
        }
        BigDecimal rent = source.getDefaultRent();
        BigDecimal deposit = source.getDefaultDeposit();
        for (BedEntity candidate : changed) {
            if (!isEmpty(rent) && isEmpty(candidate.getDefaultRent())) {
                candidate.setDefaultRent(rent);
            }
            if (!isEmpty(deposit) && isEmpty(candidate.getDefaultDeposit())) {
                candidate.setDefaultDeposit(deposit);
            }
        }
        return changed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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
