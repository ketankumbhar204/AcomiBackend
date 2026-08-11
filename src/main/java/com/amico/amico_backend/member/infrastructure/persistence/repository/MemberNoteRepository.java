package com.amico.amico_backend.member.infrastructure.persistence.repository;

import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberNoteEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberNoteRepository extends JpaRepository<MemberNoteEntity, UUID> {

    @Query("""
            SELECT n FROM MemberNoteEntity n
            WHERE n.member.id = :memberId
            ORDER BY n.createdAt DESC
            """)
    List<MemberNoteEntity> findByMemberIdOrderByCreatedAtDesc(@Param("memberId") UUID memberId);
}
