package com.acomi.acomi_backend.common.reference;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

/**
 * Mints the next business reference from a Postgres sequence. {@code nextval} is not rolled
 * back, so a failed insert leaves a gap and that number is never reused.
 */
@Service
public class BusinessReferenceAllocator {

    @PersistenceContext
    private EntityManager entityManager;

    public String next(BusinessReferencePrefix prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("prefix is required");
        }
        Number sequence = (Number) entityManager
                .createNativeQuery("SELECT nextval('" + prefix.sequenceName() + "')")
                .getSingleResult();
        return BusinessReferenceCodes.format(prefix, sequence.longValue());
    }
}
