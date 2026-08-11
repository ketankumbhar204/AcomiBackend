package com.amico.amico_backend.accommodation.application.service;

import com.amico.amico_backend.accommodation.domain.model.AccommodationProfile;
import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.space.domain.model.SpaceType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccommodationProfileService {

    private final AccommodationAccessService accessService;

    public AccommodationProfile profileForSpace(UUID spaceId) {
        return accessService.loadProfile(spaceId);
    }

    public void assertFloorsAllowed(UUID spaceId) {
        AccommodationProfile profile = profileForSpace(spaceId);
        if (!profile.isFloorsAllowed()) {
            throw new BusinessException(
                    "Floors are not supported for " + spaceTypeLabel(spaceId) + " spaces");
        }
    }

    public void assertUnitsAllowed(UUID spaceId) {
        AccommodationProfile profile = profileForSpace(spaceId);
        if (!profile.isUnitsAllowed()) {
            throw new BusinessException(
                    "Units are not supported for " + spaceTypeLabel(spaceId) + " spaces");
        }
    }

    public void assertRoomsUnderFloorAllowed(UUID spaceId) {
        AccommodationProfile profile = profileForSpace(spaceId);
        if (!profile.isRoomsUnderFloorAllowed()) {
            throw new BusinessException(
                    "Rooms under floors are not supported for " + spaceTypeLabel(spaceId) + " spaces");
        }
    }

    public void assertRoomsUnderUnitAllowed(UUID spaceId) {
        AccommodationProfile profile = profileForSpace(spaceId);
        if (!profile.isRoomsUnderUnitAllowed()) {
            throw new BusinessException(
                    "Rooms under units are not supported for " + spaceTypeLabel(spaceId) + " spaces");
        }
    }

    public void assertBedsAllowed(UUID spaceId) {
        AccommodationProfile profile = profileForSpace(spaceId);
        if (!profile.isBedsAllowed()) {
            throw new BusinessException(
                    "Beds are not supported for " + spaceTypeLabel(spaceId) + " spaces");
        }
    }

    private String spaceTypeLabel(UUID spaceId) {
        SpaceType type = accessService.loadSpaceType(spaceId);
        return type.name();
    }
}
