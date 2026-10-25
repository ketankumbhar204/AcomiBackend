package com.acomi.acomi_backend.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListingRegistrationSyncTest {

    @Mock
    private PropertyRegistrationRepository propertyRegistrationRepository;
    @Mock
    private MessRegistrationRepository messRegistrationRepository;
    @InjectMocks
    private ListingRegistrationSync sync;

    @Test
    void copiesNameGenderAndFoodWithoutChangingListingPrice() {
        UUID spaceId = UUID.randomUUID();
        SpaceEntity space = new SpaceEntity();
        space.setId(spaceId);
        space.setName("Renamed PG");
        space.setGenderPolicy(GenderPolicy.FEMALE);
        space.setFoodIncludedInRent(true);
        PropertyRegistrationEntity registration = new PropertyRegistrationEntity();
        registration.setPropertyName("Old");
        registration.setStartingPrice(new BigDecimal("4500"));
        registration.setGenderPolicy(GenderPolicy.MALE);
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));
        when(messRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());

        sync.syncFromSpace(space);

        assertThat(registration.getPropertyName()).isEqualTo("Renamed PG");
        assertThat(registration.getGenderPolicy()).isEqualTo(GenderPolicy.FEMALE);
        assertThat(registration.getFoodIncludedListing()).isTrue();
        assertThat(registration.getStartingPrice()).isEqualByComparingTo("4500");
    }

    @Test
    void missingSpaceGenderDoesNotClearRegistrationGender() {
        UUID spaceId = UUID.randomUUID();
        SpaceEntity space = new SpaceEntity();
        space.setId(spaceId);
        space.setName("Same");
        PropertyRegistrationEntity registration = new PropertyRegistrationEntity();
        registration.setGenderPolicy(GenderPolicy.MALE);
        when(propertyRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.of(registration));
        when(messRegistrationRepository.findByConvertedSpaceId(spaceId)).thenReturn(Optional.empty());

        sync.syncFromSpace(space);

        assertThat(registration.getGenderPolicy()).isEqualTo(GenderPolicy.MALE);
    }
}
