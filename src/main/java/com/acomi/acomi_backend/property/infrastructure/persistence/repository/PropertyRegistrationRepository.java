package com.acomi.acomi_backend.property.infrastructure.persistence.repository;

import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
public interface PropertyRegistrationRepository
        extends JpaRepository<PropertyRegistrationEntity, UUID> {

    Optional<PropertyRegistrationEntity> findByReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PropertyRegistrationEntity p WHERE p.id = :id")
    Optional<PropertyRegistrationEntity> lockById(@Param("id") UUID id);

    /** Sequence-backed so concurrent public submissions cannot collide on a reference. */
    @Query(value = "SELECT nextval('property_registration_reference_seq')", nativeQuery = true)
    long nextReferenceNumber();

    /**
     * Same owner mobile, same locality, same property name. Deliberately narrow: an owner with
     * several properties on one pincode still gets each lead stored as PENDING.
     */
    @Query(
            """
            SELECT COUNT(r) > 0 FROM PropertyRegistrationEntity r
            WHERE r.mobileNumber = :mobileNumber
              AND r.pincode = :pincode
              AND LOWER(TRIM(r.propertyName)) = LOWER(TRIM(:propertyName))
            """)
    boolean existsLikelyDuplicate(
            @Param("mobileNumber") String mobileNumber,
            @Param("pincode") String pincode,
            @Param("propertyName") String propertyName);

    @Query(
            """
            SELECT r FROM PropertyRegistrationEntity r
            WHERE r.mobileNumber IN :mobiles
               OR r.alternateMobileNumber IN :mobiles
               OR r.additionalMobileNumber IN :mobiles
            """)
    List<PropertyRegistrationEntity> findCandidatesByAnyMobile(@Param("mobiles") Collection<String> mobiles);

    @Query(
            """
            SELECT r FROM PropertyRegistrationEntity r
            WHERE r.pincode IN :pincodes
            """)
    List<PropertyRegistrationEntity> findCandidatesByPincodeIn(@Param("pincodes") Collection<String> pincodes);

    @Query(
            """
            SELECT r FROM PropertyRegistrationEntity r
            WHERE r.latitude IS NOT NULL
              AND r.longitude IS NOT NULL
              AND r.latitude BETWEEN :minLat AND :maxLat
              AND r.longitude BETWEEN :minLng AND :maxLng
            """)
    List<PropertyRegistrationEntity> findCandidatesInGeoBox(
            @Param("minLat") java.math.BigDecimal minLat,
            @Param("maxLat") java.math.BigDecimal maxLat,
            @Param("minLng") java.math.BigDecimal minLng,
            @Param("maxLng") java.math.BigDecimal maxLng);

    @Query(
            """
            SELECT r FROM PropertyRegistrationEntity r
            WHERE r.source = com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource.ADMIN
              AND r.claimedAt IS NULL
              AND r.status <> com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus.REJECTED
              AND r.mobileNumber = :mobileNumber
              AND r.propertyType = :propertyType
              AND LOWER(TRIM(r.propertyName)) = LOWER(TRIM(:propertyName))
            """)
    List<PropertyRegistrationEntity> findUnclaimedAdminLeads(
            @Param("mobileNumber") String mobileNumber,
            @Param("propertyType") SpaceType propertyType,
            @Param("propertyName") String propertyName);

    long countBySource(PropertyRegistrationSource source);

    long countByClaimedAtIsNotNull();

    long countByClaimedAtIsNullAndSource(PropertyRegistrationSource source);

    Page<PropertyRegistrationEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<PropertyRegistrationEntity> findByStatusNotInOrderByCreatedAtDesc(
            List<com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus> statuses,
            Pageable pageable);

    Page<PropertyRegistrationEntity> findBySourceOrderByCreatedAtDesc(
            PropertyRegistrationSource source, Pageable pageable);

    long countByStatusNotIn(
            List<com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus> statuses);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            java.time.LocalDateTime from, java.time.LocalDateTime to);

    long countByStatusNotInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            List<com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus> statuses,
            java.time.LocalDateTime from,
            java.time.LocalDateTime to);

    long countBySourceAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            PropertyRegistrationSource source, java.time.LocalDateTime from, java.time.LocalDateTime to);

    @Query(
            """
            SELECT r FROM PropertyRegistrationEntity r
            WHERE (:q IS NULL OR LOWER(r.propertyName) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                OR LOWER(r.ownerName) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                OR LOWER(r.city) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                OR LOWER(r.state) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                OR LOWER(r.mobileNumber) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                OR LOWER(r.reference) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
              AND (:source IS NULL OR r.source = :source)
              AND (:status IS NULL OR r.status = :status)
              AND (:excludeClosed = false OR r.status NOT IN :closedStatuses)
              AND (:claimed IS NULL
                OR (:claimed = true AND r.claimedAt IS NOT NULL)
                OR (:claimed = false AND r.claimedAt IS NULL))
            ORDER BY r.createdAt DESC
            """)
    Page<PropertyRegistrationEntity> searchFiltered(
            @Param("q") String q,
            @Param("source") PropertyRegistrationSource source,
            @Param("status") com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus status,
            @Param("excludeClosed") boolean excludeClosed,
            @Param("closedStatuses")
                    List<com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus>
                            closedStatuses,
            @Param("claimed") Boolean claimed,
            Pageable pageable);

    Optional<PropertyRegistrationEntity> findByConvertedSpaceId(UUID convertedSpaceId);

    @Query(
            """
            SELECT r FROM PropertyRegistrationEntity r
            WHERE r.source = com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource.ADMIN
              AND r.convertedSpaceId IS NULL
              AND r.status NOT IN (
                  com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus.CONVERTED,
                  com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus.REJECTED)
            ORDER BY r.createdAt ASC
            """)
    List<PropertyRegistrationEntity> findOpenAdminLeads();

    @Query(
            """
            select p.convertedSpaceId from PropertyRegistrationEntity p
            where p.convertedSpaceId in :spaceIds and p.testLead = true
            """)
    List<UUID> findTestLeadConvertedSpaceIds(@Param("spaceIds") Collection<UUID> spaceIds);

    @Query(
            value =
                    """
                    SELECT CAST(r.created_at AS date) AS day, COUNT(*) AS cnt
                    FROM property_registrations r
                    WHERE r.created_at >= :fromAt AND r.created_at < :toAt
                    GROUP BY CAST(r.created_at AS date)
                    ORDER BY day ASC
                    """,
            nativeQuery = true)
    List<Object[]> countDailyCreatedBetween(
            @Param("fromAt") java.time.LocalDateTime fromAt, @Param("toAt") java.time.LocalDateTime toAt);
}
