package com.acomi.acomi_backend.address.infrastructure.persistence.repository;

import com.acomi.acomi_backend.address.infrastructure.persistence.entity.SavedAddressEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SavedAddressRepository extends JpaRepository<SavedAddressEntity, UUID> {

    Optional<SavedAddressEntity> findFirstByCreatedByUserIdAndFingerprintAndIsActiveTrue(
            UUID createdByUserId, String fingerprint);

    Optional<SavedAddressEntity> findFirstByCreatedByUserIdAndFingerprintOrderByUpdatedAtDesc(
            UUID createdByUserId, String fingerprint);

    Optional<SavedAddressEntity> findByIdAndCreatedByUserIdAndIsActiveTrue(UUID id, UUID createdByUserId);

    @Query(
            value = """
                    SELECT s FROM SavedAddressEntity s
                    WHERE s.createdByUserId = :ownerId
                      AND s.isActive = true
                      AND (
                            :search IS NULL
                            OR :search = ''
                            OR LOWER(s.addressLine) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(s.city) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(s.state) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(s.pincode) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(COALESCE(s.mapUrl, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                          )
                    ORDER BY COALESCE(s.lastUsedAt, s.createdAt) DESC
                    """,
            countQuery = """
                    SELECT COUNT(s) FROM SavedAddressEntity s
                    WHERE s.createdByUserId = :ownerId
                      AND s.isActive = true
                      AND (
                            :search IS NULL
                            OR :search = ''
                            OR LOWER(s.addressLine) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(s.city) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(s.state) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(s.pincode) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(COALESCE(s.mapUrl, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                          )
                    """)
    Page<SavedAddressEntity> searchActiveByOwner(
            @Param("ownerId") UUID ownerId, @Param("search") String search, Pageable pageable);
}
