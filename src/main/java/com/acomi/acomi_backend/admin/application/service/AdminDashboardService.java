package com.acomi.acomi_backend.admin.application.service;

import com.acomi.acomi_backend.admin.api.dto.response.AdminActiveSpaceResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminDashboardSummaryResponse;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final List<SpaceType> PROPERTY_SPACE_TYPES =
            List.of(SpaceType.PG, SpaceType.HOSTEL, SpaceType.CO_LIVING, SpaceType.RENTAL);

    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final MessRegistrationRepository messRegistrationRepository;
    private final SpaceRepository spaceRepository;

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getSummary() {
        long activePropertySpaces = PROPERTY_SPACE_TYPES.stream()
                .mapToLong(spaceRepository::countByTypeAndIsActiveTrue)
                .sum();
        long activeMessSpaces = spaceRepository.countByTypeAndIsActiveTrue(SpaceType.MESS);

        return AdminDashboardSummaryResponse.builder()
                .propertyRegistrationCount(propertyRegistrationRepository.count())
                .messRegistrationCount(messRegistrationRepository.count())
                .adminPropertyLeads(propertyRegistrationRepository.countBySource(PropertyRegistrationSource.ADMIN))
                .adminMessLeads(messRegistrationRepository.countBySource(MessRegistrationSource.ADMIN))
                .websitePropertyLeads(
                        propertyRegistrationRepository.countBySource(PropertyRegistrationSource.PUBLIC_WEBSITE))
                .websiteMessLeads(messRegistrationRepository.countBySource(MessRegistrationSource.PUBLIC_WEBSITE))
                .unclaimedAdminPropertyLeads(propertyRegistrationRepository.countByClaimedAtIsNullAndSource(
                        PropertyRegistrationSource.ADMIN))
                .unclaimedAdminMessLeads(
                        messRegistrationRepository.countByClaimedAtIsNullAndSource(MessRegistrationSource.ADMIN))
                .claimedPropertyLeads(propertyRegistrationRepository.countByClaimedAtIsNotNull())
                .claimedMessLeads(messRegistrationRepository.countByClaimedAtIsNotNull())
                .activePropertySpaces(activePropertySpaces)
                .activeMessSpaces(activeMessSpaces)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AdminActiveSpaceResponse> listActiveSpaces(SpaceType type) {
        List<SpaceEntity> spaces;
        if (type == null) {
            spaces = spaceRepository.findActiveByTypes(
                    List.of(SpaceType.PG, SpaceType.HOSTEL, SpaceType.CO_LIVING, SpaceType.RENTAL, SpaceType.MESS));
        } else if (type == SpaceType.MESS) {
            spaces = spaceRepository.findByTypeAndIsActiveTrue(SpaceType.MESS);
        } else {
            spaces = spaceRepository.findByTypeAndIsActiveTrue(type);
        }

        return spaces.stream().map(this::toActiveSpace).toList();
    }

    private AdminActiveSpaceResponse toActiveSpace(SpaceEntity space) {
        return AdminActiveSpaceResponse.builder()
                .id(space.getId())
                .name(space.getName())
                .type(space.getType())
                .address(space.getAddress())
                .contactNumber(space.getContactNumber())
                .ownerId(space.getOwner().getId())
                .ownerName(space.getOwner().getFullName())
                .ownerMobile(space.getOwner().getMobileNumber())
                .createdAt(space.getCreatedAt())
                .build();
    }
}
