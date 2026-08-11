# Amico — Accommodation UI Integration Guide

Frontend reference for **Phase 4.1 Property Structure** (CRUD) and **Phase 4.2 Accommodation UX** (quick setup, builder summary, duplicate, bulk).

> **Not in this phase:** Occupancy (allocate / transfer / vacate), availability queries, dashboard metrics — coming in Phase 4.3+.

**Prerequisites:**

- [auth-ui-integration.md](./auth-ui-integration.md) — JWT login
- [my-spaces-ui-integration.md](./my-spaces-ui-integration.md) — `spaceId`, `spaceType`, `membershipRole`
- [membership-ui-integration.md](./membership-ui-integration.md) — members (occupancy will link members later)

---

## Overview

Accommodation APIs let owners and managers define the **physical inventory** of a space. All routes are scoped under the current space.

| Level | API prefix | PG / Hostel | Co-Living | Rental | Mess |
|-------|------------|-------------|-----------|--------|------|
| Building | `/buildings` | Yes | Yes | Yes | **Hidden** |
| Floor | `/buildings/{id}/floors` | Yes | No | No | Hidden |
| Unit | `/buildings/{id}/units` | No | Yes | Yes | Hidden |
| Room (under floor) | `/floors/{id}/rooms` | Yes | No | No | Hidden |
| Room (under unit) | `/units/{id}/rooms` | No | Yes | No* | Hidden |
| Bed | `/rooms/{id}/beds` | Yes | Yes | No | Hidden |

\* Rental: rooms under units are **not supported in Phase 4.1** (Building → Unit only). Co-Living uses unit → room → bed.

**Auth:** All endpoints require JWT.

```
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**Deactivate** endpoints return `204 No Content` (soft-delete via `isActive = false` on the backend).

---

## When to show Accommodation in the app

```typescript
export function isAccommodationApplicable(spaceType: SpaceType): boolean {
  return spaceType !== 'MESS';
}
```

Read `spaceType` from `GET /api/v1/spaces/my` (`MySpaceResponse.spaceType`). **Do not show** the Accommodation section/tab for `MESS` spaces.

---

## Structure by space type

### PG / HOSTEL

```
Building → Floor → Room → Bed
```

### CO_LIVING

```
Building → Unit → Room → Bed
```

### RENTAL (Phase 4.1)

```
Building → Unit
```

No floors, rooms, or beds in the UI for Rental until a later phase.

---

## Key concepts

### `active` (soft delete)

| Field | Meaning |
|-------|---------|
| `active: true` | Visible in lists |
| `active: false` | Deactivated — hidden from GET list endpoints |

Lifecycle actions use explicit `POST` endpoints. **OWNER only** for deactivate, restore, and permanent delete.

| Action | Purpose | HTTP |
|--------|---------|------|
| **Deactivate** | Soft-delete; preserve history | `POST .../{id}/deactivate` → `204` |
| **Restore** | Reactivate a deactivated entity | `POST .../{id}/restore` → `204` |
| **Delete** | Permanent removal (setup cleanup) | `POST .../{id}/delete` → `204` |

Detail GET responses include `actions` metadata:

```json
{
  "actions": {
    "canEdit": true,
    "canDeactivate": true,
    "canRestore": false,
    "canDelete": false,
    "deleteReason": "Cannot delete room because beds exist. Delete beds first."
  }
}
```

Show **Delete** in the UI only when `actions.canDelete === true`. Otherwise show Deactivate only.

**Cascade delete:** Deleting a room, floor, unit, or building automatically removes all descendant beds/rooms in one transaction when the subtree is clean. Users never need to delete children first. See [accommodation-lifecycle-ui-integration.md](./accommodation-lifecycle-ui-integration.md).

### Deactivation rules (show errors from API)

| Parent | Blocked when |
|--------|--------------|
| Building | Active floors **or** active units exist |
| Floor | Active rooms exist |
| Unit | Active rooms exist |
| Room | Active beds exist |

UI: confirm before delete; on `400`, show `message` and prompt user to deactivate children first (bottom-up order: beds → rooms → floors/units → building).

### Accommodation status (Room, Bed, Unit)

Operational availability flag — **not** the same as member occupancy (Phase 4.2).

| Value | UI label | Suggested color |
|-------|----------|-----------------|
| `AVAILABLE` | Available | Green |
| `OCCUPIED` | Occupied | Blue |
| `RESERVED` | Reserved | Purple |
| `MAINTENANCE` | Maintenance | Orange |
| `BLOCKED` | Blocked | Red |

On create, `status` defaults to `AVAILABLE` if omitted (rooms/beds/units). On update, status is required where the update DTO includes it.

### Room type (required on create)

| Value | Label |
|-------|-------|
| `PRIVATE` | Private |
| `SHARED` | Shared |
| `DORMITORY` | Dormitory |

`roomType` is **required** in `CreateRoomRequest` — do not default in the client without user selection.

### Capacity (rooms)

`capacity` = max occupants (integer ≥ 1). Used for future occupancy; display on room cards.

---

## Base URL

| Environment | URL |
|-------------|-----|
| Android emulator | `http://10.0.2.2:8080` |
| iOS simulator | `http://localhost:8080` |
| Physical device | `http://<YOUR_PC_LAN_IP>:8080` |

