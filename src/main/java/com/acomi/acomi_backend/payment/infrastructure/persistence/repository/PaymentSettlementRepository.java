package com.acomi.acomi_backend.payment.infrastructure.persistence.repository;

import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.PaymentSettlementEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentSettlementRepository extends JpaRepository<PaymentSettlementEntity, UUID> {

    List<PaymentSettlementEntity> findByPayment_IdOrderByPerformedAtAsc(UUID paymentId);

    List<PaymentSettlementEntity> findByPayment_IdInOrderByPerformedAtAsc(Collection<UUID> paymentIds);

    List<PaymentSettlementEntity> findByOccupancy_IdOrderByPerformedAtAsc(UUID occupancyId);

    boolean existsByPayment_IdAndAction(
            UUID paymentId, com.acomi.acomi_backend.payment.domain.model.PaymentSettlementAction action);
}
