package com.amico.amico_backend.accommodation.domain.policy;

import com.amico.amico_backend.accommodation.domain.model.BedLabelStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AccommodationNumberingService {

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^\\d+$");

    public String floorDisplayName(int floorNumber, boolean includeGroundFloor) {
        if (includeGroundFloor && floorNumber == 0) {
            return "Ground Floor";
        }
        return "Floor " + floorNumber;
    }

    public int floorNumberForIndex(int floorIndex, boolean includeGroundFloor) {
        return includeGroundFloor ? floorIndex : floorIndex + 1;
    }

    public String pgRoomNumber(int floorIndex, int roomIndexOneBased) {
        return String.valueOf((floorIndex + 1) * 100 + roomIndexOneBased);
    }

    public String roomDisplayName(String roomNumber) {
        return "Room " + roomNumber;
    }

    public String unitDisplayName(String unitNumber) {
        return "Unit " + unitNumber;
    }

    public String bedDisplayName(String bedNumber) {
        return "Bed " + bedNumber;
    }

    public List<String> nextUnitNumbers(String startNumber, int step, int count) {
        int start = parseNumericStart(startNumber);
        List<String> numbers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            numbers.add(String.valueOf(start + (i * step)));
        }
        return numbers;
    }

    public List<String> coLivingRoomLabels(int count) {
        List<String> labels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            labels.add(String.valueOf((char) ('A' + i)));
        }
        return labels;
    }

    public List<String> bedLabels(int count, BedLabelStyle style, Set<String> existingLabels) {
        List<String> labels = new ArrayList<>(count);
        if (style == BedLabelStyle.NUMERIC) {
            int next = 1;
            for (int i = 0; i < count; i++) {
                while (existingLabels.contains(String.valueOf(next))) {
                    next++;
                }
                String label = String.valueOf(next);
                labels.add(label);
                existingLabels.add(label);
                next++;
            }
            return labels;
        }

        for (int i = 0; i < count; i++) {
            String label;
            if (i < 26) {
                label = String.valueOf((char) ('A' + i));
            } else {
                label = String.valueOf(i + 1);
            }
            int suffix = 0;
            String candidate = label;
            while (existingLabels.contains(candidate)) {
                suffix++;
                candidate = label + suffix;
            }
            labels.add(candidate);
            existingLabels.add(candidate);
        }
        return labels;
    }

    public String suggestNextPgRoomNumber(int floorIndex, List<String> existingRoomNumbers) {
        Set<String> existing = new HashSet<>(existingRoomNumbers);
        for (int roomIndex = 1; roomIndex <= 999; roomIndex++) {
            String candidate = pgRoomNumber(floorIndex, roomIndex);
            if (!existing.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No available room numbers on floor");
    }

    public String suggestNextCoLivingRoomLabel(List<String> existingRoomNumbers) {
        Set<String> existing = new HashSet<>(existingRoomNumbers);
        for (int i = 0; i < 26; i++) {
            String label = String.valueOf((char) ('A' + i));
            if (!existing.contains(label)) {
                return label;
            }
        }
        for (int i = 1; i <= 999; i++) {
            String label = String.valueOf(i);
            if (!existing.contains(label)) {
                return label;
            }
        }
        throw new IllegalStateException("No available room labels on unit");
    }

    public List<String> allocatePgRoomNumbers(
            int floorIndex, int count, String startRoomNumber, List<String> existingRoomNumbers) {
        List<String> allocated = new ArrayList<>(count);
        Set<String> existing = new HashSet<>(existingRoomNumbers);
        if (startRoomNumber != null && !startRoomNumber.isBlank()) {
            int start = Integer.parseInt(startRoomNumber);
            for (int i = 0; i < count; i++) {
                String candidate = String.valueOf(start + i);
                if (existing.contains(candidate)) {
                    throw new IllegalArgumentException(
                            "Room number " + candidate + " already exists on this floor");
                }
                allocated.add(candidate);
            }
            return allocated;
        }
        int roomIndex = 1;
        while (allocated.size() < count) {
            String candidate = pgRoomNumber(floorIndex, roomIndex);
            roomIndex++;
            if (!existing.contains(candidate)) {
                allocated.add(candidate);
                existing.add(candidate);
            }
        }
        return allocated;
    }

    public List<String> allocateCoLivingRoomNumbers(
            int count, String startRoomNumber, List<String> existingRoomNumbers) {
        List<String> allocated = new ArrayList<>(count);
        Set<String> existing = new HashSet<>(existingRoomNumbers);
        if (startRoomNumber != null && !startRoomNumber.isBlank()) {
            char start = startRoomNumber.charAt(0);
            for (int i = 0; i < count; i++) {
                String candidate = String.valueOf((char) (start + i));
                if (existing.contains(candidate)) {
                    throw new IllegalArgumentException(
                            "Room number " + candidate + " already exists on this unit");
                }
                allocated.add(candidate);
            }
            return allocated;
        }
        for (String label : coLivingRoomLabels(count + existing.size())) {
            if (!existing.contains(label)) {
                allocated.add(label);
                if (allocated.size() == count) {
                    break;
                }
            }
        }
        while (allocated.size() < count) {
            String next = suggestNextCoLivingRoomLabel(
                    existingRoomNumbers.stream().sorted().toList());
            allocated.add(next);
            existing.add(next);
            existingRoomNumbers = new ArrayList<>(existing);
        }
        return allocated;
    }

    public List<String> allocateUnitNumbers(int count, String startUnitNumber, List<String> existingUnitNumbers) {
        List<String> allocated = new ArrayList<>(count);
        Set<String> existing = new HashSet<>(existingUnitNumbers);
        if (startUnitNumber != null && !startUnitNumber.isBlank()) {
            int start = parseNumericStart(startUnitNumber);
            for (int i = 0; i < count; i++) {
                String candidate = String.valueOf(start + i);
                if (existing.contains(candidate)) {
                    throw new IllegalArgumentException(
                            "Unit number " + candidate + " already exists in this building");
                }
                allocated.add(candidate);
            }
            return allocated;
        }

        int next = existingUnitNumbers.stream()
                .filter(n -> NUMERIC_PATTERN.matcher(n).matches())
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(100) + 1;
        while (allocated.size() < count) {
            String candidate = String.valueOf(next);
            next++;
            if (!existing.contains(candidate)) {
                allocated.add(candidate);
                existing.add(candidate);
            }
        }
        return allocated;
    }

    public int parseFloorIndexFromRoomNumber(String roomNumber, boolean includeGroundFloor) {
        if (!NUMERIC_PATTERN.matcher(roomNumber).matches() || roomNumber.length() < 3) {
            return 0;
        }
        int floorMultiplier = Integer.parseInt(roomNumber.substring(0, roomNumber.length() - 2));
        return includeGroundFloor ? floorMultiplier - 1 : floorMultiplier - 1;
    }

    private int parseNumericStart(String startNumber) {
        if (startNumber == null || startNumber.isBlank()) {
            return 101;
        }
        if (!NUMERIC_PATTERN.matcher(startNumber).matches()) {
            throw new IllegalArgumentException("Unit start number must be numeric");
        }
        return Integer.parseInt(startNumber);
    }

    public List<String> sortedRoomNumbers(List<String> roomNumbers) {
        return roomNumbers.stream()
                .sorted(Comparator.comparingInt(s -> NUMERIC_PATTERN.matcher(s).matches()
                        ? Integer.parseInt(s)
                        : s.charAt(0)))
                .toList();
    }
}
