# Acomi — Accommodation Lazy Loading UI Integration

Frontend guide for **progressive (multi-level) accommodation loading**. Replaces loading the full building tree in one response.

**Prerequisites:**

- [accommodation-ui-integration.md](./accommodation-ui-integration.md) — CRUD, enums, space-type profiles
- [accommodation-phase-4.2-ui-integration.md](./accommodation-phase-4.2-ui-integration.md) — Quick Setup, Builder, Duplicate, Bulk
- [accommodation-lifecycle-ui-integration.md](./accommodation-lifecycle-ui-integration.md) — Deactivate, restore, delete
- [my-spaces-ui-integration.md](./my-spaces-ui-integration.md) — `spaceId`, `spaceType`

---

## Problem this solves

A single builder tree for large properties is not scalable:

| Example PG | Count |
|------------|-------|
| 10 floors × 20 rooms × 3 beds | 10 floors, 200 rooms, 600 beds |

Loading everything at once causes heavy API responses, slow rendering, bloated React state, and poor mobile UX.

**Rule:** Load only what the current screen needs. **No unified tree API** — use separate, cache-friendly list endpoints.

---

## Screen map (3 levels)

### Level 1 — Building overview

**Screen:** Building Overview (entry after tapping a building)

**Load:** `GET .../buildings/{buildingId}/summary` only.

**Display:**

- Building name (+ code if present)
- Summary: floors, units, rooms, beds
- Status counts: available, occupied, reserved, maintenance, blocked

**Then show list based on `spaceType`:**

| Space type | List endpoint | Row example |
|------------|---------------|-------------|
| PG / HOSTEL | `GET .../buildings/{buildingId}/floors` | Floor 1 · 20 rooms · 60 beds |
| CO_LIVING | `GET .../buildings/{buildingId}/units` | Unit 101 · 3 rooms · 6 beds |
| RENTAL | `GET .../buildings/{buildingId}/units` | Flat 101 · Occupied (`status`) |

### Level 2 — Floor / Unit details

| Space type | Tap | Load | Notes |
|------------|-----|------|-------|
| PG / HOSTEL | Floor | `GET .../floors/{floorId}/rooms` | Rooms only |
| CO_LIVING | Unit | `GET .../units/{unitId}/rooms` | Rooms only |
| RENTAL | Unit | Detail GET `GET .../units/{unitId}` | **Leaf node** — no room list |

### Level 3 — Room details

**Tap room** → `GET .../rooms/{roomId}/beds`

**Display:** Bed A, Bed B, Bed C with status badges.

**Detail / edit / lifecycle:** Use full detail GETs (`GET .../floors/{floorId}`, `GET .../rooms/{roomId}`, etc.) — unchanged.

---

## API contract

Path prefix: `/api/v1/spaces/{spaceId}`

### Building summary

```
GET /buildings/{buildingId}/summary
```

Counts and status fields are **flattened** in JSON (no nested `counts` / `statusCounts` objects required on the client):

```json
{
  "success": true,
  "data": {
    "buildingId": "uuid",
    "name": "Building A",
    "code": "BLD-A",
    "spaceId": "uuid",
    "floors": 4,
    "units": 0,
    "rooms": 80,
    "beds": 240,
    "available": 240,
    "occupied": 0,
    "reserved": 0,
    "maintenance": 0,
    "blocked": 0
  }
}
```

`statusCounts` aggregate rooms, beds, and units in the building.

---

### List endpoints (default: lightweight + paginated)

All list GETs default to **`view=summary`** (lightweight items in a `PagedResponse`).

| Endpoint | Item type |
|----------|-----------|
| `GET .../buildings/{buildingId}/floors` | `FloorListItemResponse` |
| `GET .../buildings/{buildingId}/units` | `UnitListItemResponse` |
| `GET .../floors/{floorId}/rooms` | `RoomListItemResponse` |
| `GET .../units/{unitId}/rooms` | `RoomListItemResponse` |
| `GET .../rooms/{roomId}/beds` | `BedListItemResponse` |

**Query parameters (all list endpoints):**

| Param | Default | Description |
|-------|---------|-------------|
| `view` | `summary` | `summary` = lightweight paginated; `full` = legacy full `*Response[]` (no pagination) |
| `query` | — | Name search (floors, units, rooms lists) |
| `page` | `0` | Zero-based page index |
| `size` | `20` | Page size |
| `sort` | varies | e.g. `sortOrder,floorNumber` for floors; `unitNumber` for units; `roomNumber` for rooms; `bedNumber` for beds |

