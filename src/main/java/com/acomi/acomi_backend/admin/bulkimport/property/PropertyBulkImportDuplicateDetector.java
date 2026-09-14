package com.acomi.acomi_backend.admin.bulkimport.property;

import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportDuplicateMatch;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.registration.application.AdminLeadDefaults;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Detects likely duplicates for bulk import preview/import without persisting rows.
 *
 * <p>Name-only and mobile-only are never enough on their own. Matches require location signals
 * (address / pincode / geo) combined with name and/or contact overlap.
 */
@Component
@RequiredArgsConstructor
public class PropertyBulkImportDuplicateDetector {

    private static final double HIGH_GEO_METERS = 50.0;
    private static final double MEDIUM_GEO_METERS = 150.0;
    /** ~50m in degrees at equator (conservative for bounding-box prefilter). */
    private static final double GEO_BOX_DEGREES = 0.0015;

    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final MessRegistrationRepository messRegistrationRepository;
    private final SpaceRepository spaceRepository;

    public record Fingerprint(
            int rowNumber,
            PropertyBulkImportRowProcessor.TargetKind targetKind,
            String name,
            Set<String> mobiles,
            String addressLine,
            String city,
            String pincode,
            BigDecimal latitude,
            BigDecimal longitude) {}

    public record ExistingRecord(
            String source,
            String name,
            Set<String> mobiles,
            String addressLine,
            String city,
            String pincode,
            BigDecimal latitude,
            BigDecimal longitude,
            String reference,
            String spaceId) {}

    public List<ExistingRecord> loadExisting(Collection<Fingerprint> rows) {
        Set<String> mobiles = new LinkedHashSet<>();
        Set<String> pincodes = new LinkedHashSet<>();
        for (Fingerprint row : rows) {
            mobiles.addAll(row.mobiles());
            if (isRealPincode(row.pincode())) {
                pincodes.add(row.pincode().trim());
            }
        }

        List<ExistingRecord> existing = new ArrayList<>();
        if (!mobiles.isEmpty()) {
            for (PropertyRegistrationEntity entity :
                    propertyRegistrationRepository.findCandidatesByAnyMobile(mobiles)) {
                existing.add(fromProperty(entity));
            }
            for (MessRegistrationEntity entity :
                    messRegistrationRepository.findCandidatesByAnyMobile(mobiles)) {
                existing.add(fromMess(entity));
            }
            for (SpaceEntity space : spaceRepository.findActiveByContactNumberIn(mobiles)) {
                existing.add(fromSpace(space));
            }
        }
        if (!pincodes.isEmpty()) {
            for (PropertyRegistrationEntity entity :
                    propertyRegistrationRepository.findCandidatesByPincodeIn(pincodes)) {
                existing.add(fromProperty(entity));
            }
            for (MessRegistrationEntity entity :
                    messRegistrationRepository.findCandidatesByPincodeIn(pincodes)) {
                existing.add(fromMess(entity));
            }
        }
        for (Fingerprint row : rows) {
            if (row.latitude() == null || row.longitude() == null) {
                continue;
            }
            double lat = row.latitude().doubleValue();
            double lng = row.longitude().doubleValue();
            BigDecimal minLat = BigDecimal.valueOf(lat - GEO_BOX_DEGREES);
            BigDecimal maxLat = BigDecimal.valueOf(lat + GEO_BOX_DEGREES);
            BigDecimal minLng = BigDecimal.valueOf(lng - GEO_BOX_DEGREES);
            BigDecimal maxLng = BigDecimal.valueOf(lng + GEO_BOX_DEGREES);
            for (PropertyRegistrationEntity entity :
                    propertyRegistrationRepository.findCandidatesInGeoBox(minLat, maxLat, minLng, maxLng)) {
                existing.add(fromProperty(entity));
            }
            for (MessRegistrationEntity entity :
                    messRegistrationRepository.findCandidatesInGeoBox(minLat, maxLat, minLng, maxLng)) {
                existing.add(fromMess(entity));
            }
            for (SpaceEntity space :
                    spaceRepository.findActiveInGeoBox(minLat, maxLat, minLng, maxLng)) {
                existing.add(fromSpace(space));
            }
        }
        return dedupeExisting(existing);
    }

