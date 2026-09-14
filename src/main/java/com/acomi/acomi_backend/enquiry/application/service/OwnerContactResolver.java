package com.acomi.acomi_backend.enquiry.application.service;

import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.registration.application.AdminLeadDefaults;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves owner contact for Admin review and authorised share emails from the listing
 * lead (including bulk-import Contact 1/2/3) and a linked ACOMI owner account.
 * Provisional admin ownership is never treated as listing contact.
 */
@Component
@RequiredArgsConstructor
public class OwnerContactResolver {

    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final MessRegistrationRepository messRegistrationRepository;

    public OwnerContactResponse resolve(SpaceEntity space) {
        UserEntity owner = space.getOwner();
        ContactDraft draft = new ContactDraft();
        ListingContacts listing = loadListingContacts(space);

        if (listing != null && linkedToRealOwner(listing.linkedOwnerUserId, owner)) {
            applyLinkedOwner(owner, listing, draft);
        } else if (listing != null) {
            applyListingContacts(listing, draft);
        } else if (owner != null && owner.isLinkableOwner()) {
            applyOwnerCreatedSpace(space, owner, draft);
        } else {
            applyAdminHeldSpace(space, owner, draft);
        }

        stripAdminIdentity(draft, owner);

        boolean available = draft.ownerName != null
                || draft.mobile != null
                || draft.alternate != null
                || draft.additional != null
                || draft.email != null;
        return OwnerContactResponse.builder()
                .ownerName(draft.ownerName)
                .mobileNumber(draft.mobile)
                .alternateMobileNumber(draft.alternate)
                .additionalMobileNumber(draft.additional)
                .email(draft.email)
                .available(available && draft.mobile != null)
                .build();
    }

    private ListingContacts loadListingContacts(SpaceEntity space) {
        if (space.getType() == SpaceType.MESS) {
            MessRegistrationEntity registration =
                    messRegistrationRepository.findByConvertedSpaceId(space.getId()).orElse(null);
            if (registration == null) {
                return null;
            }
            return new ListingContacts(
                    registration.getLinkedOwnerUserId(),
                    registration.getOwnerName(),
                    registration.getMobileNumber(),
                    registration.getAlternateMobileNumber(),
                    registration.getAdditionalMobileNumber());
        }
        PropertyRegistrationEntity registration =
                propertyRegistrationRepository.findByConvertedSpaceId(space.getId()).orElse(null);
        if (registration == null) {
            return null;
        }
        return new ListingContacts(
                registration.getLinkedOwnerUserId(),
                registration.getOwnerName(),
                registration.getMobileNumber(),
                registration.getAlternateMobileNumber(),
                registration.getAdditionalMobileNumber());
    }

    /**
     * Linked ACOMI owner account is the shareable identity. Registration Contact 1 is the lead
     * number, not the linked owner, so it is omitted. Contact 2/3 stay as extra listing numbers.
     */
    private static void applyLinkedOwner(UserEntity owner, ListingContacts listing, ContactDraft draft) {
        draft.ownerName = firstNonBlank(blankToNull(owner.getFullName()), usableName(listing.ownerName));
        String ownerMobile = usableMobile(owner.getMobileNumber());
        if (ownerMobile != null) {
            draft.mobile = ownerMobile;
            draft.alternate = usableMobile(listing.alternate);
            draft.additional = usableMobile(listing.additional);
        } else {
            applyListingContacts(listing, draft);
            draft.ownerName = firstNonBlank(blankToNull(owner.getFullName()), draft.ownerName);
        }
        draft.email = blankToNull(owner.getEmail());
    }

    /**
     * Unlinked admin-published / bulk-imported listings use lead Contact 1/2/3 only.
     * Do not fall back to the provisional admin account. Blank/placeholder Contact 1
     * promotes Contact 2/3 so a real listing number can still be shared.
     */
    private static void applyListingContacts(ListingContacts listing, ContactDraft draft) {
        draft.ownerName = usableName(listing.ownerName);
        String primary = usableMobile(listing.mobile);
        if (primary != null) {
            draft.mobile = primary;
            draft.alternate = usableMobile(listing.alternate);
            draft.additional = usableMobile(listing.additional);
        } else {
            assignMobiles(draft, Set.of(), listing.alternate, listing.additional);
        }
        draft.email = null;
    }

