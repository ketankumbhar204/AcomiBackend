package com.acomi.acomi_backend.meal.application.support;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Owner of a space is the menu-library account. Callers never supply this id. */
public final class MenuLibraryOwnership {

    private MenuLibraryOwnership() {}

    public static UUID ownerId(SpaceEntity space) {
        if (space.getOwner() == null || space.getOwner().getId() == null) {
            throw new BusinessException("Space has no owner", HttpStatus.CONFLICT);
        }
        return space.getOwner().getId();
    }

    /**
     * A custom library row belongs to this space when its owner matches.
     * Rows saved before the owner column was filled still match their original space.
     */
    public static boolean sameLibrary(SpaceEntity space, UserEntity recordOwner, SpaceEntity originSpace) {
        UUID currentOwnerId = ownerId(space);
        if (recordOwner != null && recordOwner.getId() != null) {
            return currentOwnerId.equals(recordOwner.getId());
        }
        return originSpace != null && space.getId().equals(originSpace.getId());
    }

    public static void requireSameLibrary(
            SpaceEntity space, UserEntity recordOwner, SpaceEntity originSpace, String message) {
        if (!sameLibrary(space, recordOwner, originSpace)) {
            throw new BusinessException(message, HttpStatus.FORBIDDEN);
        }
    }
}
