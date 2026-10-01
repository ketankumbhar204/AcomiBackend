package com.acomi.acomi_backend.space.application.service;

import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.config.discovery.DiscoveryProperties;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.api.dto.response.DiscoverSpaceCardResponse;
import com.acomi.acomi_backend.space.api.dto.response.DiscoverSpaceDetailResponse;
import com.acomi.acomi_backend.space.application.support.ListingInformationCompleteness;
import com.acomi.acomi_backend.space.application.support.SpaceDiscoverQuery;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceDiscoverRankedPageQuery;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceDiscoverSpecs;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpaceDiscoverService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final SpaceRepository spaceRepository;
    private final SpaceDiscoverRankedPageQuery rankedPageQuery;
    private final SpaceAmenityService spaceAmenityService;
    private final SpaceMembershipRepository spaceMembershipRepository;
    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final MessRegistrationRepository messRegistrationRepository;
    private final DiscoveryProperties discoveryProperties;

    @Transactional(readOnly = true)
    public PagedResponse<DiscoverSpaceCardResponse> discover(
            UUID callerId, String search, SpaceType type, String sort, Pageable pageable) {
        return discover(callerId, search, null, type, sort, pageable);
    }

    @Transactional(readOnly = true)
    public PagedResponse<DiscoverSpaceCardResponse> discover(
            UUID callerId,
            String search,
            String location,
            SpaceType type,
            String sort,
            Pageable pageable) {
        return discover(
                callerId,
                new SpaceDiscoverQuery(
                        normalizeSearch(search),
                        normalizeSearch(location),
                        type == null ? List.of() : List.of(type),
                        null,
                        null,
                        List.of()),
                sort,
                pageable);
    }

    @Transactional(readOnly = true)
    public PagedResponse<DiscoverSpaceCardResponse> discover(
            UUID callerId, SpaceDiscoverQuery query, String sort, Pageable pageable) {
        Pageable safePageable = toSafePageable(pageable, sort);
        SpaceDiscoverQuery normalized = query == null
                ? SpaceDiscoverQuery.of(null, null, null)
                : new SpaceDiscoverQuery(
                        normalizeSearch(query.search()),
                        normalizeSearch(query.location()),
                        query.types(),
                        query.minRent(),
                        query.maxRent(),
                        query.amenityCodes());
        boolean includeTest = discoveryProperties.isIncludeTestSpaces();

        Specification<SpaceEntity> spec = SpaceDiscoverSpecs.discover(normalized, includeTest);
        Page<SpaceEntity> page = rankedPageQuery.find(spec, safePageable);
        List<SpaceEntity> spaces = page.getContent();
        List<UUID> spaceIds = spaces.stream().map(SpaceEntity::getId).toList();

        Map<UUID, List<AmenityAssignmentDto>> amenitiesBySpace =
                spaceAmenityService.getForSpaces(spaceIds);
        Set<UUID> memberSpaceIds = loadMemberSpaceIds(callerId, spaceIds);
        Set<UUID> testSpaceIds = loadTestSpaceIds(spaceIds);
        Map<UUID, PropertyRegistrationEntity> propertiesBySpace = loadPropertiesBySpace(spaceIds);
        Map<UUID, MessRegistrationEntity> messesBySpace = loadMessesBySpace(spaceIds);

        List<DiscoverSpaceCardResponse> content = spaces.stream()
                .map(space -> toCard(
                        space,
                        amenitiesBySpace.getOrDefault(space.getId(), List.of()),
                        memberSpaceIds.contains(space.getId()),
                        testSpaceIds.contains(space.getId()),
                        propertiesBySpace.get(space.getId()),
                        messesBySpace.get(space.getId())))
                .toList();

        return PagedResponse.<DiscoverSpaceCardResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public DiscoverSpaceDetailResponse getDetail(UUID callerId, UUID spaceId) {
        SpaceEntity space = findDiscoverableSpace(spaceId);

        List<AmenityAssignmentDto> amenities = spaceAmenityService.getForSpace(spaceId);
        boolean alreadyMember = callerId != null
                && spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                        callerId, spaceId, MembershipStatus.ACTIVE);
        PropertyRegistrationEntity property =
                propertyRegistrationRepository.findByConvertedSpaceId(spaceId).orElse(null);
        MessRegistrationEntity mess =
                messRegistrationRepository.findByConvertedSpaceId(spaceId).orElse(null);
        boolean testSpace = isTestLead(property, mess);
        boolean ownedByCurrentUser = callerId != null
                && spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(spaceId, callerId);

        return toDetail(space, amenities, alreadyMember, testSpace, ownedByCurrentUser, property, mess);
    }

    private SpaceEntity findDiscoverableSpace(UUID spaceId) {
        if (discoveryProperties.isIncludeTestSpaces()) {
            SpaceEntity space = spaceRepository
                    .findByIdAndIsActiveTrue(spaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
            if (space.isDiscoverable() || isTestSpace(spaceId)) {
                return space;
            }
            throw new ResourceNotFoundException("Space", "id", spaceId);
        }
        return spaceRepository
                .findByIdAndIsActiveTrueAndDiscoverableTrue(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }

    private Set<UUID> loadMemberSpaceIds(UUID callerId, List<UUID> spaceIds) {
        if (callerId == null || spaceIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(
                spaceMembershipRepository.findActiveSpaceIdsByUserIdAndSpaceIdIn(callerId, spaceIds));
    }

    private Set<UUID> loadTestSpaceIds(List<UUID> spaceIds) {
        if (spaceIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> ids = new HashSet<>(propertyRegistrationRepository.findTestLeadConvertedSpaceIds(spaceIds));
        ids.addAll(messRegistrationRepository.findTestLeadConvertedSpaceIds(spaceIds));
        return ids;
    }

    private boolean isTestSpace(UUID spaceId) {
        return isTestLead(
                propertyRegistrationRepository.findByConvertedSpaceId(spaceId).orElse(null),
                messRegistrationRepository.findByConvertedSpaceId(spaceId).orElse(null));
    }

    private static boolean isTestLead(PropertyRegistrationEntity property, MessRegistrationEntity mess) {
        return (property != null && property.isTestLead()) || (mess != null && mess.isTestLead());
    }

    private Map<UUID, PropertyRegistrationEntity> loadPropertiesBySpace(List<UUID> spaceIds) {
        if (spaceIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, PropertyRegistrationEntity> bySpace = new HashMap<>();
        for (PropertyRegistrationEntity property :
                propertyRegistrationRepository.findByConvertedSpaceIdIn(spaceIds)) {
            if (property.getConvertedSpaceId() != null) {
                bySpace.put(property.getConvertedSpaceId(), property);
            }
        }
        return bySpace;
    }

    private Map<UUID, MessRegistrationEntity> loadMessesBySpace(List<UUID> spaceIds) {
        if (spaceIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MessRegistrationEntity> bySpace = new HashMap<>();
        for (MessRegistrationEntity mess : messRegistrationRepository.findByConvertedSpaceIdIn(spaceIds)) {
            if (mess.getConvertedSpaceId() != null) {
                bySpace.put(mess.getConvertedSpaceId(), mess);
            }
        }
        return bySpace;
    }

    private static DiscoverSpaceCardResponse toCard(
            SpaceEntity space,
            List<AmenityAssignmentDto> amenities,
            boolean alreadyMember,
            boolean testSpace,
            PropertyRegistrationEntity property,
            MessRegistrationEntity mess) {
        BigDecimal startingPrice = property == null
                ? null
                : DiscoverListingSanitizer.positivePrice(property.getStartingPrice());
        String propertyMap = property == null ? null : DiscoverListingSanitizer.mapUrl(property.getMapUrl());
        String messMap = mess == null ? null : DiscoverListingSanitizer.mapUrl(mess.getMapUrl());
        return DiscoverSpaceCardResponse.builder()
                .spaceId(space.getId())
                .name(space.getName())
                .type(space.getType())
                .address(firstListedAddress(
                        space.getAddress(),
                        property == null ? null : property.getAddressLine(),
                        mess == null ? null : mess.getAddressLine()))
                .startingPrice(startingPrice)
                .monthlyPrice(mess == null ? null : DiscoverListingSanitizer.positivePrice(mess.getMonthlyPrice()))
                .mealPrice(mess == null ? null : DiscoverListingSanitizer.positivePrice(mess.getMealPrice()))
                .mapUrl(propertyMap != null ? propertyMap : messMap)
                .hasContact(hasStoredContact(space, property, mess))
                .hasMobileContact(hasMobileContact(space, property, mess))
                .amenityCodes(amenities.stream().map(AmenityAssignmentDto::getCode).toList())
                .amenityLabels(amenities.stream().map(AmenityAssignmentDto::getLabel).toList())
                .foodIncludedInRent(space.isFoodIncludedInRent())
                .genderPolicy(space.getGenderPolicy())
                .alreadyMember(alreadyMember)
                .testSpace(testSpace)
                .build();
    }

    private static DiscoverSpaceDetailResponse toDetail(
            SpaceEntity space,
            List<AmenityAssignmentDto> amenities,
            boolean alreadyMember,
            boolean testSpace,
            boolean ownedByCurrentUser,
            PropertyRegistrationEntity property,
            MessRegistrationEntity mess) {
        DiscoverSpaceDetailResponse.DiscoverSpaceDetailResponseBuilder builder =
                DiscoverSpaceDetailResponse.builder()
                        .spaceId(space.getId())
                        .name(space.getName())
                        .type(space.getType())
                        .address(DiscoverListingSanitizer.text(space.getAddress()))
                        .latitude(DiscoverListingSanitizer.latitude(space.getLatitude()))
                        .longitude(DiscoverListingSanitizer.longitude(space.getLongitude()))
                        .amenityCodes(amenities.stream().map(AmenityAssignmentDto::getCode).toList())
                        .amenityLabels(amenities.stream().map(AmenityAssignmentDto::getLabel).toList())
                        .amenities(amenities)
                        .foodIncludedInRent(space.isFoodIncludedInRent())
                        .genderPolicy(space.getGenderPolicy())
                        .alreadyMember(alreadyMember)
                        .testSpace(testSpace)
                        .ownedByCurrentUser(ownedByCurrentUser)
                        .hasContact(hasStoredContact(space, property, mess))
                        .hasMobileContact(hasMobileContact(space, property, mess));

        if (property != null) {
            applyPropertyListing(builder, space, property);
        } else if (mess != null) {
            applyMessListing(builder, space, mess);
        }

        return builder.build();
    }

    private static void applyPropertyListing(
            DiscoverSpaceDetailResponse.DiscoverSpaceDetailResponseBuilder builder,
            SpaceEntity space,
            PropertyRegistrationEntity property) {
        builder.addressLine(DiscoverListingSanitizer.text(property.getAddressLine()))
                .city(DiscoverListingSanitizer.text(property.getCity()))
                .state(DiscoverListingSanitizer.text(property.getState()))
                .pincode(DiscoverListingSanitizer.text(property.getPincode()))
                .latitude(DiscoverListingSanitizer.firstLatitude(space.getLatitude(), property.getLatitude()))
                .longitude(DiscoverListingSanitizer.firstLongitude(space.getLongitude(), property.getLongitude()))
                .mapUrl(DiscoverListingSanitizer.mapUrl(property.getMapUrl()))
                .description(DiscoverListingSanitizer.text(property.getDescription()))
                .startingPrice(DiscoverListingSanitizer.positivePrice(property.getStartingPrice()))
                .priceBasis(property.getPriceBasis())
                .sharingNotes(DiscoverListingSanitizer.text(property.getSharingNotes()));
    }

    private static void applyMessListing(
            DiscoverSpaceDetailResponse.DiscoverSpaceDetailResponseBuilder builder,
            SpaceEntity space,
            MessRegistrationEntity mess) {
        builder.addressLine(DiscoverListingSanitizer.text(mess.getAddressLine()))
                .city(DiscoverListingSanitizer.text(mess.getCity()))
                .state(DiscoverListingSanitizer.text(mess.getState()))
                .pincode(DiscoverListingSanitizer.text(mess.getPincode()))
                .latitude(DiscoverListingSanitizer.firstLatitude(space.getLatitude(), mess.getLatitude()))
                .longitude(DiscoverListingSanitizer.firstLongitude(space.getLongitude(), mess.getLongitude()))
                .mapUrl(DiscoverListingSanitizer.mapUrl(mess.getMapUrl()))
                .description(DiscoverListingSanitizer.text(mess.getDescription()))
                .monthlyPrice(DiscoverListingSanitizer.positivePrice(mess.getMonthlyPrice()))
                .mealPrice(DiscoverListingSanitizer.positivePrice(mess.getMealPrice()));
    }

    private static boolean hasStoredContact(
            SpaceEntity space, PropertyRegistrationEntity property, MessRegistrationEntity mess) {
        if (property != null) {
            return DiscoverListingSanitizer.hasUsableContact(
                    space.getContactNumber(),
                    property.getMobileNumber(),
                    property.getAlternateMobileNumber(),
                    property.getAdditionalMobileNumber());
        }
        if (mess != null) {
            return DiscoverListingSanitizer.hasUsableContact(
                    space.getContactNumber(),
                    mess.getMobileNumber(),
                    mess.getAlternateMobileNumber(),
                    mess.getAdditionalMobileNumber());
        }
        return DiscoverListingSanitizer.hasUsableContact(space.getContactNumber());
    }

    private static boolean hasMobileContact(
            SpaceEntity space, PropertyRegistrationEntity property, MessRegistrationEntity mess) {
        if (property != null) {
            return ListingInformationCompleteness.hasUsableMobile(
                    space.getContactNumber(),
                    property.getMobileNumber(),
                    property.getAlternateMobileNumber(),
                    property.getAdditionalMobileNumber());
        }
        if (mess != null) {
            return ListingInformationCompleteness.hasUsableMobile(
                    space.getContactNumber(),
                    mess.getMobileNumber(),
                    mess.getAlternateMobileNumber(),
                    mess.getAdditionalMobileNumber());
        }
        return ListingInformationCompleteness.hasUsableMobile(space.getContactNumber());
    }

    private static String firstListedAddress(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (ListingInformationCompleteness.hasUsableAddress(value)) {
                return DiscoverListingSanitizer.text(value);
            }
        }
        return null;
    }

    private static String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }

    /**
     * Page and size only. {@link SpaceDiscoverRankedPageQuery} orders the filtered set by
     * information completeness, then createdAt desc, then id desc, and only then applies
     * this offset and limit. {@code sort} stays accepted for the existing "newest" contract;
     * completeness is always the primary key.
     */
    private static Pageable toSafePageable(Pageable pageable, String sort) {
        int page = pageable != null ? Math.max(pageable.getPageNumber(), 0) : 0;
        int size = pageable != null ? pageable.getPageSize() : DEFAULT_PAGE_SIZE;
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // sort is accepted for API compatibility. "newest" is the secondary key inside the ranked query.
        if (sort != null) {
            sort = sort.trim();
        }
        return PageRequest.of(page, safeSize);
    }
}
