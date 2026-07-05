package com.countin.countin_backend.member.infrastructure.persistence.repository;

import com.countin.countin_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.countin.countin_backend.occupancy.domain.model.MemberOccupancyStatus;
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
}
