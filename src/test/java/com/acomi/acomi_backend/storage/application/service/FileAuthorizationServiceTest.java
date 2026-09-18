package com.acomi.acomi_backend.storage.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberDocumentEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberDocumentRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.storage.api.dto.request.CreateUploadSessionRequest;
import com.acomi.acomi_backend.storage.domain.model.FileErrorCodes;
import com.acomi.acomi_backend.storage.domain.model.FilePurpose;
import com.acomi.acomi_backend.storage.domain.model.FileStatus;
import com.acomi.acomi_backend.storage.domain.model.FileVisibility;
import com.acomi.acomi_backend.storage.infrastructure.persistence.entity.StoredFileEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileAuthorizationServiceTest {

    @Mock
    private com.acomi.acomi_backend.member.application.service.SpaceMembershipResolver membershipResolver;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberDocumentRepository memberDocumentRepository;

    @Mock
    private com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository userRepository;

    @Mock
    private com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository
            spacePaymentRepository;

    @Mock
    private com.acomi.acomi_backend.payment.application.service.SpacePaymentAccessService spacePaymentAccessService;

    @Mock
    private com.acomi.acomi_backend.meal.application.service.MealAccessService mealAccessService;

    @Mock
    private com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealPollDayPaymentRepository
            mealPollDayPaymentRepository;

    @Mock
    private com.acomi.acomi_backend.meal.infrastructure.persistence.repository.SubscriptionActivationRequestRepository
            subscriptionActivationRequestRepository;

    @Mock
    private com.acomi.acomi_backend.complaint.application.service.SpaceComplaintAccessService
            spaceComplaintAccessService;

    @Mock
    private com.acomi.acomi_backend.complaint.infrastructure.persistence.repository.SpaceComplaintRepository
            spaceComplaintRepository;

    @Mock
    private com.acomi.acomi_backend.complaint.infrastructure.persistence.repository.SpaceComplaintAttachmentRepository
            spaceComplaintAttachmentRepository;

    @InjectMocks
    private FileAuthorizationService fileAuthorizationService;

    private UUID ownerId;
    private UUID tenantId;
    private UUID strangerId;
    private UUID spaceId;
    private MemberEntity member;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        strangerId = UUID.randomUUID();
        spaceId = UUID.randomUUID();
        UserEntity tenantUser = UserEntity.builder().mobileNumber("9000000000").fullName("Tenant").build();
        tenantUser.setId(tenantId);
        SpaceEntity space = SpaceEntity.builder().name("Space").build();
        space.setId(spaceId);
        member = MemberEntity.builder().fullName("Tenant").space(space).user(tenantUser).build();
        member.setId(UUID.randomUUID());
    }

    @Test
    void profilePhotoSessionDoesNotRequireSpace() {
        CreateUploadSessionRequest request = new CreateUploadSessionRequest();
        request.setPurpose(FilePurpose.PROFILE_PHOTO);
        fileAuthorizationService.authorizeCreateSession(tenantId, request);
    }

    @Test
    void memberDocumentViewIsOwnerManagerOrSubjectOnly() {
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                        ownerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(true);
        when(spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                        strangerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER)))
                .thenReturn(false);

        assertThat(fileAuthorizationService.canViewMemberDocuments(spaceId, member, tenantId)).isTrue();
        assertThat(fileAuthorizationService.canViewMemberDocuments(spaceId, member, ownerId)).isTrue();
        assertThat(fileAuthorizationService.canViewMemberDocuments(spaceId, member, strangerId)).isFalse();
    }

    @Test
    void deletedFileCannotBeRead() {
        StoredFileEntity file = StoredFileEntity.builder()
                .purpose(FilePurpose.PROFILE_PHOTO)
                .visibility(FileVisibility.PRIVATE)
                .status(FileStatus.DELETED)
                .contentType("image/jpeg")
                .storageProvider("memory")
                .bucket("b")
                .objectKey("k")
                .uploadedByUserId(tenantId)
                .build();
        file.setId(UUID.randomUUID());
        assertThatThrownBy(() -> fileAuthorizationService.authorizeRead(tenantId, file))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(FileErrorCodes.FILE_ALREADY_DELETED);
    }

    @Test
    void paymentProofsCannotBeDeleted() {
        StoredFileEntity file = StoredFileEntity.builder()
                .purpose(FilePurpose.PAYMENT_PROOF)
                .visibility(FileVisibility.PRIVATE)
                .status(FileStatus.ACTIVE)
                .contentType("image/jpeg")
                .storageProvider("memory")
                .bucket("b")
                .objectKey("k")
                .uploadedByUserId(tenantId)
                .build();
        file.setId(UUID.randomUUID());
        assertThatThrownBy(() -> fileAuthorizationService.authorizeDelete(tenantId, file))
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(FileErrorCodes.FILE_ACCESS_DENIED);
    }

    @Test
    void unassociatedDocumentIsReadableOnlyByUploader() {
        StoredFileEntity file = StoredFileEntity.builder()
                .purpose(FilePurpose.IDENTITY_DOCUMENT)
                .visibility(FileVisibility.PRIVATE)
                .status(FileStatus.ACTIVE)
                .contentType("image/jpeg")
                .storageProvider("memory")
                .bucket("b")
                .objectKey("k")
                .uploadedByUserId(tenantId)
                .build();
        file.setId(UUID.randomUUID());
        when(memberDocumentRepository.findByFileId(file.getId())).thenReturn(List.<MemberDocumentEntity>of());
        fileAuthorizationService.authorizeRead(tenantId, file);
        assertThatThrownBy(() -> fileAuthorizationService.authorizeRead(strangerId, file))
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(FileErrorCodes.FILE_ACCESS_DENIED);
    }

    @Test
    void entityPhotoSessionRequiresAccountHolder() {
        CreateUploadSessionRequest request = new CreateUploadSessionRequest();
        request.setPurpose(FilePurpose.BUILDING_PHOTO);
        request.setSpaceId(spaceId);
        fileAuthorizationService.authorizeCreateSession(ownerId, request);
        org.mockito.Mockito.verify(membershipResolver).requireAccountHolder(spaceId, ownerId);
    }

    @Test
    void managerCannotCreateEntityPhotoSession() {
        CreateUploadSessionRequest request = new CreateUploadSessionRequest();
        request.setPurpose(FilePurpose.MENU_ITEM_PHOTO);
        request.setSpaceId(spaceId);
        org.mockito.Mockito.when(membershipResolver.requireAccountHolder(spaceId, tenantId))
                .thenThrow(new BusinessException(
                        "ACCOUNT_HOLDER_REQUIRED",
                        "Only the account holder can edit this photo",
                        org.springframework.http.HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> fileAuthorizationService.authorizeCreateSession(tenantId, request))
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("ACCOUNT_HOLDER_REQUIRED");
    }

    @Test
    void spaceMemberCanReadEntityPhoto() {
        StoredFileEntity file = StoredFileEntity.builder()
                .purpose(FilePurpose.FLOOR_PHOTO)
                .visibility(FileVisibility.PRIVATE)
                .status(FileStatus.ACTIVE)
                .contentType("image/jpeg")
                .storageProvider("memory")
                .bucket("b")
                .objectKey("k")
                .uploadedByUserId(ownerId)
                .spaceId(spaceId)
                .build();
        file.setId(UUID.randomUUID());
        fileAuthorizationService.authorizeRead(tenantId, file);
        org.mockito.Mockito.verify(membershipResolver).requireActive(spaceId, tenantId);
    }

    @Test
    void strangerCannotReadEntityPhoto() {
        StoredFileEntity file = StoredFileEntity.builder()
                .purpose(FilePurpose.SPACE_PHOTO)
                .visibility(FileVisibility.PRIVATE)
                .status(FileStatus.ACTIVE)
                .contentType("image/jpeg")
                .storageProvider("memory")
                .bucket("b")
                .objectKey("k")
                .uploadedByUserId(ownerId)
                .spaceId(spaceId)
                .build();
        file.setId(UUID.randomUUID());
        org.mockito.Mockito.when(membershipResolver.requireActive(spaceId, strangerId))
                .thenThrow(new BusinessException("NOT_A_MEMBER", "You are not a member of this space",
                        org.springframework.http.HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> fileAuthorizationService.authorizeRead(strangerId, file))
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("NOT_A_MEMBER");
    }

    @Test
    void nonOwnerCannotDeleteEntityPhoto() {
        StoredFileEntity file = StoredFileEntity.builder()
                .purpose(FilePurpose.COMBO_PHOTO)
                .visibility(FileVisibility.PRIVATE)
                .status(FileStatus.ACTIVE)
                .contentType("image/jpeg")
                .storageProvider("memory")
                .bucket("b")
                .objectKey("k")
                .uploadedByUserId(ownerId)
                .spaceId(spaceId)
                .build();
        file.setId(UUID.randomUUID());
        org.mockito.Mockito.when(membershipResolver.requireAccountHolder(spaceId, tenantId))
                .thenThrow(new BusinessException(
                        "ACCOUNT_HOLDER_REQUIRED",
                        "Only the account holder can edit this photo",
                        org.springframework.http.HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> fileAuthorizationService.authorizeDelete(tenantId, file))
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("ACCOUNT_HOLDER_REQUIRED");
    }
}
