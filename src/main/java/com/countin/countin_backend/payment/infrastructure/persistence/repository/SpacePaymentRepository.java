package com.countin.countin_backend.payment.infrastructure.persistence.repository;

import com.countin.countin_backend.payment.domain.model.SpacePaymentCategory;
import com.countin.countin_backend.payment.domain.model.SpacePaymentStatus;
import com.countin.countin_backend.payment.domain.model.SpacePaymentType;
import com.countin.countin_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpacePaymentRepository extends JpaRepository<SpacePaymentEntity, UUID> {

    @Query("""
            SELECT p FROM SpacePaymentEntity p
            JOIN FETCH p.member m
            LEFT JOIN FETCH m.user
            LEFT JOIN FETCH p.occupancy
            WHERE p.id = :paymentId AND p.space.id = :spaceId
            """)
    Optional<SpacePaymentEntity> findByIdAndSpaceId(
            @Param("paymentId") UUID paymentId, @Param("spaceId") UUID spaceId);

    @Query("""
            SELECT p FROM SpacePaymentEntity p
            JOIN FETCH p.member
            LEFT JOIN FETCH p.occupancy
            WHERE p.space.id = :spaceId
              AND p.month = :month
              AND (:memberId IS NULL OR p.member.id = :memberId)
              AND (:status IS NULL OR p.paymentStatus = :status)
              AND (:paymentType IS NULL OR p.paymentType = :paymentType)
              AND (:paymentCategory IS NULL OR p.paymentCategory = :paymentCategory)
            ORDER BY p.dueDate ASC, p.member.fullName ASC
            """)
    List<SpacePaymentEntity> search(
            @Param("spaceId") UUID spaceId,
            @Param("month") String month,
            @Param("memberId") UUID memberId,
            @Param("status") SpacePaymentStatus status,
            @Param("paymentType") SpacePaymentType paymentType,
            @Param("paymentCategory") SpacePaymentCategory paymentCategory);

    Optional<SpacePaymentEntity> findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
            UUID spaceId,
            UUID memberId,
            String month,
            SpacePaymentType paymentType,
            SpacePaymentCategory paymentCategory);
}
