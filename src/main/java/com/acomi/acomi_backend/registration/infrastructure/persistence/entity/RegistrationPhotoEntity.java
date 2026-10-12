package com.acomi.acomi_backend.registration.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
@Table(
        name = "registration_photos",
        indexes = {
            @Index(name = "idx_registration_photos_property", columnList = "property_registration_id, display_order"),
            @Index(name = "idx_registration_photos_mess", columnList = "mess_registration_id, display_order")
        })
public class RegistrationPhotoEntity extends BaseEntity {

    @Column(name = "property_registration_id")
    private UUID propertyRegistrationId;

    @Column(name = "mess_registration_id")
    private UUID messRegistrationId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    @Column(name = "content", nullable = false)
    private byte[] content;
}
