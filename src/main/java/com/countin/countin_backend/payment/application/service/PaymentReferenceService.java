package com.countin.countin_backend.payment.application.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mints immutable human-readable payment references: {@code PAY-YYYYMMDD-NNNNNN}.
 *
 * <p>Sequence is per space + calendar day and advanced atomically via {@code INSERT … ON CONFLICT
 * DO UPDATE … RETURNING} so concurrent submissions cannot collide.
 */
@Service
public class PaymentReferenceService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Returns {@code existing} when already set; otherwise mints a new reference for {@code
     * spaceId} on {@code day}.
     */
    @Transactional
    public String ensureReference(String existing, UUID spaceId, LocalDate day) {
        if (existing != null && !existing.isBlank()) {
            return existing.trim();
        }
        return nextReference(spaceId, day != null ? day : LocalDate.now());
    }

    @Transactional
    public String nextReference(UUID spaceId, LocalDate day) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId is required to mint a payment reference");
        }
        LocalDate refDay = day != null ? day : LocalDate.now();
        Number seq = (Number) entityManager
                .createNativeQuery(
                        """
                        INSERT INTO payment_reference_counters (space_id, day, last_seq)
                        VALUES (?1, ?2, 1)
                        ON CONFLICT (space_id, day)
                        DO UPDATE SET last_seq = payment_reference_counters.last_seq + 1
                        RETURNING last_seq
                        """)
                .setParameter(1, spaceId)
                .setParameter(2, java.sql.Date.valueOf(refDay))
                .getSingleResult();
        return "PAY-" + refDay.format(DAY) + "-" + String.format("%06d", seq.intValue());
    }
}
