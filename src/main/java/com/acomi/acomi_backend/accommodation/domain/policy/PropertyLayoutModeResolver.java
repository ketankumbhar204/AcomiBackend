package com.acomi.acomi_backend.accommodation.domain.policy;

import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PropertyLayoutModeResolver {

    public PropertyLayoutMode defaultForSpaceType(SpaceType spaceType) {
        return switch (spaceType) {
            case PG, HOSTEL -> PropertyLayoutMode.CORRIDOR_PG;
            case CO_LIVING -> PropertyLayoutMode.CO_LIVING;
            case RENTAL -> PropertyLayoutMode.RENTAL;
            default -> throw new BusinessException(
                    "Layout mode is not defined for space type " + spaceType, HttpStatus.BAD_REQUEST);
        };
    }

    public void assertLayoutCompatibleWithSpaceType(SpaceType spaceType, PropertyLayoutMode layoutMode) {
        switch (spaceType) {
            case PG, HOSTEL -> {
                if (layoutMode != PropertyLayoutMode.CORRIDOR_PG
                        && layoutMode != PropertyLayoutMode.APARTMENT_PG) {
                    throw new BusinessException(
                            "PG and Hostel spaces support CORRIDOR_PG or APARTMENT_PG layout only",
                            HttpStatus.BAD_REQUEST);
                }
            }
            case CO_LIVING -> {
                if (layoutMode != PropertyLayoutMode.CO_LIVING) {
                    throw new BusinessException(
                            "Co-Living spaces require CO_LIVING layout mode", HttpStatus.BAD_REQUEST);
                }
            }
            case RENTAL -> {
                if (layoutMode != PropertyLayoutMode.RENTAL) {
                    throw new BusinessException(
                            "Rental spaces require RENTAL layout mode", HttpStatus.BAD_REQUEST);
                }
            }
            default -> throw new BusinessException(
                    "Accommodation layout is not supported for this space type", HttpStatus.BAD_REQUEST);
        }
    }
}
