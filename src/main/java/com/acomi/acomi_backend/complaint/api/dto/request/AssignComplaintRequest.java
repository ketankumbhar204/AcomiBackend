package com.acomi.acomi_backend.complaint.api.dto.request;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignComplaintRequest {

    /** Target space membership (STAFF / MANAGER). Null clears assignment. */
    private UUID assigneeMembershipId;
}
