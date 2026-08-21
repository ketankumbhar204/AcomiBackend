package com.acomi.acomi_backend.member.infrastructure.persistence.repository;

import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpaceMembershipRepository extends JpaRepository<SpaceMembershipEntity, UUID> {

    @Query("SELECT sm FROM SpaceMembershipEntity sm JOIN FETCH sm.space WHERE sm.user.id = :userId")
    List<SpaceMembershipEntity> findByUserIdWithSpace(@Param("userId") UUID userId);

    @Query("""
            SELECT sm FROM SpaceMembershipEntity sm
            JOIN FETCH sm.space s
            WHERE sm.user.id = :userId
              AND sm.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
              AND s.isActive = true
            ORDER BY sm.isDefault DESC, sm.joinedAt DESC
            """)
    List<SpaceMembershipEntity> findUserSpaces(@Param("userId") UUID userId);

    @Query("""
            SELECT sm FROM SpaceMembershipEntity sm
            JOIN FETCH sm.space s
            WHERE sm.user.id = :userId
              AND sm.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
              AND s.isActive = true
              AND LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%'))
            ORDER BY sm.isDefault DESC, sm.joinedAt DESC
            """)
    List<SpaceMembershipEntity> searchUserSpaces(
            @Param("userId") UUID userId, @Param("search") String search);

    @Query("""
            SELECT sm FROM SpaceMembershipEntity sm
            JOIN FETCH sm.space s
            WHERE sm.user.id = :userId
              AND sm.isDefault = true
              AND sm.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
              AND s.isActive = true
            """)
    Optional<SpaceMembershipEntity> findDefaultSpace(@Param("userId") UUID userId);

    @Modifying
    @Query("""
            UPDATE SpaceMembershipEntity sm
            SET sm.isDefault = false
            WHERE sm.user.id = :userId
              AND sm.isDefault = true
            """)
    int clearDefaultSpaceForUser(@Param("userId") UUID userId);

    @Query("""
            SELECT sm FROM SpaceMembershipEntity sm
            JOIN FETCH sm.space s
            WHERE sm.user.id = :userId
              AND sm.space.id = :spaceId
              AND sm.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
              AND s.isActive = true
            """)
    Optional<SpaceMembershipEntity> findMembershipByUserAndSpace(
            @Param("userId") UUID userId, @Param("spaceId") UUID spaceId);

    List<SpaceMembershipEntity> findByUserId(UUID userId);

    List<SpaceMembershipEntity> findBySpaceId(UUID spaceId);

    Optional<SpaceMembershipEntity> findByUserIdAndSpaceId(UUID userId, UUID spaceId);

    List<SpaceMembershipEntity> findBySpaceIdAndStatus(UUID spaceId, MembershipStatus status);

    List<SpaceMembershipEntity> findBySpaceIdAndRole(UUID spaceId, MembershipRole role);

    boolean existsByUserIdAndSpaceIdAndStatus(UUID userId, UUID spaceId, MembershipStatus status);

    boolean existsByUserIdAndSpaceIdAndRoleIn(UUID userId, UUID spaceId, List<MembershipRole> roles);

    @Query("""
            SELECT sm FROM SpaceMembershipEntity sm
            JOIN FETCH sm.user u
            WHERE sm.space.id = :spaceId
              AND sm.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
            ORDER BY CASE WHEN sm.role = com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER
                     THEN 0 ELSE 1 END, sm.joinedAt ASC
            """)
    List<SpaceMembershipEntity> findActiveMembers(@Param("spaceId") UUID spaceId);

    @Query("""
            SELECT sm FROM SpaceMembershipEntity sm
            JOIN FETCH sm.user u
            WHERE sm.user.id = :userId
              AND sm.space.id = :spaceId
              AND sm.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
            """)
    Optional<SpaceMembershipEntity> findMembership(
            @Param("userId") UUID userId, @Param("spaceId") UUID spaceId);

    @Modifying
    @Query("""
            UPDATE SpaceMembershipEntity sm
            SET sm.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.REMOVED,
                sm.exitedAt = :exitedAt,
                sm.isDefault = false
            WHERE sm.user.id = :userId
              AND sm.space.id = :spaceId
              AND sm.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
            """)
    int deactivateMembership(
            @Param("userId") UUID userId,
            @Param("spaceId") UUID spaceId,
            @Param("exitedAt") java.time.LocalDateTime exitedAt);

    @Modifying
    @Query("""
            UPDATE SpaceMembershipEntity sm
            SET sm.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.REMOVED,
                sm.exitedAt = :exitedAt,
                sm.isDefault = false
            WHERE sm.user.id = :userId
              AND sm.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
            """)
    int deactivateAllActiveMembershipsForUser(
            @Param("userId") UUID userId,
            @Param("exitedAt") java.time.LocalDateTime exitedAt);

    @Query("""
            SELECT sm.space.id FROM SpaceMembershipEntity sm
            WHERE sm.user.id = :userId
              AND sm.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
              AND sm.role IN :roles
              AND sm.space.isActive = true
              AND sm.space.type IN :spaceTypes
            """)
    List<UUID> findManagedSpaceIdsByTypesAndRoles(
            @Param("userId") UUID userId,
            @Param("roles") List<MembershipRole> roles,
            @Param("spaceTypes") List<com.acomi.acomi_backend.space.domain.model.SpaceType> spaceTypes);
}
