package com.acomi.acomi_backend.storage.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.storage.domain.model.FilePurpose;
import com.acomi.acomi_backend.storage.domain.model.FileStatus;
import com.acomi.acomi_backend.storage.domain.model.FileVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stored_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredFileEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 40)
    private FilePurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private FileVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FileStatus status;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "byte_size")
    private Long byteSize;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Builder.Default
    @Column(name = "checksum_verified", nullable = false)
    private boolean checksumVerified = false;

    @Column(name = "storage_provider", nullable = false, length = 32)
    private String storageProvider;

    @Column(name = "bucket", nullable = false, length = 128)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private UUID uploadedByUserId;

    @Column(name = "space_id")
    private UUID spaceId;

    @Builder.Default
    @Column(name = "associated", nullable = false)
    private boolean associated = false;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "purge_after")
    private LocalDateTime purgeAfter;
}