**Paged response envelope** (`view=summary`):

```json
{
  "success": true,
  "data": {
    "content": [ /* list items */ ],
    "page": 0,
    "size": 20,
    "totalElements": 200,
    "totalPages": 10,
    "first": true,
    "last": false
  }
}
```

#### Floor list item

```json
{
  "floorId": "uuid",
  "name": "Floor 1",
  "roomCount": 20,
  "bedCount": 60,
  "available": 60,
  "occupied": 0
}
```

`available` / `occupied` are **bed-level** counts on that floor.

#### Unit list item

```json
{
  "unitId": "uuid",
  "name": "Unit 101",
  "roomCount": 3,
  "bedCount": 6,
  "status": "AVAILABLE"
}
```

`status` is the unit's own status — use for **RENTAL** overview rows (e.g. "Flat 101 · Occupied").

#### Room list item

```json
{
  "roomId": "uuid",
  "name": "Room 101",
  "roomType": "SHARED",
  "bedCount": 3,
  "availableBeds": 3,
  "occupiedBeds": 0
}
```

#### Bed list item

```json
{
  "bedId": "uuid",
  "label": "A",
  "status": "AVAILABLE"
}
```

`label` maps to `bedNumber` on the full `BedResponse`.

---

### Space-wide search (future-ready)

Search across the whole space without walking the tree:

```
GET /floors?query={text}&page=0&size=20
GET /units?query={text}&page=0&size=20
GET /rooms?query={text}&page=0&size=20
```

Same `PagedResponse` + list item shapes as building-scoped lists.

---

## TypeScript types

```typescript
export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface BuildingSummaryResponse {
  buildingId: string;
  name: string;
  code?: string | null;
  spaceId: string;
  floors: number;
  units: number;
  rooms: number;
  beds: number;
  available: number;
  occupied: number;
  reserved: number;
  maintenance: number;
  blocked: number;
}

export interface FloorListItemResponse {
  floorId: string;
  name: string;
  roomCount: number;
  bedCount: number;
  available: number;
  occupied: number;
}

export interface UnitListItemResponse {
  unitId: string;
  name: string;
  roomCount: number;
  bedCount: number;
  status: AccommodationStatus;
}

export interface RoomListItemResponse {
  roomId: string;
  name: string;
  roomType: RoomType;
  bedCount: number;
  availableBeds: number;
  occupiedBeds: number;
}

export interface BedListItemResponse {
  bedId: string;
  label: string;
  status: AccommodationStatus;
}

export interface ListQueryParams {
  query?: string;
  page?: number;
  size?: number;
  sort?: string;
  view?: 'summary' | 'full';
}
```

---

## Client API module

Add to `src/api/accommodationApi.ts`:

```typescript
function buildListQuery(params?: ListQueryParams): string {
  const q = new URLSearchParams();
  if (params?.view) q.set('view', params.view);
  if (params?.query) q.set('query', params.query);
  if (params?.page != null) q.set('page', String(params.page));
  if (params?.size != null) q.set('size', String(params.size));
  if (params?.sort) q.set('sort', params.sort);
  const s = q.toString();
  return s ? `?${s}` : '';
}

export async function getBuildingSummary(
  spaceId: string,
  buildingId: string,
): Promise<BuildingSummaryResponse> {
  const res = await authFetch(`/api/v1/spaces/${spaceId}/buildings/${buildingId}/summary`);
  return parseResponse<BuildingSummaryResponse>(res);
}

export async function listFloors(
  spaceId: string,
  buildingId: string,
  params?: ListQueryParams,
): Promise<PagedResponse<FloorListItemResponse>> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/buildings/${buildingId}/floors${buildListQuery({ view: 'summary', ...params })}`,
  );
  return parseResponse<PagedResponse<FloorListItemResponse>>(res);
}

export async function listUnits(
  spaceId: string,
  buildingId: string,
  params?: ListQueryParams,
): Promise<PagedResponse<UnitListItemResponse>> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/buildings/${buildingId}/units${buildListQuery({ view: 'summary', ...params })}`,
  );
  return parseResponse<PagedResponse<UnitListItemResponse>>(res);
}

export async function listRoomsByFloor(
  spaceId: string,
  floorId: string,
  params?: ListQueryParams,
): Promise<PagedResponse<RoomListItemResponse>> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/floors/${floorId}/rooms${buildListQuery({ view: 'summary', ...params })}`,
  );
  return parseResponse<PagedResponse<RoomListItemResponse>>(res);
}

