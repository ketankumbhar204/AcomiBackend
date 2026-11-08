package com.acomi.acomi_backend.payment.infrastructure.persistence.repository;

import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.MonthlyBillingControlEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyBillingControlRepository extends JpaRepository<MonthlyBillingControlEntity, Short> {}
