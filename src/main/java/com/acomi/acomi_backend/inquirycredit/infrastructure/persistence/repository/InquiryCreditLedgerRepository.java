package com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository;

import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryCreditLedgerEntryEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InquiryCreditLedgerRepository extends JpaRepository<InquiryCreditLedgerEntryEntity, UUID> {

    Optional<InquiryCreditLedgerEntryEntity> findByIdempotencyKey(String idempotencyKey);
}