    public Optional<PropertyBulkImportDuplicateMatch> findMatch(
            Fingerprint row, List<Fingerprint> earlierInFile, List<ExistingRecord> existing) {
        PropertyBulkImportDuplicateMatch best = null;
        for (Fingerprint other : earlierInFile) {
            Optional<PropertyBulkImportDuplicateMatch> match = scoreInFile(row, other);
            if (match.isPresent() && isBetter(match.get(), best)) {
                best = match.get();
            }
        }
        for (ExistingRecord candidate : existing) {
            Optional<PropertyBulkImportDuplicateMatch> match = scoreExisting(row, candidate);
            if (match.isPresent() && isBetter(match.get(), best)) {
                best = match.get();
            }
        }
        return Optional.ofNullable(best);
    }

    public static Fingerprint fingerprintFromProcessed(
            int rowNumber, PropertyBulkImportRowProcessor.ProcessedRow processed) {
        if (processed.targetKind() == PropertyBulkImportRowProcessor.TargetKind.MESS
                && processed.messRequest() != null) {
            var req = processed.messRequest();
            return new Fingerprint(
                    rowNumber,
                    processed.targetKind(),
                    req.getMessName(),
                    collectMobiles(
                            req.getMobileNumber(),
                            req.getAlternateMobileNumber(),
                            req.getAdditionalMobileNumber()),
                    req.getAddressLine(),
                    req.getCity(),
                    req.getPincode(),
                    req.getLatitude(),
                    req.getLongitude());
        }
        var req = processed.propertyRequest();
        return new Fingerprint(
                rowNumber,
                processed.targetKind(),
                req == null ? null : req.getPropertyName(),
                req == null
                        ? Set.of()
                        : collectMobiles(
                                req.getMobileNumber(),
                                req.getAlternateMobileNumber(),
                                req.getAdditionalMobileNumber()),
                req == null ? null : req.getAddressLine(),
                req == null ? null : req.getCity(),
                req == null ? null : req.getPincode(),
                req == null ? null : req.getLatitude(),
                req == null ? null : req.getLongitude());
    }

    private Optional<PropertyBulkImportDuplicateMatch> scoreInFile(Fingerprint row, Fingerprint other) {
        MatchSignals signals = score(row, other.name(), other.mobiles(), other.addressLine(), other.city(),
                other.pincode(), other.latitude(), other.longitude());
        if (signals == null) {
            return Optional.empty();
        }
        return Optional.of(PropertyBulkImportDuplicateMatch.builder()
                .confidence(signals.confidence())
                .source("IN_FILE")
                .matchedRowNumber(other.rowNumber())
                .matchedName(other.name())
                .reason(signals.reason() + " (row " + other.rowNumber() + ")")
                .build());
    }

    private Optional<PropertyBulkImportDuplicateMatch> scoreExisting(Fingerprint row, ExistingRecord other) {
        MatchSignals signals = score(row, other.name(), other.mobiles(), other.addressLine(), other.city(),
                other.pincode(), other.latitude(), other.longitude());
        if (signals == null) {
            return Optional.empty();
        }
        return Optional.of(PropertyBulkImportDuplicateMatch.builder()
                .confidence(signals.confidence())
                .source(other.source())
                .matchedReference(other.reference())
                .matchedSpaceId(other.spaceId())
                .matchedName(other.name())
                .reason(signals.reason())
                .build());
    }

