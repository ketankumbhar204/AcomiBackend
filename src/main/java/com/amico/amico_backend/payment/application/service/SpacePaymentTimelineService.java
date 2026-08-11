package com.amico.amico_backend.payment.application.service;

import com.amico.amico_backend.payment.domain.model.PaymentTimelineEventType;
import com.amico.amico_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.amico.amico_backend.payment.infrastructure.persistence.entity.SpacePaymentTimelineEventEntity;
import com.amico.amico_backend.payment.infrastructure.persistence.repository.SpacePaymentTimelineEventRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpacePaymentTimelineService {

    private final SpacePaymentTimelineEventRepository timelineEventRepository;

    @Transactional
    public void record(
            SpacePaymentEntity payment,
            PaymentTimelineEventType eventType,
            String remarks,
            UUID performedBy) {
        SpacePaymentTimelineEventEntity event = SpacePaymentTimelineEventEntity.builder()
                .payment(payment)
                .eventType(eventType)
                .performedAt(LocalDateTime.now())
                .remarks(remarks)
                .performedBy(performedBy)
                .build();
        timelineEventRepository.save(event);
    }
}
