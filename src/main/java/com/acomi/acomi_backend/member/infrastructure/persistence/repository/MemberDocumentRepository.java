package com.acomi.acomi_backend.member.infrastructure.persistence.repository;

import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberDocumentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberDocumentRepository extends JpaRepository<MemberDocumentEntity, UUID> {

    @Query("""
            SELECT d FROM MemberDocumentEntity d
            WHERE d.member.id = :memberId
            ORDER BY d.uploadedAt DESC
            """)
    List<MemberDocumentEntity> findByMemberIdOrderByUploadedAtDesc(@Param("memberId") UUID memberId);

    @Query("""
            SELECT d FROM MemberDocumentEntity d
            WHERE d.id = :documentId
              AND d.member.id = :memberId
            """)
    Optional<MemberDocumentEntity> findByIdAndMemberId(
            @Param("documentId") UUID documentId, @Param("memberId") UUID memberId);
}
