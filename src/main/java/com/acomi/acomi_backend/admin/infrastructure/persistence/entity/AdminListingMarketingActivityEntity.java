package com.acomi.acomi_backend.admin.infrastructure.persistence.entity;

import com.acomi.acomi_backend.admin.domain.model.ListingMarketingChannel;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingDeliveryMode;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingKind;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "admin_listing_marketing_activities")
public class AdminListingMarketingActivityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_kind", nullable = false, length = 20)
    private ListingMarketingKind listingKind;

    @Column(name = "property_registration_id")
    private UUID propertyRegistrationId;

    @Column(name = "mess_registration_id")
    private UUID messRegistrationId;

    @Column(name = "space_id")
    private UUID spaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private ListingMarketingChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 40)
    private ListingMarketingOutcome outcome;

    @Column(name = "template_key", length = 80)
    private String templateKey;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "message_snapshot", columnDefinition = "TEXT")
    private String messageSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", length = 20)
    private ListingMarketingDeliveryMode deliveryMode;

    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;

    @Column(name = "recipient_phone_masked", length = 32)
    private String recipientPhoneMasked;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