---

## UI flow (recommended)

```
┌─────────────────────────────┐
│  Space Home / Settings      │
│  (hide tab if spaceType=MESS)│
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│  Accommodation Tree         │  GET /buildings
│  (expandable hierarchy)     │
└──────────────┬──────────────┘
               │
     ┌─────────┴─────────┐
     ▼                   ▼
 PG/HOSTEL            CO_LIVING / RENTAL
 Building             Building
   └ Floor              └ Unit
       └ Room               └ Room (CO_LIVING only)
           └ Bed                └ Bed
```

### Navigation pattern

1. **Buildings list** — entry screen; FAB to add building (OWNER/MANAGER).
2. Tap building → **children list** based on `spaceType`:
   - PG/HOSTEL → floors list
   - CO_LIVING / RENTAL → units list
3. Tap floor → rooms list → tap room → beds list.
4. Tap unit (CO_LIVING) → rooms → beds.
5. Tap unit (RENTAL) → detail/edit only (no room/bed children in 4.1).

### Progressive loading (lazy lists)

**Do not** load the full building tree in one response. Load one level per screen.

| Level | Screen | APIs |
|-------|--------|------|
| 1 | Building overview | `GET .../buildings/{buildingId}/summary` + floors or units list |
| 2 | Floor / unit detail | `GET .../floors/{floorId}/rooms` or `GET .../units/{unitId}/rooms` |
| 3 | Room detail | `GET .../rooms/{roomId}/beds` |

List GETs return **lightweight summary items** in a **paginated** envelope by default (`view=summary`). Full legacy arrays: `view=full`.

Full integration guide: **[accommodation-lazy-loading-ui-integration.md](./accommodation-lazy-loading-ui-integration.md)**

---

## Common response envelope

```json
{
  "success": true,
  "message": "Optional message",
  "data": {},
  "timestamp": "2026-06-11T23:00:00"
}
```

`POST .../deactivate`, `POST .../restore`, `POST .../delete` → `204` empty body.

### Error response

```json
{
  "success": false,
  "message": "Cannot deactivate building while active floors exist. Deactivate floors first.",
  "data": null,
  "timestamp": "2026-06-11T23:00:00"
}
```

### HTTP status codes

| Status | When |
|--------|------|
| `200` | GET, PUT success |
| `201` | POST create |
| `204` | POST deactivate / restore / delete |
| `400` | Validation, MESS space, deactivation blocked, wrong space type for structure |
| `401` | Invalid JWT |
| `403` | Wrong role |
| `404` | Resource not found |

---

## Enums

### SpaceType (from My Spaces)

`PG` | `MESS` | `HOSTEL` | `CO_LIVING` | `RENTAL`

### AccommodationStatus

`AVAILABLE` | `OCCUPIED` | `RESERVED` | `MAINTENANCE` | `BLOCKED`

### RoomType

`PRIVATE` | `SHARED` | `DORMITORY`

---

## TypeScript types

