package com.acomi.acomi_backend.common.reference;

import jakarta.persistence.PrePersist;
import org.springframework.context.ApplicationContext;

/**
 * Assigns a business reference on insert when the row does not already have one.
 * Hibernate constructs this listener, so the allocator is resolved from Spring.
 */
public class BusinessReferenceEntityListener {

    @PrePersist
    public void assignReference(Object entity) {
        if (!(entity instanceof BusinessReferenced referenced)) {
            return;
        }
        String existing = referenced.getReferenceCode();
        if (existing != null && !existing.isBlank()) {
            return;
        }
        referenced.setReferenceCode(BusinessReferenceSupport.next(referenced.businessReferencePrefix()));
    }

    /** Holds the Spring allocator for JPA callbacks. */
    static final class BusinessReferenceSupport {

        private static ApplicationContext applicationContext;

        private BusinessReferenceSupport() {}

        static void setApplicationContext(ApplicationContext context) {
            applicationContext = context;
        }

        static String next(BusinessReferencePrefix prefix) {
            if (applicationContext == null) {
                throw new IllegalStateException("Business reference allocator is not initialized");
            }
            return applicationContext.getBean(BusinessReferenceAllocator.class).next(prefix);
        }
    }
}
