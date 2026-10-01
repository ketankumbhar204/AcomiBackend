package com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository;

import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryClientChannel;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryDailyUsageEntity;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface InquiryDailyUsageRepository extends JpaRepository<InquiryDailyUsageEntity, UUID> {

    /** Unlocked read for quota display. */
    Optional<InquiryDailyUsageEntity> findOneByUserIdAndUsageDateAndChannel(
            UUID userId, LocalDate usageDate, InquiryClientChannel channel);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InquiryDailyUsageEntity> findByUserIdAndUsageDateAndChannel(
            UUID userId, LocalDate usageDate, InquiryClientChannel channel);
}