export async function listRoomsByUnit(
  spaceId: string,
  unitId: string,
  params?: ListQueryParams,
): Promise<PagedResponse<RoomListItemResponse>> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/units/${unitId}/rooms${buildListQuery({ view: 'summary', ...params })}`,
  );
  return parseResponse<PagedResponse<RoomListItemResponse>>(res);
}

export async function listBeds(
  spaceId: string,
  roomId: string,
  params?: ListQueryParams,
): Promise<PagedResponse<BedListItemResponse>> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/rooms/${roomId}/beds${buildListQuery({ view: 'summary', ...params })}`,
  );
  return parseResponse<PagedResponse<BedListItemResponse>>(res);
}

export async function searchFloors(
  spaceId: string,
  query: string,
  params?: Omit<ListQueryParams, 'query'>,
): Promise<PagedResponse<FloorListItemResponse>> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/floors${buildListQuery({ view: 'summary', query, ...params })}`,
  );
  return parseResponse<PagedResponse<FloorListItemResponse>>(res);
}

export async function searchUnits(
  spaceId: string,
  query: string,
  params?: Omit<ListQueryParams, 'query'>,
): Promise<PagedResponse<UnitListItemResponse>> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/units${buildListQuery({ view: 'summary', query, ...params })}`,
  );
  return parseResponse<PagedResponse<UnitListItemResponse>>(res);
}

export async function searchRooms(
  spaceId: string,
  query: string,
  params?: Omit<ListQueryParams, 'query'>,
): Promise<PagedResponse<RoomListItemResponse>> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/rooms${buildListQuery({ view: 'summary', query, ...params })}`,
  );
  return parseResponse<PagedResponse<RoomListItemResponse>>(res);
}
```

**Legacy compatibility:** Manual Builder or older screens can pass `view: 'full'` to get unpaginated `FloorResponse[]` / `UnitResponse[]` / etc. Migrate to `summary` when possible.

---

## React Query keys

Use **independent, cacheable** keys per level — do not store the whole tree in one query.

```typescript
export const accommodationKeys = {
  summary: (spaceId: string, buildingId: string) =>
    ['accommodation', spaceId, 'building', buildingId, 'summary'] as const,

  floors: (spaceId: string, buildingId: string, params?: ListQueryParams) =>
    ['accommodation', spaceId, 'building', buildingId, 'floors', params ?? {}] as const,

  units: (spaceId: string, buildingId: string, params?: ListQueryParams) =>
    ['accommodation', spaceId, 'building', buildingId, 'units', params ?? {}] as const,

  roomsByFloor: (spaceId: string, floorId: string, params?: ListQueryParams) =>
    ['accommodation', spaceId, 'floor', floorId, 'rooms', params ?? {}] as const,

  roomsByUnit: (spaceId: string, unitId: string, params?: ListQueryParams) =>
    ['accommodation', spaceId, 'unit', unitId, 'rooms', params ?? {}] as const,

  beds: (spaceId: string, roomId: string, params?: ListQueryParams) =>
    ['accommodation', spaceId, 'room', roomId, 'beds', params ?? {}] as const,

  searchFloors: (spaceId: string, query: string, params?: ListQueryParams) =>
    ['accommodation', spaceId, 'search', 'floors', query, params ?? {}] as const,

  searchUnits: (spaceId: string, query: string, params?: ListQueryParams) =>
    ['accommodation', spaceId, 'search', 'units', query, params ?? {}] as const,

  searchRooms: (spaceId: string, query: string, params?: ListQueryParams) =>
    ['accommodation', spaceId, 'search', 'rooms', query, params ?? {}] as const,
};
```

**Invalidation after mutations:**

| Action | Invalidate |
|--------|------------|
| Create/edit/delete floor | `summary`, `floors` for building |
| Create/edit/delete unit | `summary`, `units` for building |
| Create/edit/delete room | `summary`, `roomsByFloor` or `roomsByUnit`, parent `floors`/`units` row |
| Create/edit/delete bed | `summary`, `beds` for room, parent `roomsBy*` |
| Quick Setup / duplicate / bulk | `summary` + all list keys for that building |

