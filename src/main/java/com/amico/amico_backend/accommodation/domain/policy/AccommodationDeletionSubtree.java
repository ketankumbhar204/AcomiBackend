package com.amico.amico_backend.accommodation.domain.policy;

import com.amico.amico_backend.accommodation.domain.model.AccommodationDeletionRoot;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AccommodationDeletionSubtree {

    private final AccommodationDeletionRoot rootType;
    private final String rootName;

    private final BuildingEntity building;
    private final FloorEntity floor;
    private final UnitEntity unit;
    private final RoomEntity room;
    private final BedEntity bed;

    @Builder.Default
    private final List<FloorEntity> floors = Collections.emptyList();

    @Builder.Default
    private final List<UnitEntity> units = Collections.emptyList();

    @Builder.Default
    private final List<RoomEntity> rooms = Collections.emptyList();

    @Builder.Default
    private final List<BedEntity> beds = Collections.emptyList();

    public String rootLabel() {
        return rootName;
    }
}
