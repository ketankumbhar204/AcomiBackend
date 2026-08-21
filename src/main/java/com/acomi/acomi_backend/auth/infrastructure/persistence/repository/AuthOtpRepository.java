package com.acomi.acomi_backend.auth.infrastructure.persistence.repository;

import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.auth.infrastructure.persistence.entity.AuthOtpEntity;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthOtpRepository extends JpaRepository<AuthOtpEntity, UUID> {

    Optional<AuthOtpEntity> findFirstByMobileNumberAndPurposeOrderByCreatedAtDesc(
            String mobileNumber, OtpPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM AuthOtpEntity o
            WHERE o.mobileNumber = :mobileNumber
              AND o.purpose = :purpose
            ORDER BY o.createdAt DESC
            """)
    List<AuthOtpEntity> findLatestForUpdate(
            @Param("mobileNumber") String mobileNumber,
            @Param("purpose") OtpPurpose purpose,
            Pageable pageable);

    long countByMobileNumberAndPurposeAndCreatedAtAfter(
            String mobileNumber, OtpPurpose purpose, LocalDateTime createdAfter);

    long countByRequestIpAndCreatedAtAfter(String requestIp, LocalDateTime createdAfter);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM AuthOtpEntity o WHERE o.verificationTokenHash = :hash")
    Optional<AuthOtpEntity> findByVerificationTokenHashForUpdate(@Param("hash") String hash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AuthOtpEntity o
            SET o.consumedAt = :now
            WHERE o.mobileNumber = :mobileNumber
              AND o.purpose = :purpose
              AND o.consumedAt IS NULL
            """)
    int consumeUnusedOtps(
            @Param("now") LocalDateTime now,
            @Param("mobileNumber") String mobileNumber,
            @Param("purpose") OtpPurpose purpose);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AuthOtpEntity o
            SET o.verificationTokenConsumedAt = :now
            WHERE o.mobileNumber = :mobileNumber
              AND o.purpose = :purpose
              AND o.verificationTokenHash IS NOT NULL
              AND o.verificationTokenConsumedAt IS NULL
            """)
    int consumeUnusedVerificationTokens(
            @Param("now") LocalDateTime now,
            @Param("mobileNumber") String mobileNumber,
            @Param("purpose") OtpPurpose purpose);
}
