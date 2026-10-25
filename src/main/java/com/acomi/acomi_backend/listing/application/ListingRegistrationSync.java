package com.acomi.acomi_backend.listing.application;

import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Copies space-level listing fields onto the converted registration so discovery, which reads
 * some fields from the lead, stays aligned with {@code PUT /spaces/{id}}. Prices and bed rents
 * are intentionally not copied.
 */
@Component
@RequiredArgsConstructor
public class ListingRegistrationSync {

    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final MessRegistrationRepository messRegistrationRepository;

    public void syncFromSpace(SpaceEntity space) {
        propertyRegistrationRepository.findByConvertedSpaceId(space.getId()).ifPresent(registration -> {
            applyProperty(registration, space);
            propertyRegistrationRepository.save(registration);
        });
        messRegistrationRepository.findByConvertedSpaceId(space.getId()).ifPresent(registration -> {
            applyMess(registration, space);
            messRegistrationRepository.save(registration);
        });
    }

    private static void applyProperty(PropertyRegistrationEntity registration, SpaceEntity space) {
        registration.setPropertyName(space.getName());
        registration.setLatitude(space.getLatitude());
        registration.setLongitude(space.getLongitude());
        if (space.getGenderPolicy() != null) {
            registration.setGenderPolicy(space.getGenderPolicy());
        }
        registration.setFoodIncludedListing(space.isFoodIncludedInRent());
    }

    private static void applyMess(MessRegistrationEntity registration, SpaceEntity space) {
        registration.setMessName(space.getName());
        registration.setLatitude(space.getLatitude());
        registration.setLongitude(space.getLongitude());
        if (space.getGenderPolicy() != null) {
            registration.setGenderPolicy(space.getGenderPolicy());
        }
        registration.setFoodIncludedListing(space.isFoodIncludedInRent());
    }
}
