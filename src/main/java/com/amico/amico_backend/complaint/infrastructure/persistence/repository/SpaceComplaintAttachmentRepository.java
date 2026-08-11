package com.amico.amico_backend.complaint.infrastructure.persistence.repository;

import com.amico.amico_backend.complaint.infrastructure.persistence.entity.SpaceComplaintAttachmentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceComplaintAttachmentRepository
        extends JpaRepository<SpaceComplaintAttachmentEntity, UUID> {

    List<SpaceComplaintAttachmentEntity> findByComplaint_IdOrderByCreatedAtAsc(UUID complaintId);
}
