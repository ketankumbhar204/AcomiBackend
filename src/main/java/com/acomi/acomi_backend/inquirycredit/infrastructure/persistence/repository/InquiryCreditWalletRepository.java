package com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository;

import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryCreditWalletEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InquiryCreditWalletRepository extends JpaRepository<InquiryCreditWalletEntity, UUID> {

    Optional<InquiryCreditWalletEntity> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM InquiryCreditWalletEntity w WHERE w.userId = :userId")
    Optional<InquiryCreditWalletEntity> findByUserIdWithLock(@Param("userId") UUID userId);
}
