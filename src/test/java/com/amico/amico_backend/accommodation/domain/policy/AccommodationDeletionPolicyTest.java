package com.amico.amico_backend.accommodation.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.accommodation.domain.model.AccommodationDeletionRoot;
import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.accommodation.domain.port.AccommodationOccupancyPort;
import com.amico.amico_backend.accommodation.domain.port.AccommodationReferencePort;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccommodationDeletionPolicyTest {

    @Mock
    private AccommodationOccupancyPort occupancyPort;

    @Mock
    private AccommodationReferencePort referencePort;

    private AccommodationDeletionPolicy policy;

    private final UUID bedId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();
    private final UUID buildingId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        policy = new AccommodationDeletionPolicy(occupancyPort, referencePort);
    }

    @Test
    void evaluateBedSubtree_whenClean_allowsDelete() {
        BedEntity bed = bed("Bed A", AccommodationStatus.AVAILABLE);
        AccommodationDeletionSubtree subtree = AccommodationDeletionSubtree.builder()
                .rootType(AccommodationDeletionRoot.BED)
                .rootName("Bed A")
                .bed(bed)
                .beds(List.of(bed))
                .build();

        assertThat(policy.evaluate(subtree).isDeletable()).isTrue();
    }

    @Test
    void evaluateBedSubtree_whenOccupied_blocksDelete() {
        BedEntity bed = bed("Bed A", AccommodationStatus.OCCUPIED);
        AccommodationDeletionSubtree subtree = AccommodationDeletionSubtree.builder()
                .rootType(AccommodationDeletionRoot.BED)
                .rootName("Bed A")
                .bed(bed)
                .beds(List.of(bed))
                .build();

        DeletionEvaluation result = policy.evaluate(subtree);

        assertThat(result.isDeletable()).isFalse();
        assertThat(result.getBlockReason()).contains("occupied");
    }

    @Test
    void evaluateRoomSubtree_withBeds_allowsCascadeDelete() {
        RoomEntity room = room("Room 101");
        BedEntity bedA = bed("Bed A", AccommodationStatus.AVAILABLE);
        BedEntity bedB = bed("Bed B", AccommodationStatus.AVAILABLE);

        AccommodationDeletionSubtree subtree = AccommodationDeletionSubtree.builder()
                .rootType(AccommodationDeletionRoot.ROOM)
                .rootName("Room 101")
                .room(room)
                .rooms(List.of(room))
                .beds(List.of(bedA, bedB))
                .build();

        assertThat(policy.evaluate(subtree).isDeletable()).isTrue();
    }

    @Test
    void evaluateBuildingSubtree_whenDescendantBedHasHistory_blocksDelete() {
        when(occupancyPort.hasOccupancyHistoryForBed(bedId)).thenReturn(true);

        BuildingEntity building = BuildingEntity.builder().name("Building A").build();
        building.setId(buildingId);
        RoomEntity room = room("Room 101");
        BedEntity bed = bed("Bed A", AccommodationStatus.AVAILABLE);
        bed.setId(bedId);

        AccommodationDeletionSubtree subtree = AccommodationDeletionSubtree.builder()
                .rootType(AccommodationDeletionRoot.BUILDING)
                .rootName("Building A")
                .building(building)
                .rooms(List.of(room))
                .beds(List.of(bed))
                .build();

        DeletionEvaluation result = policy.evaluate(subtree);

        assertThat(result.isDeletable()).isFalse();
        assertThat(result.getBlockReason())
                .isEqualTo(
                        "Cannot delete Building A because occupancy history exists within the accommodation structure. Deactivate instead.");
    }

    private BedEntity bed(String name, AccommodationStatus status) {
        BedEntity bed = BedEntity.builder()
                .name(name)
                .bedNumber(name)
                .status(status)
                .build();
        bed.setId(bedId);
        return bed;
    }

    private RoomEntity room(String name) {
        RoomEntity room = RoomEntity.builder()
                .name(name)
                .roomNumber("101")
                .status(AccommodationStatus.AVAILABLE)
                .build();
        room.setId(roomId);
        return room;
    }
}