    private MatchSignals score(
            Fingerprint row,
            String otherName,
            Set<String> otherMobiles,
            String otherAddress,
            String otherCity,
            String otherPincode,
            BigDecimal otherLat,
            BigDecimal otherLng) {
        boolean namesSimilar = namesSimilar(row.name(), otherName);
        boolean mobileOverlap = !intersection(row.mobiles(), otherMobiles).isEmpty();
        boolean samePincode =
                isRealPincode(row.pincode())
                        && isRealPincode(otherPincode)
                        && row.pincode().trim().equals(otherPincode.trim());
        boolean addressMatch = addressFingerprintsMatch(row, otherAddress, otherCity, otherPincode);
        Double meters = distanceMeters(row.latitude(), row.longitude(), otherLat, otherLng);

        // High: near-identical place (geo or address) with similar listing name
        if (namesSimilar && meters != null && meters <= HIGH_GEO_METERS) {
            return new MatchSignals(
                    "HIGH",
                    "Same location (~" + Math.round(meters) + "m) and similar name");
        }
        if (namesSimilar && addressMatch) {
            return new MatchSignals("HIGH", "Same address and similar name");
        }
        // High: same listing identity — name + shared contact (works when address/geo are missing
        // or still placeholders from incomplete admin leads).
        if (namesSimilar && mobileOverlap) {
            return new MatchSignals("HIGH", "Same mobile and similar name");
        }
        if (namesSimilar && samePincode && mobileOverlap) {
            return new MatchSignals("HIGH", "Same mobile, pincode, and name");
        }

        // Medium: same contact managing what looks like the same place
        if (mobileOverlap && addressMatch) {
            return new MatchSignals("MEDIUM", "Shared mobile and same address");
        }
        if (mobileOverlap && meters != null && meters <= MEDIUM_GEO_METERS) {
            return new MatchSignals(
                    "MEDIUM",
                    "Shared mobile and nearby location (~" + Math.round(meters) + "m)");
        }
        if (mobileOverlap && samePincode && hasRealAddress(row.addressLine()) && hasRealAddress(otherAddress)
                && tokenOverlap(row.addressLine(), otherAddress)) {
            return new MatchSignals("MEDIUM", "Shared mobile, same pincode, and overlapping address");
        }

        return null;
    }

    private static boolean namesSimilar(String a, String b) {
        String na = normalizeName(a);
        String nb = normalizeName(b);
        if (!StringUtils.hasText(na) || !StringUtils.hasText(nb)) {
            return false;
        }
        if (isPlaceholderName(na) || isPlaceholderName(nb)) {
            return false;
        }
        if (na.equals(nb)) {
            return true;
        }
        if (na.length() >= 4 && nb.length() >= 4 && (na.contains(nb) || nb.contains(na))) {
            return true;
        }
        return false;
    }

    private static boolean addressFingerprintsMatch(
            Fingerprint row, String otherAddress, String otherCity, String otherPincode) {
        String left = addressFingerprint(row.addressLine(), row.city(), row.pincode());
        String right = addressFingerprint(otherAddress, otherCity, otherPincode);
        return left != null && left.equals(right);
    }

    private static String addressFingerprint(String address, String city, String pincode) {
        if (!hasRealAddress(address)) {
            return null;
        }
        String pin = isRealPincode(pincode) ? pincode.trim() : "";
        String cityPart = hasRealAddress(city) ? normalizeName(city) : "";
        if (pin.isEmpty() && cityPart.isEmpty()) {
            return null;
        }
        return normalizeName(address) + "|" + cityPart + "|" + pin;
    }

    private static boolean hasRealAddress(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        return !trimmed.equals(AdminLeadDefaults.PLACEHOLDER_ADDRESS)
                && !trimmed.equals("-")
                && !trimmed.equalsIgnoreCase("n/a")
                && !trimmed.equalsIgnoreCase("na");
    }

    private static boolean isRealPincode(String pincode) {
        return StringUtils.hasText(pincode)
                && !AdminLeadDefaults.PLACEHOLDER_PINCODE.equals(pincode.trim());
    }

    private static boolean isPlaceholderName(String normalized) {
        return "untitled property".equals(normalized) || "untitled mess".equals(normalized);
    }

    private static String normalizeName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean tokenOverlap(String a, String b) {
        Set<String> left = tokens(a);
        Set<String> right = tokens(b);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        int hits = 0;
        for (String token : left) {
            if (token.length() >= 3 && right.contains(token)) {
                hits++;
            }
        }
        return hits >= 2;
    }

