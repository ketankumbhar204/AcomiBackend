package com.acomi.acomi_backend.property.infrastructure.persistence.repository;

import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
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
public interface PropertyRegistrationRepository
        extends JpaRepository<PropertyRegistrationEntity, UUID> {

    Optional<PropertyRegistrationEntity> findByReference(String reference);

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
            WHERE r.source = com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource.ADMIN
              AND r.claimedAt IS NULL
              AND r.status NOT IN (
                  com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus.CONVERTED,
                  com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus.REJECTED)
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
}
