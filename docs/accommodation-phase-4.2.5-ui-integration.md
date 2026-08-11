# Amico — Accommodation Phase 4.2.5 UI Integration

Frontend reference for **Property Layout Modes**, **synthetic units**, and **apartment PG** support.

> **Domain model:** [accommodation-domain-model.md](./accommodation-domain-model.md)  
> **Prerequisites:** Phase 4.1 CRUD, Phase 4.2 orchestration, lazy loading

---

## Overview

Phase 4.2.5 implements **Option C** architecture:

- **Structurally:** Unit exists for all PG inventory (including synthetic units in corridor PG).
- **Experientially:** Corridor PG UI remains Floor → Room → Bed.
- **Apartment PG:** Floor → Apartment → Room → Bed.

No occupancy, billing, complaints, or maintenance in this phase.

---

## Property layout modes

| `layoutMode` | Default space types | Visible hierarchy |
|--------------|---------------------|-------------------|
| `CORRIDOR_PG` | PG, HOSTEL | Building → Floor → Room → Bed |
| `APARTMENT_PG` | PG (optional) | Building → Floor → Apartment → Room → Bed |
| `CO_LIVING` | CO_LIVING | Building → Apartment → Room → Bed |
| `RENTAL` | RENTAL | Building → Apartment |

`BuildingResponse.layoutMode` and `BuildingSummaryResponse.layoutMode` drive UI profile selection.

```typescript
import { useAccommodationUiProfile } from '../hooks/useAccommodationUiProfile';

const { profile, summary } = useAccommodationUiProfile(spaceId, spaceType, buildingId);
```

Screens with a `buildingId` should use `useAccommodationUiProfile` so layout mode comes from the building summary. The home list uses per-building `summary.layoutMode` for subtitles.

---

## Synthetic units

- Created automatically for **CORRIDOR_PG** Quick Setup and manual room creation under floors.
- **Hidden** from list APIs by default (`includeSynthetic=false`).
- Never shown in UI lists or breadcrumbs.
- Existing PG inventory migrated via Flyway `V19` (one synthetic unit per room).

---

## API changes

### Building

- `CreateBuildingRequest.layoutMode` (optional; defaults from space type)
- `UpdateBuildingRequest.layoutMode`
- `BuildingResponse.layoutMode`
- `BuildingSummaryResponse`: `layoutMode`, `unitCount`, `visibleUnitCount`, `syntheticUnitCount`

### Units

- `UnitResponse.synthetic`, `UnitResponse.unitKind`
- `UnitListItemResponse.synthetic`, `UnitListItemResponse.unitKind`
- `GET .../buildings/{id}/units?includeSynthetic=false` (default)
- `GET .../buildings/{id}/floors/{floorId}/units`
- `POST .../buildings/{id}/floors/{floorId}/units`
- `PUT .../units/{unitId}` (flat path)

### Quick Setup

- `AccommodationSetupRequest.layoutMode`
- `PgHostelSetupConfig.apartmentsPerFloor` (required for `APARTMENT_PG`)

---

## UI navigation

| Layout | Building overview | Floor tap | Unit tap |
|--------|-------------------|-----------|----------|
| CORRIDOR_PG | Floors | Rooms (`AccommodationRooms`) | — |
| APARTMENT_PG | Floors | Apartments (`AccommodationFloorApartments`) | Rooms |
| CO_LIVING | Apartments | — | Rooms |
| RENTAL | Apartments | — | Unit detail |

---

## Quick Setup (PG)

1. **Layout mode** — Corridor PG (default) or Apartment PG.
2. **Corridor** — unchanged wizard (floors → rooms → beds).
3. **Apartment** — floors → apartments per floor → rooms per apartment → beds per room.

Pass `layoutMode` on `AccommodationSetupRequest`:

```typescript
buildSetupRequest(spaceType, building, pgConfig, undefined, 'APARTMENT_PG');
```

---

## Validation rules (client mirror)

| Rule | Behavior |
|------|----------|
| CORRIDOR_PG | Rooms under floor (synthetic unit created server-side) |
| APARTMENT_PG | Rooms under apartment only; apartments under floor |
| CORRIDOR_PG | No visible apartment create |
| RENTAL | No room/bed create |

---

## Related documents

- [accommodation-domain-model.md](./accommodation-domain-model.md)
- [accommodation-ui-integration.md](./accommodation-ui-integration.md)
- [accommodation-phase-4.2-ui-integration.md](./accommodation-phase-4.2-ui-integration.md)
