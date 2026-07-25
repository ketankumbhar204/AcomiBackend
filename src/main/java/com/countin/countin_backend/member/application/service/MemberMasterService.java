package com.countin.countin_backend.member.application.service;

import com.countin.countin_backend.common.exception.BusinessException;
import com.countin.countin_backend.common.exception.ResourceNotFoundException;
import com.countin.countin_backend.common.util.MobileNumberNormalizer;
import com.countin.countin_backend.meal.application.service.MealParticipationService;
import com.countin.countin_backend.member.api.dto.request.CreateMemberDocumentRequest;
import com.countin.countin_backend.member.api.dto.request.CreateMemberNoteRequest;
import com.countin.countin_backend.member.api.dto.request.CreateMemberRequest;
import com.countin.countin_backend.member.api.dto.request.ImportMemberRequest;
import com.countin.countin_backend.member.api.dto.request.UpdateDepositRequest;
import com.countin.countin_backend.member.api.dto.request.UpdateEmergencyContactRequest;
import com.countin.countin_backend.member.api.dto.request.UpdateMemberRequest;
import com.countin.countin_backend.member.api.dto.request.UpdateMemberStatusRequest;
import com.countin.countin_backend.member.api.dto.response.MemberDetailsResponse;
import com.countin.countin_backend.member.api.dto.response.MemberDocumentResponse;
import com.countin.countin_backend.member.api.dto.response.MemberHistoryResponse;
import com.countin.countin_backend.member.api.dto.response.MemberImportCandidateResponse;
import com.countin.countin_backend.member.api.dto.response.MemberNoteResponse;
import com.countin.countin_backend.member.api.dto.response.MemberResponse;
import com.countin.countin_backend.member.domain.model.MemberGender;
import com.countin.countin_backend.member.domain.model.MemberHistoryAction;
import com.countin.countin_backend.member.domain.model.MemberStatus;
import com.countin.countin_backend.member.domain.model.MembershipRole;
import com.countin.countin_backend.member.domain.model.MembershipStatus;
import com.countin.countin_backend.member.infrastructure.persistence.entity.MemberDocumentEntity;
import com.countin.countin_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.countin.countin_backend.member.infrastructure.persistence.entity.MemberHistoryEntity;
import com.countin.countin_backend.member.infrastructure.persistence.entity.MemberNoteEntity;
import com.countin.countin_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.countin.countin_backend.member.infrastructure.persistence.repository.MemberDocumentRepository;
import com.countin.countin_backend.member.infrastructure.persistence.repository.MemberHistoryRepository;
import com.countin.countin_backend.member.infrastructure.persistence.repository.MemberNoteRepository;
import com.countin.countin_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.countin.countin_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.countin.countin_backend.occupancy.application.service.OccupancyService;
import com.countin.countin_backend.occupancy.domain.model.MemberOccupancyStatus;
import com.countin.countin_backend.space.domain.model.MealBillingType;
import com.countin.countin_backend.space.domain.model.SpaceType;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.countin.countin_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.countin.countin_backend.user.api.dto.response.UserResponse;
import com.countin.countin_backend.user.infrastructure.persistence.entity.UserEntity;
import com.countin.countin_backend.user.infrastructure.persistence.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberMasterService {

    private static final List<MembershipRole> OWNER_OR_MANAGER =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER);

    private static final List<SpaceType> ACCOMMODATION_SPACE_TYPES = List.of(
            SpaceType.PG, SpaceType.HOSTEL, SpaceType.CO_LIVING, SpaceType.RENTAL);

    /** Portfolio scope for Mess customer import: all space types the owner may manage. */
    private static final List<SpaceType> MESS_IMPORT_SOURCE_TYPES = List.of(
            SpaceType.MESS, SpaceType.PG, SpaceType.HOSTEL, SpaceType.CO_LIVING, SpaceType.RENTAL);

    private static final Set<MemberStatus> IMPORTABLE_MEMBER_STATUSES =
            EnumSet.of(MemberStatus.ACTIVE, MemberStatus.VACATED);

    private final SpaceRepository spaceRepository;
    private final MemberRepository memberRepository;
    private final MemberDocumentRepository memberDocumentRepository;
    private final MemberNoteRepository memberNoteRepository;
    private final MemberHistoryRepository memberHistoryRepository;
    private final UserRepository userRepository;
    private final SpaceMembershipRepository spaceMembershipRepository;
    private final OccupancyService occupancyService;
    private final MealParticipationService mealParticipationService;
    private final InvitationProvisioner invitationProvisioner;

    @Transactional
    public MemberResponse createMember(UUID spaceId, UUID callerId, CreateMemberRequest request) {
        log.info("Creating member: spaceId={}, callerId={}, mobileNumber={}",
                spaceId, callerId, request.getMobileNumber());

        SpaceEntity space = loadActiveSpace(spaceId);
        assertOwnerOrManager(spaceId, callerId);
        assertRoleAllowedForMemberApi(request.getRole());

        String mobileNumber = MobileNumberNormalizer.normalize(request.getMobileNumber());
        assertMobileAllowedForMemberRecord(spaceId, mobileNumber, null);

        Optional<UserEntity> existingUser = userRepository.findByMobileNumber(mobileNumber);

        UserEntity invitedBy = userRepository
                .findByIdAndIsActiveTrue(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", callerId));

        LocalDateTime now = LocalDateTime.now();
        MemberEntity member = MemberEntity.builder()
                .space(space)
                .user(existingUser.orElse(null))
                .membership(null)
                .fullName(request.getFullName())
                .mobileNumber(mobileNumber)
                .role(request.getRole())
                .gender(request.getGender())
                .mealBillingType(request.getMealBillingType())
                .status(MemberStatus.ACTIVE)
                .statusUpdatedAt(now)
                .build();

        member = memberRepository.save(member);
        invitationProvisioner.ensurePendingInvitation(space, invitedBy, mobileNumber, request.getRole());
        return MemberResponse.from(member);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> getMembers(
            UUID spaceId, UUID callerId, String search, MemberOccupancyStatus occupancyStatus) {
        log.info(
                "Fetching members: spaceId={}, callerId={}, search={}, occupancyStatus={}",
                spaceId,
                callerId,
                search,
                occupancyStatus);

        assertSpaceExists(spaceId);
        assertCallerBelongsToSpace(spaceId, callerId);
        syncOwnerManagerMembershipRecords(spaceId);

        String normalizedSearch = normalizeSearch(search);
        List<MemberEntity> members = normalizedSearch != null || occupancyStatus != null
                ? memberRepository.searchActiveMembers(spaceId, normalizedSearch, occupancyStatus)
                : memberRepository.findBySpaceIdAndActiveTrue(spaceId);

        return members.stream().map(MemberResponse::from).toList();
    }

    /**
     * Search reusable members for the target space.
     * Lodging targets: vacated TENANTs across managed accommodation spaces.
     * Mess targets: CUSTOMERs from managed Mess spaces and TENANTs from managed lodging
     * (PG→Mess reuse). Concurrent Mess memberships are allowed.
     */
    @Transactional(readOnly = true)
    public List<MemberImportCandidateResponse> searchImportCandidates(
            UUID targetSpaceId, UUID callerId, String search) {
        log.info(
                "Searching import candidates: targetSpaceId={}, callerId={}, search={}",
                targetSpaceId,
                callerId,
                search);

        SpaceEntity targetSpace = loadActiveSpace(targetSpaceId);
        assertOwnerOrManager(targetSpaceId, callerId);

        if (targetSpace.getType() == SpaceType.MESS) {
            return searchMessImportCandidates(targetSpace, callerId, search);
        }

        assertAccommodationSpace(targetSpace);

        List<UUID> managedSpaceIds = spaceMembershipRepository.findManagedSpaceIdsByTypesAndRoles(
                callerId, OWNER_OR_MANAGER, ACCOMMODATION_SPACE_TYPES);
        if (managedSpaceIds.isEmpty() || !managedSpaceIds.contains(targetSpaceId)) {
            return List.of();
        }

        String normalizedSearch = normalizeSearch(search);
        List<MemberEntity> candidates = memberRepository.searchImportCandidates(
                targetSpaceId, managedSpaceIds, managedSpaceIds, normalizedSearch);

        return candidates.stream()
                .map(member -> MemberImportCandidateResponse.from(member, targetSpaceId, true))
                .toList();
    }

    private List<MemberImportCandidateResponse> searchMessImportCandidates(
            SpaceEntity targetSpace, UUID callerId, String search) {
        UUID targetSpaceId = targetSpace.getId();
        List<UUID> managedSpaceIds = spaceMembershipRepository.findManagedSpaceIdsByTypesAndRoles(
                callerId, OWNER_OR_MANAGER, MESS_IMPORT_SOURCE_TYPES);
        if (managedSpaceIds.isEmpty() || !managedSpaceIds.contains(targetSpaceId)) {
            return List.of();
        }

        String normalizedSearch = normalizeSearch(search);
        List<MemberEntity> candidates = memberRepository.searchMessImportCandidates(
                targetSpaceId, managedSpaceIds, normalizedSearch);

        return candidates.stream()
                .map(member -> MemberImportCandidateResponse.from(member, targetSpaceId, true))
                .toList();
    }

    /**
     * Copies an eligible member into the target space.
     * Lodging: TENANT only; blocks busy occupancy.
     * Mess: creates CUSTOMER from Mess CUSTOMER or lodging TENANT; allows concurrent Mess memberships.
     * Does not copy occupancy, deposits, or meal participation. Copies profile basics,
     * emergency contact, and document metadata when present.
     * Idempotent when the mobile already exists in the target space with the expected role.
     */
    @Transactional
    public MemberResponse importMember(UUID targetSpaceId, UUID callerId, ImportMemberRequest request) {
        log.info(
                "Importing member: targetSpaceId={}, callerId={}, sourceMemberId={}",
                targetSpaceId,
                callerId,
                request.getSourceMemberId());

        SpaceEntity targetSpace = loadActiveSpace(targetSpaceId);
        assertOwnerOrManager(targetSpaceId, callerId);

        if (targetSpace.getType() == SpaceType.MESS) {
            return importMessCustomer(targetSpace, callerId, request);
        }

        assertAccommodationSpace(targetSpace);

        MemberEntity source = memberRepository
                .findByIdAndIsActiveTrue(request.getSourceMemberId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Member", "id", request.getSourceMemberId()));

        UUID sourceSpaceId = source.getSpace().getId();
        assertOwnerOrManager(sourceSpaceId, callerId);
        assertAccommodationSpace(source.getSpace());
        assertImportableSourceMember(source);

        List<UUID> managedSpaceIds = spaceMembershipRepository.findManagedSpaceIdsByTypesAndRoles(
                callerId, OWNER_OR_MANAGER, ACCOMMODATION_SPACE_TYPES);
        if (!managedSpaceIds.contains(targetSpaceId) || !managedSpaceIds.contains(sourceSpaceId)) {
            throw new BusinessException(
                    "You do not manage both the source and target spaces", HttpStatus.FORBIDDEN);
        }

        String mobileNumber = source.getMobileNumber();
        if (memberRepository.existsBusyOccupancyForMobileAcrossSpaces(mobileNumber, managedSpaceIds)) {
            throw new BusinessException(
                    "This resident currently has an active or reserved occupancy and cannot be moved in again",
                    HttpStatus.CONFLICT);
        }

        Optional<MemberEntity> existingInTarget =
                memberRepository.findActiveBySpaceIdAndMobileNumber(targetSpaceId, mobileNumber);
        if (existingInTarget.isPresent()) {
            MemberEntity existing = existingInTarget.get();
            if (existing.getRole() != MembershipRole.TENANT) {
                throw new BusinessException(
                        "A non-tenant member with this mobile already exists in this space",
                        HttpStatus.CONFLICT);
            }
            return MemberResponse.from(existing);
        }

        CreateMemberRequest createRequest = new CreateMemberRequest();
        setCreateMemberFields(
                createRequest,
                source.getFullName(),
                mobileNumber,
                MembershipRole.TENANT,
                source.getGender(),
                source.getMealBillingType());

        MemberResponse created = createMember(targetSpaceId, callerId, createRequest);
        MemberEntity targetMember = memberRepository
                .findByIdAndSpaceIdAndActiveTrue(created.getMemberId(), targetSpaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", created.getMemberId()));

        copyProfileExtras(source, targetMember, callerId);
        return MemberResponse.from(targetMember);
    }

    private MemberResponse importMessCustomer(
            SpaceEntity targetSpace, UUID callerId, ImportMemberRequest request) {
        UUID targetSpaceId = targetSpace.getId();

        MemberEntity source = memberRepository
                .findByIdAndIsActiveTrue(request.getSourceMemberId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Member", "id", request.getSourceMemberId()));

        UUID sourceSpaceId = source.getSpace().getId();
        assertOwnerOrManager(sourceSpaceId, callerId);
        assertMessImportableSourceMember(source);

        List<UUID> managedSpaceIds = spaceMembershipRepository.findManagedSpaceIdsByTypesAndRoles(
                callerId, OWNER_OR_MANAGER, MESS_IMPORT_SOURCE_TYPES);
        if (!managedSpaceIds.contains(targetSpaceId) || !managedSpaceIds.contains(sourceSpaceId)) {
            throw new BusinessException(
                    "You do not manage both the source and target spaces", HttpStatus.FORBIDDEN);
        }

        String mobileNumber = source.getMobileNumber();
        Optional<MemberEntity> existingInTarget =
                memberRepository.findActiveBySpaceIdAndMobileNumber(targetSpaceId, mobileNumber);
        if (existingInTarget.isPresent()) {
            MemberEntity existing = existingInTarget.get();
            if (existing.getRole() != MembershipRole.CUSTOMER) {
                throw new BusinessException(
                        "A non-customer member with this mobile already exists in this mess",
                        HttpStatus.CONFLICT);
            }
            return MemberResponse.from(existing);
        }

        CreateMemberRequest createRequest = new CreateMemberRequest();
        setCreateMemberFields(
                createRequest,
                source.getFullName(),
                mobileNumber,
                MembershipRole.CUSTOMER,
                source.getGender(),
                source.getMealBillingType());

        MemberResponse created = createMember(targetSpaceId, callerId, createRequest);
        MemberEntity targetMember = memberRepository
                .findByIdAndSpaceIdAndActiveTrue(created.getMemberId(), targetSpaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", created.getMemberId()));

        copyProfileExtras(source, targetMember, callerId);
        return MemberResponse.from(targetMember);
    }

    @Transactional(readOnly = true)
    public MemberResponse getMyLinkedMember(UUID spaceId, UUID callerId) {
        assertSpaceExists(spaceId);
        assertCallerBelongsToSpace(spaceId, callerId);

        MemberEntity member = memberRepository
                .findActiveBySpaceIdAndUserId(spaceId, callerId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "linkedUser", callerId));

        return MemberResponse.from(member);
    }

    private static String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String trimmed = search.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Ensures active OWNER/MANAGER app memberships also appear in the member master list.
     * Backfills legacy spaces where only space_memberships existed.
     */
    @Transactional
    public void syncOwnerManagerMembershipRecords(UUID spaceId) {
        for (SpaceMembershipEntity membership : spaceMembershipRepository.findActiveMembers(spaceId)) {
            MembershipRole role = membership.getRole();
            if (role != MembershipRole.OWNER && role != MembershipRole.MANAGER) {
                continue;
            }
            UserEntity user = membership.getUser();
            if (user == null) {
                continue;
            }
            linkMemberToMembership(membership, user.getFullName(), user.getMobileNumber());
        }
    }

    @Transactional(readOnly = true)
    public MemberDetailsResponse getMember(UUID spaceId, UUID memberId, UUID callerId) {
        log.info("Fetching member details: spaceId={}, memberId={}, callerId={}",
                spaceId, memberId, callerId);

        assertSpaceExists(spaceId);
        assertCallerBelongsToSpace(spaceId, callerId);

        MemberEntity member = memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        MemberDetailsResponse response = toMemberDetails(member);
        UserResponse linkedProfile = resolveLinkedUserProfile(member, spaceId, callerId);
        if (linkedProfile != null) {
            return response.toBuilder().linkedUserProfile(linkedProfile).build();
        }
        return response;
    }

    @Transactional
    public MemberDetailsResponse updateMember(
            UUID spaceId, UUID memberId, UUID callerId, UpdateMemberRequest request) {
        log.info("Updating member: spaceId={}, memberId={}, callerId={}",
                spaceId, memberId, callerId);

        assertSpaceExists(spaceId);
        assertOwnerOrManager(spaceId, callerId);
        assertRoleAllowedForMemberApi(request.getRole());

        MemberEntity member = memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        if (member.getRole() == MembershipRole.OWNER) {
            throw new BusinessException("OWNER role cannot be modified");
        }

        String mobileNumber = MobileNumberNormalizer.normalize(request.getMobileNumber());
        assertMobileAllowedForMemberRecord(spaceId, mobileNumber, memberId);

        Optional<UserEntity> existingUser = userRepository.findByMobileNumber(mobileNumber);
        member.setFullName(request.getFullName());
        member.setMobileNumber(mobileNumber);
        member.setRole(request.getRole());
        member.setGender(request.getGender());
        member.setMealBillingType(request.getMealBillingType());
        member.setUser(existingUser.orElse(null));

        if (existingUser.isPresent()) {
            Optional<SpaceMembershipEntity> membership = spaceMembershipRepository.findMembership(
                    existingUser.get().getId(), spaceId);
            if (member.getMembership() != null
                    && membership.isPresent()
                    && member.getMembership().getId().equals(membership.get().getId())) {
                member.setMembership(membership.get());
            } else {
                member.setMembership(null);
            }
        } else {
            member.setMembership(null);
        }

        syncLinkedMembershipRole(member);

        MemberEntity saved = memberRepository.save(member);
        return MemberDetailsResponse.from(saved);
    }

    @Transactional
    public void removeMember(UUID spaceId, UUID memberId, UUID callerId) {
        log.info("Removing member: spaceId={}, memberId={}, callerId={}",
                spaceId, memberId, callerId);

        assertSpaceExists(spaceId);
        assertCallerIsOwner(spaceId, callerId);

        MemberEntity member = memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        if (member.getRole() == MembershipRole.OWNER) {
            throw new BusinessException("OWNER cannot be removed");
        }

        member.setActive(false);
        memberRepository.save(member);

        deactivateLinkedMembership(member);
    }

    @Transactional
    public MemberDetailsResponse updateMemberStatus(
            UUID spaceId, UUID memberId, UUID callerId, UpdateMemberStatusRequest request) {
        log.info("Updating member status: spaceId={}, memberId={}, callerId={}, status={}",
                spaceId, memberId, callerId, request.getStatus());

        assertSpaceExists(spaceId);
        assertOwnerOrManager(spaceId, callerId);

        MemberEntity member = loadActiveMember(spaceId, memberId);
        MemberStatus previousStatus = member.getStatus();

        if (previousStatus == request.getStatus()) {
            return MemberDetailsResponse.from(member);
        }

        member.setStatus(request.getStatus());
        member.setStatusUpdatedAt(LocalDateTime.now());

        MemberEntity saved = memberRepository.save(member);
        recordHistory(
                saved,
                MemberHistoryAction.STATUS_CHANGED,
                previousStatus.name(),
                request.getStatus().name(),
                callerId);

        return MemberDetailsResponse.from(saved);
    }

    @Transactional
    public MemberDetailsResponse updateEmergencyContact(
            UUID spaceId, UUID memberId, UUID callerId, UpdateEmergencyContactRequest request) {
        log.info("Updating emergency contact: spaceId={}, memberId={}, callerId={}",
                spaceId, memberId, callerId);

        assertSpaceExists(spaceId);
        assertOwnerOrManager(spaceId, callerId);

        MemberEntity member = loadActiveMember(spaceId, memberId);
        String previousValue = formatEmergencyContact(member);

        member.setEmergencyContactName(request.getEmergencyContactName());
        member.setEmergencyContactRelation(request.getEmergencyContactRelation());
        member.setEmergencyContactMobile(request.getEmergencyContactMobile());

        MemberEntity saved = memberRepository.save(member);
        recordHistory(
                saved,
                MemberHistoryAction.EMERGENCY_CONTACT_UPDATED,
                previousValue,
                formatEmergencyContact(saved),
                callerId);

        return MemberDetailsResponse.from(saved);
    }

    @Transactional
    public MemberDetailsResponse updateDeposit(
            UUID spaceId, UUID memberId, UUID callerId, UpdateDepositRequest request) {
        log.info("Updating deposit: spaceId={}, memberId={}, callerId={}",
                spaceId, memberId, callerId);

        assertSpaceExists(spaceId);
        assertOwnerOrManager(spaceId, callerId);
        validateDeposit(request);

        MemberEntity member = loadActiveMember(spaceId, memberId);
        String previousValue = formatDeposit(member);

        member.setDepositAmount(request.getDepositAmount());
        member.setDepositPaid(request.getDepositPaid());
        member.setDepositRefunded(request.getDepositRefunded());

        MemberEntity saved = memberRepository.save(member);
        recordHistory(
                saved,
                MemberHistoryAction.DEPOSIT_UPDATED,
                previousValue,
                formatDeposit(saved),
                callerId);

        return MemberDetailsResponse.from(saved);
    }

    @Transactional
    public MemberDocumentResponse addMemberDocument(
            UUID spaceId, UUID memberId, UUID callerId, CreateMemberDocumentRequest request) {
        log.info("Adding member document: spaceId={}, memberId={}, callerId={}, type={}",
                spaceId, memberId, callerId, request.getDocumentType());

        assertSpaceExists(spaceId);
        MemberEntity member = loadActiveMember(spaceId, memberId);
        assertCanManageMemberDocuments(spaceId, member, callerId);

        MemberDocumentEntity document = MemberDocumentEntity.builder()
                .member(member)
                .documentType(request.getDocumentType())
                .documentNumber(request.getDocumentNumber())
                .fileUrl(request.getFileUrl())
                .uploadedAt(LocalDateTime.now())
                .build();

        document = memberDocumentRepository.save(document);
        return MemberDocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public List<MemberDocumentResponse> getMemberDocuments(
            UUID spaceId, UUID memberId, UUID callerId) {
        log.info("Fetching member documents: spaceId={}, memberId={}, callerId={}",
                spaceId, memberId, callerId);

        assertSpaceExists(spaceId);
        assertCallerBelongsToSpace(spaceId, callerId);
        loadActiveMember(spaceId, memberId);

        return memberDocumentRepository.findByMemberIdOrderByUploadedAtDesc(memberId)
                .stream()
                .map(MemberDocumentResponse::from)
                .toList();
    }

    @Transactional
    public void deleteMemberDocument(UUID spaceId, UUID memberId, UUID documentId, UUID callerId) {
        log.info("Deleting member document: spaceId={}, memberId={}, documentId={}, callerId={}",
                spaceId, memberId, documentId, callerId);

        assertSpaceExists(spaceId);
        MemberEntity member = loadActiveMember(spaceId, memberId);
        assertCanManageMemberDocuments(spaceId, member, callerId);

        MemberDocumentEntity document = memberDocumentRepository
                .findByIdAndMemberId(documentId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member document", "id", documentId));

        memberDocumentRepository.delete(document);
    }

    @Transactional
    public MemberNoteResponse addMemberNote(
            UUID spaceId, UUID memberId, UUID callerId, CreateMemberNoteRequest request) {
        log.info("Adding member note: spaceId={}, memberId={}, callerId={}",
                spaceId, memberId, callerId);

        assertSpaceExists(spaceId);
        assertOwnerOrManager(spaceId, callerId);

        MemberEntity member = loadActiveMember(spaceId, memberId);
        UserEntity author = loadCallerUser(callerId);

        MemberNoteEntity note = MemberNoteEntity.builder()
                .member(member)
                .note(request.getNote())
                .createdBy(author)
                .build();

        note = memberNoteRepository.save(note);
        return MemberNoteResponse.from(note);
    }

    @Transactional(readOnly = true)
    public List<MemberNoteResponse> getMemberNotes(UUID spaceId, UUID memberId, UUID callerId) {
        log.info("Fetching member notes: spaceId={}, memberId={}, callerId={}",
                spaceId, memberId, callerId);

        assertSpaceExists(spaceId);
        assertCallerBelongsToSpace(spaceId, callerId);
        loadActiveMember(spaceId, memberId);

        return memberNoteRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(MemberNoteResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MemberHistoryResponse> getMemberHistory(
            UUID spaceId, UUID memberId, UUID callerId) {
        log.info("Fetching member history: spaceId={}, memberId={}, callerId={}",
                spaceId, memberId, callerId);

        assertSpaceExists(spaceId);
        assertCallerBelongsToSpace(spaceId, callerId);
        loadActiveMember(spaceId, memberId);

        return memberHistoryRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(MemberHistoryResponse::from)
                .toList();
    }

    /**
     * Reserved for future invitation-accept flow: link or create a member from membership.
     */
    @Transactional
    public MemberEntity linkMemberToMembership(
            SpaceMembershipEntity membership, String fullName, String mobileNumber) {
        Optional<MemberEntity> existing = memberRepository.findActiveBySpaceIdAndMobileNumber(
                membership.getSpace().getId(), mobileNumber);

        if (existing.isPresent()) {
            MemberEntity member = existing.get();
            member.setUser(membership.getUser());
            member.setMembership(membership);
            member.setRole(membership.getRole());
            return memberRepository.save(member);
        }

        LocalDateTime now = LocalDateTime.now();
        MemberEntity member = MemberEntity.builder()
                .space(membership.getSpace())
                .user(membership.getUser())
                .membership(membership)
                .fullName(fullName)
                .mobileNumber(mobileNumber)
                .role(membership.getRole())
                .status(MemberStatus.ACTIVE)
                .statusUpdatedAt(now)
                .build();

        return memberRepository.save(member);
    }

    private MemberEntity loadActiveMember(UUID spaceId, UUID memberId) {
        return memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));
    }

    private UserEntity loadCallerUser(UUID callerId) {
        return userRepository.findById(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", callerId));
    }

    private void validateDeposit(UpdateDepositRequest request) {
        BigDecimal amount = request.getDepositAmount();
        BigDecimal paid = request.getDepositPaid();
        BigDecimal refunded = request.getDepositRefunded();

        if (paid.compareTo(amount) > 0) {
            throw new BusinessException("Deposit paid cannot exceed deposit amount");
        }
        if (refunded.compareTo(paid) > 0) {
            throw new BusinessException("Deposit refunded cannot exceed deposit paid");
        }
    }

    private void recordHistory(
            MemberEntity member,
            MemberHistoryAction action,
            String oldValue,
            String newValue,
            UUID callerId) {
        UserEntity changedBy = loadCallerUser(callerId);
        MemberHistoryEntity history = MemberHistoryEntity.builder()
                .member(member)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .changedBy(changedBy)
                .build();
        memberHistoryRepository.save(history);
    }

    private String formatEmergencyContact(MemberEntity member) {
        return String.format(
                "name=%s, relation=%s, mobile=%s",
                nullToEmpty(member.getEmergencyContactName()),
                nullToEmpty(member.getEmergencyContactRelation()),
                nullToEmpty(member.getEmergencyContactMobile()));
    }

    private String formatDeposit(MemberEntity member) {
        return String.format(
                "amount=%s, paid=%s, refunded=%s",
                member.getDepositAmount(), member.getDepositPaid(), member.getDepositRefunded());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void syncLinkedMembershipRole(MemberEntity member) {
        SpaceMembershipEntity membership = member.getMembership();
        if (membership == null || membership.getRole() == MembershipRole.OWNER) {
            return;
        }
        membership.setRole(member.getRole());
        spaceMembershipRepository.save(membership);
    }

    private void deactivateLinkedMembership(MemberEntity member) {
        if (member.getMembership() == null || member.getUser() == null) {
            return;
        }
        spaceMembershipRepository.deactivateMembership(
                member.getUser().getId(),
                member.getSpace().getId(),
                LocalDateTime.now());
    }

    private SpaceEntity loadActiveSpace(UUID spaceId) {
        return spaceRepository.findByIdAndIsActiveTrue(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }

    private void assertSpaceExists(UUID spaceId) {
        spaceRepository.findByIdAndIsActiveTrue(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }

    private void assertRoleAllowedForMemberApi(MembershipRole role) {
        if (role == MembershipRole.OWNER) {
            throw new BusinessException("OWNER role cannot be assigned via member APIs");
        }
    }

    private void assertCallerBelongsToSpace(UUID spaceId, UUID callerId) {
        boolean belongs = spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                callerId, spaceId, MembershipStatus.ACTIVE);
        if (!belongs) {
            throw new BusinessException(
                    "You do not have access to this space", HttpStatus.FORBIDDEN);
        }
    }

    private void assertCallerIsOwner(UUID spaceId, UUID callerId) {
        boolean isOwner = spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                callerId, spaceId, List.of(MembershipRole.OWNER));
        if (!isOwner) {
            throw new BusinessException(
                    "Only the space owner can perform this action", HttpStatus.FORBIDDEN);
        }
    }

    private void assertOwnerOrManager(UUID spaceId, UUID callerId) {
        boolean allowed = spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                callerId, spaceId, OWNER_OR_MANAGER);
        if (!allowed) {
            throw new BusinessException(
                    "Only OWNER or MANAGER can perform this action", HttpStatus.FORBIDDEN);
        }
    }

    private void assertAccommodationSpace(SpaceEntity space) {
        if (space.getType() == null || !ACCOMMODATION_SPACE_TYPES.contains(space.getType())) {
            throw new BusinessException(
                    "Resident import is only available for PG, Hostel, Co-living, and Rental spaces");
        }
    }

    private void assertImportableSourceMember(MemberEntity source) {
        if (source.getRole() != MembershipRole.TENANT) {
            throw new BusinessException("Only TENANT residents can be imported for accommodation Move In");
        }
        if (!IMPORTABLE_MEMBER_STATUSES.contains(source.getStatus())) {
            throw new BusinessException("This resident is blocked or suspended and cannot be imported");
        }
        if (source.getOccupancyStatus() == MemberOccupancyStatus.ALLOCATED
                || source.getOccupancyStatus() == MemberOccupancyStatus.RESERVED) {
            throw new BusinessException(
                    "This resident currently has an active or reserved occupancy and cannot be moved in again",
                    HttpStatus.CONFLICT);
        }
    }

    private void assertMessImportableSourceMember(MemberEntity source) {
        if (!IMPORTABLE_MEMBER_STATUSES.contains(source.getStatus())) {
            throw new BusinessException("This member is blocked or suspended and cannot be imported");
        }
        SpaceType sourceType = source.getSpace() != null ? source.getSpace().getType() : null;
        MembershipRole role = source.getRole();
        boolean messCustomer =
                sourceType == SpaceType.MESS && role == MembershipRole.CUSTOMER;
        boolean lodgingTenant =
                sourceType != null
                        && ACCOMMODATION_SPACE_TYPES.contains(sourceType)
                        && role == MembershipRole.TENANT;
        if (!messCustomer && !lodgingTenant) {
            throw new BusinessException(
                    "Only Mess customers or accommodation residents can be imported into a Mess");
        }
    }

    private void setCreateMemberFields(
            CreateMemberRequest request,
            String fullName,
            String mobileNumber,
            MembershipRole role,
            MemberGender gender,
            MealBillingType mealBillingType) {
        setField(request, "fullName", fullName);
        setField(request, "mobileNumber", mobileNumber);
        setField(request, "role", role);
        setField(request, "gender", gender);
        setField(request, "mealBillingType", mealBillingType);
    }

    private void copyProfileExtras(MemberEntity source, MemberEntity target, UUID callerId) {
        boolean dirty = false;
        if (source.getMemberCategory() != null && target.getMemberCategory() == null) {
            target.setMemberCategory(source.getMemberCategory());
            dirty = true;
        }

        boolean hasEmergency = source.getEmergencyContactName() != null
                && !source.getEmergencyContactName().isBlank()
                && source.getEmergencyContactRelation() != null
                && !source.getEmergencyContactRelation().isBlank()
                && source.getEmergencyContactMobile() != null
                && !source.getEmergencyContactMobile().isBlank();
        if (hasEmergency
                && (target.getEmergencyContactName() == null || target.getEmergencyContactName().isBlank())) {
            target.setEmergencyContactName(source.getEmergencyContactName());
            target.setEmergencyContactRelation(source.getEmergencyContactRelation());
            target.setEmergencyContactMobile(source.getEmergencyContactMobile());
            dirty = true;
        }
        if (dirty) {
            memberRepository.save(target);
        }

        List<MemberDocumentEntity> documents =
                memberDocumentRepository.findByMemberIdOrderByUploadedAtDesc(source.getId());
        for (MemberDocumentEntity document : documents) {
            MemberDocumentEntity copy = MemberDocumentEntity.builder()
                    .member(target)
                    .documentType(document.getDocumentType())
                    .documentNumber(document.getDocumentNumber())
                    .fileUrl(document.getFileUrl())
                    .verificationStatus(document.getVerificationStatus())
                    .uploadedAt(LocalDateTime.now())
                    .build();
            memberDocumentRepository.save(copy);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to set field " + fieldName, e);
        }
    }

    private void assertCanManageMemberDocuments(
            UUID spaceId, MemberEntity member, UUID callerId) {
        if (member.getUser() != null && member.getUser().getId().equals(callerId)) {
            assertCallerBelongsToSpace(spaceId, callerId);
            return;
        }
        assertOwnerOrManager(spaceId, callerId);
    }

    private void assertMobileAllowedForMemberRecord(
            UUID spaceId, String mobileNumber, UUID updatingMemberId) {
        Optional<MemberEntity> memberWithMobile =
                memberRepository.findActiveBySpaceIdAndMobileNumber(spaceId, mobileNumber);

        if (memberWithMobile.isPresent()
                && (updatingMemberId == null
                        || !memberWithMobile.get().getId().equals(updatingMemberId))) {
            throw new BusinessException(
                    "A member with this mobile number already exists in this space.",
                    HttpStatus.CONFLICT);
        }

        Optional<UserEntity> existingUser = userRepository.findByMobileNumber(mobileNumber);
        if (existingUser.isEmpty()) {
            return;
        }

        UUID userId = existingUser.get().getId();
        if (!spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                userId, spaceId, MembershipStatus.ACTIVE)) {
            return;
        }

        if (updatingMemberId != null) {
            MemberEntity updating = memberRepository
                    .findByIdAndSpaceIdAndActiveTrue(updatingMemberId, spaceId)
                    .orElse(null);
            if (updating != null
                    && updating.getUser() != null
                    && updating.getUser().getId().equals(userId)
                    && updating.getMembership() != null) {
                return;
            }
        }

        throw new BusinessException(
                "An account with this mobile number already exists in this space.",
                HttpStatus.CONFLICT);
    }

    private MemberDetailsResponse toMemberDetails(MemberEntity member) {
        UUID spaceId = member.getSpace().getId();
        return MemberDetailsResponse.from(
                member,
                occupancyService.findCurrentOccupancySummary(spaceId, member.getId()).orElse(null),
                mealParticipationService.findActiveSummaryForMember(spaceId, member.getId()).orElse(null),
                occupancyService.findAssignedAmenitiesForMember(spaceId, member.getId()));
    }

    private UserResponse resolveLinkedUserProfile(
            MemberEntity member, UUID spaceId, UUID callerId) {
        UserEntity linkedUser = member.getUser();
        if (linkedUser == null) {
            return null;
        }

        if (linkedUser.getId().equals(callerId)) {
            return UserResponse.from(linkedUser);
        }

        boolean canView = spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                callerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER));
        if (!canView) {
            return null;
        }

        return UserResponse.from(linkedUser);
    }
}
