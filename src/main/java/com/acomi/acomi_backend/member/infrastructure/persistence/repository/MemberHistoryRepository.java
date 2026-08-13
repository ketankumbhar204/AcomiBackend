package com.acomi.acomi_backend.member.infrastructure.persistence.repository;

import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberHistoryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberHistoryRepository extends JpaRepository<MemberHistoryEntity, UUID> {

    @Query("""
            SELECT h FROM MemberHistoryEntity h
            WHERE h.member.id = :memberId
            ORDER BY h.createdAt DESC
            """)
    List<MemberHistoryEntity> findByMemberIdOrderByCreatedAtDesc(@Param("memberId") UUID memberId);
}
