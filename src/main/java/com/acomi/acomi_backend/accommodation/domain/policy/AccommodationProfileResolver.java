package com.acomi.acomi_backend.accommodation.domain.policy;

import com.acomi.acomi_backend.accommodation.domain.model.AccommodationProfile;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import org.springframework.stereotype.Component;

@Component
public class AccommodationProfileResolver {

    public AccommodationProfile resolve(SpaceType spaceType) {
        return switch (spaceType) {
            case PG, HOSTEL -> AccommodationProfile.forPgOrHostel();
            case CO_LIVING -> AccommodationProfile.forCoLiving();
            case RENTAL -> AccommodationProfile.forRental();
            case MESS -> throw new IllegalArgumentException("Accommodation is not applicable for Mess spaces");
        };
    }

    public boolean isAccommodationApplicable(SpaceType spaceType) {
        return spaceType != SpaceType.MESS;
    }
}
