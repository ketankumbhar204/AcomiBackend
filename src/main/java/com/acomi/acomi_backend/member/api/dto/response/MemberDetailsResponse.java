package com.acomi.acomi_backend.member.api.dto.response;

import com.acomi.acomi_backend.meal.api.dto.response.MemberMealParticipationSummaryResponse;
import com.acomi.acomi_backend.member.domain.model.MemberGender;
import com.acomi.acomi_backend.member.domain.model.MemberStatus;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.occupancy.api.dto.response.CurrentOccupancySummaryResponse;
import com.acomi.acomi_backend.occupancy.domain.model.MemberOccupancyStatus;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.domain.model.MealBillingType;
import com.acomi.acomi_backend.user.api.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@Schema(description = "Complete member master record details")
public class MemberDetailsResponse {

    private UUID memberId;
    private UUID spaceId;
    private String fullName;
    private String mobileNumber;

    @Schema(description = "Member role in the space")
    private MembershipRole role;

    @Schema(description = "Whether this member is linked to a registered User account")
    private boolean linkedUser;

    private UUID linkedUserId;

    @Schema(description = "Internal space membership link when the member has app access")
    private UUID membershipId;

    private boolean active;

    @Schema(description = "Operational member status (distinct from soft-delete isActive)")
    private MemberStatus status;

    @Schema(description = "Whether the member currently holds an accommodation allocation")
    private MemberOccupancyStatus occupancyStatus;

    @Schema(description = "Member gender when recorded")
    private MemberGender gender;

    @Schema(description = "Current accommodation assignment when occupancyStatus is ALLOCATED")
    private CurrentOccupancySummaryResponse currentOccupancy;

    @Schema(description = "Active meal participation when enrolled")
    private MemberMealParticipationSummaryResponse mealParticipation;

    private LocalDateTime statusUpdatedAt;
    private String emergencyContactName;
    private String emergencyContactRelation;
    private String emergencyContactMobile;
    private BigDecimal depositAmount;
    private BigDecimal depositPaid;
    private BigDecimal depositRefunded;

    @Schema(description = "Computed deposit balance: depositPaid minus depositRefunded")
    private BigDecimal depositBalance;

    @Schema(description = "Member meal billing override; null inherits the space default")
    private MealBillingType mealBillingType;

    @Schema(description = "Effective meal billing type after applying space default")
    private MealBillingType effectiveMealBillingType;

    @Schema(description = "Amenities assigned on the current occupancy")
    private List<AmenityAssignmentDto> assignedAmenities;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Schema(description = "Linked app user profile when visible to the caller")
    private UserResponse linkedUserProfile;

    public static MemberDetailsResponse from(MemberEntity member) {
        return from(member, null);
    }

    public static MemberDetailsResponse from(
            MemberEntity member, CurrentOccupancySummaryResponse currentOccupancy) {
        return from(member, currentOccupancy, null);
    }

    public static MemberDetailsResponse from(
            MemberEntity member,
            CurrentOccupancySummaryResponse currentOccupancy,
            MemberMealParticipationSummaryResponse mealParticipation) {
        return from(member, currentOccupancy, mealParticipation, List.of());
    }

    public static MemberDetailsResponse from(
            MemberEntity member,
            CurrentOccupancySummaryResponse currentOccupancy,
            MemberMealParticipationSummaryResponse mealParticipation,
            List<AmenityAssignmentDto> assignedAmenities) {
        BigDecimal depositBalance = member.getDepositPaid().subtract(member.getDepositRefunded());
        return MemberDetailsResponse.builder()
                .memberId(member.getId())
                .spaceId(member.getSpace().getId())
                .fullName(member.getFullName())
                .mobileNumber(member.getMobileNumber())
                .role(member.getRole())
                .linkedUser(member.getUser() != null)
                .linkedUserId(member.getUser() != null ? member.getUser().getId() : null)
                .membershipId(member.getMembership() != null ? member.getMembership().getId() : null)
                .active(member.isActive())
                .status(member.getStatus())
                .occupancyStatus(member.getOccupancyStatus())
                .gender(member.getGender())
                .currentOccupancy(currentOccupancy)
                .mealParticipation(mealParticipation)
                .statusUpdatedAt(member.getStatusUpdatedAt())
                .emergencyContactName(member.getEmergencyContactName())
                .emergencyContactRelation(member.getEmergencyContactRelation())
                .emergencyContactMobile(member.getEmergencyContactMobile())
                .depositAmount(member.getDepositAmount())
                .depositPaid(member.getDepositPaid())
                .depositRefunded(member.getDepositRefunded())
                .depositBalance(depositBalance)
                .mealBillingType(member.getMealBillingType())
                .effectiveMealBillingType(resolveEffectiveMealBillingType(member))
                .assignedAmenities(assignedAmenities)
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }

    private static MealBillingType resolveEffectiveMealBillingType(MemberEntity member) {
        if (member.getMealBillingType() != null) {
            return member.getMealBillingType();
        }
        if (member.getSpace() != null && member.getSpace().getMealBillingType() != null) {
            return member.getSpace().getMealBillingType();
        }
        return MealBillingType.PAY_PER_MEAL;
    }
}