```typescript
export type SpaceType = 'PG' | 'MESS' | 'HOSTEL' | 'CO_LIVING' | 'RENTAL';

export type AccommodationStatus =
  | 'AVAILABLE'
  | 'OCCUPIED'
  | 'RESERVED'
  | 'MAINTENANCE'
  | 'BLOCKED';

export type RoomType = 'PRIVATE' | 'SHARED' | 'DORMITORY';

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  timestamp?: string;
}

// --- Requests ---

export interface CreateBuildingRequest {
  name: string;
  code?: string;
}

export interface UpdateBuildingRequest {
  name: string;
  code?: string;
}

export interface CreateFloorRequest {
  name: string;
  floorNumber: number;
  sortOrder?: number;
}

export interface UpdateFloorRequest {
  name: string;
  floorNumber: number;
  sortOrder: number;
}

export interface CreateUnitRequest {
  name: string;
  unitNumber: string;
  status?: AccommodationStatus;
}

export interface UpdateUnitRequest {
  name: string;
  unitNumber: string;
  status: AccommodationStatus;
}

export interface CreateRoomRequest {
  name: string;
  roomNumber: string;
  roomType: RoomType; // required
  capacity: number;
  status?: AccommodationStatus;
}

export interface UpdateRoomRequest {
  name: string;
  roomNumber: string;
  roomType: RoomType;
  capacity: number;
  status: AccommodationStatus;
}

export interface CreateBedRequest {
  name: string;
  bedNumber: string;
  status?: AccommodationStatus;
}

export interface UpdateBedRequest {
  name: string;
  bedNumber: string;
  status: AccommodationStatus;
}

// --- Responses ---

export interface BuildingResponse {
  buildingId: string;
  spaceId: string;
  name: string;
  code?: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface FloorResponse {
  floorId: string;
  buildingId: string;
  name: string;
  floorNumber: number;
  sortOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UnitResponse {
  unitId: string;
  buildingId: string;
  floorId?: string | null;
  name: string;
  unitNumber: string;
  status: AccommodationStatus;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RoomResponse {
  roomId: string;
  floorId?: string | null;
  unitId?: string | null;
  name: string;
  roomNumber: string;
  roomType: RoomType;
  capacity: number;
  status: AccommodationStatus;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface BedResponse {
  bedId: string;
  roomId: string;
  name: string;
  bedNumber: string;
  status: AccommodationStatus;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

// --- Lazy-loading list types (view=summary) ---

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

### Space-type profile helper (mirror backend rules in UI)

```typescript
export interface AccommodationUiProfile {
  showFloors: boolean;
  showUnits: boolean;
  showRoomsUnderFloor: boolean;
  showRoomsUnderUnit: boolean;
  showBeds: boolean;
}

export function getAccommodationUiProfile(spaceType: SpaceType): AccommodationUiProfile | null {
  switch (spaceType) {
    case 'PG':
    case 'HOSTEL':
      return {
        showFloors: true,
        showUnits: false,
        showRoomsUnderFloor: true,
        showRoomsUnderUnit: false,
        showBeds: true,
      };
    case 'CO_LIVING':
      return {
        showFloors: false,
        showUnits: true,
        showRoomsUnderFloor: false,
        showRoomsUnderUnit: true,
        showBeds: true,
      };
    case 'RENTAL':
      return {
        showFloors: false,
        showUnits: true,
        showRoomsUnderFloor: false,
        showRoomsUnderUnit: false,
        showBeds: false,
      };
    case 'MESS':
      return null;
    default:
      return null;
  }
}
```

---

## Permission matrix

Use `membershipRole` from `GET /spaces/my`.

| Action | OWNER | MANAGER | TENANT / CUSTOMER / STAFF |
|--------|-------|---------|----------------------------|
| View structure (GET) | Yes | Yes | Yes |
| Create / Update | Yes | Yes | No |
| Deactivate / Restore / Delete (POST) | Yes | No | No |

Hide add/edit FABs when role is not OWNER or MANAGER. Hide lifecycle actions for MANAGER. Show **Delete** only when `actions.canDelete` is true on detail GET.

---

## API reference

### Buildings

| Method | Path | Body | Response |
|--------|------|------|----------|
| `POST` | `/api/v1/spaces/{spaceId}/buildings` | `CreateBuildingRequest` | `BuildingResponse` `201` |
| `GET` | `/api/v1/spaces/{spaceId}/buildings` | — | `BuildingResponse[]` `200` |
| `GET` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}` | — | `BuildingResponse` `200` |
| `PUT` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}` | `UpdateBuildingRequest` | `BuildingResponse` `200` |
| `POST` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/deactivate` | — | `204` |
| `POST` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/restore` | — | `204` |
| `POST` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/delete` | — | `204` |

#### Create building — example

```json
POST /api/v1/spaces/{spaceId}/buildings
{
  "name": "Building A",
  "code": "BLD-A"
}
```

```json
{
  "success": true,
  "message": "Building created successfully",
  "data": {
    "buildingId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "spaceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Building A",
    "code": "BLD-A",
    "active": true,
    "createdAt": "2026-06-11T23:00:00",
    "updatedAt": "2026-06-11T23:00:00"
  }
}
```

---

### Floors (PG / Hostel only)

