package com.acomi.acomi_backend.admin.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.property.api.dto.request.AdminCreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationDetailResponse;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationListItemResponse;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationResponse;
import com.acomi.acomi_backend.property.application.mapper.PropertyRegistrationMapper;
import com.acomi.acomi_backend.property.application.service.PropertyRegistrationService;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPropertyRegistrationService {

    private static final List<PropertyRegistrationStatus> CLOSED_STATUSES =
            List.of(PropertyRegistrationStatus.CONVERTED, PropertyRegistrationStatus.REJECTED);

    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final PropertyRegistrationService propertyRegistrationService;

    @Transactional(readOnly = true)
    public Page<PropertyRegistrationListItemResponse> list(
            PropertyRegistrationSource source, boolean leadsOnly, Pageable pageable) {
        Page<PropertyRegistrationEntity> page;
        if (source != null) {
            page = propertyRegistrationRepository.findBySourceOrderByCreatedAtDesc(source, pageable);
        } else if (leadsOnly) {
            page = propertyRegistrationRepository.findByStatusNotInOrderByCreatedAtDesc(CLOSED_STATUSES, pageable);
        } else {
            page = propertyRegistrationRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return page.map(PropertyRegistrationMapper::toListItem);
    }

    @Transactional(readOnly = true)
    public PropertyRegistrationDetailResponse getById(UUID id) {
        return PropertyRegistrationMapper.toDetail(propertyRegistrationService.requireEntity(id));
    }

    @Transactional
    public PropertyRegistrationResponse create(AdminCreatePropertyRegistrationRequest request) {
        return propertyRegistrationService.registerAdmin(request);
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
