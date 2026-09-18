package com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository;

import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryCreditPurchaseStatus;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryCreditPurchaseRequestEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InquiryCreditPurchaseRequestRepository
        extends JpaRepository<InquiryCreditPurchaseRequestEntity, UUID> {

    Optional<InquiryCreditPurchaseRequestEntity> findFirstByUserIdAndPackageIdAndStatusOrderByRequestedAtDesc(
            UUID userId, UUID packageId, InquiryCreditPurchaseStatus status);

    Page<InquiryCreditPurchaseRequestEntity> findByStatusOrderByRequestedAtDesc(
            InquiryCreditPurchaseStatus status, Pageable pageable);

    Page<InquiryCreditPurchaseRequestEntity> findAllByOrderByRequestedAtDesc(Pageable pageable);

    List<InquiryCreditPurchaseRequestEntity> findByUserIdOrderByRequestedAtDesc(UUID userId);

    long countByStatus(InquiryCreditPurchaseStatus status);

    Optional<InquiryCreditPurchaseRequestEntity> findByIdAndStatus(UUID id, InquiryCreditPurchaseStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM InquiryCreditPurchaseRequestEntity r WHERE r.id = :id AND r.status = :status")
    Optional<InquiryCreditPurchaseRequestEntity> findByIdAndStatusForUpdate(
            @Param("id") UUID id, @Param("status") InquiryCreditPurchaseStatus status);
}
