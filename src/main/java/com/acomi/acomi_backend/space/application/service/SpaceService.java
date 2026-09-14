package com.acomi.acomi_backend.space.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.inventory.application.service.InventorySeedService;
import com.acomi.acomi_backend.meal.application.service.MealPlanService;
import com.acomi.acomi_backend.meal.application.service.MealSpaceSetupService;
import com.acomi.acomi_backend.member.application.service.MemberMasterService;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.notification.application.service.MembershipNotificationSyncService;
import com.acomi.acomi_backend.space.api.dto.request.CreateSpaceRequest;
import com.acomi.acomi_backend.space.api.dto.request.UpdateSpaceRequest;
import com.acomi.acomi_backend.space.api.dto.response.DefaultSpaceResponse;
import com.acomi.acomi_backend.space.api.dto.response.MySpaceResponse;
import com.acomi.acomi_backend.space.api.dto.response.SetDefaultSpaceResponse;
import com.acomi.acomi_backend.space.api.dto.response.SpaceDetailsResponse;
import com.acomi.acomi_backend.space.api.dto.response.SpaceResponse;
import com.acomi.acomi_backend.space.api.dto.response.UserSpaceResponse;
import com.acomi.acomi_backend.space.application.mapper.SpaceMapper;
import com.acomi.acomi_backend.space.domain.model.Space;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpaceService {

    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;
    private final SpaceMembershipRepository spaceMembershipRepository;
    private final MemberMasterService memberMasterService;
    private final MealSpaceSetupService mealSpaceSetupService;
    private final MealPlanService mealPlanService;
    private final InventorySeedService inventorySeedService;
    private final SpaceAmenityService spaceAmenityService;
    private final MembershipNotificationSyncService membershipNotificationSyncService;

    @Transactional
    public SpaceResponse createSpace(CreateSpaceRequest request) {
        log.info("Creating space: name={}, type={}, ownerId={}",
                request.getName(), request.getType(), request.getOwnerId());

        UserEntity owner = userRepository.findByIdAndIsActiveTrue(request.getOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getOwnerId()));

        String spaceName = request.getName() == null ? "" : request.getName().trim();
        if (spaceName.isEmpty()) {
            throw new BusinessException("Space name is required", HttpStatus.BAD_REQUEST);
        }
        if (spaceRepository.existsByOwnerIdAndIsActiveTrueAndNameIgnoreCase(owner.getId(), spaceName)) {
            throw new BusinessException(
                    "SPACE_NAME_TAKEN",
                    "You already have a space with this name.",
                    HttpStatus.CONFLICT);
        }

        SpaceEntity space = SpaceEntity.builder()
                .owner(owner)
                .name(spaceName)
                .type(request.getType())
                .address(request.getAddress())
                .contactNumber(request.getContactNumber())
                .discoverable(request.getDiscoverable() == null || Boolean.TRUE.equals(request.getDiscoverable()))
                .genderPolicy(request.getGenderPolicy())
                .foodIncludedInRent(Boolean.TRUE.equals(request.getFoodIncludedInRent()))
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();

        space = spaceRepository.save(space);

        spaceAmenityService.replaceForSpace(space, request.getAmenities());

        SpaceMembershipEntity ownerMembership = SpaceMembershipEntity.builder()
                .user(owner)
                .space(space)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

        spaceMembershipRepository.save(ownerMembership);
        memberMasterService.linkMemberToMembership(
                ownerMembership, owner.getFullName(), owner.getMobileNumber());

        mealSpaceSetupService.ensureSampleCombos(space);
        mealPlanService.ensurePresetPlans(space.getId());
        inventorySeedService.seedDefaults(space);

        return SpaceMapper.toCreateResponse(space);
    }

    @Transactional(readOnly = true)
    public SpaceDetailsResponse getSpaceById(UUID spaceId, UUID callerId) {
        log.info("Fetching space: spaceId={}, callerId={}", spaceId, callerId);

        SpaceEntity entity = spaceRepository.findByIdAndIsActiveTrue(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));

        boolean member = spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                callerId, spaceId, MembershipStatus.ACTIVE);
        if (!member) {
            throw new ResourceNotFoundException("Space", "id", spaceId);
        }

        return SpaceMapper.toDetailsResponse(
                SpaceMapper.toDomain(entity), spaceAmenityService.getForSpace(spaceId));
    }

    @Transactional
    public SpaceDetailsResponse updateSpace(UUID spaceId, UUID callerId, UpdateSpaceRequest request) {
        log.info("Updating space: spaceId={}, callerId={}", spaceId, callerId);

        SpaceEntity entity = spaceRepository.findByIdAndIsActiveTrue(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));

        assertCanManageSpace(entity, callerId);

        Space updated = SpaceMapper.applyUpdate(SpaceMapper.toDomain(entity), request);
        if (request.getName() != null) {
            String nextName = request.getName().trim();
            if (!nextName.isEmpty()
                    && !nextName.equalsIgnoreCase(entity.getName())
                    && spaceRepository.existsByOwnerIdAndIsActiveTrueAndNameIgnoreCaseAndIdNot(
                            entity.getOwner().getId(), nextName, spaceId)) {
                throw new BusinessException(
                        "SPACE_NAME_TAKEN",
                        "You already have a space with this name.",
                        HttpStatus.CONFLICT);
            }
        }
        SpaceMapper.applyToEntity(entity, updated);

        SpaceEntity saved = spaceRepository.save(entity);
        if (request.getAmenities() != null) {
            spaceAmenityService.replaceForSpace(saved, request.getAmenities());
        }
        return SpaceMapper.toDetailsResponse(
                SpaceMapper.toDomain(saved), spaceAmenityService.getForSpace(spaceId));
    }

    @Transactional(readOnly = true)
    public List<UserSpaceResponse> getUserSpaces(UUID userId) {
        log.info("Fetching user spaces: userId={}", userId);

        return spaceMembershipRepository.findByUserIdWithSpace(userId)
                .stream()
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                .filter(membership -> membership.getSpace().isActive())
                .map(UserSpaceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MySpaceResponse> getMySpaces(UUID userId) {
        log.info("Fetching my spaces: userId={}", userId);

        return spaceMembershipRepository.findUserSpaces(userId)
                .stream()
                .map(MySpaceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MySpaceResponse> searchMySpaces(UUID userId, String search) {
        log.info("Searching spaces: userId={}, search={}", userId, search);

        return spaceMembershipRepository.searchUserSpaces(userId, search.trim())
                .stream()
                .map(MySpaceResponse::from)
                .toList();
    }

    @Transactional
    public SetDefaultSpaceResponse setDefaultSpace(UUID userId, UUID spaceId) {
        log.info("Setting default space: userId={}, spaceId={}", userId, spaceId);

        SpaceMembershipEntity membership = spaceMembershipRepository
                .findMembershipByUserAndSpace(userId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Space membership", "spaceId", spaceId));

        spaceMembershipRepository.clearDefaultSpaceForUser(userId);

        membership.setDefault(true);
        spaceMembershipRepository.save(membership);

        return SetDefaultSpaceResponse.builder()
                .spaceId(membership.getSpace().getId())
                .spaceName(membership.getSpace().getName())
                .isDefault(true)
                .build();
    }

    @Transactional(readOnly = true)
    public DefaultSpaceResponse getDefaultSpace(UUID userId) {
        log.info("Fetching default space: userId={}", userId);

        SpaceMembershipEntity membership = spaceMembershipRepository.findDefaultSpace(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Default space", "userId", userId));

        return DefaultSpaceResponse.from(membership);
    }

    @Transactional
    public void deactivateSpace(UUID spaceId, UUID callerId) {
        log.info("Deactivating space: spaceId={}, callerId={}", spaceId, callerId);

        SpaceEntity entity = spaceRepository.findByIdAndIsActiveTrue(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));

        assertOwner(entity, callerId);

        entity.setActive(false);
        spaceRepository.save(entity);
        membershipNotificationSyncService.onSpaceDeactivated(entity, callerId);
    }

    /**
     * Moves Space ownership to another active user. Used when a real owner claims/replaces an
     * admin-published listing that was held under a provisional admin owner.
     */
    @Transactional
    public void transferOwnership(UUID spaceId, UUID newOwnerUserId) {
        SpaceEntity space = spaceRepository
                .lockById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
        if (!space.isActive()) {
            throw new ResourceNotFoundException("Space", "id", spaceId);
        }
        UserEntity newOwner = userRepository
                .findByIdAndIsActiveTrue(newOwnerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", newOwnerUserId));

        UUID previousOwnerId = space.getOwner().getId();
        if (previousOwnerId.equals(newOwnerUserId)) {
            return;
        }

        space.setOwner(newOwner);
        spaceRepository.save(space);

        for (SpaceMembershipEntity ownerMembership :
                spaceMembershipRepository.findBySpaceIdAndRole(spaceId, MembershipRole.OWNER)) {
            if (ownerMembership.getUser().getId().equals(previousOwnerId)
                    && ownerMembership.getStatus() == MembershipStatus.ACTIVE) {
                ownerMembership.setStatus(MembershipStatus.REMOVED);
                ownerMembership.setExitedAt(LocalDateTime.now());
                ownerMembership.setDefault(false);
                spaceMembershipRepository.save(ownerMembership);
            }
        }

        SpaceMembershipEntity existing =
                spaceMembershipRepository.findByUserIdAndSpaceId(newOwnerUserId, spaceId).orElse(null);
        if (existing != null) {
            existing.setRole(MembershipRole.OWNER);
            existing.setStatus(MembershipStatus.ACTIVE);
            existing.setExitedAt(null);
            if (existing.getJoinedAt() == null) {
                existing.setJoinedAt(LocalDateTime.now());
            }
            spaceMembershipRepository.save(existing);
            memberMasterService.linkMemberToMembership(
                    existing, newOwner.getFullName(), newOwner.getMobileNumber());
        } else {
            SpaceMembershipEntity ownerMembership = SpaceMembershipEntity.builder()
                    .user(newOwner)
                    .space(space)
                    .role(MembershipRole.OWNER)
                    .status(MembershipStatus.ACTIVE)
                    .joinedAt(LocalDateTime.now())
                    .build();
            spaceMembershipRepository.save(ownerMembership);
            memberMasterService.linkMemberToMembership(
                    ownerMembership, newOwner.getFullName(), newOwner.getMobileNumber());
        }

        membershipNotificationSyncService.onOwnershipTransferred(space, previousOwnerId, newOwnerUserId);

        log.info(
                "Transferred space {} ownership from {} to {}",
                spaceId,
                previousOwnerId,
                newOwnerUserId);
    }

    private void assertOwner(SpaceEntity space, UUID callerId) {
        if (!space.getOwner().getId().equals(callerId)) {
            throw new BusinessException(
                    "Only the space owner can perform this action", HttpStatus.FORBIDDEN);
        }
    }

    private void assertCanManageSpace(SpaceEntity space, UUID callerId) {
        if (space.getOwner().getId().equals(callerId)) {
            return;
        }
        if (spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                callerId, space.getId(), List.of(MembershipRole.OWNER, MembershipRole.MANAGER))) {
            return;
        }
        throw new BusinessException(
                "Only OWNER or MANAGER can perform this action", HttpStatus.FORBIDDEN);
    }
}
