package com.countin.countin_backend.space.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Computed capabilities for the caller in a space")
public class SpacePermissionsResponse {

    private boolean canViewAccommodation;
    private boolean canManageAccommodation;
    private boolean canDeactivateAccommodation;
    private boolean canManageOccupancy;
    private boolean canViewSpaceOccupancies;
    private boolean canManageMembers;
    private boolean canRemoveMember;
    private boolean canManageMeals;
    private boolean canViewMeals;
    private boolean canManageMealParticipation;
    private boolean canViewOwnMealParticipation;
    private boolean canRaiseComplaint;
    private boolean canViewAllComplaints;
    private boolean canManageComplaints;
}
