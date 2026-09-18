package com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository;

import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryCreditPackageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InquiryCreditPackageRepository extends JpaRepository<InquiryCreditPackageEntity, UUID> {

    List<InquiryCreditPackageEntity> findByEnabledTrueOrderByDisplayOrderAsc();
}
