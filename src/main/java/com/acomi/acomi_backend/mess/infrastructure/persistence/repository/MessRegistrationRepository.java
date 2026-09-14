package com.acomi.acomi_backend.mess.infrastructure.persistence.repository;

import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
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
public interface MessRegistrationRepository extends JpaRepository<MessRegistrationEntity, UUID> {

    Optional<MessRegistrationEntity> findByReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MessRegistrationEntity m WHERE m.id = :id")
    Optional<MessRegistrationEntity> lockById(@Param("id") UUID id);

    @Query(value = "SELECT nextval('mess_registration_reference_seq')", nativeQuery = true)
    long nextReferenceNumber();

    @Query(
            """
            SELECT COUNT(r) > 0 FROM MessRegistrationEntity r
            WHERE r.mobileNumber = :mobileNumber
              AND r.pincode = :pincode
              AND LOWER(TRIM(r.messName)) = LOWER(TRIM(:messName))
            """)
    boolean existsLikelyDuplicate(
            @Param("mobileNumber") String mobileNumber,
            @Param("pincode") String pincode,
            @Param("messName") String messName);

    @Query(
            """
            SELECT r FROM MessRegistrationEntity r
            WHERE r.mobileNumber IN :mobiles
               OR r.alternateMobileNumber IN :mobiles
               OR r.additionalMobileNumber IN :mobiles
            """)
    List<MessRegistrationEntity> findCandidatesByAnyMobile(@Param("mobiles") Collection<String> mobiles);

    @Query(
            """
            SELECT r FROM MessRegistrationEntity r
            WHERE r.pincode IN :pincodes
            """)
    List<MessRegistrationEntity> findCandidatesByPincodeIn(@Param("pincodes") Collection<String> pincodes);

    @Query(
            """
            SELECT r FROM MessRegistrationEntity r
            WHERE r.latitude IS NOT NULL
              AND r.longitude IS NOT NULL
              AND r.latitude BETWEEN :minLat AND :maxLat
              AND r.longitude BETWEEN :minLng AND :maxLng
            """)
    List<MessRegistrationEntity> findCandidatesInGeoBox(
            @Param("minLat") java.math.BigDecimal minLat,
            @Param("maxLat") java.math.BigDecimal maxLat,
            @Param("minLng") java.math.BigDecimal minLng,
            @Param("maxLng") java.math.BigDecimal maxLng);

    @Query(
            """
            SELECT r FROM MessRegistrationEntity r
            WHERE r.source = com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource.ADMIN
              AND r.claimedAt IS NULL
              AND r.status <> com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus.REJECTED
              AND r.mobileNumber = :mobileNumber
              AND LOWER(TRIM(r.messName)) = LOWER(TRIM(:messName))
            """)
    List<MessRegistrationEntity> findUnclaimedAdminLeads(
            @Param("mobileNumber") String mobileNumber, @Param("messName") String messName);

    long countBySource(MessRegistrationSource source);

    long countByClaimedAtIsNotNull();

    long countByClaimedAtIsNullAndSource(MessRegistrationSource source);

    Page<MessRegistrationEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<MessRegistrationEntity> findByStatusNotInOrderByCreatedAtDesc(
            List<com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus> statuses,
            Pageable pageable);

    Page<MessRegistrationEntity> findBySourceOrderByCreatedAtDesc(
            MessRegistrationSource source, Pageable pageable);

    long countByStatusNotIn(List<com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus> statuses);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            java.time.LocalDateTime from, java.time.LocalDateTime to);

    long countByStatusNotInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            List<com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus> statuses,
            java.time.LocalDateTime from,
            java.time.LocalDateTime to);

    long countBySourceAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            MessRegistrationSource source, java.time.LocalDateTime from, java.time.LocalDateTime to);

    @Query(
            """
            SELECT r FROM MessRegistrationEntity r
            WHERE (:q IS NULL OR LOWER(r.messName) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
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
    Page<MessRegistrationEntity> searchFiltered(
            @Param("q") String q,
            @Param("source") MessRegistrationSource source,
            @Param("status") com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus status,
            @Param("excludeClosed") boolean excludeClosed,
            @Param("closedStatuses")
                    List<com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus> closedStatuses,
            @Param("claimed") Boolean claimed,
            Pageable pageable);

    Optional<MessRegistrationEntity> findByConvertedSpaceId(UUID convertedSpaceId);

    @Query(
            """
            SELECT r FROM MessRegistrationEntity r
            WHERE r.source = com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource.ADMIN
              AND r.convertedSpaceId IS NULL
              AND r.status NOT IN (
                  com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus.CONVERTED,
                  com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus.REJECTED)
            ORDER BY r.createdAt ASC
            """)
    List<MessRegistrationEntity> findOpenAdminLeads();

    @Query(
            """
            select m.convertedSpaceId from MessRegistrationEntity m
            where m.convertedSpaceId in :spaceIds and m.testLead = true
            """)
    List<UUID> findTestLeadConvertedSpaceIds(@Param("spaceIds") Collection<UUID> spaceIds);
}
