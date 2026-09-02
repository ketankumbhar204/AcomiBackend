package com.acomi.acomi_backend.admin.application.service;

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

    @Transactional(readOnly = true)
    public Page<MessRegistrationListItemResponse> list(
            MessRegistrationSource source, boolean leadsOnly, Pageable pageable) {
        Page<MessRegistrationEntity> page;
        if (source != null) {
            page = messRegistrationRepository.findBySourceOrderByCreatedAtDesc(source, pageable);
        } else if (leadsOnly) {
            page = messRegistrationRepository.findByStatusNotInOrderByCreatedAtDesc(CLOSED_STATUSES, pageable);
        } else {
            page = messRegistrationRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return page.map(MessRegistrationMapper::toListItem);
    }

    @Transactional(readOnly = true)
    public MessRegistrationDetailResponse getById(UUID id) {
        return MessRegistrationMapper.toDetail(messRegistrationService.requireEntity(id));
    }

    @Transactional
    public MessRegistrationResponse create(AdminCreateMessRegistrationRequest request) {
        MessRegistrationResponse response = messRegistrationService.registerAdmin(request);
        savedAddressService.rememberFromLead(
                request.getAddressLine(),
                request.getCity(),
                request.getState(),
                request.getPincode(),
                request.getMapUrl());
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
        return MessRegistrationMapper.toDetail(messRegistrationRepository.save(entity));
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