    private static Set<String> tokens(String value) {
        Set<String> out = new HashSet<>();
        for (String part : normalizeName(value).split(" ")) {
            if (StringUtils.hasText(part)) {
                out.add(part);
            }
        }
        return out;
    }

    private static Set<String> collectMobiles(String... mobiles) {
        Set<String> out = new LinkedHashSet<>();
        if (mobiles == null) {
            return out;
        }
        for (String mobile : mobiles) {
            if (StringUtils.hasText(mobile)
                    && !AdminLeadDefaults.PLACEHOLDER_MOBILE.equals(mobile.trim())) {
                out.add(mobile.trim());
            }
        }
        return out;
    }

    private static Set<String> intersection(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.retainAll(b);
        return out;
    }

    private static Double distanceMeters(
            BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }
        double r = 6371000.0;
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double dPhi = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLambda = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    private static boolean isBetter(
            PropertyBulkImportDuplicateMatch candidate, PropertyBulkImportDuplicateMatch current) {
        if (current == null) {
            return true;
        }
        int c = rank(candidate.getConfidence());
        int cur = rank(current.getConfidence());
        return c > cur;
    }

    private static int rank(String confidence) {
        if ("HIGH".equalsIgnoreCase(confidence)) {
            return 2;
        }
        if ("MEDIUM".equalsIgnoreCase(confidence)) {
            return 1;
        }
        return 0;
    }

    private static ExistingRecord fromProperty(PropertyRegistrationEntity entity) {
        return new ExistingRecord(
                "EXISTING_PROPERTY",
                entity.getPropertyName(),
                collectMobiles(
                        entity.getMobileNumber(),
                        entity.getAlternateMobileNumber(),
                        entity.getAdditionalMobileNumber()),
                entity.getAddressLine(),
                entity.getCity(),
                entity.getPincode(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getReference(),
                entity.getConvertedSpaceId() == null ? null : entity.getConvertedSpaceId().toString());
    }

    private static ExistingRecord fromMess(MessRegistrationEntity entity) {
        return new ExistingRecord(
                "EXISTING_MESS",
                entity.getMessName(),
                collectMobiles(
                        entity.getMobileNumber(),
                        entity.getAlternateMobileNumber(),
                        entity.getAdditionalMobileNumber()),
                entity.getAddressLine(),
                entity.getCity(),
                entity.getPincode(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getReference(),
                entity.getConvertedSpaceId() == null ? null : entity.getConvertedSpaceId().toString());
    }

    private static ExistingRecord fromSpace(SpaceEntity space) {
        String address = space.getAddress();
        String addressLine = address;
        String city = null;
        String pincode = null;
        if (StringUtils.hasText(address) && address.contains(",")) {
            String[] parts = address.split(",");
            if (parts.length >= 1) {
                addressLine = parts[0].trim();
            }
            if (parts.length >= 2) {
                city = parts[1].trim();
            }
            if (parts.length >= 4) {
                pincode = parts[parts.length - 1].trim().replaceAll("\\D", "");
                if (pincode.length() != 6) {
                    pincode = null;
                }
            }
        }
        return new ExistingRecord(
                "EXISTING_SPACE",
                space.getName(),
                collectMobiles(space.getContactNumber()),
                addressLine,
                city,
                pincode,
                space.getLatitude(),
                space.getLongitude(),
                null,
                space.getId() == null ? null : space.getId().toString());
    }

    private static List<ExistingRecord> dedupeExisting(List<ExistingRecord> records) {
        Set<String> seen = new HashSet<>();
        List<ExistingRecord> out = new ArrayList<>();
        for (ExistingRecord record : records) {
            String key = record.source()
                    + "|"
                    + (record.reference() == null ? "" : record.reference())
                    + "|"
                    + (record.spaceId() == null ? "" : record.spaceId());
            if (seen.add(key)) {
                out.add(record);
            }
        }
        return out;
    }

    private record MatchSignals(String confidence, String reason) {}
}