| Method | Path |
|--------|------|
| `POST` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/floors` |
| `GET` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/floors` |
| `GET` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/floors/{floorId}` |
| `PUT` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/floors/{floorId}` |
| `GET` | `/api/v1/spaces/{spaceId}/floors/{floorId}` | Flat detail with `actions` |
| `POST` | `/api/v1/spaces/{spaceId}/floors/{floorId}/deactivate` | |
| `POST` | `/api/v1/spaces/{spaceId}/floors/{floorId}/restore` | |
| `POST` | `/api/v1/spaces/{spaceId}/floors/{floorId}/delete` | |
| `POST` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/floors/{floorId}/deactivate` | Nested alias |
| `POST` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/floors/{floorId}/restore` | |
| `POST` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/floors/{floorId}/delete` | |

#### Create floor — example

```json
{
  "name": "Ground Floor",
  "floorNumber": 0,
  "sortOrder": 0
}
```

| HTTP | `message` (typical) | UI action |
|------|---------------------|-----------|
| `400` | Floors are not supported for CO_LIVING spaces | Hide floors UI for non-PG types |

---

### Units (Co-Living / Rental)

| Method | Path |
|--------|------|
| `POST` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/units` |
| `GET` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/units` |
| `GET` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/units/{unitId}` |
| `PUT` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/units/{unitId}` |
| `GET` | `/api/v1/spaces/{spaceId}/units/{unitId}` | Flat detail with `actions` |
| `POST` | `/api/v1/spaces/{spaceId}/units/{unitId}/deactivate` | |
| `POST` | `/api/v1/spaces/{spaceId}/units/{unitId}/restore` | |
| `POST` | `/api/v1/spaces/{spaceId}/units/{unitId}/delete` | |
| `POST` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/units/{unitId}/deactivate` | Nested alias |
| `POST` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/units/{unitId}/restore` | |
| `POST` | `/api/v1/spaces/{spaceId}/buildings/{buildingId}/units/{unitId}/delete` | |

#### Create unit — example

```json
{
  "name": "Flat 101",
  "unitNumber": "101",
  "status": "AVAILABLE"
}
```

---

### Rooms

| Method | Path | Notes |
|--------|------|-------|
| `POST` | `/api/v1/spaces/{spaceId}/floors/{floorId}/rooms` | PG / Hostel |
| `POST` | `/api/v1/spaces/{spaceId}/units/{unitId}/rooms` | Co-Living only |
| `GET` | `/api/v1/spaces/{spaceId}/floors/{floorId}/rooms` | |
| `GET` | `/api/v1/spaces/{spaceId}/units/{unitId}/rooms` | |
| `GET` | `/api/v1/spaces/{spaceId}/rooms/{roomId}` | |
| `PUT` | `/api/v1/spaces/{spaceId}/rooms/{roomId}` | |
| `POST` | `/api/v1/spaces/{spaceId}/rooms/{roomId}/deactivate` | |
| `POST` | `/api/v1/spaces/{spaceId}/rooms/{roomId}/restore` | |
| `POST` | `/api/v1/spaces/{spaceId}/rooms/{roomId}/delete` | |

#### Create room — example

```json
{
  "name": "Room 101",
  "roomNumber": "101",
  "roomType": "SHARED",
  "capacity": 2,
  "status": "AVAILABLE"
}
```

`roomType` is **required**. Show a picker with PRIVATE / SHARED / DORMITORY.

---

### Beds (PG / Hostel / Co-Living — not Rental)

| Method | Path |
|--------|------|
| `POST` | `/api/v1/spaces/{spaceId}/rooms/{roomId}/beds` |
| `GET` | `/api/v1/spaces/{spaceId}/rooms/{roomId}/beds` |
| `GET` | `/api/v1/spaces/{spaceId}/rooms/{roomId}/beds/{bedId}` |
| `PUT` | `/api/v1/spaces/{spaceId}/rooms/{roomId}/beds/{bedId}` |
| `GET` | `/api/v1/spaces/{spaceId}/beds/{bedId}` | Flat detail with `actions` |
| `POST` | `/api/v1/spaces/{spaceId}/beds/{bedId}/deactivate` | |
| `POST` | `/api/v1/spaces/{spaceId}/beds/{bedId}/restore` | |
| `POST` | `/api/v1/spaces/{spaceId}/beds/{bedId}/delete` | |
| `POST` | `/api/v1/spaces/{spaceId}/rooms/{roomId}/beds/{bedId}/deactivate` | Nested alias |
| `POST` | `/api/v1/spaces/{spaceId}/rooms/{roomId}/beds/{bedId}/restore` | |
| `POST` | `/api/v1/spaces/{spaceId}/rooms/{roomId}/beds/{bedId}/delete` | |

#### Create bed — example

```json
{
  "name": "Bed A",
  "bedNumber": "A",
  "status": "AVAILABLE"
}
```

---

## Client API module

Add to `src/api/accommodationApi.ts` (extend patterns from [membership-ui-integration.md](./membership-ui-integration.md)):

```typescript
import type {
  ApiResponse,
  BedResponse,
  BuildingResponse,
  CreateBedRequest,
  CreateBuildingRequest,
  CreateFloorRequest,
  CreateRoomRequest,
  CreateUnitRequest,
  FloorResponse,
  RoomResponse,
  UnitResponse,
  UpdateBedRequest,
  UpdateBuildingRequest,
  UpdateFloorRequest,
  UpdateRoomRequest,
  UpdateUnitRequest,
} from './types';