---

## UI migration checklist

| Area | Change |
|------|--------|
| Building entry screen | Fetch `summary` + paginated floor/unit list — **not** full tree |
| Floor / Unit list screens | Use `FloorListItemResponse` / `UnitListItemResponse` rows |
| Room list screens | Use `RoomListItemResponse`; show `availableBeds` / `occupiedBeds` |
| Bed list screens | Use `BedListItemResponse`; `label` + status badge |
| Detail / edit screens | Unchanged — full `GET .../{id}` with `actions` |
| Manual Builder expand | Replace `getFloors`/`getBeds` full lists with `listFloors`/`listBeds` summary (or `view=full` temporarily) |
| Search | Prefer server `query` param; space-wide search via `/floors`, `/units`, `/rooms` |
| Pagination | Wire `FlatList` `onEndReached` → `page + 1` while `!last` |
| React state | **No** single `buildingTree` state object — one query per screen level |

---

## What did NOT change

- CRUD POST/PUT endpoints
- Lifecycle: deactivate, restore, delete (`POST .../deactivate`, etc.)
- Quick Setup, duplicate, bulk
- Detail GET responses with `actions` metadata
- Accommodation domain rules per `spaceType`

---

## Related docs

- [accommodation-ui-integration.md](./accommodation-ui-integration.md)
- [accommodation-phase-4.2-ui-integration.md](./accommodation-phase-4.2-ui-integration.md)
- [accommodation-lifecycle-ui-integration.md](./accommodation-lifecycle-ui-integration.md)
- [accommodation-flow-redesign.md](./accommodation-flow-redesign.md)
- [accommodation-navigation-ui-fix.md](./accommodation-navigation-ui-fix.md)

---

## Frontend implementation prompt

Copy into the mobile/web repo agent:

```
Migrate Acomi accommodation UI from full-tree / full-list loading to multi-level progressive loading.

Read and follow exactly:
- docs/accommodation-lazy-loading-ui-integration.md (primary spec)
- docs/accommodation-ui-integration.md (CRUD + shared types)
- docs/accommodation-lifecycle-ui-integration.md (invalidate caches after deactivate/restore/delete)
- docs/accommodation-phase-4.2-ui-integration.md (builder + quick setup; refresh summary after bulk/duplicate)

Architecture — load one screen level at a time:
1. Building overview: GET .../buildings/{buildingId}/summary + paginated floors OR units list (view=summary default)
2. Floor/unit detail: GET .../floors/{floorId}/rooms OR .../units/{unitId}/rooms
3. Room detail: GET .../rooms/{roomId}/beds
RENTAL units are leaf nodes — unit detail only, no room list.

API changes:
- List GETs return PagedResponse<LightweightItem> by default (view=summary)
- Legacy full FloorResponse[] etc.: pass view=full (temporary; migrate off this)
- Building summary JSON has flattened floors/units/rooms/beds + available/occupied/reserved/maintenance/blocked
- Search: GET .../floors?query=, .../units?query=, .../rooms?query= (space-scoped)
- Pagination: page, size, sort query params on all list endpoints

Implementation tasks:
1. Add TypeScript types: PagedResponse, BuildingSummaryResponse, FloorListItemResponse, UnitListItemResponse, RoomListItemResponse, BedListItemResponse, ListQueryParams
2. Add accommodationApi functions: getBuildingSummary, listFloors, listUnits, listRoomsByFloor, listRoomsByUnit, listBeds, searchFloors, searchUnits, searchRooms
3. Replace React Query hooks (useFloors, useRooms, useBeds, etc.) to use summary list APIs + independent cache keys per level — remove any buildingTree state
4. Update list row components to show counts/status from lightweight items (not full entities)
5. Wire FlatList pagination (onEndReached → fetch page+1 while !last)
6. Use server query param for search where possible instead of client-side filter on full arrays
7. Keep detail/edit screens on full GET .../{id} with actions metadata — unchanged
8. After mutations (CRUD, lifecycle, bulk, duplicate): invalidate summary + affected list query keys

Do not:
- Fetch entire building hierarchy upfront
- Introduce a client-side tree merge of all levels
- Break lifecycle POST actions or CRUD endpoints

Test with a building that has multiple floors/rooms; verify pagination and that navigation passes correct spaceId + entity IDs.
```
