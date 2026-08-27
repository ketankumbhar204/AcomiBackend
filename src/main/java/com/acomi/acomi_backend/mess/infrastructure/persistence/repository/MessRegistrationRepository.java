package com.acomi.acomi_backend.mess.infrastructure.persistence.repository;

import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessRegistrationRepository extends JpaRepository<MessRegistrationEntity, UUID> {

    Optional<MessRegistrationEntity> findByReference(String reference);

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
}
