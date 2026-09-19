package com.acomi.acomi_backend.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.admin.api.dto.request.AdminCreateRegisteredUserRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminRegisteredUserResponse;
import com.acomi.acomi_backend.auth.api.dto.request.LoginRequest;
import com.acomi.acomi_backend.auth.api.dto.request.RegisterRequest;
import com.acomi.acomi_backend.auth.application.service.AccountDeletionService;
import com.acomi.acomi_backend.auth.application.service.AuthService;
import com.acomi.acomi_backend.auth.application.service.OtpService;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.config.security.JwtService;
import com.acomi.acomi_backend.config.security.OtpProperties;
import com.acomi.acomi_backend.config.security.UserPrincipal;
import com.acomi.acomi_backend.member.application.service.InvitationProvisioner;
import com.acomi.acomi_backend.member.application.service.InvitationService;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.InvitationEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberDocumentRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.space.api.dto.request.CreateSpaceRequest;
import com.acomi.acomi_backend.space.api.dto.response.SpaceResponse;
import com.acomi.acomi_backend.space.application.service.SpaceService;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.storage.application.service.StoredFileService;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminCreateTestUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private AccountDeletionService accountDeletionService;

    @Mock
    private SpaceService spaceService;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private InvitationProvisioner invitationProvisioner;

    @Mock
    private InvitationService invitationService;

    @Mock
    private OtpService otpService;

    @Mock
    private OtpProperties otpProperties;

    @Mock
    private JwtService jwtService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberDocumentRepository memberDocumentRepository;

    @Mock
    private StoredFileService storedFileService;

    private final PasswordEncoder passwordEncoder =
            PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private AdminRegisteredUsersService adminUsersService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        adminUsersService = new AdminRegisteredUsersService(
                userRepository,
                spaceMembershipRepository,
                accountDeletionService,
                passwordEncoder,
                spaceService,
                spaceRepository,
                invitationProvisioner,
                invitationService);
        authService = new AuthService(
                otpService,
                otpProperties,
                jwtService,
                userRepository,
                memberRepository,
                memberDocumentRepository,
                accountDeletionService,
                passwordEncoder,
                storedFileService);
    }

    @Test
    void createTestUser_owner_createsUserAndSpaceViaSpaceService() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        stubUserSave(userId);
        when(spaceService.createSpace(any(CreateSpaceRequest.class)))
                .thenReturn(SpaceResponse.builder().id(spaceId).name("QA - Owner QA").build());
        when(spaceMembershipRepository.findActiveByUserIdsWithSpace(List.of(userId)))
                .thenReturn(List.of(ownerMembership(userId, spaceId)));

        AdminRegisteredUserResponse response = adminUsersService.createTestUser(
                createRequest("Owner QA", "9876500010", null, "Secret12", "Secret12",
                        MembershipRole.OWNER, null, "QA Owner PG", SpaceType.PG));

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        UserEntity saved = userCaptor.getValue();
        assertThat(saved.getSystemRole()).isEqualTo(SystemRole.USER);
        assertThat(saved.isTestUser()).isTrue();
        assertThat(passwordEncoder.matches("Secret12", saved.getPasswordHash())).isTrue();

        ArgumentCaptor<CreateSpaceRequest> spaceCaptor = ArgumentCaptor.forClass(CreateSpaceRequest.class);
        verify(spaceService).createSpace(spaceCaptor.capture());
        assertThat(spaceCaptor.getValue().getOwnerId()).isEqualTo(userId);
        assertThat(spaceCaptor.getValue().getType()).isEqualTo(SpaceType.PG);
        assertThat(spaceCaptor.getValue().getName()).isEqualTo("QA Owner PG");
        assertThat(spaceCaptor.getValue().getDiscoverable()).isFalse();
        verify(invitationService, never()).acceptInvitation(any(), any());
        verify(otpService, never()).consumeRegistrationVerificationToken(any(), any());

        assertThat(response.getSystemRole()).isEqualTo("USER");
        assertThat(response.isTestUser()).isTrue();
        assertThat(response.getSelectedRole()).isEqualTo(AdminRegisteredUsersService.ROLE_OWNER);
        assertThat(response.getSpaces()).hasSize(1);
        assertThat(response.getSpaces().get(0).getMembershipRole()).isEqualTo(MembershipRole.OWNER);
    }

    @ParameterizedTest
    @EnumSource(
            value = MembershipRole.class,
            names = {"MANAGER", "TENANT", "CUSTOMER", "STAFF"})
    void createTestUser_nonOwner_usesInviteThenAccept(MembershipRole role) {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        stubUserSave(userId);
        SpaceEntity space = activeSpace(spaceId, ownerId);
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                        eq(ownerId), eq(spaceId), eq(List.of(MembershipRole.OWNER, MembershipRole.MANAGER))))
                .thenReturn(true);

        InvitationEntity invitation = InvitationEntity.builder()
                .space(space)
                .invitedBy(space.getOwner())
                .mobileNumber("9876500020")
                .role(role)
                .build();
        invitation.setId(invitationId);
        when(invitationProvisioner.ensurePendingInvitation(
                        eq(space), eq(space.getOwner()), eq("9876500020"), eq(role)))
                .thenReturn(Optional.of(invitation));
        when(spaceMembershipRepository.findActiveByUserIdsWithSpace(List.of(userId)))
                .thenReturn(List.of(membership(userId, space, role)));

        AdminRegisteredUserResponse response = adminUsersService.createTestUser(
                createRequest("Member QA", "9876500020", null, "Secret12", "Secret12",
                        role, spaceId, null, null));

        verify(spaceService, never()).createSpace(any());
        verify(invitationProvisioner)
                .ensurePendingInvitation(space, space.getOwner(), "9876500020", role);
        verify(invitationService).acceptInvitation(invitationId, userId);
        verify(otpService, never()).consumeRegistrationVerificationToken(any(), any());

        assertThat(response.isTestUser()).isTrue();
        assertThat(response.getSystemRole()).isEqualTo("USER");
        assertThat(response.getSpaces()).hasSize(1);
        assertThat(response.getSpaces().get(0).getMembershipRole()).isEqualTo(role);
        assertThat(new UserPrincipal(savedUserWithId(userId)).getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
    }

    @Test
    void createTestUser_invalidRole_rejectedByValidationSemantics() {
        assertThatThrownBy(() -> adminUsersService.createTestUser(
                        createRequest("QA", "9876500030", null, "Secret12", "Secret12",
                                null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Space role");
        verify(userRepository, never()).save(any());
    }

    @Test
    void createTestUser_nonOwnerWithoutSpace_createsUserOnly() {
        UUID userId = UUID.randomUUID();
        stubUserSave(userId);
        when(spaceMembershipRepository.findActiveByUserIdsWithSpace(List.of(userId))).thenReturn(List.of());

        AdminRegisteredUserResponse response = adminUsersService.createTestUser(
                createRequest("QA", "9876500031", null, "Secret12", "Secret12",
                        MembershipRole.TENANT, null, null, null));

        verify(spaceService, never()).createSpace(any());
        verify(invitationProvisioner, never()).ensurePendingInvitation(any(), any(), any(), any());
        verify(invitationService, never()).acceptInvitation(any(), any());
        assertThat(response.isTestUser()).isTrue();
        assertThat(response.getSpaces()).isEmpty();
    }

    @Test
    void createTestUser_ownerWithoutSpaceType_isRejected() {
        assertThatThrownBy(() -> adminUsersService.createTestUser(
                        createRequest("QA", "9876500032", null, "Secret12", "Secret12",
                                MembershipRole.OWNER, null, "My Space", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Space type is required");
        verify(userRepository, never()).save(any());
    }

    @Test
    void createTestUser_passwordMismatch_isRejected() {
        assertThatThrownBy(() -> adminUsersService.createTestUser(
                        createRequest("QA", "9876500001", null, "Secret12", "Secret99",
                                MembershipRole.OWNER, null, null, SpaceType.PG)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Passwords do not match");
        verify(userRepository, never()).save(any());
    }

    @Test
    void createTestUser_duplicateActiveMobile_isConflict() {
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876500001"))
                .thenReturn(Optional.of(UserEntity.builder()
                        .mobileNumber("9876500001")
                        .fullName("Existing")
                        .build()));

        assertThatThrownBy(() -> adminUsersService.createTestUser(
                        createRequest("QA", "9876500001", null, "Secret12", "Secret12",
                                MembershipRole.OWNER, null, null, SpaceType.PG)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createTestUser_ignoresSystemRoleInJsonAndAlwaysCreatesUser() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        AdminCreateRegisteredUserRequest request = mapper.readValue(
                """
                {
                  "fullName": "QA Tester",
                  "mobileNumber": "9876500002",
                  "password": "Secret12",
                  "confirmPassword": "Secret12",
                  "spaceRole": "OWNER",
                  "spaceType": "PG",
                  "systemRole": "ADMIN"
                }
                """,
                AdminCreateRegisteredUserRequest.class);

        UUID userId = UUID.randomUUID();
        stubUserSave(userId);
        when(spaceService.createSpace(any(CreateSpaceRequest.class)))
                .thenReturn(SpaceResponse.builder().id(UUID.randomUUID()).name("QA").build());
        when(spaceMembershipRepository.findActiveByUserIdsWithSpace(List.of(userId))).thenReturn(List.of());

        adminUsersService.createTestUser(request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getSystemRole()).isEqualTo(SystemRole.USER);
        assertThat(captor.getValue().isTestUser()).isTrue();
        assertThat(new UserPrincipal(captor.getValue()).getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
        assertThat(new UserPrincipal(captor.getValue()).isAdmin()).isFalse();
    }

    @Test
    void createdTestUser_canLoginWithPassword_andGetsNormalJwt() {
        UserEntity[] holder = new UserEntity[1];
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876500003")).thenAnswer(inv -> {
            if (holder[0] == null) {
                return Optional.empty();
            }
            return Optional.of(holder[0]);
        });
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity created = invocation.getArgument(0);
            created.setId(UUID.randomUUID());
            holder[0] = created;
            return created;
        });
        when(spaceService.createSpace(any(CreateSpaceRequest.class)))
                .thenReturn(SpaceResponse.builder().id(UUID.randomUUID()).name("QA").build());
        when(spaceMembershipRepository.findActiveByUserIdsWithSpace(any())).thenReturn(List.of());
        when(jwtService.generateToken(any(UserEntity.class))).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);

        adminUsersService.createTestUser(
                createRequest("QA Login", "9876500003", null, "Secret12", "Secret12",
                        MembershipRole.OWNER, null, null, SpaceType.PG));

        LoginRequest login = new LoginRequest();
        login.setMobileNumber("9876500003");
        login.setPassword("Secret12");
        var token = authService.login(login);

        assertThat(token.getAccessToken()).isEqualTo("jwt-token");
        assertThat(token.getUser().getMobileNumber()).isEqualTo("9876500003");
        assertThat(token.getUser().getFullName()).isEqualTo("QA Login");
        UserPrincipal principal = new UserPrincipal(holder[0]);
        assertThat(principal.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
        assertThat(principal.isAdmin()).isFalse();
        verify(jwtService).generateToken(holder[0]);
    }

    @Test
    void publicRegister_stillRequiresOtpVerificationToken() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Public User");
        request.setMobileNumber("9876500005");
        request.setPassword("Secret12");
        request.setConfirmPassword("Secret12");
        request.setVerificationToken(null);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mobile number verification is required");
        verify(otpService, never()).consumeRegistrationVerificationToken(any(), any());
    }

    @Test
    void createTestUser_hashedPasswordNeverPlaintext() {
        UUID userId = UUID.randomUUID();
        stubUserSave(userId);
        when(spaceService.createSpace(any(CreateSpaceRequest.class)))
                .thenReturn(SpaceResponse.builder().id(UUID.randomUUID()).name("QA").build());
        when(spaceMembershipRepository.findActiveByUserIdsWithSpace(List.of(userId))).thenReturn(List.of());

        adminUsersService.createTestUser(
                createRequest("QA", "9876500040", "qa@example.com", "Secret12", "Secret12",
                        MembershipRole.OWNER, null, null, SpaceType.MESS));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("Secret12");
        assertThat(captor.getValue().getPasswordHash()).doesNotContain("Secret12");
        assertThat(passwordEncoder.matches("Secret12", captor.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void createTestUser_spaceAuthorizationComesFromMembershipRole() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        stubUserSave(userId);
        SpaceEntity space = activeSpace(spaceId, ownerId);
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                        eq(ownerId), eq(spaceId), eq(List.of(MembershipRole.OWNER, MembershipRole.MANAGER))))
                .thenReturn(true);
        InvitationEntity invitation = InvitationEntity.builder()
                .space(space)
                .invitedBy(space.getOwner())
                .mobileNumber("9876500050")
                .role(MembershipRole.MANAGER)
                .build();
        invitation.setId(invitationId);
        when(invitationProvisioner.ensurePendingInvitation(any(), any(), any(), eq(MembershipRole.MANAGER)))
                .thenReturn(Optional.of(invitation));
        when(spaceMembershipRepository.findActiveByUserIdsWithSpace(List.of(userId)))
                .thenReturn(List.of(membership(userId, space, MembershipRole.MANAGER)));

        AdminRegisteredUserResponse response = adminUsersService.createTestUser(
                createRequest("Mgr", "9876500050", null, "Secret12", "Secret12",
                        MembershipRole.MANAGER, spaceId, null, null));

        assertThat(response.getSpaces().get(0).getMembershipRole()).isEqualTo(MembershipRole.MANAGER);
        verify(invitationService).acceptInvitation(invitationId, userId);
        // Platform authority remains USER — space powers come from membership model, not systemRole.
        assertThat(response.getSystemRole()).isEqualTo("USER");
    }

    private void stubUserSave(UUID userId) {
        when(userRepository.findByMobileNumberAndIsActiveTrue(any())).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity created = invocation.getArgument(0);
            created.setId(userId);
            return created;
        });
    }

    private static UserEntity savedUserWithId(UUID userId) {
        UserEntity user = UserEntity.builder()
                .mobileNumber("9876500020")
                .fullName("Member QA")
                .systemRole(SystemRole.USER)
                .isActive(true)
                .testUser(true)
                .build();
        user.setId(userId);
        return user;
    }

    private static SpaceEntity activeSpace(UUID spaceId, UUID ownerId) {
        UserEntity owner = UserEntity.builder()
                .mobileNumber("9000000001")
                .fullName("Space Owner")
                .systemRole(SystemRole.USER)
                .isActive(true)
                .build();
        owner.setId(ownerId);
        SpaceEntity space = SpaceEntity.builder()
                .owner(owner)
                .name("Existing Space")
                .type(SpaceType.PG)
                .isActive(true)
                .build();
        space.setId(spaceId);
        return space;
    }

    private static SpaceMembershipEntity ownerMembership(UUID userId, UUID spaceId) {
        UserEntity user = UserEntity.builder()
                .mobileNumber("9876500010")
                .fullName("Owner QA")
                .systemRole(SystemRole.USER)
                .isActive(true)
                .testUser(true)
                .build();
        user.setId(userId);
        SpaceEntity space = SpaceEntity.builder()
                .owner(user)
                .name("QA Owner PG")
                .type(SpaceType.PG)
                .isActive(true)
                .build();
        space.setId(spaceId);
        return SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();
    }

    private static SpaceMembershipEntity membership(UUID userId, SpaceEntity space, MembershipRole role) {
        UserEntity user = UserEntity.builder()
                .mobileNumber("9876500020")
                .fullName("Member QA")
                .systemRole(SystemRole.USER)
                .isActive(true)
                .testUser(true)
                .build();
        user.setId(userId);
        return SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(role)
                .status(MembershipStatus.ACTIVE)
                .build();
    }

    private static AdminCreateRegisteredUserRequest createRequest(
            String fullName,
            String mobile,
            String email,
            String password,
            String confirm,
            MembershipRole spaceRole,
            UUID spaceId,
            String spaceName,
            SpaceType spaceType) {
        AdminCreateRegisteredUserRequest request = new AdminCreateRegisteredUserRequest();
        request.setFullName(fullName);
        request.setMobileNumber(mobile);
        request.setEmail(email);
        request.setPassword(password);
        request.setConfirmPassword(confirm);
        request.setSpaceRole(spaceRole);
        request.setSpaceId(spaceId);
        request.setSpaceName(spaceName);
        request.setSpaceType(spaceType);
        return request;
    }
}
