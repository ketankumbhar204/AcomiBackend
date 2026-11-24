package com.acomi.acomi_backend.servicecontact.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.servicecontact.application.CallerScope;
import com.acomi.acomi_backend.servicecontact.infrastructure.persistence.entity.ServiceContactEntity;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServiceContactAccessService {

    private static final Set<MembershipRole> KNOWN =
            EnumSet.of(
                    MembershipRole.OWNER,
                    MembershipRole.MANAGER,
                    MembershipRole.STAFF,
                    MembershipRole.TENANT,
                    MembershipRole.CUSTOMER);

    private final SpaceMembershipRepository membershipRepository;

    public CallerScope requireDirectoryAccess(UUID callerId) {
        List<SpaceMembershipEntity> memberships = membershipRepository.findUserSpaces(callerId);
        boolean hasOwner = false;
        boolean hasOperator = false;
        boolean hasUnknown = false;
        Set<UUID> manageable = new HashSet<>();
        Set<UUID> visible = new HashSet<>();

        for (SpaceMembershipEntity membership : memberships) {
            MembershipRole role = membership.getRole();
            if (role == null || !KNOWN.contains(role)) {
                hasUnknown = true;
                continue;
            }
            UUID spaceId = membership.getSpace().getId();
            switch (role) {
                case OWNER -> {
                    hasOwner = true;
                    hasOperator = true;
                    manageable.add(spaceId);
                    visible.add(spaceId);
                }
                case MANAGER -> {
                    hasOperator = true;
                    manageable.add(spaceId);
                    visible.add(spaceId);
                }
                case STAFF -> {
                    hasOperator = true;
                    visible.add(spaceId);
                }
                case TENANT, CUSTOMER -> {
                    // Private supplier directory is not part of member access.
                }
                default -> hasUnknown = true;
            }
        }

        if (hasUnknown && !hasOperator && !hasOwner) {
            deny("You do not have permission to view service contacts");
        }
        if (!memberships.isEmpty() && !hasOperator) {
            deny("You do not have permission to view service contacts");
        }

        boolean ownsDirectory = memberships.isEmpty() || hasOwner;
        Set<UUID> directoryOwners = new HashSet<>();
        if (ownsDirectory) {
            directoryOwners.add(callerId);
        }
        for (SpaceMembershipEntity membership : memberships) {
            MembershipRole role = membership.getRole();
            if (role != MembershipRole.OWNER && role != MembershipRole.MANAGER && role != MembershipRole.STAFF) {
                continue;
            }
            if (membership.getSpace() == null || membership.getSpace().getOwner() == null) {
                continue;
            }
            UUID accountOwnerId = membership.getSpace().getOwner().getId();
            if (accountOwnerId != null) {
                directoryOwners.add(accountOwnerId);
            }
        }
        return new CallerScope(
                callerId, ownsDirectory, Set.copyOf(manageable), Set.copyOf(visible), Set.copyOf(directoryOwners));
    }

    public void requireManageCapability(CallerScope scope) {
        if (!scope.ownsAccountDirectory() && scope.manageableSpaceIds().isEmpty()) {
            deny("You do not have permission to manage service contacts");
        }
    }

    public boolean canRead(CallerScope scope, ServiceContactEntity contact) {
        return contact != null
                && contact.getOwnerUserId() != null
                && scope.directoryOwnerIds().contains(contact.getOwnerUserId());
    }

    public boolean canEdit(CallerScope scope, ServiceContactEntity contact) {
        if (!canRead(scope, contact) || !contact.isActive()) {
            return false;
        }
        if (scope.ownsAccountDirectory() && scope.callerId().equals(contact.getOwnerUserId())) {
            return true;
        }
        return !scope.manageableSpaceIds().isEmpty()
                && scope.directoryOwnerIds().contains(contact.getOwnerUserId());
    }

    public boolean canArchive(CallerScope scope, ServiceContactEntity contact) {
        return contact != null
                && contact.isActive()
                && scope.ownsAccountDirectory()
                && scope.callerId().equals(contact.getOwnerUserId());
    }

    public void requireRead(CallerScope scope, ServiceContactEntity contact) {
        if (!canRead(scope, contact)) {
            notFound();
        }
    }

    public void requireEdit(CallerScope scope, ServiceContactEntity contact) {
        requireRead(scope, contact);
        if (!canEdit(scope, contact)) {
            deny("You do not have permission to edit this service contact");
        }
    }

    public void requireArchive(CallerScope scope, ServiceContactEntity contact) {
        requireRead(scope, contact);
        if (!canArchive(scope, contact)) {
            deny("You do not have permission to archive this service contact");
        }
    }

    private static void deny(String message) {
        throw new BusinessException("SERVICE_CONTACT_FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }

    private static void notFound() {
        throw new BusinessException(
                "SERVICE_CONTACT_NOT_FOUND", "Service contact not found", HttpStatus.NOT_FOUND);
    }
}
