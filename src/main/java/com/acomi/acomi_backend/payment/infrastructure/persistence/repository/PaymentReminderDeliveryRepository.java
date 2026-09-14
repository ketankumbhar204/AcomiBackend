package com.acomi.acomi_backend.payment.infrastructure.persistence.repository;

import com.acomi.acomi_backend.payment.domain.model.PaymentReminderType;
import com.acomi.acomi_backend.payment.domain.model.ReminderChannel;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.PaymentReminderDeliveryEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentReminderDeliveryRepository
        extends JpaRepository<PaymentReminderDeliveryEntity, UUID> {

    Optional<PaymentReminderDeliveryEntity> findByPaymentIdAndReminderTypeAndBusinessDateAndChannel(
            UUID paymentId,
            PaymentReminderType reminderType,
            LocalDate businessDate,
            ReminderChannel channel);

    List<PaymentReminderDeliveryEntity> findBySpaceIdAndBusinessDateAndChannel(
            UUID spaceId, LocalDate businessDate, ReminderChannel channel);

    List<PaymentReminderDeliveryEntity> findByPaymentIdInAndBusinessDateAndChannel(
            Collection<UUID> paymentIds, LocalDate businessDate, ReminderChannel channel);

    Optional<PaymentReminderDeliveryEntity> findFirstByPaymentIdAndChannelOrderByBusinessDateDescCreatedAtDesc(
            UUID paymentId, ReminderChannel channel);
}
