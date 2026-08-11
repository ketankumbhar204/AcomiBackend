package com.amico.amico_backend.complaint.infrastructure.persistence.repository;

import com.amico.amico_backend.complaint.infrastructure.persistence.entity.SpaceComplaintTimelineEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceComplaintTimelineEventRepository
        extends JpaRepository<SpaceComplaintTimelineEventEntity, UUID> {

    List<SpaceComplaintTimelineEventEntity> findByComplaint_IdOrderByPerformedAtAsc(UUID complaintId);
}
