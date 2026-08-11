package com.amico.amico_backend.member.infrastructure.persistence.repository;

import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.occupancy.domain.model.MemberOccupancyStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberRepository extends JpaRepository<MemberEntity, UUID> {

    Optional<MemberEntity> findByIdAndIsActiveTrue(UUID id);

    @Query("""
            SELECT m FROM MemberEntity m
            WHERE m.space.id = :spaceId
              AND m.isActive = true
            ORDER BY m.createdAt DESC
            """)
    List<MemberEntity> findBySpaceIdAndActiveTrue(@Param("spaceId") UUID spaceId);

    @Query("""
            SELECT m FROM MemberEntity m
            WHERE m.space.id = :spaceId
              AND m.isActive = true
              AND (
                  :search IS NULL OR :search = ''
                  OR LOWER(m.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR m.mobileNumber LIKE CONCAT('%', :search, '%')
              )
              AND (:occupancyStatus IS NULL OR m.occupancyStatus = :occupancyStatus)
            ORDER BY m.fullName ASC, m.createdAt DESC
            """)
    List<MemberEntity> searchActiveMembers(
            @Param("spaceId") UUID spaceId,
            @Param("search") String search,
            @Param("occupancyStatus") MemberOccupancyStatus occupancyStatus);

    @Query("""
            SELECT m FROM MemberEntity m
            WHERE m.space.id = :spaceId
              AND m.mobileNumber = :mobileNumber
            """)
    Optional<MemberEntity> findBySpaceIdAndMobileNumber(
            @Param("spaceId") UUID spaceId, @Param("mobileNumber") String mobileNumber);

    boolean existsBySpaceIdAndMobileNumberAndIsActiveTrue(UUID spaceId, String mobileNumber);

    @Query("""
            SELECT m FROM MemberEntity m
            WHERE m.id = :id
              AND m.space.id = :spaceId
              AND m.isActive = true
            """)
    Optional<MemberEntity> findByIdAndSpaceIdAndActiveTrue(
            @Param("id") UUID id, @Param("spaceId") UUID spaceId);

    @Query("""
            SELECT m FROM MemberEntity m
            WHERE m.space.id = :spaceId
              AND m.mobileNumber = :mobileNumber
              AND m.isActive = true
            """)
    Optional<MemberEntity> findActiveBySpaceIdAndMobileNumber(
            @Param("spaceId") UUID spaceId, @Param("mobileNumber") String mobileNumber);

    @Query("""
            SELECT m FROM MemberEntity m
            WHERE m.space.id = :spaceId
              AND m.user.id = :userId
              AND m.isActive = true
            """)
    Optional<MemberEntity> findActiveBySpaceIdAndUserId(
            @Param("spaceId") UUID spaceId, @Param("userId") UUID userId);

    @Query("""
            SELECT m FROM MemberEntity m
            WHERE m.user.id = :userId
              AND m.isActive = true
            """)
    List<MemberEntity> findActiveByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT m FROM MemberEntity m
            JOIN FETCH m.space s
            WHERE m.isActive = true
              AND m.status IN (
                  com.amico.amico_backend.member.domain.model.MemberStatus.ACTIVE,
                  com.amico.amico_backend.member.domain.model.MemberStatus.VACATED
              )
              AND m.role = com.amico.amico_backend.member.domain.model.MembershipRole.TENANT
              AND s.id IN :sourceSpaceIds
              AND m.occupancyStatus = com.amico.amico_backend.occupancy.domain.model.MemberOccupancyStatus.VACATED
              AND (
                  :search IS NULL OR :search = ''
                  OR LOWER(m.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR m.mobileNumber LIKE CONCAT('%', :search, '%')
              )
              AND NOT EXISTS (
                  SELECT 1 FROM MemberEntity busy
                  WHERE busy.isActive = true
                    AND busy.mobileNumber = m.mobileNumber
                    AND busy.space.id IN :managedSpaceIds
                    AND busy.occupancyStatus IN (
                        com.amico.amico_backend.occupancy.domain.model.MemberOccupancyStatus.ALLOCATED,
                        com.amico.amico_backend.occupancy.domain.model.MemberOccupancyStatus.RESERVED
                    )
              )
              AND (
                  s.id = :targetSpaceId
                  OR NOT EXISTS (
                      SELECT 1 FROM MemberEntity target
                      WHERE target.space.id = :targetSpaceId
                        AND target.isActive = true
                        AND target.mobileNumber = m.mobileNumber
                  )
              )
            ORDER BY m.fullName ASC, m.createdAt DESC
            """)
    List<MemberEntity> searchImportCandidates(
            @Param("targetSpaceId") UUID targetSpaceId,
            @Param("sourceSpaceIds") List<UUID> sourceSpaceIds,
            @Param("managedSpaceIds") List<UUID> managedSpaceIds,
            @Param("search") String search);

    /**
     * Mess customer import candidates: CUSTOMER from managed Mess spaces, or TENANT from
     * managed lodging spaces (PG→Mess reuse). Concurrent Mess memberships are allowed
     * (no occupancy busy filter). Excludes mobiles already present in the target Mess.
     */
    @Query("""
            SELECT m FROM MemberEntity m
            JOIN FETCH m.space s
            WHERE m.isActive = true
              AND m.status IN (
                  com.amico.amico_backend.member.domain.model.MemberStatus.ACTIVE,
                  com.amico.amico_backend.member.domain.model.MemberStatus.VACATED
              )
              AND (
                  (
                      s.type = com.amico.amico_backend.space.domain.model.SpaceType.MESS
                      AND m.role = com.amico.amico_backend.member.domain.model.MembershipRole.CUSTOMER
                  )
                  OR (
                      s.type IN (
                          com.amico.amico_backend.space.domain.model.SpaceType.PG,
                          com.amico.amico_backend.space.domain.model.SpaceType.HOSTEL,
                          com.amico.amico_backend.space.domain.model.SpaceType.CO_LIVING,
                          com.amico.amico_backend.space.domain.model.SpaceType.RENTAL
                      )
                      AND m.role = com.amico.amico_backend.member.domain.model.MembershipRole.TENANT
                  )
              )
              AND s.id IN :sourceSpaceIds
              AND (
                  :search IS NULL OR :search = ''
                  OR LOWER(m.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR m.mobileNumber LIKE CONCAT('%', :search, '%')
              )
              AND (
                  s.id = :targetSpaceId
                  OR NOT EXISTS (
                      SELECT 1 FROM MemberEntity target
                      WHERE target.space.id = :targetSpaceId
                        AND target.isActive = true
                        AND target.mobileNumber = m.mobileNumber
                  )
              )
            ORDER BY m.fullName ASC, m.createdAt DESC
            """)
    List<MemberEntity> searchMessImportCandidates(
            @Param("targetSpaceId") UUID targetSpaceId,
            @Param("sourceSpaceIds") List<UUID> sourceSpaceIds,
            @Param("search") String search);

    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
            FROM MemberEntity m
            WHERE m.isActive = true
              AND m.mobileNumber = :mobileNumber
              AND m.space.id IN :managedSpaceIds
              AND m.occupancyStatus IN (
                  com.amico.amico_backend.occupancy.domain.model.MemberOccupancyStatus.ALLOCATED,
                  com.amico.amico_backend.occupancy.domain.model.MemberOccupancyStatus.RESERVED
              )
            """)
    boolean existsBusyOccupancyForMobileAcrossSpaces(
            @Param("mobileNumber") String mobileNumber,
            @Param("managedSpaceIds") List<UUID> managedSpaceIds);
}
