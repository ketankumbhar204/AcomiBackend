package com.acomi.acomi_backend.address.infrastructure.persistence.repository;

import com.acomi.acomi_backend.address.infrastructure.persistence.entity.SavedAddressEntity;
import java.util.List;
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
            value =
                    """
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
                      AND (:city IS NULL OR LOWER(s.city) = LOWER(:city))
                      AND (:state IS NULL OR LOWER(s.state) = LOWER(:state))
                      AND (
                            :usage IS NULL
                            OR :usage = ''
                            OR (:usage = 'UNUSED' AND s.usageCount = 0)
                            OR (:usage = 'USED' AND s.usageCount > 0)
                            OR (:usage = 'SHARED' AND (
                                  s.usageCount > 1
                                  OR (s.propertyUsageCount > 0 AND s.messUsageCount > 0)
                                ))
                            OR (:usage = 'PROPERTY' AND s.propertyUsageCount > 0)
                            OR (:usage = 'MESS' AND s.messUsageCount > 0)
                          )
                    ORDER BY COALESCE(s.lastUsedAt, s.createdAt) DESC
                    """,
            countQuery =
                    """
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
                      AND (:city IS NULL OR LOWER(s.city) = LOWER(:city))
                      AND (:state IS NULL OR LOWER(s.state) = LOWER(:state))
                      AND (
                            :usage IS NULL
                            OR :usage = ''
                            OR (:usage = 'UNUSED' AND s.usageCount = 0)
                            OR (:usage = 'USED' AND s.usageCount > 0)
                            OR (:usage = 'SHARED' AND (
                                  s.usageCount > 1
                                  OR (s.propertyUsageCount > 0 AND s.messUsageCount > 0)
                                ))
                            OR (:usage = 'PROPERTY' AND s.propertyUsageCount > 0)
                            OR (:usage = 'MESS' AND s.messUsageCount > 0)
                          )
                    """)
    Page<SavedAddressEntity> searchFiltered(
            @Param("ownerId") UUID ownerId,
            @Param("search") String search,
            @Param("city") String city,
            @Param("state") String state,
            @Param("usage") String usage,
            Pageable pageable);

    @Query(
            value =
                    """
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
            countQuery =
                    """
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

    long countByIsActiveTrue();

    long countByCreatedByUserIdAndIsActiveTrue(UUID createdByUserId);

    long countByCreatedByUserIdAndIsActiveTrueAndPropertyUsageCountGreaterThan(
            UUID createdByUserId, int propertyUsageCount);

    long countByCreatedByUserIdAndIsActiveTrueAndMessUsageCountGreaterThan(
            UUID createdByUserId, int messUsageCount);

    @Query(
            """
            SELECT COUNT(s) FROM SavedAddressEntity s
            WHERE s.createdByUserId = :ownerId
              AND s.isActive = true
              AND (s.usageCount > 1 OR (s.propertyUsageCount > 0 AND s.messUsageCount > 0))
            """)
    long countSharedByOwner(@Param("ownerId") UUID ownerId);

    @Query(
            """
            SELECT DISTINCT s.city FROM SavedAddressEntity s
            WHERE s.createdByUserId = :ownerId AND s.isActive = true
            ORDER BY s.city ASC
            """)
    List<String> findDistinctCitiesByOwner(@Param("ownerId") UUID ownerId);

    @Query(
            """
            SELECT DISTINCT s.state FROM SavedAddressEntity s
            WHERE s.createdByUserId = :ownerId AND s.isActive = true
            ORDER BY s.state ASC
            """)
    List<String> findDistinctStatesByOwner(@Param("ownerId") UUID ownerId);

    long countByIsActiveTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            java.time.LocalDateTime from, java.time.LocalDateTime to);
}