// authFetch, parseResponse, API_BASE — reuse from memberApi.ts

export async function getBuildings(spaceId: string): Promise<BuildingResponse[]> {
  const res = await authFetch(`/api/v1/spaces/${spaceId}/buildings`);
  return parseResponse<BuildingResponse[]>(res);
}

export async function createBuilding(
  spaceId: string,
  body: CreateBuildingRequest,
): Promise<BuildingResponse> {
  const res = await authFetch(`/api/v1/spaces/${spaceId}/buildings`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  return parseResponse<BuildingResponse>(res);
}

export async function updateBuilding(
  spaceId: string,
  buildingId: string,
  body: UpdateBuildingRequest,
): Promise<BuildingResponse> {
  const res = await authFetch(`/api/v1/spaces/${spaceId}/buildings/${buildingId}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
  return parseResponse<BuildingResponse>(res);
}

export async function deactivateBuilding(spaceId: string, buildingId: string): Promise<void> {
  const res = await authFetch(`/api/v1/spaces/${spaceId}/buildings/${buildingId}`, {
    method: 'DELETE',
  });
  await parseResponse<void>(res);
}

// Default list GETs return PagedResponse<LightweightItem> (view=summary).
// Pass view: 'full' for legacy FloorResponse[] without pagination.
export async function listFloors(
  spaceId: string,
  buildingId: string,
  params?: ListQueryParams,
): Promise<PagedResponse<FloorListItemResponse>> {
  const q = new URLSearchParams({ view: 'summary', ...params as Record<string, string> });
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/buildings/${buildingId}/floors?${q}`,
  );
  return parseResponse<PagedResponse<FloorListItemResponse>>(res);
}

/** @deprecated Prefer listFloors. Use { view: 'full' } for legacy shape. */
export async function getFloors(spaceId: string, buildingId: string): Promise<FloorResponse[]> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/buildings/${buildingId}/floors?view=full`,
  );
  return parseResponse<FloorResponse[]>(res);
}

export async function createFloor(
  spaceId: string,
  buildingId: string,
  body: CreateFloorRequest,
): Promise<FloorResponse> {
  const res = await authFetch(`/api/v1/spaces/${spaceId}/buildings/${buildingId}/floors`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  return parseResponse<FloorResponse>(res);
}

export async function listUnits(
  spaceId: string,
  buildingId: string,
  params?: ListQueryParams,
): Promise<PagedResponse<UnitListItemResponse>> {
  const q = new URLSearchParams({ view: 'summary', ...params as Record<string, string> });
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/buildings/${buildingId}/units?${q}`,
  );
  return parseResponse<PagedResponse<UnitListItemResponse>>(res);
}

