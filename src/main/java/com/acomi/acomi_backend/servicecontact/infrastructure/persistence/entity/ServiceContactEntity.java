package com.acomi.acomi_backend.servicecontact.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.servicecontact.domain.model.ServiceContactCategory;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "service_contacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceContactEntity extends BaseEntity {

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "business_name", nullable = false, length = 150)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ServiceContactCategory category;

    @Column(name = "custom_category_name", length = 80)
    private String customCategoryName;

    @Column(name = "contact_person_name", length = 150)
    private String contactPersonName;

    @Column(name = "primary_phone", length = 15)
    private String primaryPhone;

    @Column(name = "alternate_phone", length = 15)
    private String alternatePhone;

    @Column(name = "whatsapp_phone", length = 15)
    private String whatsappPhone;

    @Column(length = 254)
    private String email;

    @Column(length = 500)
    private String description;

    @Column(length = 300)
    private String address;

    @Column(length = 2000)
    private String notes;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    @Builder.Default
    @OneToMany(mappedBy = "contact", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ServiceContactSpaceEntity> spaceLinks = new ArrayList<>();
}
