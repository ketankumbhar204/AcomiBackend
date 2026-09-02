package com.acomi.acomi_backend.address.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "saved_addresses",
        indexes = {
            @Index(name = "idx_saved_addresses_owner_recent", columnList = "created_by_user_id, last_used_at, created_at"),
            @Index(name = "idx_saved_addresses_owner_city", columnList = "created_by_user_id, city"),
            @Index(name = "idx_saved_addresses_owner_pincode", columnList = "created_by_user_id, pincode")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedAddressEntity extends BaseEntity {

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "address_line", nullable = false, length = 255)
    private String addressLine;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Column(name = "state", nullable = false, length = 80)
    private String state;

    @Column(name = "pincode", nullable = false, length = 6)
    private String pincode;

    @Column(name = "map_url", length = 512)
    private String mapUrl;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Builder.Default
    @Column(name = "usage_count", nullable = false)
    private int usageCount = 0;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
