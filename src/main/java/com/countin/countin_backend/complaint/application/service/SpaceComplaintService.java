package com.countin.countin_backend.complaint.application.service;

import com.countin.countin_backend.common.exception.BusinessException;
import com.countin.countin_backend.complaint.api.dto.request.AddComplaintAttachmentRequest;
import com.countin.countin_backend.complaint.api.dto.request.AddComplaintCommentRequest;
import com.countin.countin_backend.complaint.api.dto.request.AssignComplaintRequest;
import com.countin.countin_backend.complaint.api.dto.request.CreateComplaintRequest;
import com.countin.countin_backend.complaint.api.dto.request.ReopenComplaintRequest;
import com.countin.countin_backend.complaint.api.dto.request.UpdateComplaintResolutionRequest;
import com.countin.countin_backend.complaint.api.dto.request.UpdateComplaintStatusRequest;
import com.countin.countin_backend.complaint.api.dto.response.ComplaintAttachmentResponse;
import com.countin.countin_backend.complaint.api.dto.response.ComplaintCommentResponse;
import com.countin.countin_backend.complaint.api.dto.response.ComplaintListResponse;
import com.countin.countin_backend.complaint.api.dto.response.ComplaintResponse;
import com.countin.countin_backend.complaint.api.dto.response.ComplaintTimelineEventResponse;
import com.countin.countin_backend.complaint.domain.model.ComplaintCategory;
import com.countin.countin_backend.complaint.domain.model.ComplaintPriority;
import com.countin.countin_backend.complaint.domain.model.ComplaintReopenPolicy;
import com.countin.countin_backend.complaint.domain.model.ComplaintStatus;
import com.countin.countin_backend.complaint.domain.model.ComplaintStatusTransition;
import com.countin.countin_backend.complaint.domain.model.ComplaintTimelineEventType;
import com.countin.countin_backend.complaint.infrastructure.persistence.entity.SpaceComplaintAttachmentEntity;
import com.countin.countin_backend.complaint.infrastructure.persistence.entity.SpaceComplaintCommentEntity;
import com.countin.countin_backend.complaint.infrastructure.persistence.entity.SpaceComplaintEntity;
import com.countin.countin_backend.complaint.infrastructure.persistence.entity.SpaceComplaintTimelineEventEntity;
import com.countin.countin_backend.complaint.infrastructure.persistence.repository.SpaceComplaintAttachmentRepository;
import com.countin.countin_backend.complaint.infrastructure.persistence.repository.SpaceComplaintCommentRepository;
import com.countin.countin_backend.complaint.infrastructure.persistence.repository.SpaceComplaintRepository;
import com.countin.countin_backend.complaint.infrastructure.persistence.repository.SpaceComplaintTimelineEventRepository;
import com.countin.countin_backend.member.domain.model.MembershipStatus;
import com.countin.countin_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.countin.countin_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.countin.countin_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.countin.countin_backend.space.domain.model.SpaceType;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.countin.countin_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SpaceComplaintService {

    private static final int MAX_ATTACHMENTS_PER_COMPLAINT = 5;

    private final SpaceComplaintRepository complaintRepository;
    private final SpaceComplaintCommentRepository commentRepository;
    private final SpaceComplaintAttachmentRepository attachmentRepository;
    private final SpaceComplaintTimelineEventRepository timelineRepository;
    private final SpaceComplaintAccessService accessService;
    private final SpaceRepository spaceRepository;
    private final SpaceMembershipRepository membershipRepository;
    private final ComplaintNotificationSyncService notificationSyncService;

    @Transactional
    public ComplaintResponse create(UUID spaceId, UUID callerId, CreateComplaintRequest request) {
        SpaceMembershipEntity membership = accessService.requireRaiseComplaint(spaceId, callerId);
        SpaceEntity space = spaceRepository
                .findByIdAndIsActiveTrue(spaceId)
                .orElseThrow(() -> new BusinessException("Space not found", HttpStatus.NOT_FOUND));
        MemberEntity member = accessService.requireOwnMember(spaceId, callerId);

        validateCategory(space.getType(), request.getCategory());
        validateMealFields(request);

        LocalDateTime now = LocalDateTime.now();
        SpaceComplaintEntity complaint = SpaceComplaintEntity.builder()
                .space(space)
                .createdByMember(member)
                .createdByUserId(callerId)
                .category(request.getCategory())
                .priority(request.getPriority())
                .status(ComplaintStatus.OPEN)
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .mealDate(request.getMealDate())
                .mealType(request.getMealType())
                .build();
        complaint = complaintRepository.save(complaint);

        appendTimeline(
                complaint,
                ComplaintTimelineEventType.CREATED,
                now,
                callerId,
                "Complaint created (" + request.getCategory() + ", " + request.getPriority() + ")");

        if (request.getAttachmentImagesBase64() != null) {
            for (String image : request.getAttachmentImagesBase64()) {
                if (!StringUtils.hasText(image)) {
                    continue;
                }
                addAttachmentInternal(complaint, callerId, image.trim(), null, null, now);
            }
        }

        notificationSyncService.onComplaintCreated(complaint);
        return toDetail(complaint, membership, callerId);
    }

    @Transactional(readOnly = true)
    public ComplaintListResponse list(
            UUID spaceId,
            UUID callerId,
            ComplaintStatus status,
            ComplaintPriority priority,
            ComplaintCategory category,
            UUID assigneeMembershipId,
            Boolean mine) {
        SpaceMembershipEntity membership = accessService.requireActiveMembership(spaceId, callerId);

        UUID createdByMemberId = null;
        UUID effectiveAssignee = assigneeMembershipId;

        if (accessService.isOwnScopeOnly(membership) || Boolean.TRUE.equals(mine)) {
            createdByMemberId = accessService.resolveOwnMemberId(spaceId, callerId);
            effectiveAssignee = null;
        } else if (accessService.isStaff(membership) && !accessService.canViewAllComplaints(membership)) {
            effectiveAssignee = membership.getId();
        }

        List<SpaceComplaintEntity> entities = complaintRepository.findFiltered(
                spaceId, status, priority, category, effectiveAssignee, createdByMemberId);

        LocalDateTime now = LocalDateTime.now();
        List<ComplaintResponse> items = entities.stream()
                .map(c -> ComplaintResponse.summary(c, canReopenFor(c, membership, callerId, now)))
                .toList();

        long openCount = entities.stream().filter(c -> c.getStatus() == ComplaintStatus.OPEN).count();
        long inProgressCount =
                entities.stream().filter(c -> c.getStatus() == ComplaintStatus.IN_PROGRESS).count();
        long resolvedCount =
                entities.stream().filter(c -> c.getStatus() == ComplaintStatus.RESOLVED).count();

        return ComplaintListResponse.builder()
                .totalCount(items.size())
                .openCount(openCount)
                .inProgressCount(inProgressCount)
                .resolvedCount(resolvedCount)
                .complaints(items)
                .build();
    }

    @Transactional(readOnly = true)
    public ComplaintResponse get(UUID spaceId, UUID complaintId, UUID callerId) {
        SpaceMembershipEntity membership = accessService.requireActiveMembership(spaceId, callerId);
        SpaceComplaintEntity complaint = requireComplaint(spaceId, complaintId);
        accessService.requireViewComplaint(membership, complaint, callerId);
        return toDetail(complaint, membership, callerId);
    }

    @Transactional
    public ComplaintResponse updateStatus(
            UUID spaceId, UUID complaintId, UUID callerId, UpdateComplaintStatusRequest request) {
        SpaceMembershipEntity membership = accessService.requireManageComplaints(spaceId, callerId);
        SpaceComplaintEntity complaint = requireComplaint(spaceId, complaintId);
        applyStatusChange(complaint, request.getStatus(), callerId, request.getNote(), false);
        notificationSyncService.onComplaintStatusChanged(complaint);
        return toDetail(complaint, membership, callerId);
    }

    @Transactional
    public ComplaintResponse addComment(
            UUID spaceId, UUID complaintId, UUID callerId, AddComplaintCommentRequest request) {
        SpaceMembershipEntity membership = accessService.requireActiveMembership(spaceId, callerId);
        SpaceComplaintEntity complaint = requireComplaint(spaceId, complaintId);
        accessService.requireViewComplaint(membership, complaint, callerId);

        boolean internal = request.isInternal();
        if (internal) {
            accessService.requireInternalNote(membership);
        }

        MemberEntity authorMember = null;
        try {
            authorMember = accessService.requireOwnMember(spaceId, callerId);
        } catch (BusinessException ignored) {
            // Operators may comment without a member profile.
        }

        LocalDateTime now = LocalDateTime.now();
        SpaceComplaintCommentEntity comment = SpaceComplaintCommentEntity.builder()
                .complaint(complaint)
                .authorMember(authorMember)
                .authorUserId(callerId)
                .body(request.getBody().trim())
                .internal(internal)
                .build();
        commentRepository.save(comment);

        appendTimeline(
                complaint,
                internal
                        ? ComplaintTimelineEventType.INTERNAL_NOTE
                        : ComplaintTimelineEventType.COMMENTED,
                now,
                callerId,
                truncate(request.getBody().trim(), 200));

        if (!internal) {
            notificationSyncService.onComplaintCommented(complaint, callerId);
        }

        return toDetail(complaint, membership, callerId);
    }

    @Transactional
    public ComplaintResponse addAttachment(
            UUID spaceId, UUID complaintId, UUID callerId, AddComplaintAttachmentRequest request) {
        SpaceMembershipEntity membership = accessService.requireActiveMembership(spaceId, callerId);
        SpaceComplaintEntity complaint = requireComplaint(spaceId, complaintId);
        accessService.requireViewComplaint(membership, complaint, callerId);

        if (complaint.getStatus() == ComplaintStatus.CLOSED
                || complaint.getStatus() == ComplaintStatus.CANCELLED) {
            throw new BusinessException(
                    "Cannot attach files to a closed or cancelled complaint", HttpStatus.BAD_REQUEST);
        }

        LocalDateTime now = LocalDateTime.now();
        addAttachmentInternal(
                complaint,
                callerId,
                request.getImageBase64(),
                request.getFileName(),
                request.getContentType(),
                now);
        return toDetail(complaint, membership, callerId);
    }

    @Transactional
    public ComplaintResponse reopen(
            UUID spaceId, UUID complaintId, UUID callerId, ReopenComplaintRequest request) {
        SpaceMembershipEntity membership = accessService.requireActiveMembership(spaceId, callerId);
        SpaceComplaintEntity complaint = requireComplaint(spaceId, complaintId);
        accessService.requireViewComplaint(membership, complaint, callerId);

        boolean allowedActor = accessService.canManageComplaints(membership)
                || (accessService.isOwnScopeOnly(membership)
                        && complaint.getCreatedByUserId().equals(callerId));
        if (!allowedActor) {
            throw new BusinessException("You cannot reopen this complaint", HttpStatus.FORBIDDEN);
        }

        LocalDateTime now = LocalDateTime.now();
        if (!ComplaintReopenPolicy.canReopen(complaint.getStatus(), complaint.getResolvedAt(), now)) {
            throw new BusinessException(
                    "Complaint cannot be reopened (must be RESOLVED within "
                            + ComplaintReopenPolicy.REOPEN_WINDOW_DAYS
                            + " days)",
                    HttpStatus.BAD_REQUEST);
        }

        complaint.setStatus(ComplaintStatus.OPEN);
        complaint.setReopenedAt(now);
        complaint.setClosedAt(null);
        complaintRepository.save(complaint);

        String remarks = StringUtils.hasText(request.getReason())
                ? "Reopened: " + request.getReason().trim()
                : "Complaint reopened";
        appendTimeline(complaint, ComplaintTimelineEventType.REOPENED, now, callerId, remarks);
        notificationSyncService.onComplaintReopened(complaint);
        return toDetail(complaint, membership, callerId);
    }

    @Transactional
    public ComplaintResponse assign(
            UUID spaceId, UUID complaintId, UUID callerId, AssignComplaintRequest request) {
        SpaceMembershipEntity membership = accessService.requireManageComplaints(spaceId, callerId);
        SpaceComplaintEntity complaint = requireComplaint(spaceId, complaintId);

        SpaceMembershipEntity assignee = null;
        if (request.getAssigneeMembershipId() != null) {
            assignee = membershipRepository
                    .findById(request.getAssigneeMembershipId())
                    .orElseThrow(() -> new BusinessException(
                            "Assignee membership not found", HttpStatus.NOT_FOUND));
            if (!assignee.getSpace().getId().equals(spaceId)
                    || assignee.getStatus() != MembershipStatus.ACTIVE) {
                throw new BusinessException(
                        "Assignee must be an active member of this space", HttpStatus.BAD_REQUEST);
            }
        }

        complaint.setAssignedToMembership(assignee);
        complaintRepository.save(complaint);

        LocalDateTime now = LocalDateTime.now();
        String remarks = assignee == null
                ? "Assignment cleared"
                : "Assigned to "
                        + (assignee.getUser() != null ? assignee.getUser().getFullName() : assignee.getId());
        appendTimeline(complaint, ComplaintTimelineEventType.ASSIGNED, now, callerId, remarks);

        if (complaint.getStatus() == ComplaintStatus.OPEN) {
            applyStatusChange(complaint, ComplaintStatus.IN_PROGRESS, callerId, "Auto-progress on assign", true);
        }

        notificationSyncService.onComplaintAssigned(complaint);
        return toDetail(complaint, membership, callerId);
    }

    @Transactional
    public ComplaintResponse updateResolution(
            UUID spaceId, UUID complaintId, UUID callerId, UpdateComplaintResolutionRequest request) {
        SpaceMembershipEntity membership = accessService.requireManageComplaints(spaceId, callerId);
        SpaceComplaintEntity complaint = requireComplaint(spaceId, complaintId);

        complaint.setResolutionSummary(request.getResolutionSummary().trim());
        complaintRepository.save(complaint);

        if (request.isMarkResolved()
                && (complaint.getStatus() == ComplaintStatus.OPEN
                        || complaint.getStatus() == ComplaintStatus.IN_PROGRESS)) {
            applyStatusChange(
                    complaint,
                    ComplaintStatus.RESOLVED,
                    callerId,
                    request.getResolutionSummary().trim(),
                    false);
        } else {
            appendTimeline(
                    complaint,
                    ComplaintTimelineEventType.RESOLVED,
                    LocalDateTime.now(),
                    callerId,
                    "Resolution updated");
        }

        notificationSyncService.onComplaintStatusChanged(complaint);
        return toDetail(complaint, membership, callerId);
    }

    private void applyStatusChange(
            SpaceComplaintEntity complaint,
            ComplaintStatus target,
            UUID callerId,
            String note,
            boolean skipTransitionCheck) {
        ComplaintStatus from = complaint.getStatus();
        if (from == target) {
            return;
        }
        if (!skipTransitionCheck && !ComplaintStatusTransition.canTransition(from, target)) {
            throw new BusinessException(
                    "Invalid status transition: " + from + " → " + target, HttpStatus.BAD_REQUEST);
        }

        LocalDateTime now = LocalDateTime.now();
        complaint.setStatus(target);
        if (target == ComplaintStatus.RESOLVED) {
            complaint.setResolvedAt(now);
            complaint.setResolvedByUserId(callerId);
            if (!StringUtils.hasText(complaint.getResolutionSummary()) && StringUtils.hasText(note)) {
                complaint.setResolutionSummary(note.trim());
            }
        } else if (target == ComplaintStatus.CLOSED) {
            complaint.setClosedAt(now);
        } else if (target == ComplaintStatus.CANCELLED) {
            complaint.setCancelledAt(now);
        }
        complaintRepository.save(complaint);

        ComplaintTimelineEventType eventType = switch (target) {
            case RESOLVED -> ComplaintTimelineEventType.RESOLVED;
            case CLOSED -> ComplaintTimelineEventType.CLOSED;
            case CANCELLED -> ComplaintTimelineEventType.CANCELLED;
            default -> ComplaintTimelineEventType.STATUS_CHANGED;
        };
        String remarks = from + " → " + target
                + (StringUtils.hasText(note) ? " (" + note.trim() + ")" : "");
        appendTimeline(complaint, eventType, now, callerId, remarks);
    }

    private void addAttachmentInternal(
            SpaceComplaintEntity complaint,
            UUID callerId,
            String imageBase64,
            String fileName,
            String contentType,
            LocalDateTime now) {
        long existing = attachmentRepository.findByComplaint_IdOrderByCreatedAtAsc(complaint.getId()).size();
        if (existing >= MAX_ATTACHMENTS_PER_COMPLAINT) {
            throw new BusinessException(
                    "Maximum " + MAX_ATTACHMENTS_PER_COMPLAINT + " attachments allowed",
                    HttpStatus.BAD_REQUEST);
        }
        String normalized = normalizeImage(imageBase64);
        SpaceComplaintAttachmentEntity attachment = SpaceComplaintAttachmentEntity.builder()
                .complaint(complaint)
                .storageUrl(normalized)
                .contentType(contentType)
                .fileName(fileName)
                .createdByUserId(callerId)
                .build();
        attachmentRepository.save(attachment);
        appendTimeline(
                complaint,
                ComplaintTimelineEventType.ATTACHMENT_ADDED,
                now,
                callerId,
                fileName != null ? fileName : "Photo attached");
    }

    private SpaceComplaintEntity requireComplaint(UUID spaceId, UUID complaintId) {
        return complaintRepository
                .findByIdAndSpace_Id(complaintId, spaceId)
                .orElseThrow(() -> new BusinessException("Complaint not found", HttpStatus.NOT_FOUND));
    }

    private ComplaintResponse toDetail(
            SpaceComplaintEntity complaint, SpaceMembershipEntity membership, UUID callerId) {
        boolean includeInternal = accessService.canSeeInternalNotes(membership);
        List<ComplaintCommentResponse> comments =
                (includeInternal
                                ? commentRepository.findByComplaint_IdOrderByCreatedAtAsc(complaint.getId())
                                : commentRepository.findByComplaint_IdAndInternalFalseOrderByCreatedAtAsc(
                                        complaint.getId()))
                        .stream()
                        .map(ComplaintCommentResponse::from)
                        .toList();
        List<ComplaintAttachmentResponse> attachments = attachmentRepository
                .findByComplaint_IdOrderByCreatedAtAsc(complaint.getId())
                .stream()
                .map(ComplaintAttachmentResponse::from)
                .toList();
        List<ComplaintTimelineEventResponse> timeline = timelineRepository
                .findByComplaint_IdOrderByPerformedAtAsc(complaint.getId())
                .stream()
                .filter(e -> includeInternal
                        || e.getEventType() != ComplaintTimelineEventType.INTERNAL_NOTE)
                .map(ComplaintTimelineEventResponse::from)
                .toList();

        LocalDateTime now = LocalDateTime.now();
        return ComplaintResponse.detail(
                complaint,
                canReopenFor(complaint, membership, callerId, now),
                comments,
                attachments,
                timeline);
    }

    private boolean canReopenFor(
            SpaceComplaintEntity complaint,
            SpaceMembershipEntity membership,
            UUID callerId,
            LocalDateTime now) {
        if (!ComplaintReopenPolicy.canReopen(complaint.getStatus(), complaint.getResolvedAt(), now)) {
            return false;
        }
        return accessService.canManageComplaints(membership)
                || (accessService.isOwnScopeOnly(membership)
                        && complaint.getCreatedByUserId().equals(callerId));
    }

    private void appendTimeline(
            SpaceComplaintEntity complaint,
            ComplaintTimelineEventType type,
            LocalDateTime at,
            UUID actorId,
            String remarks) {
        timelineRepository.save(SpaceComplaintTimelineEventEntity.builder()
                .complaint(complaint)
                .eventType(type)
                .performedAt(at)
                .performedBy(actorId)
                .remarks(remarks)
                .build());
    }

    private void validateCategory(SpaceType spaceType, ComplaintCategory category) {
        if (!ComplaintCategory.allowedFor(spaceType).contains(category)) {
            throw new BusinessException(
                    "Category " + category + " is not allowed for space type " + spaceType,
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void validateMealFields(CreateComplaintRequest request) {
        if (request.getMealDate() != null || request.getMealType() != null) {
            if (!request.getCategory().isFoodRelated()) {
                throw new BusinessException(
                        "mealDate/mealType only allowed for food-related categories",
                        HttpStatus.BAD_REQUEST);
            }
        }
    }

    private static String normalizeImage(String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new BusinessException("Image is required", HttpStatus.BAD_REQUEST);
        }
        String normalized = imageBase64.trim();
        if (!(normalized.startsWith("data:image/") || normalized.matches("^[A-Za-z0-9+/=\\r\\n]+$"))) {
            throw new BusinessException("Invalid image payload", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }

    /** Open actionable statuses for pending-action sync. */
    public static EnumSet<ComplaintStatus> openActionStatuses() {
        return EnumSet.of(ComplaintStatus.OPEN, ComplaintStatus.IN_PROGRESS);
    }
}
