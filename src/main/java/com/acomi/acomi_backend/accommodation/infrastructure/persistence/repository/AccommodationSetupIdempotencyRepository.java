package com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository;

import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.AccommodationSetupIdempotencyEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccommodationSetupIdempotencyRepository
        extends JpaRepository<AccommodationSetupIdempotencyEntity, UUID> {

    Optional<AccommodationSetupIdempotencyEntity> findBySpaceIdAndIdempotencyKey(
            UUID spaceId, String idempotencyKey);

    @Modifying
    @Query("DELETE FROM AccommodationSetupIdempotencyEntity e WHERE e.building.id = :buildingId")
    int deleteByBuildingId(@Param("buildingId") UUID buildingId);
}