export async function createRoomUnderFloor(
  spaceId: string,
  floorId: string,
  body: CreateRoomRequest,
): Promise<RoomResponse> {
  const res = await authFetch(`/api/v1/spaces/${spaceId}/floors/${floorId}/rooms`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  return parseResponse<RoomResponse>(res);
}

export async function createRoomUnderUnit(
  spaceId: string,
  unitId: string,
  body: CreateRoomRequest,
): Promise<RoomResponse> {
  const res = await authFetch(`/api/v1/spaces/${spaceId}/units/${unitId}/rooms`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  return parseResponse<RoomResponse>(res);
}

export async function listBeds(
  spaceId: string,
  roomId: string,
  params?: ListQueryParams,
): Promise<PagedResponse<BedListItemResponse>> {
  const q = new URLSearchParams({ view: 'summary', ...params as Record<string, string> });
  const res = await authFetch(`/api/v1/spaces/${spaceId}/rooms/${roomId}/beds?${q}`);
  return parseResponse<PagedResponse<BedListItemResponse>>(res);
}

export async function getBuildingSummary(
  spaceId: string,
  buildingId: string,
): Promise<BuildingSummaryResponse> {
  const res = await authFetch(`/api/v1/spaces/${spaceId}/buildings/${buildingId}/summary`);
  return parseResponse<BuildingSummaryResponse>(res);
}

// See accommodation-lazy-loading-ui-integration.md for listRoomsByFloor/Unit,
// searchFloors/Units/Rooms, React Query keys, and screen migration checklist.
```

### Status badge helper

```typescript
export const ACCOMMODATION_STATUS_OPTIONS = [
  { value: 'AVAILABLE', label: 'Available', color: '#22c55e' },
  { value: 'OCCUPIED', label: 'Occupied', color: '#3b82f6' },
  { value: 'RESERVED', label: 'Reserved', color: '#a855f7' },
  { value: 'MAINTENANCE', label: 'Maintenance', color: '#f97316' },
  { value: 'BLOCKED', label: 'Blocked', color: '#ef4444' },
] as const;

export const ROOM_TYPE_OPTIONS = [
  { value: 'PRIVATE', label: 'Private' },
  { value: 'SHARED', label: 'Shared' },
  { value: 'DORMITORY', label: 'Dormitory' },
] as const;
```

### `useBuildings` hook (example)

```typescript
import { useCallback, useEffect, useState } from 'react';
import { getBuildings } from '../api/accommodationApi';
import type { BuildingResponse } from '../api/types';

export function useBuildings(spaceId: string | null) {
  const [buildings, setBuildings] = useState<BuildingResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!spaceId) return;
    setLoading(true);
    setError(null);
    try {
      setBuildings(await getBuildings(spaceId));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load buildings');
    } finally {
      setLoading(false);
    }
  }, [spaceId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { buildings, loading, error, refresh };
}
```

---

## Phase 4.2 — Quick Setup, Builder, Duplicate, Bulk

> **Full Phase 4.2 UI guide:** [accommodation-phase-4.2-ui-integration.md](./accommodation-phase-4.2-ui-integration.md) — TypeScript types, wizard flows, all request/response examples, limits, and frontend checklist.

Orchestration APIs for the Quick Setup wizard and Manual Builder. All Phase 4.1 CRUD endpoints remain unchanged. See [accommodation-flow-redesign.md](./accommodation-flow-redesign.md) for UX flows.

**Permissions:** Quick setup, duplicate, and bulk require **OWNER** or **MANAGER**. Building summary is available to any active space member.

### Quick Setup — preview

```
POST /api/v1/spaces/{spaceId}/accommodation/setup/preview
```

**PG / Hostel body:**

```json
{
  "spaceType": "PG",
  "building": { "name": "Sunrise PG", "code": "SUN-01" },
  "floors": {
    "count": 3,
    "includeGroundFloor": true,
    "roomsPerFloor": 10,
    "bedsPerRoom": 3,
    "defaultRoomType": "SHARED",
    "capacityPerRoom": 3
  }
}
```

**Co-Living body:**

```json
{
  "spaceType": "CO_LIVING",
  "building": { "name": "CoLive Tower" },
  "units": {
    "count": 10,
    "startNumber": "101",
    "numberingStep": 1,
    "roomsPerUnit": 3,
    "bedsPerRoom": 2,
    "defaultRoomType": "PRIVATE",
    "capacityPerRoom": 2
  }
}
```

**Rental body:**

```json
{
  "spaceType": "RENTAL",
  "building": { "name": "Rental Block A" },
  "units": {
    "count": 25,
    "startNumber": "101",
    "defaultStatus": "AVAILABLE"
  }
}
```

**Response `data`:** `AccommodationSetupPreviewResponse` — `totals`, `sample` (tree snippet), `warnings[]`. No persistence.

**Limits:** max 20 floors, max 500 beds per setup. `spaceType` must match the space.

### Quick Setup — execute

```
POST /api/v1/spaces/{spaceId}/accommodation/setup
Header: Idempotency-Key: <uuid>   (required)
```

Same body as preview. **Response `data`:** `AccommodationSetupResultResponse` — `buildingId`, `totals`, `idempotentReplay`. Returns `201` on first run, `200` on replay.

Server generates all floor/room/bed numbers and display names.

### Building summary (Manual Builder header)

```
GET /api/v1/spaces/{spaceId}/buildings/{buildingId}/summary
```

**Response `data`:** `BuildingSummaryResponse` — flattened fields: `buildingId`, `name`, `code`, `spaceId`, `floors`, `units`, `rooms`, `beds`, `available`, `occupied`, `reserved`, `maintenance`, `blocked`.

No tree API — use **paginated lightweight list endpoints** (`listFloors`, `listRoomsByFloor`, `listBeds`, etc.). See [accommodation-lazy-loading-ui-integration.md](./accommodation-lazy-loading-ui-integration.md).

### Duplicate building

```
POST /api/v1/spaces/{spaceId}/buildings/{buildingId}/duplicate
```

```json
{
  "targetBuildingName": "Building B",
  "targetBuildingCode": "BLD-B"
}
```

Clones the full structure into a new building: floors/units → rooms → beds (per space type). PG/Hostel clones floors; Co-Living clones units with rooms and beds; Rental clones units only.

**Response `data`:** `DuplicateBuildingResponse` — `buildingId`, `name`, `code`, `floorsCreated`, `unitsCreated`, `roomsCreated`, `bedsCreated`.

### Duplicate floor

```
POST /api/v1/spaces/{spaceId}/buildings/{buildingId}/floors/{floorId}/duplicate
```

```json
{
  "targetFloorNumber": 2,
  "targetName": "Floor 2",
  "renumberRooms": true
}
```

**Response `data`:** `DuplicateFloorResponse` — `floorId`, `floorNumber`, `roomsCreated`, `bedsCreated`.

### Duplicate room

```
POST /api/v1/spaces/{spaceId}/rooms/{roomId}/duplicate
```

```json
{ "targetRoomNumber": "102" }
```

Omit `targetRoomNumber` to let the server allocate the next free number.

**Response `data`:** `DuplicateRoomResponse` — `roomId`, `roomNumber`, `bedsCreated`.

### Bulk create units

```
POST /api/v1/spaces/{spaceId}/buildings/{buildingId}/units/bulk
```

```json
{
  "count": 25,
  "startUnitNumber": "101",
  "defaultStatus": "AVAILABLE"
}
```

Max 50 units per bulk. Omit `startUnitNumber` for server-side allocation.

**Response `data`:** `BulkCreateUnitsResponse` — `unitsCreated`, `unitIds[]`.

### Bulk create rooms

```
POST /api/v1/spaces/{spaceId}/floors/{floorId}/rooms/bulk
POST /api/v1/spaces/{spaceId}/units/{unitId}/rooms/bulk
```

```json
{
  "count": 5,
  "startRoomNumber": "201",
  "roomType": "SHARED",
  "capacity": 2,
  "bedsPerRoom": 2,
  "defaultStatus": "AVAILABLE"
}
```

Max 50 rooms per bulk. Omit `startRoomNumber` for server-side allocation.

**Response `data`:** `BulkCreateRoomsResponse` — `roomsCreated`, `bedsCreated`, `roomIds[]`.

### Bulk create beds

```
POST /api/v1/spaces/{spaceId}/rooms/{roomId}/beds/bulk
```

```json
{
  "count": 3,
  "labelStyle": "ALPHA"
}
```

`labelStyle`: `ALPHA` or `NUMERIC`. Labels append after existing beds in the room.

**Response `data`:** `BulkCreateBedsResponse` — `bedsCreated`, `bedIds[]`.

---

## Quick reference

| Method | Path | Response `data` | Status |
|--------|------|-----------------|--------|
| `POST` | `.../buildings` | `BuildingResponse` | `201` |
| `GET` | `.../buildings` | `BuildingResponse[]` | `200` |
| `GET` | `.../buildings/{buildingId}` | `BuildingResponse` | `200` |
| `PUT` | `.../buildings/{buildingId}` | `BuildingResponse` | `200` |
| `DELETE` | `.../buildings/{buildingId}` | — | `204` |
| `POST` | `.../buildings/{buildingId}/floors` | `FloorResponse` | `201` |
| `GET` | `.../buildings/{buildingId}/floors` | `PagedResponse<FloorListItemResponse>` (`view=summary`) or `FloorResponse[]` (`view=full`) | `200` |
| `PUT` | `.../buildings/{buildingId}/floors/{floorId}` | `FloorResponse` | `200` |
| `DELETE` | `.../buildings/{buildingId}/floors/{floorId}` | — | `204` |
| `POST` | `.../buildings/{buildingId}/units` | `UnitResponse` | `201` |
| `GET` | `.../buildings/{buildingId}/units` | `PagedResponse<UnitListItemResponse>` or `UnitResponse[]` (`view=full`) | `200` |
| `PUT` | `.../buildings/{buildingId}/units/{unitId}` | `UnitResponse` | `200` |
| `DELETE` | `.../buildings/{buildingId}/units/{unitId}` | — | `204` |
| `POST` | `.../floors/{floorId}/rooms` | `RoomResponse` | `201` |
| `POST` | `.../units/{unitId}/rooms` | `RoomResponse` | `201` |
| `GET` | `.../rooms/{roomId}` | `RoomResponse` | `200` |
| `PUT` | `.../rooms/{roomId}` | `RoomResponse` | `200` |
| `DELETE` | `.../rooms/{roomId}` | — | `204` |
| `POST` | `.../rooms/{roomId}/beds` | `BedResponse` | `201` |
| `GET` | `.../floors/{floorId}/rooms` | `PagedResponse<RoomListItemResponse>` or `RoomResponse[]` (`view=full`) | `200` |
| `GET` | `.../units/{unitId}/rooms` | `PagedResponse<RoomListItemResponse>` or `RoomResponse[]` (`view=full`) | `200` |
| `GET` | `.../rooms/{roomId}/beds` | `PagedResponse<BedListItemResponse>` or `BedResponse[]` (`view=full`) | `200` |
| `GET` | `.../floors?query=` | `PagedResponse<FloorListItemResponse>` | `200` |
| `GET` | `.../units?query=` | `PagedResponse<UnitListItemResponse>` | `200` |
| `GET` | `.../rooms?query=` | `PagedResponse<RoomListItemResponse>` | `200` |
| `PUT` | `.../rooms/{roomId}/beds/{bedId}` | `BedResponse` | `200` |
| `DELETE` | `.../rooms/{roomId}/beds/{bedId}` | — | `204` |
| `POST` | `.../accommodation/setup/preview` | `AccommodationSetupPreviewResponse` | `200` |
| `POST` | `.../accommodation/setup` | `AccommodationSetupResultResponse` | `201` / `200` |
| `GET` | `.../buildings/{buildingId}/summary` | `BuildingSummaryResponse` | `200` |
| `POST` | `.../buildings/{buildingId}/duplicate` | `DuplicateBuildingResponse` | `201` |
| `POST` | `.../buildings/{buildingId}/units/bulk` | `BulkCreateUnitsResponse` | `201` |
| `POST` | `.../buildings/{buildingId}/floors/{floorId}/duplicate` | `DuplicateFloorResponse` | `201` |
| `POST` | `.../rooms/{roomId}/duplicate` | `DuplicateRoomResponse` | `201` |
| `POST` | `.../floors/{floorId}/rooms/bulk` | `BulkCreateRoomsResponse` | `201` |
| `POST` | `.../units/{unitId}/rooms/bulk` | `BulkCreateRoomsResponse` | `201` |
| `POST` | `.../rooms/{roomId}/beds/bulk` | `BulkCreateBedsResponse` | `201` |

---

## Coming in Phase 4.3+

| Feature | Notes |
|---------|-------|
| Allocate member to bed/room/unit | Links `Member` to structure |
| Transfer / vacate | Updates status automatically |
| `occupancyStartDate`, `expectedVacateDate` | On allocation — for rent/billing |
| Availability lists | Available beds, vacant units, filters |
| Dashboard metrics | Occupancy %, counts |

Design navigation and detail screens so an **“Assign member”** action can be added on bed/room/unit rows later.

---

## Related docs

- [my-spaces-ui-integration.md](./my-spaces-ui-integration.md) — `spaceType`, `membershipRole`
- [membership-ui-integration.md](./membership-ui-integration.md) — members
- [member-management-ui-integration.md](./member-management-ui-integration.md) — member profile
- [accommodation-lazy-loading-ui-integration.md](./accommodation-lazy-loading-ui-integration.md) — Progressive loading, pagination, search
- [accommodation-phase-4.2-ui-integration.md](./accommodation-phase-4.2-ui-integration.md) — Phase 4.2 Quick Setup, Builder, Duplicate, Bulk
- [accommodation-lifecycle-ui-integration.md](./accommodation-lifecycle-ui-integration.md) — Deactivate, restore, permanent delete
- [accommodation-navigation-ui-fix.md](./accommodation-navigation-ui-fix.md) — Room/beds 404 & navigation cache fixes
- [accommodation-flow-redesign.md](./accommodation-flow-redesign.md) — Phase 4.2 UX spec
- [development-roadmap.md](./development-roadmap.md) — Phase 4 roadmap
