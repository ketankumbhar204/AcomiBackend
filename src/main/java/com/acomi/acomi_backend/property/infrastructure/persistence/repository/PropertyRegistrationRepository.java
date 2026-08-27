package com.acomi.acomi_backend.property.infrastructure.persistence.repository;

import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import java.util.Optional;
import java.util.UUID;
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
}
