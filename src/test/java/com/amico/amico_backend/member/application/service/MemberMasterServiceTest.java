package com.amico.amico_backend.member.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.common.exception.ResourceNotFoundException;
import com.amico.amico_backend.member.api.dto.request.CreateMemberDocumentRequest;
import com.amico.amico_backend.member.api.dto.request.CreateMemberNoteRequest;
import com.amico.amico_backend.member.api.dto.request.CreateMemberRequest;
import com.amico.amico_backend.member.api.dto.request.ImportMemberRequest;
import com.amico.amico_backend.member.api.dto.request.UpdateDepositRequest;
import com.amico.amico_backend.member.api.dto.request.UpdateEmergencyContactRequest;
import com.amico.amico_backend.member.api.dto.request.UpdateMemberRequest;
import com.amico.amico_backend.member.api.dto.request.UpdateMemberStatusRequest;
import com.amico.amico_backend.member.api.dto.response.MemberDetailsResponse;
import com.amico.amico_backend.member.api.dto.response.MemberDocumentResponse;
import com.amico.amico_backend.member.api.dto.response.MemberHistoryResponse;
import com.amico.amico_backend.member.api.dto.response.MemberImportCandidateResponse;
import com.amico.amico_backend.member.api.dto.response.MemberNoteResponse;
import com.amico.amico_backend.member.api.dto.response.MemberResponse;
import com.amico.amico_backend.member.domain.model.DocumentVerificationStatus;
import com.amico.amico_backend.member.domain.model.MemberDocumentType;
import com.amico.amico_backend.member.domain.model.MemberHistoryAction;
import com.amico.amico_backend.member.domain.model.MemberGender;
import com.amico.amico_backend.member.domain.model.MemberStatus;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.domain.model.MembershipStatus;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberDocumentEntity;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberHistoryEntity;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberNoteEntity;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.amico.amico_backend.member.infrastructure.persistence.repository.MemberDocumentRepository;
import com.amico.amico_backend.member.infrastructure.persistence.repository.MemberHistoryRepository;
import com.amico.amico_backend.member.infrastructure.persistence.repository.MemberNoteRepository;
import com.amico.amico_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.amico.amico_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.amico.amico_backend.meal.application.service.MealParticipationService;
import com.amico.amico_backend.occupancy.application.service.OccupancyService;
import com.amico.amico_backend.occupancy.domain.model.MemberOccupancyStatus;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.amico.amico_backend.user.infrastructure.persistence.entity.UserEntity;
import com.amico.amico_backend.user.infrastructure.persistence.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MemberMasterServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberDocumentRepository memberDocumentRepository;

    @Mock
    private MemberNoteRepository memberNoteRepository;

    @Mock
    private MemberHistoryRepository memberHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private OccupancyService occupancyService;

    @Mock
    private MealParticipationService mealParticipationService;

    @Mock
    private InvitationProvisioner invitationProvisioner;

    @InjectMocks
    private MemberMasterService memberMasterService;

    private UUID spaceId;
    private UUID ownerId;
    private UUID memberId;
    private SpaceEntity space;
    private UserEntity owner;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        owner = UserEntity.builder()
                .mobileNumber("9876543210")
                .fullName("Owner User")
                .build();
        owner.setId(ownerId);

        space = SpaceEntity.builder()
                .owner(owner)
                .name("Sunrise PG")
                .type(SpaceType.PG)
                .isActive(true)
                .build();
        space.setId(spaceId);
    }

    @Test
    void createMember_whenOwner_createsActiveMember() {
        CreateMemberRequest request = new CreateMemberRequest();
        setField(request, "fullName", "Rahul Sharma");
        setField(request, "mobileNumber", "9123456789");
        setField(request, "role", MembershipRole.TENANT);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findActiveBySpaceIdAndMobileNumber(spaceId, "9123456789"))
                .thenReturn(Optional.empty());
        when(userRepository.findByMobileNumber("9123456789")).thenReturn(Optional.empty());
        when(userRepository.findByIdAndIsActiveTrue(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.save(any(MemberEntity.class))).thenAnswer(invocation -> {
            MemberEntity member = invocation.getArgument(0);
            member.setId(memberId);
            member.setCreatedAt(LocalDateTime.now());
            return member;
        });

        MemberResponse response = memberMasterService.createMember(spaceId, ownerId, request);

        assertThat(response.getMemberId()).isEqualTo(memberId);
        assertThat(response.getFullName()).isEqualTo("Rahul Sharma");
        assertThat(response.getRole()).isEqualTo(MembershipRole.TENANT);
        assertThat(response.isLinkedUser()).isFalse();
        verify(invitationProvisioner)
                .ensurePendingInvitation(eq(space), eq(owner), eq("9123456789"), eq(MembershipRole.TENANT));
    }

    @Test
    void createMember_persistsGender() {
        CreateMemberRequest request = new CreateMemberRequest();
        setField(request, "fullName", "Rahul Sharma");
        setField(request, "mobileNumber", "9123456789");
        setField(request, "role", MembershipRole.TENANT);
        setField(request, "gender", MemberGender.MALE);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findActiveBySpaceIdAndMobileNumber(spaceId, "9123456789"))
                .thenReturn(Optional.empty());
        when(userRepository.findByMobileNumber("9123456789")).thenReturn(Optional.empty());
        when(userRepository.findByIdAndIsActiveTrue(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.save(any(MemberEntity.class))).thenAnswer(invocation -> {
            MemberEntity member = invocation.getArgument(0);
            member.setId(memberId);
            member.setCreatedAt(LocalDateTime.now());
            return member;
        });

        MemberResponse response = memberMasterService.createMember(spaceId, ownerId, request);

        assertThat(response.getGender()).isEqualTo(MemberGender.MALE);
    }

    @Test
    void createMember_persistsMealBillingType() {
        CreateMemberRequest request = new CreateMemberRequest();
        setField(request, "fullName", "Rahul Sharma");
        setField(request, "mobileNumber", "9123456789");
        setField(request, "role", MembershipRole.CUSTOMER);
        setField(request, "mealBillingType", com.amico.amico_backend.space.domain.model.MealBillingType.PREPAID_BALANCE);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findActiveBySpaceIdAndMobileNumber(spaceId, "9123456789"))
                .thenReturn(Optional.empty());
        when(userRepository.findByMobileNumber("9123456789")).thenReturn(Optional.empty());
        when(userRepository.findByIdAndIsActiveTrue(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.save(any(MemberEntity.class))).thenAnswer(invocation -> {
            MemberEntity member = invocation.getArgument(0);
            member.setId(memberId);
            member.setCreatedAt(LocalDateTime.now());
            assertThat(member.getMealBillingType())
                    .isEqualTo(com.amico.amico_backend.space.domain.model.MealBillingType.PREPAID_BALANCE);
            return member;
        });

        memberMasterService.createMember(spaceId, ownerId, request);
    }

    @Test
    void updateMember_persistsGender() {
        MemberEntity member = activeMember("Rahul Sharma", "9123456789", MembershipRole.TENANT);
        UpdateMemberRequest request = new UpdateMemberRequest();
        setField(request, "fullName", "Rahul Sharma");
        setField(request, "mobileNumber", "9123456789");
        setField(request, "role", MembershipRole.TENANT);
        setField(request, "gender", MemberGender.FEMALE);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId))
                .thenReturn(Optional.of(member));
        when(userRepository.findByMobileNumber("9123456789")).thenReturn(Optional.empty());
        when(memberRepository.save(any(MemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MemberDetailsResponse response =
                memberMasterService.updateMember(spaceId, memberId, ownerId, request);

        assertThat(response.getGender()).isEqualTo(MemberGender.FEMALE);
        assertThat(member.getGender()).isEqualTo(MemberGender.FEMALE);
    }

    @Test
    void createMember_whenRoleIsOwner_throwsBusinessException() {
        CreateMemberRequest request = new CreateMemberRequest();
        setField(request, "fullName", "Rahul Sharma");
        setField(request, "mobileNumber", "9123456789");
        setField(request, "role", MembershipRole.OWNER);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);

        assertThatThrownBy(() -> memberMasterService.createMember(spaceId, ownerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OWNER role cannot be assigned");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void createMember_whenMobileAlreadyUsedByMember_throwsBusinessException() {
        CreateMemberRequest request = new CreateMemberRequest();
        setField(request, "fullName", "Duplicate User");
        setField(request, "mobileNumber", "9123456789");
        setField(request, "role", MembershipRole.CUSTOMER);

        MemberEntity existing = activeMember("Existing User", "9123456789", MembershipRole.CUSTOMER);
        existing.setId(UUID.randomUUID());

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findActiveBySpaceIdAndMobileNumber(spaceId, "9123456789"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> memberMasterService.createMember(spaceId, ownerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("member with this mobile number already exists")
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(memberRepository, never()).save(any());
    }

    @Test
    void createMember_whenMobileBelongsToExistingAccountInSpace_throwsBusinessException() {
        CreateMemberRequest request = new CreateMemberRequest();
        setField(request, "fullName", "Owner As Customer");
        setField(request, "mobileNumber", owner.getMobileNumber());
        setField(request, "role", MembershipRole.CUSTOMER);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findActiveBySpaceIdAndMobileNumber(spaceId, owner.getMobileNumber()))
                .thenReturn(Optional.empty());
        when(userRepository.findByMobileNumber(owner.getMobileNumber())).thenReturn(Optional.of(owner));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                ownerId, spaceId, MembershipStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> memberMasterService.createMember(spaceId, ownerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("account with this mobile number already exists")
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(memberRepository, never()).save(any());
    }

    @Test
    void getMembers_returnsActiveMemberRecords() {
        MemberEntity member = activeMember("Rahul Sharma", "9123456789", MembershipRole.TENANT);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                ownerId, spaceId, MembershipStatus.ACTIVE)).thenReturn(true);
        when(spaceMembershipRepository.findActiveMembers(spaceId)).thenReturn(List.of());
        when(memberRepository.findBySpaceIdAndActiveTrue(spaceId)).thenReturn(List.of(member));

        List<MemberResponse> members = memberMasterService.getMembers(spaceId, ownerId, null, null);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).getMemberId()).isEqualTo(memberId);
        assertThat(members.get(0).getFullName()).isEqualTo("Rahul Sharma");
    }

    @Test
    void getMember_returnsDetails() {
        MemberEntity member = activeMember("Rahul Sharma", "9123456789", MembershipRole.TENANT);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                ownerId, spaceId, MembershipStatus.ACTIVE)).thenReturn(true);
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId))
                .thenReturn(Optional.of(member));

        MemberDetailsResponse response = memberMasterService.getMember(spaceId, memberId, ownerId);

        assertThat(response.getMemberId()).isEqualTo(memberId);
        assertThat(response.getFullName()).isEqualTo("Rahul Sharma");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void updateMember_whenManager_updatesMember() {
        MemberEntity member = activeMember("Rahul Sharma", "9123456789", MembershipRole.TENANT);
        UpdateMemberRequest request = new UpdateMemberRequest();
        setField(request, "fullName", "Rahul S");
        setField(request, "mobileNumber", "9123456789");
        setField(request, "role", MembershipRole.MANAGER);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId))
                .thenReturn(Optional.of(member));
        when(userRepository.findByMobileNumber("9123456789")).thenReturn(Optional.empty());
        when(memberRepository.save(any(MemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MemberDetailsResponse response =
                memberMasterService.updateMember(spaceId, memberId, ownerId, request);

        assertThat(response.getFullName()).isEqualTo("Rahul S");
        assertThat(response.getRole()).isEqualTo(MembershipRole.MANAGER);
    }

    @Test
    void removeMember_whenOwner_softDeletesMember() {
        MemberEntity member = activeMember("Rahul Sharma", "9123456789", MembershipRole.TENANT);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER))).thenReturn(true);
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId))
                .thenReturn(Optional.of(member));
        when(memberRepository.save(any(MemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        memberMasterService.removeMember(spaceId, memberId, ownerId);

        assertThat(member.isActive()).isFalse();
        verify(memberRepository).save(member);
    }

    @Test
    void removeMember_whenCallerIsManager_throwsForbidden() {
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER))).thenReturn(false);

        assertThatThrownBy(() -> memberMasterService.removeMember(spaceId, memberId, ownerId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void updateMember_syncsLinkedMembershipRole() {
        UserEntity tenant = UserEntity.builder()
                .mobileNumber("9123456789")
                .fullName("Rahul Sharma")
                .build();
        tenant.setId(UUID.randomUUID());

        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(tenant)
                .space(space)
                .role(MembershipRole.TENANT)
                .status(MembershipStatus.ACTIVE)
                .build();
        membership.setId(UUID.randomUUID());

        MemberEntity member = MemberEntity.builder()
                .space(space)
                .user(tenant)
                .membership(membership)
                .fullName("Rahul Sharma")
                .mobileNumber("9123456789")
                .role(MembershipRole.TENANT)
                .isActive(true)
                .build();
        member.setId(memberId);

        UpdateMemberRequest request = new UpdateMemberRequest();
        setField(request, "fullName", "Rahul Sharma");
        setField(request, "mobileNumber", "9123456789");
        setField(request, "role", MembershipRole.MANAGER);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId))
                .thenReturn(Optional.of(member));
        when(userRepository.findByMobileNumber("9123456789")).thenReturn(Optional.of(tenant));
        when(spaceMembershipRepository.findMembership(tenant.getId(), spaceId))
                .thenReturn(Optional.of(membership));
        when(memberRepository.save(any(MemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(spaceMembershipRepository.save(any(SpaceMembershipEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        memberMasterService.updateMember(spaceId, memberId, ownerId, request);

        assertThat(membership.getRole()).isEqualTo(MembershipRole.MANAGER);
        verify(spaceMembershipRepository).save(membership);
    }

    @Test
    void updateMemberStatus_recordsHistoryAndUpdatesStatus() {
        MemberEntity member = activeMember("Rahul Sharma", "9123456789", MembershipRole.TENANT);
        UpdateMemberStatusRequest request = new UpdateMemberStatusRequest();
        setField(request, "status", MemberStatus.SUSPENDED);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.save(any(MemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberHistoryRepository.save(any(MemberHistoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MemberDetailsResponse response =
                memberMasterService.updateMemberStatus(spaceId, memberId, ownerId, request);

        assertThat(response.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getStatusUpdatedAt()).isNotNull();
        verify(memberHistoryRepository).save(any(MemberHistoryEntity.class));
    }

    @Test
    void updateDeposit_whenPaidExceedsAmount_throwsBusinessException() {
        UpdateDepositRequest request = new UpdateDepositRequest();
        setField(request, "depositAmount", new BigDecimal("1000.00"));
        setField(request, "depositPaid", new BigDecimal("1500.00"));
        setField(request, "depositRefunded", BigDecimal.ZERO);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);

        assertThatThrownBy(() ->
                        memberMasterService.updateDeposit(spaceId, memberId, ownerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Deposit paid cannot exceed deposit amount");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void updateDeposit_whenValid_updatesDepositAndRecordsHistory() {
        MemberEntity member = activeMember("Rahul Sharma", "9123456789", MembershipRole.TENANT);
        UpdateDepositRequest request = new UpdateDepositRequest();
        setField(request, "depositAmount", new BigDecimal("10000.00"));
        setField(request, "depositPaid", new BigDecimal("10000.00"));
        setField(request, "depositRefunded", new BigDecimal("2000.00"));

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.save(any(MemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberHistoryRepository.save(any(MemberHistoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MemberDetailsResponse response =
                memberMasterService.updateDeposit(spaceId, memberId, ownerId, request);

        assertThat(response.getDepositAmount()).isEqualByComparingTo("10000.00");
        assertThat(response.getDepositBalance()).isEqualByComparingTo("8000.00");
        verify(memberHistoryRepository).save(any(MemberHistoryEntity.class));
    }

    @Test
    void updateEmergencyContact_updatesFieldsAndRecordsHistory() {
        MemberEntity member = activeMember("Rahul Sharma", "9123456789", MembershipRole.TENANT);
        UpdateEmergencyContactRequest request = new UpdateEmergencyContactRequest();
        setField(request, "emergencyContactName", "Priya Sharma");
        setField(request, "emergencyContactRelation", "Mother");
        setField(request, "emergencyContactMobile", "9988776655");

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.save(any(MemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberHistoryRepository.save(any(MemberHistoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MemberDetailsResponse response =
                memberMasterService.updateEmergencyContact(spaceId, memberId, ownerId, request);

        assertThat(response.getEmergencyContactName()).isEqualTo("Priya Sharma");
        assertThat(response.getEmergencyContactRelation()).isEqualTo("Mother");
        verify(memberHistoryRepository).save(any(MemberHistoryEntity.class));
    }

    @Test
    void addMemberDocument_createsPendingDocument() {
        MemberEntity member = activeMember("Rahul Sharma", "9123456789", MembershipRole.TENANT);
        CreateMemberDocumentRequest request = new CreateMemberDocumentRequest();
        setField(request, "documentType", MemberDocumentType.AADHAAR);
        setField(request, "documentNumber", "1234-5678-9012");
        setField(request, "fileUrl", "pending-upload");

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId))
                .thenReturn(Optional.of(member));
        when(memberDocumentRepository.save(any(MemberDocumentEntity.class))).thenAnswer(invocation -> {
            MemberDocumentEntity document = invocation.getArgument(0);
            document.setId(UUID.randomUUID());
            return document;
        });

        MemberDocumentResponse response =
                memberMasterService.addMemberDocument(spaceId, memberId, ownerId, request);

        assertThat(response.getDocumentType()).isEqualTo(MemberDocumentType.AADHAAR);
        assertThat(response.getVerificationStatus()).isEqualTo(DocumentVerificationStatus.PENDING);
    }

    @Test
    void addMemberNote_createsNoteWithAuthor() {
        MemberEntity member = activeMember("Rahul Sharma", "9123456789", MembershipRole.TENANT);
        CreateMemberNoteRequest request = new CreateMemberNoteRequest();
        setField(request, "note", "Requested early checkout.");

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(memberNoteRepository.save(any(MemberNoteEntity.class))).thenAnswer(invocation -> {
            MemberNoteEntity note = invocation.getArgument(0);
            note.setId(UUID.randomUUID());
            note.setCreatedAt(LocalDateTime.now());
            return note;
        });

        MemberNoteResponse response =
                memberMasterService.addMemberNote(spaceId, memberId, ownerId, request);

        assertThat(response.getNote()).isEqualTo("Requested early checkout.");
        assertThat(response.getCreatedBy()).isEqualTo(ownerId);
    }

    @Test
    void getMemberHistory_returnsHistoryEntries() {
        MemberEntity member = activeMember("Rahul Sharma", "9123456789", MembershipRole.TENANT);
        MemberHistoryEntity history = MemberHistoryEntity.builder()
                .member(member)
                .action(MemberHistoryAction.STATUS_CHANGED)
                .oldValue("ACTIVE")
                .newValue("SUSPENDED")
                .changedBy(owner)
                .build();
        history.setId(UUID.randomUUID());
        history.setCreatedAt(LocalDateTime.now());

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                ownerId, spaceId, MembershipStatus.ACTIVE)).thenReturn(true);
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId))
                .thenReturn(Optional.of(member));
        when(memberHistoryRepository.findByMemberIdOrderByCreatedAtDesc(memberId))
                .thenReturn(List.of(history));

        List<MemberHistoryResponse> responses =
                memberMasterService.getMemberHistory(spaceId, memberId, ownerId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getAction()).isEqualTo(MemberHistoryAction.STATUS_CHANGED);
        assertThat(responses.get(0).getOldValue()).isEqualTo("ACTIVE");
    }

    @Test
    void searchImportCandidates_returnsEligibleTenants() {
        UUID sourceSpaceId = UUID.randomUUID();
        SpaceEntity sourceSpace = SpaceEntity.builder()
                .owner(owner)
                .name("PG B")
                .type(SpaceType.PG)
                .isActive(true)
                .build();
        sourceSpace.setId(sourceSpaceId);

        MemberEntity sourceMember = MemberEntity.builder()
                .space(sourceSpace)
                .fullName("Rahul Patil")
                .mobileNumber("9123456789")
                .role(MembershipRole.TENANT)
                .isActive(true)
                .status(MemberStatus.ACTIVE)
                .occupancyStatus(MemberOccupancyStatus.VACATED)
                .depositAmount(BigDecimal.ZERO)
                .depositPaid(BigDecimal.ZERO)
                .depositRefunded(BigDecimal.ZERO)
                .build();
        sourceMember.setId(UUID.randomUUID());
        sourceMember.setCreatedAt(LocalDateTime.now());

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(spaceMembershipRepository.findManagedSpaceIdsByTypesAndRoles(
                eq(ownerId),
                eq(List.of(MembershipRole.OWNER, MembershipRole.MANAGER)),
                any()))
                .thenReturn(List.of(spaceId, sourceSpaceId));
        when(memberRepository.searchImportCandidates(
                eq(spaceId), eq(List.of(spaceId, sourceSpaceId)), eq(List.of(spaceId, sourceSpaceId)), eq(null)))
                .thenReturn(List.of(sourceMember));

        List<MemberImportCandidateResponse> result =
                memberMasterService.searchImportCandidates(spaceId, ownerId, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFullName()).isEqualTo("Rahul Patil");
        assertThat(result.get(0).getSourceSpaceName()).isEqualTo("PG B");
        assertThat(result.get(0).isAvailableForMoveIn()).isTrue();
        assertThat(result.get(0).isAlreadyInTargetSpace()).isFalse();
    }

    @Test
    void importMember_copiesTenantIntoTargetSpace() {
        UUID sourceSpaceId = UUID.randomUUID();
        UUID sourceMemberId = UUID.randomUUID();
        SpaceEntity sourceSpace = SpaceEntity.builder()
                .owner(owner)
                .name("PG B")
                .type(SpaceType.HOSTEL)
                .isActive(true)
                .build();
        sourceSpace.setId(sourceSpaceId);

        MemberEntity sourceMember = MemberEntity.builder()
                .space(sourceSpace)
                .fullName("Rahul Patil")
                .mobileNumber("9123456789")
                .role(MembershipRole.TENANT)
                .gender(MemberGender.MALE)
                .isActive(true)
                .status(MemberStatus.ACTIVE)
                .occupancyStatus(MemberOccupancyStatus.VACATED)
                .emergencyContactName("Priya")
                .emergencyContactRelation("Mother")
                .emergencyContactMobile("9000000000")
                .depositAmount(BigDecimal.ZERO)
                .depositPaid(BigDecimal.ZERO)
                .depositRefunded(BigDecimal.ZERO)
                .build();
        sourceMember.setId(sourceMemberId);
        sourceMember.setCreatedAt(LocalDateTime.now());

        ImportMemberRequest request = new ImportMemberRequest();
        request.setSourceMemberId(sourceMemberId);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, sourceSpaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findByIdAndIsActiveTrue(sourceMemberId)).thenReturn(Optional.of(sourceMember));
        when(spaceMembershipRepository.findManagedSpaceIdsByTypesAndRoles(
                eq(ownerId),
                eq(List.of(MembershipRole.OWNER, MembershipRole.MANAGER)),
                any()))
                .thenReturn(List.of(spaceId, sourceSpaceId));
        when(memberRepository.existsBusyOccupancyForMobileAcrossSpaces(
                eq("9123456789"), eq(List.of(spaceId, sourceSpaceId))))
                .thenReturn(false);
        when(memberRepository.findActiveBySpaceIdAndMobileNumber(spaceId, "9123456789"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(userRepository.findByMobileNumber("9123456789")).thenReturn(Optional.empty());
        when(userRepository.findByIdAndIsActiveTrue(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.save(any(MemberEntity.class))).thenAnswer(invocation -> {
            MemberEntity member = invocation.getArgument(0);
            if (member.getId() == null) {
                member.setId(memberId);
                member.setCreatedAt(LocalDateTime.now());
            }
            return member;
        });
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)).thenAnswer(invocation -> {
            MemberEntity created = activeMember("Rahul Patil", "9123456789", MembershipRole.TENANT);
            created.setGender(MemberGender.MALE);
            return Optional.of(created);
        });
        when(memberDocumentRepository.findByMemberIdOrderByUploadedAtDesc(sourceMemberId))
                .thenReturn(List.of());

        MemberResponse response = memberMasterService.importMember(spaceId, ownerId, request);

        assertThat(response.getMemberId()).isEqualTo(memberId);
        assertThat(response.getFullName()).isEqualTo("Rahul Patil");
        assertThat(response.getRole()).isEqualTo(MembershipRole.TENANT);
        verify(invitationProvisioner)
                .ensurePendingInvitation(eq(space), eq(owner), eq("9123456789"), eq(MembershipRole.TENANT));
    }

    @Test
    void importMember_rejectsBusyResident() {
        UUID sourceSpaceId = UUID.randomUUID();
        UUID sourceMemberId = UUID.randomUUID();
        SpaceEntity sourceSpace = SpaceEntity.builder()
                .owner(owner)
                .name("PG B")
                .type(SpaceType.PG)
                .isActive(true)
                .build();
        sourceSpace.setId(sourceSpaceId);

        MemberEntity sourceMember = MemberEntity.builder()
                .space(sourceSpace)
                .fullName("Rahul Patil")
                .mobileNumber("9123456789")
                .role(MembershipRole.TENANT)
                .isActive(true)
                .status(MemberStatus.ACTIVE)
                .occupancyStatus(MemberOccupancyStatus.VACATED)
                .depositAmount(BigDecimal.ZERO)
                .depositPaid(BigDecimal.ZERO)
                .depositRefunded(BigDecimal.ZERO)
                .build();
        sourceMember.setId(sourceMemberId);

        ImportMemberRequest request = new ImportMemberRequest();
        request.setSourceMemberId(sourceMemberId);

        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, sourceSpaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findByIdAndIsActiveTrue(sourceMemberId)).thenReturn(Optional.of(sourceMember));
        when(spaceMembershipRepository.findManagedSpaceIdsByTypesAndRoles(
                eq(ownerId),
                eq(List.of(MembershipRole.OWNER, MembershipRole.MANAGER)),
                any()))
                .thenReturn(List.of(spaceId, sourceSpaceId));
        when(memberRepository.existsBusyOccupancyForMobileAcrossSpaces(
                eq("9123456789"), eq(List.of(spaceId, sourceSpaceId))))
                .thenReturn(true);

        assertThatThrownBy(() -> memberMasterService.importMember(spaceId, ownerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("active or reserved occupancy");
        verify(invitationProvisioner, never()).ensurePendingInvitation(any(), any(), any(), any());
    }

    @Test
    void searchImportCandidates_messTarget_returnsCustomersAndLodgingTenants() {
        UUID messSpaceId = UUID.randomUUID();
        SpaceEntity messSpace = SpaceEntity.builder()
                .owner(owner)
                .name("Mess B")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        messSpace.setId(messSpaceId);

        UUID sourceMessId = UUID.randomUUID();
        SpaceEntity sourceMess = SpaceEntity.builder()
                .owner(owner)
                .name("Mess A")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        sourceMess.setId(sourceMessId);

        MemberEntity customer = MemberEntity.builder()
                .space(sourceMess)
                .fullName("Rahul Patil")
                .mobileNumber("9123456789")
                .role(MembershipRole.CUSTOMER)
                .isActive(true)
                .status(MemberStatus.ACTIVE)
                .occupancyStatus(MemberOccupancyStatus.VACATED)
                .depositAmount(BigDecimal.ZERO)
                .depositPaid(BigDecimal.ZERO)
                .depositRefunded(BigDecimal.ZERO)
                .build();
        customer.setId(UUID.randomUUID());
        customer.setCreatedAt(LocalDateTime.now());

        when(spaceRepository.findByIdAndIsActiveTrue(messSpaceId)).thenReturn(Optional.of(messSpace));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, messSpaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(spaceMembershipRepository.findManagedSpaceIdsByTypesAndRoles(
                eq(ownerId),
                eq(List.of(MembershipRole.OWNER, MembershipRole.MANAGER)),
                any()))
                .thenReturn(List.of(messSpaceId, sourceMessId, spaceId));
        when(memberRepository.searchMessImportCandidates(
                eq(messSpaceId), eq(List.of(messSpaceId, sourceMessId, spaceId)), eq(null)))
                .thenReturn(List.of(customer));

        List<MemberImportCandidateResponse> result =
                memberMasterService.searchImportCandidates(messSpaceId, ownerId, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFullName()).isEqualTo("Rahul Patil");
        assertThat(result.get(0).getSourceSpaceName()).isEqualTo("Mess A");
        assertThat(result.get(0).getRole()).isEqualTo(MembershipRole.CUSTOMER);
    }

    @Test
    void importMember_messTarget_createsCustomerFromMessSource() {
        UUID messSpaceId = UUID.randomUUID();
        SpaceEntity messSpace = SpaceEntity.builder()
                .owner(owner)
                .name("Mess B")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        messSpace.setId(messSpaceId);

        UUID sourceMessId = UUID.randomUUID();
        UUID sourceMemberId = UUID.randomUUID();
        SpaceEntity sourceMess = SpaceEntity.builder()
                .owner(owner)
                .name("Mess A")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        sourceMess.setId(sourceMessId);

        MemberEntity sourceMember = MemberEntity.builder()
                .space(sourceMess)
                .fullName("Rahul Patil")
                .mobileNumber("9123456789")
                .role(MembershipRole.CUSTOMER)
                .gender(MemberGender.MALE)
                .isActive(true)
                .status(MemberStatus.ACTIVE)
                .occupancyStatus(MemberOccupancyStatus.VACATED)
                .depositAmount(BigDecimal.ZERO)
                .depositPaid(BigDecimal.ZERO)
                .depositRefunded(BigDecimal.ZERO)
                .build();
        sourceMember.setId(sourceMemberId);
        sourceMember.setCreatedAt(LocalDateTime.now());

        ImportMemberRequest request = new ImportMemberRequest();
        request.setSourceMemberId(sourceMemberId);

        when(spaceRepository.findByIdAndIsActiveTrue(messSpaceId)).thenReturn(Optional.of(messSpace));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, messSpaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, sourceMessId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findByIdAndIsActiveTrue(sourceMemberId)).thenReturn(Optional.of(sourceMember));
        when(spaceMembershipRepository.findManagedSpaceIdsByTypesAndRoles(
                eq(ownerId),
                eq(List.of(MembershipRole.OWNER, MembershipRole.MANAGER)),
                any()))
                .thenReturn(List.of(messSpaceId, sourceMessId));
        when(memberRepository.findActiveBySpaceIdAndMobileNumber(messSpaceId, "9123456789"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(userRepository.findByMobileNumber("9123456789")).thenReturn(Optional.empty());
        when(userRepository.findByIdAndIsActiveTrue(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.save(any(MemberEntity.class))).thenAnswer(invocation -> {
            MemberEntity member = invocation.getArgument(0);
            if (member.getId() == null) {
                member.setId(memberId);
                member.setCreatedAt(LocalDateTime.now());
            }
            return member;
        });
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, messSpaceId)).thenAnswer(invocation -> {
            MemberEntity created = MemberEntity.builder()
                    .space(messSpace)
                    .fullName("Rahul Patil")
                    .mobileNumber("9123456789")
                    .role(MembershipRole.CUSTOMER)
                    .isActive(true)
                    .status(MemberStatus.ACTIVE)
                    .depositAmount(BigDecimal.ZERO)
                    .depositPaid(BigDecimal.ZERO)
                    .depositRefunded(BigDecimal.ZERO)
                    .build();
            created.setId(memberId);
            created.setCreatedAt(LocalDateTime.now());
            return Optional.of(created);
        });
        when(memberDocumentRepository.findByMemberIdOrderByUploadedAtDesc(sourceMemberId))
                .thenReturn(List.of());

        MemberResponse response = memberMasterService.importMember(messSpaceId, ownerId, request);

        assertThat(response.getMemberId()).isEqualTo(memberId);
        assertThat(response.getRole()).isEqualTo(MembershipRole.CUSTOMER);
        verify(invitationProvisioner)
                .ensurePendingInvitation(eq(messSpace), eq(owner), eq("9123456789"), eq(MembershipRole.CUSTOMER));
    }

    @Test
    void importMember_messTarget_createsCustomerFromLodgingTenant() {
        UUID messSpaceId = UUID.randomUUID();
        SpaceEntity messSpace = SpaceEntity.builder()
                .owner(owner)
                .name("Mess B")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        messSpace.setId(messSpaceId);

        UUID sourceMemberId = UUID.randomUUID();
        MemberEntity sourceMember = MemberEntity.builder()
                .space(space)
                .fullName("Priya Sharma")
                .mobileNumber("9988776655")
                .role(MembershipRole.TENANT)
                .isActive(true)
                .status(MemberStatus.ACTIVE)
                .occupancyStatus(MemberOccupancyStatus.ALLOCATED)
                .depositAmount(BigDecimal.ZERO)
                .depositPaid(BigDecimal.ZERO)
                .depositRefunded(BigDecimal.ZERO)
                .build();
        sourceMember.setId(sourceMemberId);
        sourceMember.setCreatedAt(LocalDateTime.now());

        ImportMemberRequest request = new ImportMemberRequest();
        request.setSourceMemberId(sourceMemberId);

        when(spaceRepository.findByIdAndIsActiveTrue(messSpaceId)).thenReturn(Optional.of(messSpace));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, messSpaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(memberRepository.findByIdAndIsActiveTrue(sourceMemberId)).thenReturn(Optional.of(sourceMember));
        when(spaceMembershipRepository.findManagedSpaceIdsByTypesAndRoles(
                eq(ownerId),
                eq(List.of(MembershipRole.OWNER, MembershipRole.MANAGER)),
                any()))
                .thenReturn(List.of(messSpaceId, spaceId));
        when(memberRepository.findActiveBySpaceIdAndMobileNumber(messSpaceId, "9988776655"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(userRepository.findByMobileNumber("9988776655")).thenReturn(Optional.empty());
        when(userRepository.findByIdAndIsActiveTrue(ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.save(any(MemberEntity.class))).thenAnswer(invocation -> {
            MemberEntity member = invocation.getArgument(0);
            if (member.getId() == null) {
                member.setId(memberId);
                member.setCreatedAt(LocalDateTime.now());
            }
            return member;
        });
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, messSpaceId)).thenAnswer(invocation -> {
            MemberEntity created = MemberEntity.builder()
                    .space(messSpace)
                    .fullName("Priya Sharma")
                    .mobileNumber("9988776655")
                    .role(MembershipRole.CUSTOMER)
                    .isActive(true)
                    .status(MemberStatus.ACTIVE)
                    .depositAmount(BigDecimal.ZERO)
                    .depositPaid(BigDecimal.ZERO)
                    .depositRefunded(BigDecimal.ZERO)
                    .build();
            created.setId(memberId);
            created.setCreatedAt(LocalDateTime.now());
            return Optional.of(created);
        });
        when(memberDocumentRepository.findByMemberIdOrderByUploadedAtDesc(sourceMemberId))
                .thenReturn(List.of());

        MemberResponse response = memberMasterService.importMember(messSpaceId, ownerId, request);

        assertThat(response.getRole()).isEqualTo(MembershipRole.CUSTOMER);
        verify(invitationProvisioner)
                .ensurePendingInvitation(eq(messSpace), eq(owner), eq("9988776655"), eq(MembershipRole.CUSTOMER));
    }

    private MemberEntity activeMember(String fullName, String mobile, MembershipRole role) {
        MemberEntity member = MemberEntity.builder()
                .space(space)
                .fullName(fullName)
                .mobileNumber(mobile)
                .role(role)
                .isActive(true)
                .status(MemberStatus.ACTIVE)
                .statusUpdatedAt(LocalDateTime.now())
                .depositAmount(BigDecimal.ZERO)
                .depositPaid(BigDecimal.ZERO)
                .depositRefunded(BigDecimal.ZERO)
                .build();
        member.setId(memberId);
        member.setCreatedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());
        return member;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
