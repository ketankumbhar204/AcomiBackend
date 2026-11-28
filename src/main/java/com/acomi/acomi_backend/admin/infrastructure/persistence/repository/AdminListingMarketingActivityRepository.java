package com.acomi.acomi_backend.admin.infrastructure.persistence.repository;

import com.acomi.acomi_backend.admin.infrastructure.persistence.entity.AdminListingMarketingActivityEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminListingMarketingActivityRepository
        extends JpaRepository<AdminListingMarketingActivityEntity, UUID> {

    List<AdminListingMarketingActivityEntity> findByPropertyRegistrationIdOrderByCreatedAtDesc(
            UUID propertyRegistrationId);

    List<AdminListingMarketingActivityEntity> findByMessRegistrationIdOrderByCreatedAtDesc(
            UUID messRegistrationId);

    List<AdminListingMarketingActivityEntity> findBySpaceIdOrderByCreatedAtDesc(UUID spaceId);
}