    private static void applyOwnerCreatedSpace(SpaceEntity space, UserEntity owner, ContactDraft draft) {
        draft.ownerName = blankToNull(owner.getFullName());
        assignMobiles(draft, Set.of(), space.getContactNumber(), owner.getMobileNumber());
        draft.email = blankToNull(owner.getEmail());
    }

    private static void applyAdminHeldSpace(SpaceEntity space, UserEntity owner, ContactDraft draft) {
        assignMobiles(draft, adminMobiles(owner), space.getContactNumber());
        draft.email = null;
    }

    private static void stripAdminIdentity(ContactDraft draft, UserEntity owner) {
        if (owner == null || !owner.isPlatformAdmin()) {
            return;
        }
        if (sameText(draft.ownerName, owner.getFullName())) {
            draft.ownerName = null;
        }
        if (sameText(draft.email, owner.getEmail())) {
            draft.email = null;
        }
        assignMobiles(
                draft,
                adminMobiles(owner),
                draft.mobile,
                draft.alternate,
                draft.additional);
    }

    private static boolean linkedToRealOwner(UUID linkedOwnerUserId, UserEntity owner) {
        return owner != null
                && owner.isLinkableOwner()
                && linkedOwnerUserId != null
                && linkedOwnerUserId.equals(owner.getId());
    }

    private static void assignMobiles(ContactDraft draft, Set<String> excluded, String... candidates) {
        List<String> mobiles = new ArrayList<>();
        for (String candidate : candidates) {
            String mobile = usableMobile(candidate);
            if (mobile == null || excluded.contains(mobile) || mobiles.contains(mobile)) {
                continue;
            }
            mobiles.add(mobile);
        }
        draft.mobile = mobiles.isEmpty() ? null : mobiles.get(0);
        draft.alternate = mobiles.size() > 1 ? mobiles.get(1) : null;
        draft.additional = mobiles.size() > 2 ? mobiles.get(2) : null;
    }

    private static Set<String> adminMobiles(UserEntity owner) {
        if (owner == null || !owner.isPlatformAdmin()) {
            return Set.of();
        }
        String mobile = usableMobile(owner.getMobileNumber());
        return mobile == null ? Set.of() : Set.of(mobile);
    }

    private static String usableName(String value) {
        String name = blankToNull(value);
        if (name == null || name.equalsIgnoreCase(AdminLeadDefaults.UNKNOWN_OWNER)) {
            return null;
        }
        return name;
    }

    private static String usableMobile(String value) {
        String mobile = blankToNull(value);
        if (mobile == null || AdminLeadDefaults.PLACEHOLDER_MOBILE.equals(mobile)) {
            return null;
        }
        return mobile;
    }

    private static final class ContactDraft {
        private String ownerName;
        private String mobile;
        private String alternate;
        private String additional;
        private String email;
    }

    private record ListingContacts(
            UUID linkedOwnerUserId, String ownerName, String mobile, String alternate, String additional) {}

    public boolean hasShareableContact(OwnerContactResponse contact) {
        return contact != null && contact.isAvailable() && !isBlank(contact.getMobileNumber());
    }

    private static String firstNonBlank(String preferred, String fallback) {
        String value = blankToNull(preferred);
        return value != null ? value : fallback;
    }

    private static boolean sameText(String left, String right) {
        String a = blankToNull(left);
        String b = blankToNull(right);
        return a != null && a.equalsIgnoreCase(b);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public OwnerContactResponse empty() {
        return OwnerContactResponse.builder().available(false).build();
    }

    public OwnerContactResponse resolveOrEmpty(SpaceEntity space) {
        if (space == null) {
            return empty();
        }
        return resolve(space);
    }

    public OwnerContactResponse resolveBySpaceId(
            UUID spaceId, java.util.function.Function<UUID, SpaceEntity> loader) {
        SpaceEntity space = loader.apply(spaceId);
        return resolveOrEmpty(space);
    }
}
