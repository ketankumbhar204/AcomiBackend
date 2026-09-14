package com.acomi.acomi_backend.admin.application.service;

import com.acomi.acomi_backend.admin.api.dto.response.AdminPropertyRegistrationsSummaryResponse;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.property.api.dto.request.AdminCreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationDetailResponse;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationListItemResponse;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationResponse;
import com.acomi.acomi_backend.property.application.mapper.PropertyRegistrationMapper;
import com.acomi.acomi_backend.property.application.service.PropertyRegistrationService;
import com.acomi.acomi_backend.registration.api.dto.request.AdminUpdateRegistrationContactRequest;
import com.acomi.acomi_backend.registration.application.RegistrationMobiles;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.address.application.service.SavedAddressService;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminPropertyRegistrationService {

    private static final List<PropertyRegistrationStatus> CLOSED_STATUSES =
            List.of(PropertyRegistrationStatus.CONVERTED, PropertyRegistrationStatus.REJECTED);

    private static final List<SpaceType> PROPERTY_SPACE_TYPES =
            List.of(SpaceType.PG, SpaceType.HOSTEL, SpaceType.CO_LIVING, SpaceType.RENTAL);

    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final PropertyRegistrationService propertyRegistrationService;
    private final SavedAddressService savedAddressService;
    private final AdminRegistrationConversionService adminRegistrationConversionService;
    private final SpaceRepository spaceRepository;

    @Transactional(readOnly = true)
    public Page<PropertyRegistrationListItemResponse> list(
            PropertyRegistrationSource source, boolean leadsOnly, Pageable pageable) {
        return list(null, source, null, leadsOnly, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PropertyRegistrationListItemResponse> list(
            String q,
            PropertyRegistrationSource source,
            PropertyRegistrationStatus status,
            boolean leadsOnly,
            Boolean claimed,
            Pageable pageable) {
        String query = StringUtils.hasText(q) ? q.trim() : null;
        boolean filtered = query != null || source != null || status != null || claimed != null || leadsOnly;
        Page<PropertyRegistrationEntity> page;
        if (filtered) {
            page = propertyRegistrationRepository.searchFiltered(
                    query, source, status, leadsOnly, CLOSED_STATUSES, claimed, pageable);
        } else {
            page = propertyRegistrationRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return page.map(PropertyRegistrationMapper::toListItem);
    }

    @Transactional(readOnly = true)
    public AdminPropertyRegistrationsSummaryResponse summary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last30From = now.minusDays(30);
        LocalDateTime prev30From = now.minusDays(60);

        // Total = open leads + active spaces (matches what admins can see in list tabs).
        // Closed registrations (CONVERTED/REJECTED) are excluded even if the DB row remains.
        long leads = propertyRegistrationRepository.countByStatusNotIn(CLOSED_STATUSES);
        long active = PROPERTY_SPACE_TYPES.stream()
                .mapToLong(spaceRepository::countByTypeAndIsActiveTrue)
                .sum();
        long total = leads + active;
        long byOwners = propertyRegistrationRepository.countBySource(PropertyRegistrationSource.PUBLIC_WEBSITE);

        long leadsNow = propertyRegistrationRepository.countByStatusNotInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                CLOSED_STATUSES, last30From, now);
        long leadsPrev =
                propertyRegistrationRepository.countByStatusNotInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        CLOSED_STATUSES, prev30From, last30From);
        long activeNow = spaceRepository.countByTypeInAndIsActiveTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                PROPERTY_SPACE_TYPES, last30From, now);
        long activePrev = spaceRepository.countByTypeInAndIsActiveTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                PROPERTY_SPACE_TYPES, prev30From, last30From);
        long ownersNow = propertyRegistrationRepository.countBySourceAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                PropertyRegistrationSource.PUBLIC_WEBSITE, last30From, now);
        long ownersPrev = propertyRegistrationRepository.countBySourceAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                PropertyRegistrationSource.PUBLIC_WEBSITE, prev30From, last30From);

        return AdminPropertyRegistrationsSummaryResponse.builder()
                .totalProperties(total)
                .leads(leads)
                .activeProperties(active)
                .registeredByOwners(byOwners)
                .totalPropertiesDeltaPercent(deltaPercent(leadsNow + activeNow, leadsPrev + activePrev))
                .leadsDeltaPercent(deltaPercent(leadsNow, leadsPrev))
                .activePropertiesDeltaPercent(deltaPercent(activeNow, activePrev))
                .registeredByOwnersDeltaPercent(deltaPercent(ownersNow, ownersPrev))
                .build();
    }

    private static Double deltaPercent(long current, long previous) {
        if (previous == 0) {
            return current == 0 ? 0.0 : 100.0;
        }
        return ((current - previous) * 100.0) / previous;
    }

    @Transactional(readOnly = true)
    public PropertyRegistrationDetailResponse getById(UUID id) {
        return adminRegistrationConversionService.enrichPropertyDetail(
                propertyRegistrationService.requireEntity(id));
    }

    @Transactional
    public PropertyRegistrationResponse create(AdminCreatePropertyRegistrationRequest request) {
        PropertyRegistrationResponse response = propertyRegistrationService.registerAdmin(request);
        savedAddressService.rememberFromLead(
                request.getAddressLine(),
                request.getCity(),
                request.getState(),
                request.getPincode(),
                request.getMapUrl(),
                com.acomi.acomi_backend.address.domain.model.SavedAddressLeadKind.PROPERTY);
        propertyRegistrationRepository
                .findByReference(response.getReference())
                .ifPresent(entity -> adminRegistrationConversionService.convertProperty(entity.getId()));
        return response;
    }

    @Transactional
    public PropertyRegistrationDetailResponse updateContact(UUID id, AdminUpdateRegistrationContactRequest request) {
        PropertyRegistrationEntity entity = propertyRegistrationService.requireEntity(id);
        if (StringUtils.hasText(request.getOwnerName())) {
            entity.setOwnerName(request.getOwnerName().trim());
        }
        if (StringUtils.hasText(request.getMobileNumber())) {
            entity.setMobileNumber(request.getMobileNumber().trim());
        }
        entity.setAlternateMobileNumber(
                RegistrationMobiles.resolveAlternate(entity.getMobileNumber(), request.getAlternateMobileNumber()));
        return adminRegistrationConversionService.enrichPropertyDetail(
                propertyRegistrationRepository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        PropertyRegistrationEntity entity = propertyRegistrationService.requireEntity(id);
        if (entity.getStatus() == PropertyRegistrationStatus.CONVERTED || entity.getConvertedSpaceId() != null) {
            throw new BusinessException(
                    "This registration has been converted to an active space and cannot be deleted",
                    HttpStatus.CONFLICT);
        }
        propertyRegistrationRepository.delete(entity);
    }
}
