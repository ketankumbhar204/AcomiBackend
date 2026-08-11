package com.amico.amico_backend.accommodation.application.service;

import com.amico.amico_backend.accommodation.domain.model.PropertyLayoutMode;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.amico.amico_backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccommodationLayoutService {

    public void assertVisibleUnitCreationAllowed(BuildingEntity building) {
        PropertyLayoutMode mode = building.getLayoutMode();
        if (mode == PropertyLayoutMode.CORRIDOR_PG) {
            throw new BusinessException(
                    "Visible apartments cannot be created in corridor PG layout. "
                            + "Use apartment PG layout or add rooms under a floor.",
                    HttpStatus.BAD_REQUEST);
        }
        if (mode == PropertyLayoutMode.RENTAL) {
            return;
        }
        if (mode != PropertyLayoutMode.APARTMENT_PG && mode != PropertyLayoutMode.CO_LIVING) {
            throw new BusinessException(
                    "Apartments are not supported for layout mode " + mode, HttpStatus.BAD_REQUEST);
        }
    }

    public void assertFloorScopedUnitCreationAllowed(BuildingEntity building) {
        if (building.getLayoutMode() != PropertyLayoutMode.APARTMENT_PG) {
            throw new BusinessException(
                    "Apartments under a floor are only supported in apartment PG layout",
                    HttpStatus.BAD_REQUEST);
        }
    }

    public void assertRoomCreationUnderFloor(BuildingEntity building) {
        if (building.getLayoutMode() == PropertyLayoutMode.APARTMENT_PG) {
            throw new BusinessException(
                    "Rooms cannot be created directly under a floor in apartment PG layout. "
                            + "Create the room under an apartment instead.",
                    HttpStatus.BAD_REQUEST);
        }
        if (building.getLayoutMode() == PropertyLayoutMode.RENTAL) {
            throw new BusinessException(
                    "Rooms are not supported for rental layout", HttpStatus.BAD_REQUEST);
        }
        if (building.getLayoutMode() != PropertyLayoutMode.CORRIDOR_PG) {
            throw new BusinessException(
                    "Direct floor rooms are only supported in corridor PG layout",
                    HttpStatus.BAD_REQUEST);
        }
    }

    public void assertRoomCreationUnderUnit(BuildingEntity building, UnitEntity unit) {
        PropertyLayoutMode mode = building.getLayoutMode();
        if (mode == PropertyLayoutMode.RENTAL) {
            throw new BusinessException(
                    "Rooms are not supported for rental layout", HttpStatus.BAD_REQUEST);
        }
        if (mode == PropertyLayoutMode.CORRIDOR_PG && !unit.isSynthetic()) {
            throw new BusinessException(
                    "Only internal synthetic apartments may hold rooms in corridor PG layout",
                    HttpStatus.BAD_REQUEST);
        }
        if (mode == PropertyLayoutMode.APARTMENT_PG) {
            if (unit.isSynthetic()) {
                throw new BusinessException(
                        "Rooms cannot be created under synthetic apartments in apartment PG layout",
                        HttpStatus.BAD_REQUEST);
            }
            if (unit.getFloor() == null) {
                throw new BusinessException(
                        "Apartment must belong to a floor in apartment PG layout",
                        HttpStatus.BAD_REQUEST);
            }
        }
        if (mode == PropertyLayoutMode.CO_LIVING && unit.isSynthetic()) {
            throw new BusinessException(
                    "Rooms cannot be created under synthetic apartments", HttpStatus.BAD_REQUEST);
        }
    }

    public void assertRoomParent(BuildingEntity building, FloorEntity floor, UnitEntity unit) {
        PropertyLayoutMode mode = building.getLayoutMode();
        if (mode == PropertyLayoutMode.APARTMENT_PG) {
            if (floor != null || unit == null) {
                throw new BusinessException(
                        "Rooms in apartment PG layout must belong to an apartment on a floor",
                        HttpStatus.BAD_REQUEST);
            }
            if (unit.getFloor() == null) {
                throw new BusinessException(
                        "Apartment must belong to a floor in apartment PG layout",
                        HttpStatus.BAD_REQUEST);
            }
            return;
        }
        if (mode == PropertyLayoutMode.CORRIDOR_PG) {
            if (unit != null && !unit.isSynthetic()) {
                throw new BusinessException(
                        "Visible apartments cannot hold rooms in corridor PG layout",
                        HttpStatus.BAD_REQUEST);
            }
            if (floor == null && unit == null) {
                throw new BusinessException(
                        "Room must belong to a floor or synthetic apartment", HttpStatus.BAD_REQUEST);
            }
            return;
        }
        if (mode == PropertyLayoutMode.CO_LIVING) {
            if (floor != null || unit == null) {
                throw new BusinessException(
                        "Rooms in co-living layout must belong to an apartment",
                        HttpStatus.BAD_REQUEST);
            }
            return;
        }
        throw new BusinessException("Rooms are not supported for rental layout", HttpStatus.BAD_REQUEST);
    }

    public void assertUnitUpdateAllowed(UnitEntity unit) {
        if (unit.isSynthetic()) {
            throw new BusinessException(
                    "Synthetic apartments cannot be edited directly", HttpStatus.BAD_REQUEST);
        }
    }

    public void assertUnitVisible(UnitEntity unit) {
        if (unit.isSynthetic()) {
            throw new BusinessException("Synthetic apartment not found", HttpStatus.NOT_FOUND);
        }
    }

    public void validateRoomEntity(RoomEntity room, BuildingEntity building) {
        assertRoomParent(building, room.getFloor(), room.getUnit());
    }
}
