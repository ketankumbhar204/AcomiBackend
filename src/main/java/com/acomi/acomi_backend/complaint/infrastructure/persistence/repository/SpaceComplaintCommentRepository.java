package com.acomi.acomi_backend.complaint.infrastructure.persistence.repository;

import com.acomi.acomi_backend.complaint.infrastructure.persistence.entity.SpaceComplaintCommentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceComplaintCommentRepository
        extends JpaRepository<SpaceComplaintCommentEntity, UUID> {

    List<SpaceComplaintCommentEntity> findByComplaint_IdOrderByCreatedAtAsc(UUID complaintId);

    List<SpaceComplaintCommentEntity> findByComplaint_IdAndInternalFalseOrderByCreatedAtAsc(
            UUID complaintId);
}
