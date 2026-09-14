package com.acomi.acomi_backend.user.infrastructure.persistence.repository;

import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByMobileNumber(String mobileNumber);

    Optional<UserEntity> findByMobileNumberAndIsActiveTrue(String mobileNumber);

    boolean existsByMobileNumber(String mobileNumber);

    boolean existsByMobileNumberAndIsActiveTrue(String mobileNumber);

    Optional<UserEntity> findByIdAndIsActiveTrue(UUID id);

    Optional<UserEntity> findByProfilePhotoFileId(UUID profilePhotoFileId);

    List<UserEntity> findBySystemRoleAndIsActiveTrue(SystemRole systemRole);

    long countByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(SystemRole systemRole);

    long countByIsActiveTrueAndSystemRole(SystemRole systemRole);

    Page<UserEntity> findByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(
            SystemRole systemRole, Pageable pageable);

    @Query(
            """
            SELECT u FROM UserEntity u
            WHERE u.mobileVerifiedAt IS NOT NULL
              AND u.isActive = true
              AND u.systemRole = :systemRole
              AND (
                   LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR u.mobileNumber LIKE CONCAT('%', :q, '%')
                   OR (u.email IS NOT NULL AND LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))
              )
            """)
    Page<UserEntity> searchVerifiedActiveUsers(
            @Param("systemRole") SystemRole systemRole, @Param("q") String q, Pageable pageable);

    @Query(
            """
            SELECT COUNT(u) FROM UserEntity u
            WHERE u.mobileVerifiedAt IS NOT NULL
              AND u.isActive = true
              AND u.systemRole = :systemRole
              AND COALESCE(u.mobileVerifiedAt, u.createdAt) >= :fromAt
              AND COALESCE(u.mobileVerifiedAt, u.createdAt) < :toAt
            """)
    long countVerifiedUsersRegisteredBetween(
            @Param("systemRole") SystemRole systemRole,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toAt") LocalDateTime toAt);

    @Query(
            """
            SELECT u FROM UserEntity u
            WHERE u.mobileVerifiedAt IS NOT NULL
              AND u.isActive = true
              AND u.systemRole = :systemRole
              AND COALESCE(u.mobileVerifiedAt, u.createdAt) >= :fromAt
              AND COALESCE(u.mobileVerifiedAt, u.createdAt) < :toAt
            ORDER BY COALESCE(u.mobileVerifiedAt, u.createdAt) DESC
            """)
    List<UserEntity> findVerifiedUsersRegisteredBetween(
            @Param("systemRole") SystemRole systemRole,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toAt") LocalDateTime toAt);

    @Query(
            """
            SELECT DISTINCT u FROM UserEntity u
            WHERE u.mobileVerifiedAt IS NOT NULL
              AND u.isActive = true
              AND u.systemRole = :systemRole
              AND (
                   :q IS NULL OR :q = '' OR
                   LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR
                   u.mobileNumber LIKE CONCAT('%', :q, '%') OR
                   (u.email IS NOT NULL AND LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))
              )
              AND (:fromAt IS NULL OR COALESCE(u.mobileVerifiedAt, u.createdAt) >= :fromAt)
              AND (:toAt IS NULL OR COALESCE(u.mobileVerifiedAt, u.createdAt) < :toAt)
              AND (
                   :hasSpace IS NULL OR
                   (:hasSpace = TRUE AND EXISTS (
                        SELECT 1 FROM SpaceMembershipEntity m
                        WHERE m.user = u
                          AND m.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
                   )) OR
                   (:hasSpace = FALSE AND NOT EXISTS (
                        SELECT 1 FROM SpaceMembershipEntity m
                        WHERE m.user = u
                          AND m.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
                   ))
              )
              AND (
                   :role IS NULL OR :role = '' OR
                   (:role = 'NOT_SELECTED' AND NOT EXISTS (
                        SELECT 1 FROM SpaceMembershipEntity m
                        WHERE m.user = u
                          AND m.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
                   )) OR
                   (:role = 'OWNER' AND EXISTS (
                        SELECT 1 FROM SpaceMembershipEntity m
                        WHERE m.user = u
                          AND m.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
                          AND m.role = com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER
                   ) AND NOT EXISTS (
                        SELECT 1 FROM SpaceMembershipEntity m
                        WHERE m.user = u
                          AND m.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
                          AND m.role <> com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER
                   )) OR
                   (:role = 'MEMBER' AND EXISTS (
                        SELECT 1 FROM SpaceMembershipEntity m
                        WHERE m.user = u
                          AND m.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
                          AND m.role <> com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER
                   ) AND NOT EXISTS (
                        SELECT 1 FROM SpaceMembershipEntity m
                        WHERE m.user = u
                          AND m.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
                          AND m.role = com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER
                   )) OR
                   (:role = 'OWNER_AND_MEMBER' AND EXISTS (
                        SELECT 1 FROM SpaceMembershipEntity m
                        WHERE m.user = u
                          AND m.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
                          AND m.role = com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER
                   ) AND EXISTS (
                        SELECT 1 FROM SpaceMembershipEntity m
                        WHERE m.user = u
                          AND m.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
                          AND m.role <> com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER
                   ))
              )
            """)
    Page<UserEntity> searchVerifiedUsersFiltered(
            @Param("systemRole") SystemRole systemRole,
            @Param("q") String q,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toAt") LocalDateTime toAt,
            @Param("hasSpace") Boolean hasSpace,
            @Param("role") String role,
            Pageable pageable);

    @Query(
            """
            SELECT COUNT(DISTINCT u.id) FROM UserEntity u
            WHERE u.mobileVerifiedAt IS NOT NULL
              AND u.isActive = true
              AND u.systemRole = :systemRole
              AND EXISTS (
                   SELECT 1 FROM SpaceMembershipEntity m
                   WHERE m.user = u
                     AND m.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
              )
            """)
    long countVerifiedUsersWithActiveSpace(@Param("systemRole") SystemRole systemRole);

    @Query(
            """
            SELECT COUNT(DISTINCT u.id) FROM UserEntity u
            WHERE u.mobileVerifiedAt IS NOT NULL
              AND u.isActive = true
              AND u.systemRole = :systemRole
              AND COALESCE(u.mobileVerifiedAt, u.createdAt) >= :fromAt
              AND COALESCE(u.mobileVerifiedAt, u.createdAt) < :toAt
              AND EXISTS (
                   SELECT 1 FROM SpaceMembershipEntity m
                   WHERE m.user = u
                     AND m.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
              )
            """)
    long countVerifiedUsersWithActiveSpaceRegisteredBetween(
            @Param("systemRole") SystemRole systemRole,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toAt") LocalDateTime toAt);
}
