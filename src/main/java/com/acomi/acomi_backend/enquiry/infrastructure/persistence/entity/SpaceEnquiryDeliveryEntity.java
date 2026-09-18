package com.acomi.acomi_backend.enquiry.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.enquiry.domain.model.EnquiryDeliveryChannel;
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
        name = "space_enquiry_deliveries",
        indexes = {
            @Index(name = "idx_space_enquiry_deliveries_enquiry", columnList = "enquiry_id"),
            @Index(
                    name = "idx_space_enquiry_deliveries_requester_space",
                    columnList = "requester_user_id, space_id, delivery_channel")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaceEnquiryDeliveryEntity extends BaseEntity {

    @Column(name = "enquiry_id", nullable = false)
    private UUID enquiryId;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "requester_user_id", nullable = false)
    private UUID requesterUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_channel", nullable = false, length = 20)
    private EnquiryDeliveryChannel deliveryChannel;

    /** Normalized destination email for EMAIL channel; null for APP. */
    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "delivered_at", nullable = false)
    private LocalDateTime deliveredAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
