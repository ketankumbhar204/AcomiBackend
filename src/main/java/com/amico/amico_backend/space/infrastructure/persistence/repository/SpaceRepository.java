package com.amico.amico_backend.space.infrastructure.persistence.repository;

import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpaceRepository extends JpaRepository<SpaceEntity, UUID> {

    Optional<SpaceEntity> findByIdAndIsActiveTrue(UUID id);

    List<SpaceEntity> findAllByOwnerId(UUID ownerId);

    @Query("""
            SELECT DISTINCT s FROM SpaceEntity s
            JOIN SpaceMembershipEntity sm ON sm.space = s
            WHERE sm.user.id = :userId
              AND sm.status = com.amico.amico_backend.member.domain.model.MembershipStatus.ACTIVE
              AND s.isActive = true
            """)
    List<SpaceEntity> findAllActiveSpacesForUser(@Param("userId") UUID userId);

    List<SpaceEntity> findByOwnerIdAndIsActiveTrue(UUID ownerId);

    List<SpaceEntity> findByTypeAndIsActiveTrue(SpaceType type);

    boolean existsByIdAndOwnerIdAndIsActiveTrue(UUID id, UUID ownerId);
}
