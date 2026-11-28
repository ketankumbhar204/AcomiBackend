package com.acomi.acomi_backend.admin.infrastructure.persistence.repository;

import com.acomi.acomi_backend.admin.infrastructure.persistence.entity.AdminListingMarketingTemplateEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminListingMarketingTemplateRepository
        extends JpaRepository<AdminListingMarketingTemplateEntity, UUID> {

    List<AdminListingMarketingTemplateEntity> findAllByOrderByDisplayOrderAscNameAsc();

    List<AdminListingMarketingTemplateEntity> findByActiveTrueOrderByDisplayOrderAscNameAsc();
}
