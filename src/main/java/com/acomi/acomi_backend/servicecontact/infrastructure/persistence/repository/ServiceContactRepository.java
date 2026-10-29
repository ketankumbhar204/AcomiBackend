package com.acomi.acomi_backend.servicecontact.infrastructure.persistence.repository;

import com.acomi.acomi_backend.servicecontact.infrastructure.persistence.entity.ServiceContactEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceContactRepository
        extends JpaRepository<ServiceContactEntity, UUID>, JpaSpecificationExecutor<ServiceContactEntity> {

    @Query("""
            SELECT c FROM ServiceContactEntity c
            WHERE c.ownerUserId = :ownerId
              AND c.active = true
              AND (
                    c.primaryPhone IN :phones
                    OR c.alternatePhone IN :phones
                    OR c.whatsappPhone IN :phones
                  )
            """)
    List<ServiceContactEntity> findActiveByOwnerAndAnyPhone(
            @Param("ownerId") UUID ownerId, @Param("phones") Collection<String> phones);
}
