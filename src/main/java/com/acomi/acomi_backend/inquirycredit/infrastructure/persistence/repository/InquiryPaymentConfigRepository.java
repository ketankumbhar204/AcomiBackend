package com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository;

import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryPaymentConfigEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InquiryPaymentConfigRepository extends JpaRepository<InquiryPaymentConfigEntity, UUID> {

    Optional<InquiryPaymentConfigEntity> findFirstByOrderByCreatedAtAsc();
}
