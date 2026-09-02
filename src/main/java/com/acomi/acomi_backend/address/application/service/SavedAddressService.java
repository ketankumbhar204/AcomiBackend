package com.acomi.acomi_backend.address.application.service;

import com.acomi.acomi_backend.address.api.dto.request.SavedAddressRequest;
import com.acomi.acomi_backend.address.api.dto.response.SavedAddressResponse;
import com.acomi.acomi_backend.address.infrastructure.persistence.entity.SavedAddressEntity;
import com.acomi.acomi_backend.address.infrastructure.persistence.repository.SavedAddressRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.config.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SavedAddressService {

    private static final String PLACEHOLDER_MARK = "—";

    private final SavedAddressRepository savedAddressRepository;

    @Transactional(readOnly = true)
    public Page<SavedAddressResponse> list(String search, Pageable pageable) {
        UUID ownerId = currentAdminId();
        String term = sanitizeSearch(search);
        return savedAddressRepository.searchActiveByOwner(ownerId, term, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SavedAddressResponse getById(UUID id) {
        return toResponse(requireOwnedActive(id, currentAdminId()));
    }

    @Transactional
    public SavedAddressResponse create(SavedAddressRequest request) {
        UUID ownerId = currentAdminId();
        AddressFields fields = requireRealAddress(request);
        SavedAddressEntity saved = findOrCreate(ownerId, fields, false);
        return toResponse(saved);
    }

    @Transactional
    public SavedAddressResponse update(UUID id, SavedAddressRequest request) {
        UUID ownerId = currentAdminId();
        SavedAddressEntity existing = requireOwnedActive(id, ownerId);
        AddressFields fields = requireRealAddress(request);
        String fingerprint = fingerprint(fields);
        if (!fingerprint.equals(existing.getFingerprint())) {
            savedAddressRepository
                    .findFirstByCreatedByUserIdAndFingerprintAndIsActiveTrue(ownerId, fingerprint)
                    .filter(other -> other.isActive() && !other.getId().equals(existing.getId()))
                    .ifPresent(other -> {
                        throw new BusinessException(
                                "An equivalent saved address already exists.", HttpStatus.CONFLICT);
                    });
        }
        applyFields(existing, fields, fingerprint);
        return toResponse(savedAddressRepository.save(existing));
    }

    @Transactional
    public void deactivate(UUID id) {
        UUID ownerId = currentAdminId();
        SavedAddressEntity existing = requireOwnedActive(id, ownerId);
        existing.setActive(false);
        savedAddressRepository.save(existing);
    }

    /**
     * Called after a property/mess lead is saved with a real address.
     * Reuses an equivalent saved address instead of inserting a duplicate, then marks it recently used.
     */
    @Transactional
    public void rememberFromLead(String addressLine, String city, String state, String pincode, String mapUrl) {
        if (isPlaceholder(addressLine, city, state)) {
            return;
        }
        UUID ownerId = currentAdminId();
        AddressFields fields = AddressFields.from(addressLine, city, state, pincode, mapUrl);
        if (!StringUtils.hasText(fields.pincode())) {
            return;
        }
        findOrCreate(ownerId, fields, true);
    }

    private SavedAddressEntity findOrCreate(UUID ownerId, AddressFields fields, boolean markUsed) {
        String fingerprint = fingerprint(fields);
        SavedAddressEntity existing = savedAddressRepository
                .findFirstByCreatedByUserIdAndFingerprintAndIsActiveTrue(ownerId, fingerprint)
                .or(() -> savedAddressRepository.findFirstByCreatedByUserIdAndFingerprintOrderByUpdatedAtDesc(
                        ownerId, fingerprint))
                .orElse(null);
        if (existing != null) {
            if (!existing.isActive()) {
                existing.setActive(true);
                applyFields(existing, fields, fingerprint);
            }
            if (markUsed) {
                markUsed(existing);
            }
            return savedAddressRepository.save(existing);
        }

        SavedAddressEntity created = SavedAddressEntity.builder()
                .createdByUserId(ownerId)
                .addressLine(fields.addressLine())
                .city(fields.city())
                .state(fields.state())
                .pincode(fields.pincode())
                .mapUrl(fields.mapUrl())
                .fingerprint(fingerprint)
                .usageCount(markUsed ? 1 : 0)
                .lastUsedAt(markUsed ? LocalDateTime.now() : null)
                .isActive(true)
                .build();
        try {
            return savedAddressRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException ex) {
            SavedAddressEntity raced = savedAddressRepository
                    .findFirstByCreatedByUserIdAndFingerprintAndIsActiveTrue(ownerId, fingerprint)
                    .or(() -> savedAddressRepository.findFirstByCreatedByUserIdAndFingerprintOrderByUpdatedAtDesc(
                            ownerId, fingerprint))
                    .orElseThrow(() -> ex);
            if (markUsed) {
                markUsed(raced);
                return savedAddressRepository.save(raced);
            }
            return raced;
        }
    }

    private void markUsed(SavedAddressEntity entity) {
        entity.setUsageCount(entity.getUsageCount() + 1);
        entity.setLastUsedAt(LocalDateTime.now());
    }

    private SavedAddressEntity requireOwnedActive(UUID id, UUID ownerId) {
        return savedAddressRepository
                .findByIdAndCreatedByUserIdAndIsActiveTrue(id, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Saved address", "id", id));
    }

    private AddressFields requireRealAddress(SavedAddressRequest request) {
        if (isPlaceholder(request.getAddressLine(), request.getCity(), request.getState())) {
            throw new BusinessException("Enter a complete address before saving it.", HttpStatus.BAD_REQUEST);
        }
        AddressFields fields = AddressFields.from(
                request.getAddressLine(),
                request.getCity(),
                request.getState(),
                request.getPincode(),
                request.getMapUrl());
        if (!StringUtils.hasText(fields.pincode())) {
            throw new BusinessException("Enter a valid 6-digit pincode.", HttpStatus.BAD_REQUEST);
        }
        return fields;
    }

    static boolean isPlaceholder(String addressLine, String city, String state) {
        return isBlankOrPlaceholder(addressLine) && isBlankOrPlaceholder(city) && isBlankOrPlaceholder(state);
    }

    private static boolean isBlankOrPlaceholder(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        return PLACEHOLDER_MARK.equals(value.trim());
    }

    static String sanitizeSearch(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String collapsed = raw.trim().replaceAll("\\s+", " ");
        return collapsed.replace("%", "").replace("_", "").replace("\\", "").toLowerCase(Locale.ROOT);
    }

    static String fingerprint(AddressFields fields) {
        String material = String.join(
                "|",
                normalizeKey(fields.addressLine()),
                normalizeKey(fields.city()),
                normalizeKey(fields.state()),
                normalizeKey(fields.pincode()),
                normalizeKey(fields.mapUrl()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required to fingerprint saved addresses", ex);
        }
    }

    static String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static String normalizeStored(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private void applyFields(SavedAddressEntity entity, AddressFields fields, String fingerprint) {
        entity.setAddressLine(fields.addressLine());
        entity.setCity(fields.city());
        entity.setState(fields.state());
        entity.setPincode(fields.pincode());
        entity.setMapUrl(fields.mapUrl());
        entity.setFingerprint(fingerprint);
    }

    private SavedAddressResponse toResponse(SavedAddressEntity entity) {
        return SavedAddressResponse.builder()
                .id(entity.getId())
                .addressLine(entity.getAddressLine())
                .city(entity.getCity())
                .state(entity.getState())
                .pincode(entity.getPincode())
                .mapUrl(entity.getMapUrl())
                .usageCount(entity.getUsageCount())
                .lastUsedAt(entity.getLastUsedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private UUID currentAdminId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new BusinessException("Admin authentication is required", HttpStatus.UNAUTHORIZED);
        }
        if (!principal.isAdmin()) {
            throw new BusinessException("Only admins can manage saved addresses", HttpStatus.FORBIDDEN);
        }
        return principal.getId();
    }

    record AddressFields(String addressLine, String city, String state, String pincode, String mapUrl) {
        static AddressFields from(String addressLine, String city, String state, String pincode, String mapUrl) {
            String pin = normalizeStored(pincode);
            return new AddressFields(
                    normalizeStored(addressLine),
                    normalizeStored(city),
                    normalizeStored(state),
                    pin,
                    trimToNull(mapUrl));
        }
    }
}
