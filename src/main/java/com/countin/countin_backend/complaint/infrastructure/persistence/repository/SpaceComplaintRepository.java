package com.countin.countin_backend.complaint.infrastructure.persistence.repository;

import com.countin.countin_backend.complaint.domain.model.ComplaintCategory;
import com.countin.countin_backend.complaint.domain.model.ComplaintPriority;
import com.countin.countin_backend.complaint.domain.model.ComplaintStatus;
import com.countin.countin_backend.complaint.infrastructure.persistence.entity.SpaceComplaintEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpaceComplaintRepository extends JpaRepository<SpaceComplaintEntity, UUID> {

    Optional<SpaceComplaintEntity> findByIdAndSpace_Id(UUID id, UUID spaceId);

    @Query(
            """
            SELECT c FROM SpaceComplaintEntity c
            WHERE c.space.id = :spaceId
              AND (:status IS NULL OR c.status = :status)
              AND (:priority IS NULL OR c.priority = :priority)
              AND (:category IS NULL OR c.category = :category)
              AND (:assigneeMembershipId IS NULL OR c.assignedToMembership.id = :assigneeMembershipId)
              AND (:createdByMemberId IS NULL OR c.createdByMember.id = :createdByMemberId)
            ORDER BY c.createdAt DESC
            """)
    List<SpaceComplaintEntity> findFiltered(
            @Param("spaceId") UUID spaceId,
            @Param("status") ComplaintStatus status,
            @Param("priority") ComplaintPriority priority,
            @Param("category") ComplaintCategory category,
            @Param("assigneeMembershipId") UUID assigneeMembershipId,
            @Param("createdByMemberId") UUID createdByMemberId);

    long countBySpace_IdAndStatusIn(UUID spaceId, Collection<ComplaintStatus> statuses);

    List<SpaceComplaintEntity> findBySpace_IdAndStatusInOrderByCreatedAtDesc(
            UUID spaceId, Collection<ComplaintStatus> statuses);
}
