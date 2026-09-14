package com.acomi.acomi_backend.storage.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.complaint.application.service.SpaceComplaintAccessService;
import com.acomi.acomi_backend.complaint.infrastructure.persistence.entity.SpaceComplaintAttachmentEntity;
import com.acomi.acomi_backend.complaint.infrastructure.persistence.entity.SpaceComplaintEntity;
import com.acomi.acomi_backend.complaint.infrastructure.persistence.repository.SpaceComplaintAttachmentRepository;
import com.acomi.acomi_backend.complaint.infrastructure.persistence.repository.SpaceComplaintRepository;
import com.acomi.acomi_backend.meal.application.service.MealAccessService;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPollDayPaymentEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SubscriptionActivationRequestEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealPollDayPaymentRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.SubscriptionActivationRequestRepository;
import com.acomi.acomi_backend.member.application.service.SpaceMembershipResolver;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberDocumentEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberDocumentRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.payment.application.service.SpacePaymentAccessService;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.storage.api.dto.request.CreateUploadSessionRequest;
import com.acomi.acomi_backend.storage.domain.model.FileErrorCodes;
import com.acomi.acomi_backend.storage.domain.model.FilePurpose;
import com.acomi.acomi_backend.storage.domain.model.FileStatus;
import com.acomi.acomi_backend.storage.infrastructure.persistence.entity.StoredFileEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileAuthorizationService {

    private static final List<MembershipRole> OWNER_OR_MANAGER =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER);

    private final SpaceMembershipResolver membershipResolver;
    private final SpaceMembershipRepository spaceMembershipRepository;
    private final MemberRepository memberRepository;
    private final MemberDocumentRepository memberDocumentRepository;
    private final UserRepository userRepository;
    private final SpacePaymentRepository spacePaymentRepository;
    private final SpacePaymentAccessService spacePaymentAccessService;
    private final MealAccessService mealAccessService;
    private final MealPollDayPaymentRepository mealPollDayPaymentRepository;
    private final SubscriptionActivationRequestRepository subscriptionActivationRequestRepository;
    private final SpaceComplaintAccessService spaceComplaintAccessService;
    private final SpaceComplaintRepository spaceComplaintRepository;
    private final SpaceComplaintAttachmentRepository spaceComplaintAttachmentRepository;

    public void authorizeCreateSession(UUID callerId, CreateUploadSessionRequest request) {
        FilePurpose purpose = request.getPurpose();
        if (purpose.requiresSpace() && request.getSpaceId() == null
                && purpose != FilePurpose.IDENTITY_DOCUMENT
                && purpose != FilePurpose.ADDRESS_PROOF
                && purpose != FilePurpose.MEMBER_DOCUMENT) {
            throw new BusinessException(
                    FileErrorCodes.FILE_ACCESS_DENIED, "spaceId is required for this file type", HttpStatus.BAD_REQUEST);
        }
        switch (purpose) {
            case PROFILE_PHOTO -> {
                // Any authenticated user may upload their own profile photo.
            }
            case IDENTITY_DOCUMENT, ADDRESS_PROOF, MEMBER_DOCUMENT -> {
                if (request.getSpaceId() == null && request.getMemberId() == null) {
                    return;
                }
                authorizeManageMemberDocuments(request.getSpaceId(), request.getMemberId(), callerId);
            }
            case PAYMENT_PROOF -> authorizeSubmitPaymentProof(request.getSpaceId(), request.getPaymentId(), callerId);
            case MEAL_PAYMENT_PROOF -> authorizeSubmitMealProof(request.getSpaceId(), callerId);
            case SUBSCRIPTION_PAYMENT_PROOF ->
                    authorizeSubmitSubscriptionProof(request.getSpaceId(), request.getMemberId(), callerId);
            case COMPLAINT_ATTACHMENT ->
                    authorizeComplaintUpload(request.getSpaceId(), request.getComplaintId(), callerId);
        }
    }

    public void authorizeRead(UUID callerId, StoredFileEntity file) {
        denyIfDeleted(file);
        if (file.getStatus() == FileStatus.PENDING) {
            requireUploader(file, callerId);
            return;
        }
        if (file.getStatus() != FileStatus.ACTIVE) {
            throw accessDenied();
        }
        switch (file.getPurpose()) {
            case PROFILE_PHOTO -> authorizeProfilePhotoRead(callerId, file);
            case IDENTITY_DOCUMENT, ADDRESS_PROOF, MEMBER_DOCUMENT -> authorizeMemberDocumentRead(callerId, file);
            case PAYMENT_PROOF -> authorizePaymentProofRead(callerId, file);
            case MEAL_PAYMENT_PROOF -> authorizeMealProofRead(callerId, file);
            case SUBSCRIPTION_PAYMENT_PROOF -> authorizeSubscriptionProofRead(callerId, file);
            case COMPLAINT_ATTACHMENT -> authorizeComplaintRead(callerId, file);
        }
    }

    public void authorizeComplete(UUID callerId, StoredFileEntity file) {
        denyIfDeleted(file);
        requireUploader(file, callerId);
    }

    public void authorizeDelete(UUID callerId, StoredFileEntity file) {
        denyIfDeleted(file);
        switch (file.getPurpose()) {
            case PROFILE_PHOTO -> requireUploader(file, callerId);
            case IDENTITY_DOCUMENT, ADDRESS_PROOF, MEMBER_DOCUMENT -> {
                List<MemberDocumentEntity> documents = memberDocumentRepository.findByFileId(file.getId());
                if (documents.isEmpty()) {
                    requireUploader(file, callerId);
                    return;
                }
                for (MemberDocumentEntity document : documents) {
                    MemberEntity member = document.getMember();
                    if (canManageMemberDocuments(member.getSpace().getId(), member, callerId)) {
                        return;
                    }
                }
                throw accessDenied();
            }
            case COMPLAINT_ATTACHMENT -> {
                List<SpaceComplaintAttachmentEntity> attachments =
                        spaceComplaintAttachmentRepository.findByFileId(file.getId());
                if (attachments.isEmpty()) {
                    requireUploader(file, callerId);
                    return;
                }
                for (SpaceComplaintAttachmentEntity attachment : attachments) {
                    SpaceComplaintEntity complaint = attachment.getComplaint();
                    SpaceMembershipEntity membership =
                            spaceComplaintAccessService.requireActiveMembership(
                                    complaint.getSpace().getId(), callerId);
                    if (spaceComplaintAccessService.canManageComplaints(membership)
                            || callerId.equals(complaint.getCreatedByUserId())) {
                        return;
                    }
                }
                throw accessDenied();
            }
            case PAYMENT_PROOF, MEAL_PAYMENT_PROOF, SUBSCRIPTION_PAYMENT_PROOF ->
                    throw new BusinessException(
                            FileErrorCodes.FILE_ACCESS_DENIED,
                            "Payment proofs cannot be deleted",
                            HttpStatus.FORBIDDEN);
        }
    }

    public void authorizeAssociate(UUID callerId, StoredFileEntity file, FilePurpose expectedPurpose, UUID spaceId) {
        denyIfDeleted(file);
        if (file.getStatus() != FileStatus.ACTIVE) {
            throw new BusinessException(
                    FileErrorCodes.UPLOAD_NOT_COMPLETED, "File upload is not complete", HttpStatus.BAD_REQUEST);
        }
        if (file.getPurpose() != expectedPurpose) {
            throw new BusinessException(
                    FileErrorCodes.FILE_TYPE_NOT_ALLOWED,
                    "File is not valid for this purpose",
                    HttpStatus.BAD_REQUEST);
        }
        requireUploader(file, callerId);
        if (expectedPurpose.requiresSpace()
                && spaceId != null
                && file.getSpaceId() != null
                && !spaceId.equals(file.getSpaceId())) {
            throw accessDenied();
        }
    }

    private void authorizeProfilePhotoRead(UUID callerId, StoredFileEntity file) {
        if (callerId.equals(file.getUploadedByUserId())) {
            return;
        }
        UserEntity owner = userRepository.findByProfilePhotoFileId(file.getId()).orElse(null);
        if (owner != null && owner.getId().equals(callerId)) {
            return;
        }
        throw accessDenied();
    }

    private void authorizeMemberDocumentRead(UUID callerId, StoredFileEntity file) {
        List<MemberDocumentEntity> documents = memberDocumentRepository.findByFileId(file.getId());
        if (documents.isEmpty()) {
            requireUploader(file, callerId);
            return;
        }
        for (MemberDocumentEntity document : documents) {
            MemberEntity member = document.getMember();
            if (canManageMemberDocuments(member.getSpace().getId(), member, callerId)
                    || isDocumentSubject(member, callerId)) {
                return;
            }
        }
        throw accessDenied();
    }

    private void authorizePaymentProofRead(UUID callerId, StoredFileEntity file) {
        List<SpacePaymentEntity> payments = spacePaymentRepository.findByProofFileId(file.getId());
        if (payments.isEmpty()) {
            requireUploader(file, callerId);
            return;
        }
        for (SpacePaymentEntity payment : payments) {
            SpaceMembershipEntity membership =
                    spacePaymentAccessService.requireActiveMembership(payment.getSpace().getId(), callerId);
            try {
                spacePaymentAccessService.requireViewPayment(membership, payment, callerId);
                return;
            } catch (BusinessException ignored) {
                // try next association
            }
        }
        throw accessDenied();
    }

    private void authorizeMealProofRead(UUID callerId, StoredFileEntity file) {
        List<MealPollDayPaymentEntity> payments = mealPollDayPaymentRepository.findByProofFileId(file.getId());
        if (payments.isEmpty()) {
            requireUploader(file, callerId);
            return;
        }
        for (MealPollDayPaymentEntity payment : payments) {
            UUID spaceId = payment.getSpace().getId();
            mealAccessService.requireViewMeals(spaceId, callerId);
            try {
                mealAccessService.requireViewParticipation(
                        spaceId, payment.getMember().getId(), callerId, payment.getMember());
                return;
            } catch (BusinessException ignored) {
                SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
                if (mealAccessService.canManageMeals(membership)
                        || membership.getRole() == MembershipRole.STAFF) {
                    return;
                }
            }
        }
        throw accessDenied();
    }

    private void authorizeSubscriptionProofRead(UUID callerId, StoredFileEntity file) {
        List<SubscriptionActivationRequestEntity> requests =
                subscriptionActivationRequestRepository.findByPaymentProofFileId(file.getId());
        if (requests.isEmpty()) {
            requireUploader(file, callerId);
            return;
        }
        for (SubscriptionActivationRequestEntity request : requests) {
            UUID spaceId = request.getSpace().getId();
            MemberEntity member = request.getMember();
            try {
                mealAccessService.requireViewParticipation(spaceId, member.getId(), callerId, member);
                return;
            } catch (BusinessException ignored) {
                try {
                    mealAccessService.requireManageMeals(spaceId, callerId);
                    return;
                } catch (BusinessException ignoredManage) {
                    // next
                }
            }
        }
        throw accessDenied();
    }

    private void authorizeComplaintRead(UUID callerId, StoredFileEntity file) {
        List<SpaceComplaintAttachmentEntity> attachments =
                spaceComplaintAttachmentRepository.findByFileId(file.getId());
        if (attachments.isEmpty()) {
            requireUploader(file, callerId);
            return;
        }
        for (SpaceComplaintAttachmentEntity attachment : attachments) {
            SpaceComplaintEntity complaint = attachment.getComplaint();
            SpaceMembershipEntity membership =
                    spaceComplaintAccessService.requireActiveMembership(complaint.getSpace().getId(), callerId);
            try {
                spaceComplaintAccessService.requireViewComplaint(membership, complaint, callerId);
                return;
            } catch (BusinessException ignored) {
                // next
            }
        }
        throw accessDenied();
    }

    private void authorizeManageMemberDocuments(UUID spaceId, UUID memberId, UUID callerId) {
        if (spaceId == null || memberId == null) {
            throw new BusinessException(
                    FileErrorCodes.FILE_ACCESS_DENIED,
                    "spaceId and memberId are required for document uploads",
                    HttpStatus.BAD_REQUEST);
        }
        MemberEntity member = memberRepository
                .findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)
                .orElseThrow(() -> new BusinessException("Member not found", HttpStatus.NOT_FOUND));
        if (!canManageMemberDocuments(spaceId, member, callerId)) {
            throw accessDenied();
        }
    }

    public boolean canManageMemberDocuments(UUID spaceId, MemberEntity member, UUID callerId) {
        return isDocumentSubject(member, callerId) || isOwnerOrManager(spaceId, callerId);
    }

    public boolean canViewMemberDocuments(UUID spaceId, MemberEntity member, UUID callerId) {
        return canManageMemberDocuments(spaceId, member, callerId);
    }

    private void authorizeSubmitPaymentProof(UUID spaceId, UUID paymentId, UUID callerId) {
        if (spaceId == null || paymentId == null) {
            throw new BusinessException(
                    FileErrorCodes.FILE_ACCESS_DENIED,
                    "spaceId and paymentId are required for payment proof uploads",
                    HttpStatus.BAD_REQUEST);
        }
        SpaceMembershipEntity membership = spacePaymentAccessService.requireActiveMembership(spaceId, callerId);
        SpacePaymentEntity payment = spacePaymentRepository
                .findByIdAndSpaceId(paymentId, spaceId)
                .orElseThrow(() -> new BusinessException("Payment not found", HttpStatus.NOT_FOUND));
        spacePaymentAccessService.requireSubmitProof(membership, payment, callerId);
    }

    private void authorizeSubmitMealProof(UUID spaceId, UUID callerId) {
        if (spaceId == null) {
            throw new BusinessException(
                    FileErrorCodes.FILE_ACCESS_DENIED, "spaceId is required", HttpStatus.BAD_REQUEST);
        }
        mealAccessService.requireViewMeals(spaceId, callerId);
        mealAccessService.resolveOwnMemberId(spaceId, callerId);
    }

    private void authorizeSubmitSubscriptionProof(UUID spaceId, UUID memberId, UUID callerId) {
        if (spaceId == null || memberId == null) {
            throw new BusinessException(
                    FileErrorCodes.FILE_ACCESS_DENIED,
                    "spaceId and memberId are required for subscription proof uploads",
                    HttpStatus.BAD_REQUEST);
        }
        mealAccessService.requireViewMeals(spaceId, callerId);
        UUID ownMemberId = mealAccessService.resolveOwnMemberId(spaceId, callerId);
        if (!ownMemberId.equals(memberId)) {
            throw accessDenied();
        }
    }

    private void authorizeComplaintUpload(UUID spaceId, UUID complaintId, UUID callerId) {
        if (spaceId == null) {
            throw new BusinessException(
                    FileErrorCodes.FILE_ACCESS_DENIED, "spaceId is required", HttpStatus.BAD_REQUEST);
        }
        if (complaintId == null) {
            spaceComplaintAccessService.requireRaiseComplaint(spaceId, callerId);
            return;
        }
        SpaceComplaintEntity complaint = spaceComplaintRepository
                .findByIdAndSpace_Id(complaintId, spaceId)
                .orElseThrow(() -> new BusinessException("Complaint not found", HttpStatus.NOT_FOUND));
        SpaceMembershipEntity membership = spaceComplaintAccessService.requireActiveMembership(spaceId, callerId);
        spaceComplaintAccessService.requireViewComplaint(membership, complaint, callerId);
        if (!(spaceComplaintAccessService.canManageComplaints(membership)
                || callerId.equals(complaint.getCreatedByUserId()))) {
            throw accessDenied();
        }
    }

    private boolean isOwnerOrManager(UUID spaceId, UUID callerId) {
        return spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(callerId, spaceId, OWNER_OR_MANAGER);
    }

    private static boolean isDocumentSubject(MemberEntity member, UUID callerId) {
        return member.getUser() != null && callerId.equals(member.getUser().getId());
    }

    private static void requireUploader(StoredFileEntity file, UUID callerId) {
        if (!callerId.equals(file.getUploadedByUserId())) {
            throw accessDenied();
        }
    }

    private static void denyIfDeleted(StoredFileEntity file) {
        if (file.getStatus() == FileStatus.DELETED || file.getStatus() == FileStatus.PENDING_DELETE) {
            throw new BusinessException(
                    FileErrorCodes.FILE_ALREADY_DELETED, "File is not available", HttpStatus.NOT_FOUND);
        }
    }

    private static BusinessException accessDenied() {
        return new BusinessException(
                FileErrorCodes.FILE_ACCESS_DENIED, "You do not have access to this file", HttpStatus.FORBIDDEN);
    }
}
