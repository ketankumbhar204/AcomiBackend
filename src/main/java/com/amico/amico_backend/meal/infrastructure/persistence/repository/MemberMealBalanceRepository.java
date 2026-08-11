package com.amico.amico_backend.meal.infrastructure.persistence.repository;

import com.amico.amico_backend.meal.infrastructure.persistence.entity.MemberMealBalanceEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberMealBalanceRepository extends JpaRepository<MemberMealBalanceEntity, UUID> {

    Optional<MemberMealBalanceEntity> findBySpaceIdAndMemberId(UUID spaceId, UUID memberId);

    List<MemberMealBalanceEntity> findBySpaceId(UUID spaceId);

    @Query(
            """
            SELECT COALESCE(SUM(b.balance), 0) FROM MemberMealBalanceEntity b
            WHERE b.space.id = :spaceId
            """)
    java.math.BigDecimal sumBalanceBySpaceId(@Param("spaceId") UUID spaceId);
}
