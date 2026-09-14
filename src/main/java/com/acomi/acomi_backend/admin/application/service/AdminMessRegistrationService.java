package com.acomi.acomi_backend.admin.application.service;

import com.acomi.acomi_backend.admin.api.dto.response.AdminMessRegistrationsSummaryResponse;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.mess.api.dto.request.AdminCreateMessRegistrationRequest;
import com.acomi.acomi_backend.mess.api.dto.response.MessRegistrationDetailResponse;
import com.acomi.acomi_backend.mess.api.dto.response.MessRegistrationListItemResponse;
import com.acomi.acomi_backend.mess.api.dto.response.MessRegistrationResponse;
import com.acomi.acomi_backend.mess.application.mapper.MessRegistrationMapper;
import com.acomi.acomi_backend.mess.application.service.MessRegistrationService;
import com.acomi.acomi_backend.registration.api.dto.request.AdminUpdateRegistrationContactRequest;
import com.acomi.acomi_backend.registration.application.RegistrationMobiles;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
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
public class AdminMessRegistrationService {

    private static final List<MessRegistrationStatus> CLOSED_STATUSES =
            List.of(MessRegistrationStatus.CONVERTED, MessRegistrationStatus.REJECTED);

    private final MessRegistrationRepository messRegistrationRepository;
    private final MessRegistrationService messRegistrationService;
    private final SavedAddressService savedAddressService;
    private final AdminRegistrationConversionService adminRegistrationConversionService;
    private final SpaceRepository spaceRepository;

    @Transactional(readOnly = true)
    public Page<MessRegistrationListItemResponse> list(
            MessRegistrationSource source, boolean leadsOnly, Pageable pageable) {
        return list(null, source, null, leadsOnly, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<MessRegistrationListItemResponse> list(
            String q,
            MessRegistrationSource source,
            MessRegistrationStatus status,
            boolean leadsOnly,
            Boolean claimed,
            Pageable pageable) {
        String query = StringUtils.hasText(q) ? q.trim() : null;
        boolean filtered = query != null || source != null || status != null || claimed != null || leadsOnly;
        Page<MessRegistrationEntity> page;
        if (filtered) {
            page = messRegistrationRepository.searchFiltered(
                    query, source, status, leadsOnly, CLOSED_STATUSES, claimed, pageable);
        } else {
            page = messRegistrationRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return page.map(MessRegistrationMapper::toListItem);
    }

    @Transactional(readOnly = true)
    public AdminMessRegistrationsSummaryResponse summary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last30From = now.minusDays(30);
        LocalDateTime prev30From = now.minusDays(60);

        // Total = open leads + active mess spaces (matches list tabs; excludes closed registrations).
        long leads = messRegistrationRepository.countByStatusNotIn(CLOSED_STATUSES);
        long active = spaceRepository.countByTypeAndIsActiveTrue(SpaceType.MESS);
        long total = leads + active;
        long byVendors = messRegistrationRepository.countBySource(MessRegistrationSource.PUBLIC_WEBSITE);

        long leadsNow = messRegistrationRepository.countByStatusNotInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                CLOSED_STATUSES, last30From, now);
        long leadsPrev = messRegistrationRepository.countByStatusNotInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                CLOSED_STATUSES, prev30From, last30From);
        long activeNow = spaceRepository.countByTypeInAndIsActiveTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                List.of(SpaceType.MESS), last30From, now);
        long activePrev = spaceRepository.countByTypeInAndIsActiveTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                List.of(SpaceType.MESS), prev30From, last30From);
        long vendorsNow = messRegistrationRepository.countBySourceAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                MessRegistrationSource.PUBLIC_WEBSITE, last30From, now);
        long vendorsPrev = messRegistrationRepository.countBySourceAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                MessRegistrationSource.PUBLIC_WEBSITE, prev30From, last30From);

        return AdminMessRegistrationsSummaryResponse.builder()
                .totalMess(total)
                .leads(leads)
                .activeMess(active)
                .registeredByVendors(byVendors)
                .totalMessDeltaPercent(deltaPercent(leadsNow + activeNow, leadsPrev + activePrev))
                .leadsDeltaPercent(deltaPercent(leadsNow, leadsPrev))
                .activeMessDeltaPercent(deltaPercent(activeNow, activePrev))
                .registeredByVendorsDeltaPercent(deltaPercent(vendorsNow, vendorsPrev))
                .build();
    }

    private static Double deltaPercent(long current, long previous) {
        if (previous == 0) {
            return current == 0 ? 0.0 : 100.0;
        }
        return ((current - previous) * 100.0) / previous;
    }

    @Transactional(readOnly = true)
    public MessRegistrationDetailResponse getById(UUID id) {
        return adminRegistrationConversionService.enrichMessDetail(messRegistrationService.requireEntity(id));
    }

    @Transactional
    public MessRegistrationResponse create(AdminCreateMessRegistrationRequest request) {
        MessRegistrationResponse response = messRegistrationService.registerAdmin(request);
        savedAddressService.rememberFromLead(
                request.getAddressLine(),
                request.getCity(),
                request.getState(),
                request.getPincode(),
                request.getMapUrl(),
                com.acomi.acomi_backend.address.domain.model.SavedAddressLeadKind.MESS);
        messRegistrationRepository
                .findByReference(response.getReference())
                .ifPresent(entity -> adminRegistrationConversionService.convertMess(entity.getId()));
        return response;
    }

    @Transactional
    public MessRegistrationDetailResponse updateContact(UUID id, AdminUpdateRegistrationContactRequest request) {
        MessRegistrationEntity entity = messRegistrationService.requireEntity(id);
        if (StringUtils.hasText(request.getOwnerName())) {
            entity.setOwnerName(request.getOwnerName().trim());
        }
        if (StringUtils.hasText(request.getMobileNumber())) {
            entity.setMobileNumber(request.getMobileNumber().trim());
        }
        entity.setAlternateMobileNumber(
                RegistrationMobiles.resolveAlternate(entity.getMobileNumber(), request.getAlternateMobileNumber()));
        return adminRegistrationConversionService.enrichMessDetail(messRegistrationRepository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        MessRegistrationEntity entity = messRegistrationService.requireEntity(id);
        if (entity.getStatus() == MessRegistrationStatus.CONVERTED || entity.getConvertedSpaceId() != null) {
            throw new BusinessException(
                    "This registration has been converted to an active mess and cannot be deleted",
                    HttpStatus.CONFLICT);
        }
        messRegistrationRepository.delete(entity);
    }
}
