package com.acomi.acomi_backend.mess.infrastructure.persistence.repository;

import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
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

    @Query(
            """
            SELECT r FROM MessRegistrationEntity r
            WHERE r.source = com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource.ADMIN
              AND r.claimedAt IS NULL
              AND r.status NOT IN (
                  com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus.CONVERTED,
                  com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus.REJECTED)
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
}
