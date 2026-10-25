package com.acomi.acomi_backend.listing.application;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.listing.api.dto.MeListingResponse;
import com.acomi.acomi_backend.listing.api.dto.MeListingResponse.Kind;
import com.acomi.acomi_backend.listing.api.dto.MeListingResponse.ListingPhotoResponse;
import com.acomi.acomi_backend.listing.api.dto.MeListingResponse.Publication;
import com.acomi.acomi_backend.listing.api.dto.UpdateListingCatalogRequest;
import com.acomi.acomi_backend.listing.api.dto.UpdateOwnedMessLeadRequest;
import com.acomi.acomi_backend.listing.api.dto.UpdateOwnedPropertyLeadRequest;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.mess.application.service.MessRegistrationService;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.application.service.PropertyRegistrationService;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.registration.application.RegistrationPhotoService;
import com.acomi.acomi_backend.registration.infrastructure.persistence.entity.RegistrationPhotoEntity;
import com.acomi.acomi_backend.registration.infrastructure.persistence.repository.RegistrationPhotoRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationAmenityEntity;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.api.dto.request.UpdateSpaceRequest;
import com.acomi.acomi_backend.space.application.service.SpaceAmenityService;
import com.acomi.acomi_backend.space.application.service.SpaceService;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MeListingsService {

    private final SpaceMembershipRepository spaceMembershipRepository;
    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final MessRegistrationRepository messRegistrationRepository;
    private final RegistrationPhotoRepository registrationPhotoRepository;
    private final RegistrationPhotoService registrationPhotoService;
    private final PropertyRegistrationService propertyRegistrationService;
    private final MessRegistrationService messRegistrationService;
    private final SpaceService spaceService;
    private final SpaceAmenityService spaceAmenityService;

    @Transactional(readOnly = true)
    public List<MeListingResponse> list(UUID callerId, String kind, String spaceType, String status, String search) {
        List<MeListingResponse> items = new ArrayList<>();
        boolean wantLive = status == null || status.isBlank() || "LIVE".equalsIgnoreCase(status);
        boolean wantPending = status == null || status.isBlank() || "PENDING".equalsIgnoreCase(status);
        if (wantLive) {
            for (SpaceMembershipEntity membership : spaceMembershipRepository.findUserSpaces(callerId)) {
                if (!isListingRole(membership.getRole())) {
                    continue;
                }
                items.add(fromSpace(membership));
            }
        }
        if (wantPending) {
            for (PropertyRegistrationEntity lead :
                    propertyRegistrationRepository.findByLinkedOwnerUserIdAndConvertedSpaceIdIsNull(callerId)) {
                if (!isOpenLead(lead.getStatus())) {
                    continue;
                }
                items.add(fromPropertyLead(lead));
            }
            for (MessRegistrationEntity lead :
                    messRegistrationRepository.findByLinkedOwnerUserIdAndConvertedSpaceIdIsNull(callerId)) {
                if (!isOpenMessLead(lead.getStatus())) {
                    continue;
                }
                items.add(fromMessLead(lead));
            }
        }
        return items.stream()
                .filter(item -> matchesKind(item, kind))
                .filter(item -> matchesType(item, spaceType))
                .filter(item -> matchesSearch(item, search))
                .sorted(Comparator.comparing(MeListingResponse::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional
    public MeListingResponse updatePropertyLead(UUID callerId, UUID registrationId, UpdateOwnedPropertyLeadRequest request) {
        PropertyRegistrationEntity saved = propertyRegistrationService.updateOwnedLead(callerId, registrationId, request);
        return fromPropertyLead(saved);
    }

    @Transactional
    public MeListingResponse updateMessLead(UUID callerId, UUID registrationId, UpdateOwnedMessLeadRequest request) {
        MessRegistrationEntity saved = messRegistrationService.updateOwnedLead(callerId, registrationId, request);
        return fromMessLead(saved);
    }

    @Transactional
    public MeListingResponse updateCatalog(UUID callerId, UUID spaceId, UpdateListingCatalogRequest request) {
        SpaceMembershipEntity membership = spaceMembershipRepository
                .findMembershipByUserAndSpace(callerId, spaceId)
                .orElseThrow(() -> new BusinessException("Listing was not found", HttpStatus.NOT_FOUND));
        if (!isListingRole(membership.getRole())) {
            throw new BusinessException("Listing was not found", HttpStatus.NOT_FOUND);
        }
        PropertyRegistrationEntity property =
                propertyRegistrationRepository.findByConvertedSpaceId(spaceId).orElse(null);
        MessRegistrationEntity mess = messRegistrationRepository.findByConvertedSpaceId(spaceId).orElse(null);
        if (hasCatalogOnlyFields(request) && property == null && mess == null) {
            throw new BusinessException(
                    "LISTING_CATALOG_NOT_LINKED",
                    "This space has no listing record for price, description, map, or gallery fields.",
                    HttpStatus.CONFLICT);
        }
        if (hasSpaceFields(request)) {
            SpaceEntity space = membership.getSpace();
            UpdateSpaceRequest spaceRequest = new UpdateSpaceRequest();
            spaceRequest.setName(request.getName() != null ? request.getName() : space.getName());
            spaceRequest.setAddress(request.getAddress() != null ? request.getAddress() : space.getAddress());
            spaceRequest.setLatitude(request.getLatitude());
            spaceRequest.setLongitude(request.getLongitude());
            spaceRequest.setContactNumber(
                    request.getContactNumber() != null ? request.getContactNumber() : space.getContactNumber());
            spaceRequest.setDefaultFoodCharge(space.getDefaultFoodCharge());
            spaceRequest.setFoodIncludedInRent(request.getFoodIncludedInRent());
            spaceRequest.setGenderPolicy(request.getGenderPolicy());
            spaceRequest.setAmenities(request.getAmenities());
            spaceService.updateSpace(spaceId, callerId, spaceRequest);
        }
        if (property != null) {
            applyPropertyCatalog(property, request);
            propertyRegistrationRepository.save(property);
            if (request.getPhotos() != null) {
                registrationPhotoService.replaceForProperty(property.getId(), request.getPhotos());
            }
        }
        if (mess != null) {
            applyMessCatalog(mess, request);
            messRegistrationRepository.save(mess);
            if (request.getPhotos() != null) {
                registrationPhotoService.replaceForMess(mess.getId(), request.getPhotos());
            }
        }
        return reloadLive(callerId, spaceId, property, mess);
    }

    private MeListingResponse reloadLive(
            UUID callerId, UUID spaceId, PropertyRegistrationEntity property, MessRegistrationEntity mess) {
        SpaceMembershipEntity membership = spaceMembershipRepository
                .findMembershipByUserAndSpace(callerId, spaceId)
                .orElseThrow(() -> new BusinessException("Listing was not found", HttpStatus.NOT_FOUND));
        MeListingResponse base = fromSpace(membership);
        PropertyRegistrationEntity freshProperty = property == null
                ? null
                : propertyRegistrationRepository.findById(property.getId()).orElse(property);
        MessRegistrationEntity freshMess =
                mess == null ? null : messRegistrationRepository.findById(mess.getId()).orElse(mess);
        return overlayRegistration(base, freshProperty, freshMess);
    }

    private static boolean isListingRole(MembershipRole role) {
        return role == MembershipRole.OWNER || role == MembershipRole.MANAGER;
    }

    private static boolean isOpenLead(PropertyRegistrationStatus status) {
        return status == PropertyRegistrationStatus.PENDING
                || status == PropertyRegistrationStatus.IN_REVIEW
                || status == PropertyRegistrationStatus.CONTACTED;
    }

    private static boolean isOpenMessLead(MessRegistrationStatus status) {
        return status == MessRegistrationStatus.PENDING
                || status == MessRegistrationStatus.IN_REVIEW
                || status == MessRegistrationStatus.CONTACTED;
    }

    private MeListingResponse fromSpace(SpaceMembershipEntity membership) {
        SpaceEntity space = membership.getSpace();
        boolean mess = space.getType() == SpaceType.MESS;
        PropertyRegistrationEntity property = mess
                ? null
                : propertyRegistrationRepository.findByConvertedSpaceId(space.getId()).orElse(null);
        MessRegistrationEntity messLead = mess
                ? messRegistrationRepository.findByConvertedSpaceId(space.getId()).orElse(null)
                : null;
        List<AmenityAssignmentDto> amenities = spaceAmenityService.getForSpace(space.getId());
        if (amenities.isEmpty() && property != null) {
            amenities = registrationAmenities(property);
        }
        MeListingResponse base = MeListingResponse.builder()
                .spaceId(space.getId())
                .kind(mess ? Kind.MESS : Kind.PROPERTY)
                .spaceType(space.getType())
                .name(space.getName())
                .address(space.getAddress())
                .contactNumber(space.getContactNumber())
                .genderPolicy(space.getGenderPolicy())
                .foodIncluded(space.isFoodIncludedInRent())
                .amenities(amenities)
                .photoFileId(space.getPhotoFileId())
                .publication(Publication.LIVE)
                .membershipRole(membership.getRole())
                .updatedAt(space.getUpdatedAt())
                .operationalManagementAvailable(true)
                .publicPath(mess ? "/meals/" + space.getId() : "/places/" + space.getId())
                .managePath("/spaces/" + space.getId() + "/dashboard")
                .accommodationPath(mess ? null : "/spaces/" + space.getId() + "/accommodation")
                .mealsPath(mess ? "/spaces/" + space.getId() + "/meals" : null)
                .build();
        return overlayRegistration(base, property, messLead);
    }

    private MeListingResponse overlayRegistration(
            MeListingResponse base, PropertyRegistrationEntity property, MessRegistrationEntity mess) {
        if (property == null && mess == null) {
            return base;
        }
        List<ListingPhotoResponse> gallery = property != null
                ? photosForProperty(property.getId())
                : photosForMess(mess.getId());
        String cover = gallery.isEmpty() ? null : gallery.get(0).getContentUrl();
        if (property != null) {
            return MeListingResponse.builder()
                    .spaceId(base.getSpaceId())
                    .registrationId(property.getId())
                    .kind(base.getKind())
                    .spaceType(base.getSpaceType())
                    .name(base.getName())
                    .address(firstText(property.getAddressLine(), base.getAddress()))
                    .city(property.getCity())
                    .state(property.getState())
                    .pincode(property.getPincode())
                .mapUrl(property.getMapUrl())
                .description(property.getDescription())
                .ownerName(property.getOwnerName())
                .buildingName(property.getBuildingName())
                .sharingNotes(property.getSharingNotes())
                .capacityEstimate(property.getCapacityEstimate())
                .latitude(property.getLatitude())
                .longitude(property.getLongitude())
                .amenities(base.getAmenities())
                .contactNumber(base.getContactNumber())
                .genderPolicy(property.getGenderPolicy() != null ? property.getGenderPolicy() : base.getGenderPolicy())
                .foodIncluded(property.getFoodIncludedListing() != null
                        ? property.getFoodIncludedListing()
                        : base.getFoodIncluded())
                .photoFileId(base.getPhotoFileId())
                    .coverImageUrl(cover)
                    .gallery(gallery)
                    .startingPrice(property.getStartingPrice())
                    .priceBasis(property.getPriceBasis())
                    .publication(base.getPublication())
                    .registrationReference(property.getReference())
                    .registrationStatus(property.getStatus().name())
                    .membershipRole(base.getMembershipRole())
                    .updatedAt(base.getUpdatedAt())
                    .operationalManagementAvailable(base.isOperationalManagementAvailable())
                    .publicPath(base.getPublicPath())
                    .managePath(base.getManagePath())
                    .accommodationPath(base.getAccommodationPath())
                    .mealsPath(base.getMealsPath())
                    .build();
        }
        return MeListingResponse.builder()
                .spaceId(base.getSpaceId())
                .registrationId(mess.getId())
                .kind(Kind.MESS)
                .spaceType(base.getSpaceType())
                .name(base.getName())
                .address(firstText(mess.getAddressLine(), base.getAddress()))
                .city(mess.getCity())
                .state(mess.getState())
                .pincode(mess.getPincode())
                .mapUrl(mess.getMapUrl())
                .description(mess.getDescription())
                .ownerName(mess.getOwnerName())
                .buildingName(mess.getBuildingName())
                .sharingNotes(mess.getSharingNotes())
                .capacityEstimate(mess.getCapacityEstimate())
                .latitude(mess.getLatitude())
                .longitude(mess.getLongitude())
                .amenities(base.getAmenities())
                .contactNumber(base.getContactNumber())
                .genderPolicy(mess.getGenderPolicy() != null ? mess.getGenderPolicy() : base.getGenderPolicy())
                .foodIncluded(mess.getFoodIncludedListing() != null
                        ? mess.getFoodIncludedListing()
                        : base.getFoodIncluded())
                .photoFileId(base.getPhotoFileId())
                .coverImageUrl(cover)
                .gallery(gallery)
                .monthlyPrice(mess.getMonthlyPrice())
                .mealPrice(mess.getMealPrice())
                .priceBasis(mess.getMonthlyPriceBasis())
                .publication(base.getPublication())
                .registrationReference(mess.getReference())
                .registrationStatus(mess.getStatus().name())
                .membershipRole(base.getMembershipRole())
                .updatedAt(base.getUpdatedAt())
                .operationalManagementAvailable(base.isOperationalManagementAvailable())
                .publicPath(base.getPublicPath())
                .managePath(base.getManagePath())
                .accommodationPath(base.getAccommodationPath())
                .mealsPath(base.getMealsPath())
                .build();
    }

    private MeListingResponse fromPropertyLead(PropertyRegistrationEntity lead) {
        List<ListingPhotoResponse> gallery = photosForProperty(lead.getId());
        return MeListingResponse.builder()
                .registrationId(lead.getId())
                .kind(Kind.PROPERTY)
                .spaceType(lead.getPropertyType())
                .name(lead.getPropertyName())
                .address(lead.getAddressLine())
                .city(lead.getCity())
                .state(lead.getState())
                .pincode(lead.getPincode())
                .mapUrl(lead.getMapUrl())
                .description(lead.getDescription())
                .ownerName(lead.getOwnerName())
                .buildingName(lead.getBuildingName())
                .sharingNotes(lead.getSharingNotes())
                .capacityEstimate(lead.getCapacityEstimate())
                .latitude(lead.getLatitude())
                .longitude(lead.getLongitude())
                .amenities(registrationAmenities(lead))
                .genderPolicy(lead.getGenderPolicy())
                .foodIncluded(lead.getFoodIncludedListing())
                .coverImageUrl(gallery.isEmpty() ? null : gallery.get(0).getContentUrl())
                .gallery(gallery)
                .startingPrice(lead.getStartingPrice())
                .priceBasis(lead.getPriceBasis())
                .publication(Publication.PENDING)
                .registrationReference(lead.getReference())
                .registrationStatus(lead.getStatus().name())
                .updatedAt(lead.getUpdatedAt())
                .operationalManagementAvailable(false)
                .build();
    }

    private MeListingResponse fromMessLead(MessRegistrationEntity lead) {
        List<ListingPhotoResponse> gallery = photosForMess(lead.getId());
        return MeListingResponse.builder()
                .registrationId(lead.getId())
                .kind(Kind.MESS)
                .spaceType(SpaceType.MESS)
                .name(lead.getMessName())
                .address(lead.getAddressLine())
                .city(lead.getCity())
                .state(lead.getState())
                .pincode(lead.getPincode())
                .mapUrl(lead.getMapUrl())
                .description(lead.getDescription())
                .ownerName(lead.getOwnerName())
                .buildingName(lead.getBuildingName())
                .sharingNotes(lead.getSharingNotes())
                .capacityEstimate(lead.getCapacityEstimate())
                .latitude(lead.getLatitude())
                .longitude(lead.getLongitude())
                .genderPolicy(lead.getGenderPolicy())
                .foodIncluded(lead.getFoodIncludedListing())
                .coverImageUrl(gallery.isEmpty() ? null : gallery.get(0).getContentUrl())
                .gallery(gallery)
                .monthlyPrice(lead.getMonthlyPrice())
                .mealPrice(lead.getMealPrice())
                .priceBasis(lead.getMonthlyPriceBasis())
                .publication(Publication.PENDING)
                .registrationReference(lead.getReference())
                .registrationStatus(lead.getStatus().name())
                .updatedAt(lead.getUpdatedAt())
                .operationalManagementAvailable(false)
                .build();
    }

    private List<ListingPhotoResponse> photosForProperty(UUID registrationId) {
        return registrationPhotoRepository.findByPropertyRegistrationIdOrderByDisplayOrderAsc(registrationId).stream()
                .map(this::toPhoto)
                .toList();
    }

    private List<ListingPhotoResponse> photosForMess(UUID registrationId) {
        return registrationPhotoRepository.findByMessRegistrationIdOrderByDisplayOrderAsc(registrationId).stream()
                .map(this::toPhoto)
                .toList();
    }

    private ListingPhotoResponse toPhoto(RegistrationPhotoEntity photo) {
        return ListingPhotoResponse.builder()
                .id(photo.getId())
                .contentType(photo.getContentType())
                .contentUrl("/api/v1/registration-photos/" + photo.getId() + "/content")
                .build();
    }

    private static void applyPropertyCatalog(PropertyRegistrationEntity property, UpdateListingCatalogRequest request) {
        if (StringUtils.hasText(request.getName())) {
            property.setPropertyName(request.getName().trim());
        }
        if (StringUtils.hasText(request.getOwnerName())) {
            property.setOwnerName(request.getOwnerName().trim());
        }
        if (request.getBuildingName() != null) {
            property.setBuildingName(blankToNull(request.getBuildingName()));
        }
        if (request.getAddress() != null) {
            property.setAddressLine(request.getAddress().trim());
        }
        if (request.getCity() != null) {
            property.setCity(blankToNull(request.getCity()));
        }
        if (request.getState() != null) {
            property.setState(blankToNull(request.getState()));
        }
        if (request.getPincode() != null) {
            property.setPincode(blankToNull(request.getPincode()));
        }
        if (request.getMapUrl() != null) {
            property.setMapUrl(blankToNull(request.getMapUrl()));
        }
        if (request.getDescription() != null) {
            property.setDescription(blankToNull(request.getDescription()));
        }
        if (request.getSharingNotes() != null) {
            property.setSharingNotes(blankToNull(request.getSharingNotes()));
        }
        if (request.getStartingPrice() != null) {
            property.setStartingPrice(request.getStartingPrice());
        }
        if (request.getPriceBasis() != null) {
            property.setPriceBasis(request.getPriceBasis());
        }
        if (request.getLatitude() != null) {
            property.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            property.setLongitude(request.getLongitude());
        }
        if (request.getGenderPolicy() != null) {
            property.setGenderPolicy(request.getGenderPolicy());
        }
        if (request.getFoodIncludedInRent() != null) {
            property.setFoodIncludedListing(request.getFoodIncludedInRent());
        }
        if (request.getCapacityEstimate() != null) {
            property.setCapacityEstimate(request.getCapacityEstimate());
        }
    }

    private static void applyMessCatalog(MessRegistrationEntity mess, UpdateListingCatalogRequest request) {
        if (StringUtils.hasText(request.getName())) {
            mess.setMessName(request.getName().trim());
        }
        if (StringUtils.hasText(request.getOwnerName())) {
            mess.setOwnerName(request.getOwnerName().trim());
        }
        if (request.getBuildingName() != null) {
            mess.setBuildingName(blankToNull(request.getBuildingName()));
        }
        if (request.getAddress() != null) {
            mess.setAddressLine(request.getAddress().trim());
        }
        if (request.getCity() != null) {
            mess.setCity(blankToNull(request.getCity()));
        }
        if (request.getState() != null) {
            mess.setState(blankToNull(request.getState()));
        }
        if (request.getPincode() != null) {
            mess.setPincode(blankToNull(request.getPincode()));
        }
        if (request.getMapUrl() != null) {
            mess.setMapUrl(blankToNull(request.getMapUrl()));
        }
        if (request.getDescription() != null) {
            mess.setDescription(blankToNull(request.getDescription()));
        }
        if (request.getSharingNotes() != null) {
            mess.setSharingNotes(blankToNull(request.getSharingNotes()));
        }
        if (request.getMonthlyPrice() != null) {
            mess.setMonthlyPrice(request.getMonthlyPrice());
        }
        if (request.getMealPrice() != null) {
            mess.setMealPrice(request.getMealPrice());
        }
        if (request.getMonthlyPriceBasis() != null) {
            mess.setMonthlyPriceBasis(request.getMonthlyPriceBasis());
        }
        if (request.getLatitude() != null) {
            mess.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            mess.setLongitude(request.getLongitude());
        }
        if (request.getGenderPolicy() != null) {
            mess.setGenderPolicy(request.getGenderPolicy());
        }
        if (request.getFoodIncludedInRent() != null) {
            mess.setFoodIncludedListing(request.getFoodIncludedInRent());
        }
        if (request.getCapacityEstimate() != null) {
            mess.setCapacityEstimate(request.getCapacityEstimate());
        }
    }

    private static boolean hasSpaceFields(UpdateListingCatalogRequest request) {
        return request.getName() != null
                || request.getAddress() != null
                || request.getLatitude() != null
                || request.getLongitude() != null
                || request.getContactNumber() != null
                || request.getFoodIncludedInRent() != null
                || request.getGenderPolicy() != null
                || request.getAmenities() != null;
    }

    private static boolean hasCatalogOnlyFields(UpdateListingCatalogRequest request) {
        return request.getCity() != null
                || request.getState() != null
                || request.getPincode() != null
                || request.getMapUrl() != null
                || request.getDescription() != null
                || request.getSharingNotes() != null
                || request.getStartingPrice() != null
                || request.getPriceBasis() != null
                || request.getMonthlyPrice() != null
                || request.getMonthlyPriceBasis() != null
                || request.getMealPrice() != null
                || request.getPhotos() != null
                || request.getCapacityEstimate() != null
                || request.getOwnerName() != null
                || request.getBuildingName() != null;
    }

    private static boolean matchesKind(MeListingResponse item, String kind) {
        if (!StringUtils.hasText(kind)) {
            return true;
        }
        return item.getKind().name().equalsIgnoreCase(kind.trim());
    }

    private static boolean matchesType(MeListingResponse item, String spaceType) {
        if (!StringUtils.hasText(spaceType) || item.getSpaceType() == null) {
            return !StringUtils.hasText(spaceType) || item.getSpaceType() != null;
        }
        return item.getSpaceType().name().equalsIgnoreCase(spaceType.trim());
    }

    private static boolean matchesSearch(MeListingResponse item, String search) {
        if (!StringUtils.hasText(search)) {
            return true;
        }
        String needle = search.trim().toLowerCase(Locale.ROOT);
        return contains(item.getName(), needle)
                || contains(item.getAddress(), needle)
                || contains(item.getCity(), needle)
                || contains(item.getRegistrationReference(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private static List<AmenityAssignmentDto> registrationAmenities(PropertyRegistrationEntity property) {
        if (property.getAmenities() == null || property.getAmenities().isEmpty()) {
            return List.of();
        }
        return property.getAmenities().stream().map(MeListingsService::toAmenity).toList();
    }

    private static AmenityAssignmentDto toAmenity(PropertyRegistrationAmenityEntity entity) {
        AmenityAssignmentDto dto = new AmenityAssignmentDto();
        dto.setCode(entity.getAmenityCode());
        dto.setLabel(
                StringUtils.hasText(entity.getCustomLabel()) ? entity.getCustomLabel().trim() : entity.getAmenityCode());
        return dto;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
