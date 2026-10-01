package com.acomi.acomi_backend.enquiry.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.enquiry.domain.model.EnquiryRequesterType;
import com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryClientChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "space_enquiries",
        indexes = {
            @Index(name = "idx_space_enquiries_requester_requested", columnList = "requester_user_id, requested_at"),
            @Index(name = "idx_space_enquiries_status_requested", columnList = "status, requested_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaceEnquiryEntity extends BaseEntity {

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "space_name_snapshot", nullable = false, length = 150)
    private String spaceNameSnapshot;

    @Column(name = "requester_user_id", nullable = false)
    private UUID requesterUserId;

    @Column(name = "requester_name_snapshot", nullable = false, length = 120)
    private String requesterNameSnapshot;

    /** Optional — required only when delivering owner contact by email. */
    @Column(name = "requester_email", length = 255)
    private String requesterEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "requester_type", nullable = false, length = 20)
    private EnquiryRequesterType requesterType;

    /**
     * Client channel at create time. Immutable for delivery routing.
     * WEB → owner contact emailed; ANDROID → in-app My Enquiries.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "client_channel", nullable = false, length = 20)
    @Builder.Default
    private InquiryClientChannel clientChannel = InquiryClientChannel.WEB;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SpaceEnquiryStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "shared_at")
    private LocalDateTime sharedAt;

    /** When ENQUIRY_SHARED owner-contact email was enqueued; null for ANDROID in-app delivery. */
    @Column(name = "contact_email_sent_at")
    private LocalDateTime contactEmailSentAt;

    @Column(name = "shared_by_admin_id")
    private UUID sharedByAdminId;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
}
