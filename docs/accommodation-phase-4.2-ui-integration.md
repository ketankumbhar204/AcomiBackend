# Acomi — Accommodation Phase 4.2 UI Integration Guide

Frontend reference for **Quick Setup**, **Manual Builder**, **Duplicate**, and **Bulk** orchestration APIs.

> Phase 4.1 CRUD (create/update/detail) is unchanged. **List GETs** now default to lightweight paginated summaries — see [accommodation-lazy-loading-ui-integration.md](./accommodation-lazy-loading-ui-integration.md). Lifecycle: [accommodation-lifecycle-ui-integration.md](./accommodation-lifecycle-ui-integration.md). CRUD reference: [accommodation-ui-integration.md](./accommodation-ui-integration.md).  
> UX flows and wireframes: [accommodation-flow-redesign.md](./accommodation-flow-redesign.md)

---

## Overview

Phase 4.2 adds **orchestration** endpoints so the UI can:

| Feature | Purpose | Primary screens |
|---------|---------|-----------------|
| **Quick Setup** | Generate a full building in one transaction | `QuickSetupWizardScreen` |
| **Building summary** | Header stats for Manual Builder | `AccommodationBuilderScreen` |
| **Duplicate** | Clone building / floor / room with children | Duplicate sheets |
| **Bulk create** | Add many units / rooms / beds at once | Bulk modals |

All orchestration writes use the **same domain entities** as Phase 4.1 CRUD (`Building`, `Floor`, `Unit`, `Room`, `Bed`). There is no separate “generated” model.

**Not in Phase 4.2:** occupancy, member allocation, availability dashboards, unified tree API — Phase 4.3+.

---

## Prerequisites

| Doc | Use for |
|-----|---------|
| [auth-ui-integration.md](./auth-ui-integration.md) | JWT login, `Authorization` header |
| [my-spaces-ui-integration.md](./my-spaces-ui-integration.md) | `spaceId`, `spaceType`, `membershipRole` |
| [accommodation-ui-integration.md](./accommodation-ui-integration.md) | Phase 4.1 CRUD + enums (`RoomType`, `AccommodationStatus`) |

**Base URL** (same as other modules):

| Environment | URL |
|-------------|-----|
| Android emulator | `http://10.0.2.2:8080` |
| iOS simulator | `http://localhost:8080` |
| Physical device | `http://<YOUR_PC_LAN_IP>:8080` |

**Headers (all Phase 4.2 endpoints):**

```
Authorization: Bearer <accessToken>
Content-Type: application/json
```

**Execute Quick Setup only — additional header:**

```
Idempotency-Key: <uuid-v4>
```

---

## When to show Phase 4.2 features

```typescript
export function isAccommodationApplicable(spaceType: SpaceType): boolean {
  return spaceType !== 'MESS';
}

export function canManageAccommodation(membershipRole: MembershipRole): boolean {
  return membershipRole === 'OWNER' || membershipRole === 'MANAGER';
}
```

| Feature | Show when |
|---------|-----------|
| Accommodation tab | `spaceType !== 'MESS'` |
| Quick Setup, Duplicate, Bulk | `canManageAccommodation(role)` |
| Building summary (read) | Any active space member |
| Manual Builder edit (CRUD) | `canManageAccommodation(role)` — Phase 4.1 |

Hide Quick Setup / Duplicate / Bulk for `TENANT`, `CUSTOMER`, `STAFF`.

---

## Structure by space type (unchanged from 4.1)

| Space type | Hierarchy | Quick Setup generates | Bulk units | Bulk rooms parent | Bulk beds |
|------------|-----------|----------------------|------------|-------------------|-----------|
| `PG` | Building → Floor → Room → Bed | Yes | No | Floor | Room |
| `HOSTEL` | Building → Floor → Room → Bed | Yes | No | Floor | Room |
| `CO_LIVING` | Building → Unit → Room → Bed | Yes | Building | Unit | Room |
| `RENTAL` | Building → Unit | Units only | Building | **Not supported** | **Not supported** |
| `MESS` | Hidden | — | — | — | — |

---

## Permissions

| Action | OWNER | MANAGER | TENANT / CUSTOMER / STAFF |
|--------|-------|---------|----------------------------|
| Quick Setup preview + execute | ✅ | ✅ | ❌ |
| Building summary (GET) | ✅ | ✅ | ✅ |
| Duplicate building / floor / room | ✅ | ✅ | ❌ |
| Bulk units / rooms / beds | ✅ | ✅ | ❌ |
| Phase 4.1 CRUD create/update | ✅ | ✅ | ❌ |
| Phase 4.1 deactivate / restore / delete (POST) | ✅ | ❌ | ❌ |

---

## Limits (enforce in UI before API call)

| Limit | Value | Applies to |
|-------|-------|------------|
| Max floors per Quick Setup | **20** | `floors.count` |
| Max beds per Quick Setup | **500** | Computed: floors×rooms×beds or units×rooms×beds |
| Warning threshold (beds) | **400** | Shown in preview `warnings[]` |
| Max rooms per bulk | **50** | `BulkCreateRoomsRequest.count` |
| Max units per bulk | **50** | `BulkCreateUnitsRequest.count` |
| Max beds per bulk rooms | **500** | `count × bedsPerRoom` |

---

## Server-side numbering (critical for UI)

**Do not** generate or send floor names, room numbers, unit numbers, or bed labels for Quick Setup execute. The backend assigns everything via `AccommodationNumberingService`.

| Entity | Server behavior |
|--------|-----------------|
| **PG/HOSTEL floors** | `floorNumber` 0 = Ground Floor when `includeGroundFloor: true`, else starts at 1 |
| **PG/HOSTEL rooms** | Formula: `(floorIndex + 1) * 100 + roomIndex` → 101–110, 201–210, … |
| **PG/HOSTEL beds** | Labels `A`, `B`, `C`, … (alpha); numeric fallback if >26 in one room |
| **CO_LIVING units** | Numeric from `startNumber` (default `101`), step from `numberingStep` (default `1`) |
| **CO_LIVING rooms** | Letters `A`, `B`, `C`, … per unit |
| **RENTAL units** | Numeric from `startNumber` (default `101`), step `1` |
| **Bulk rooms** | Omit `startRoomNumber` → server allocates next free block on parent |
| **Bulk units** | Omit `startUnitNumber` → server allocates next free block in building |
| **Bulk beds** | Appends after existing beds; `labelStyle`: `ALPHA` or `NUMERIC` |

Display names (`name` fields) are also set server-side: `Room 101`, `Unit 101`, `Bed A`, etc.

---

## Common response envelope

Success (JSON body):

```json
{
  "success": true,
  "message": "Optional human-readable message",
  "data": { },
  "timestamp": "2026-06-12T02:00:00"
}
```

Error:

```json
{
  "success": false,
  "message": "Request spaceType does not match space type",
  "data": null,
  "timestamp": "2026-06-12T02:00:00"
}
```

| HTTP status | Typical cause |
|-------------|---------------|
| `400` | Validation, limits, space type mismatch, duplicate numbers |
| `403` | Wrong role or not a space member |
| `404` | Space, building, floor, room, or unit not found |
| `201` | Resource created (first-time setup execute, duplicate, bulk) |
| `200` | Read success; setup execute **idempotent replay** |

Show `message` from the envelope in alerts/toasts.

---

## TypeScript types

```typescript
type SpaceType = 'PG' | 'HOSTEL' | 'CO_LIVING' | 'RENTAL' | 'MESS';
type RoomType = 'PRIVATE' | 'SHARED' | 'DORMITORY';
type AccommodationStatus = 'AVAILABLE' | 'OCCUPIED' | 'RESERVED' | 'MAINTENANCE' | 'BLOCKED';
type BedLabelStyle = 'ALPHA' | 'NUMERIC';

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T | null;
  timestamp: string;
}

// ─── Quick Setup ───

interface BuildingSetupInput {
  name: string;       // required
  code?: string;
}

interface PgHostelSetupConfig {
  count: number;                    // 1–20
  includeGroundFloor?: boolean;     // default true
  roomsPerFloor: number;            // ≥ 1
  bedsPerRoom: number;              // ≥ 1
  defaultRoomType: RoomType;        // required
  capacityPerRoom: number;          // ≥ 1
}

interface UnitSetupConfig {
  count: number;                    // ≥ 1
  startNumber?: string;             // default "101"
  numberingStep?: number;           // Co-Living only, default 1
  roomsPerUnit?: number;            // Co-Living required
  bedsPerRoom?: number;             // Co-Living required
  defaultRoomType?: RoomType;       // Co-Living required
  capacityPerRoom?: number;         // Co-Living required
  defaultStatus?: AccommodationStatus; // Rental optional, default AVAILABLE
}

interface AccommodationSetupRequest {
  spaceType: SpaceType;             // must match space
  building: BuildingSetupInput;
  floors?: PgHostelSetupConfig;     // PG / HOSTEL only
  units?: UnitSetupConfig;          // CO_LIVING / RENTAL only
}

interface AccommodationSetupTotals {
  floors: number;
  units: number;
  rooms: number;
  beds: number;
}

interface AccommodationSetupSampleNode {
  type: 'FLOOR' | 'UNIT' | 'ROOM' | 'BED' | string;
  label: string;
  number: string;
  children?: AccommodationSetupSampleNode[];
}

interface AccommodationSetupPreviewResponse {
  totals: AccommodationSetupTotals;
  sample: AccommodationSetupSampleNode[];
  warnings: string[];
}

interface AccommodationSetupResultResponse {
  buildingId: string;
  totals: AccommodationSetupTotals;
  idempotentReplay: boolean;
}

// ─── Building summary ───

interface StructureCountsResponse {
  floors: number;
  units: number;
  rooms: number;
  beds: number;
}

interface StatusCountsResponse {
  available: number;
  occupied: number;
  reserved: number;
  maintenance: number;
  blocked: number;
}

interface BuildingSummaryResponse {
  buildingId: string;
  name: string;
  code: string | null;
  spaceId: string;
  counts: StructureCountsResponse;
  statusCounts: StatusCountsResponse;
}

// ─── Duplicate ───

interface DuplicateBuildingRequest {
  targetBuildingName: string;
  targetBuildingCode?: string;
}

interface DuplicateBuildingResponse {
  buildingId: string;
  name: string;
  code: string | null;
  floorsCreated: number;
  unitsCreated: number;
  roomsCreated: number;
  bedsCreated: number;
}

interface DuplicateFloorRequest {
  targetFloorNumber: number;
  targetName?: string;
  renumberRooms?: boolean;          // default true
}

interface DuplicateFloorResponse {
  floorId: string;
  floorNumber: number;
  roomsCreated: number;
  bedsCreated: number;
}

interface DuplicateRoomRequest {
  targetRoomNumber?: string;        // omit → server suggests next free
}

interface DuplicateRoomResponse {
  roomId: string;
  roomNumber: string;
  bedsCreated: number;
}

// ─── Bulk ───

interface BulkCreateUnitsRequest {
  count: number;                    // 1–50
  startUnitNumber?: string;
  defaultStatus?: AccommodationStatus;
}

interface BulkCreateUnitsResponse {
  unitsCreated: number;
  unitIds: string[];
}

interface BulkCreateRoomsRequest {
  count: number;                    // 1–50
  startRoomNumber?: string;
  roomType: RoomType;
  capacity: number;
  bedsPerRoom: number;                // 0 = rooms only
  defaultStatus?: AccommodationStatus;
}

interface BulkCreateRoomsResponse {
  roomsCreated: number;
  bedsCreated: number;
  roomIds: string[];
}

interface BulkCreateBedsRequest {
  count: number;                    // ≥ 1
  labelStyle: BedLabelStyle;
}

interface BulkCreateBedsResponse {
  bedsCreated: number;
  bedIds: string[];
}
```

---

## UI flow — Accommodation Home

```
┌─────────────────────────────┐
│  AccommodationHomeScreen    │
│  GET .../buildings          │
└──────────────┬──────────────┘
               │
     ┌─────────┴─────────┐
     ▼                   ▼
┌─────────────┐   ┌──────────────────┐
│ Quick Setup │   │ Manual Builder   │
│   wizard    │   │ (pick building)  │
└─────────────┘   └────────┬─────────┘
                           │
                           ▼
                  GET .../buildings/{id}/summary
                  + lazy list APIs (Phase 4.1)
```

**Empty state copy (suggested):**

- Title: *No property structure yet*
- Body: *Describe your building in a few steps — floors, rooms, and beds — or add manually.*
- Primary CTA: **Quick Setup** → wizard
- Secondary: **Add building manually** → Phase 4.1 `POST .../buildings`

**Building card (non-empty):**

```
Sunrise PG
90 beds · 3 floors
[Manage →]
```

Use summary endpoint after navigation, or derive from list + summary refresh.

---

## 4.2A — Quick Setup Wizard

### Endpoints

| Step | Method | Path |
|------|--------|------|
| Preview (step 4) | `POST` | `/api/v1/spaces/{spaceId}/accommodation/setup/preview` |
| Execute (step 5) | `POST` | `/api/v1/spaces/{spaceId}/accommodation/setup` |

### Wizard steps (by space type)

**PG / HOSTEL**

1. Building name (+ optional code)
2. Floors: count (1–20), include ground floor toggle
3. Rooms & beds: `roomsPerFloor`, `bedsPerRoom`, `defaultRoomType`, `capacityPerRoom`
4. **Preview** → call preview API; show totals, sample tree, warnings
5. **Generate** → call execute with `Idempotency-Key`; show progress; disable double-submit

**CO_LIVING**

1. Building
2. Units: count, `startNumber`, `numberingStep`
3. Rooms & beds per unit (same fields as PG step 3)
4. Preview → Execute

**RENTAL**

1. Building
2. Units: count, `startNumber`, optional `defaultStatus`
3. Preview → Execute (no rooms/beds)

### Request bodies

**PG example:**

```json
{
  "spaceType": "PG",
  "building": {
    "name": "Sunrise PG",
    "code": "SUN-01"
  },
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

**CO_LIVING example:**

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

**RENTAL example:**

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

### Preview response example

```json
{
  "success": true,
  "data": {
    "totals": {
      "floors": 3,
      "units": 0,
      "rooms": 30,
      "beds": 90
    },
    "sample": [
      {
        "type": "FLOOR",
        "label": "Ground Floor",
        "number": "0",
        "children": [
          {
            "type": "ROOM",
            "label": "Room 101",
            "number": "101",
            "children": [
              { "type": "BED", "label": "Bed A", "number": "A" },
              { "type": "BED", "label": "Bed B", "number": "B" },
              { "type": "BED", "label": "Bed C", "number": "C" }
            ]
          },
          {
            "type": "ROOM",
            "label": "Room 102",
            "number": "102",
            "children": [
              { "type": "BED", "label": "Bed A", "number": "A" }
            ]
          }
        ]
      }
    ],
    "warnings": []
  }
}
```

With 400+ beds, `warnings` may include: *"Approaching the 500-bed limit per setup"*.

### Execute

**Request:** Same JSON as preview.

**Headers:**

```
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

Generate a new UUID v4 per wizard submission attempt. Reuse the same key if retrying after network failure (safe replay).

**Success response (first run — HTTP 201):**

```json
{
  "success": true,
  "message": "Accommodation setup completed successfully",
  "data": {
    "buildingId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "totals": {
      "floors": 3,
      "units": 0,
      "rooms": 30,
      "beds": 90
    },
    "idempotentReplay": false
  }
}
```

**Idempotent replay (HTTP 200):**

```json
{
  "success": true,
  "message": "Setup already completed for this idempotency key",
  "data": {
    "buildingId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "totals": { "floors": 3, "units": 0, "rooms": 30, "beds": 90 },
    "idempotentReplay": true
  }
}
```

**UI after success:** Navigate to Manual Builder for `buildingId` or refresh building list. Show: *"90 beds ready in Sunrise PG"*.

### Quick Setup validation errors (show inline / alert)

| Message (substring) | UI action |
|---------------------|-----------|
| `spaceType does not match` | Refresh space context; reset wizard `spaceType` from `GET /spaces/my` |
| `Idempotency-Key header is required` | Bug — always send header on execute |
| `floors configuration is required` | PG/HOSTEL: show floors step |
| `units configuration is required` | CO_LIVING/RENTAL: show units step |
| `Only floors configuration is allowed` | Remove `units` from PG/HOSTEL payload |
| `Only units configuration is allowed` | Remove `floors` from CO_LIVING/RENTAL payload |
| `exceeds maximum` (floors/beds/units) | Block next step; show limit in form |
| `building with this name already exists` | Prompt different building name |

### Example: preview + execute

```typescript
async function previewSetup(spaceId: string, body: AccommodationSetupRequest) {
  const res = await fetch(
    `${API}/api/v1/spaces/${spaceId}/accommodation/setup/preview`,
    {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(body),
    }
  );
  const json: ApiResponse<AccommodationSetupPreviewResponse> = await res.json();
  if (!json.success) throw new Error(json.message ?? 'Preview failed');
  return json.data!;
}

async function executeSetup(spaceId: string, body: AccommodationSetupRequest, idempotencyKey: string) {
  const res = await fetch(
    `${API}/api/v1/spaces/${spaceId}/accommodation/setup`,
    {
      method: 'POST',
      headers: {
        ...authHeaders(),
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(body),
    }
  );
  const json: ApiResponse<AccommodationSetupResultResponse> = await res.json();
  if (!json.success) throw new Error(json.message ?? 'Setup failed');
  return json.data!;
}
```

**Wizard payload builder:**

```typescript
function buildSetupRequest(
  spaceType: SpaceType,
  building: BuildingSetupInput,
  pgHostel?: PgHostelSetupConfig,
  units?: UnitSetupConfig
): AccommodationSetupRequest {
  const req: AccommodationSetupRequest = { spaceType, building };
  if (spaceType === 'PG' || spaceType === 'HOSTEL') {
    req.floors = pgHostel;
  } else {
    req.units = units;
  }
  return req;
}
```

---

## 4.2B — Manual Builder (summary + progressive lists)

> **Full lazy-loading spec:** [accommodation-lazy-loading-ui-integration.md](./accommodation-lazy-loading-ui-integration.md)

### Building summary

```
GET /api/v1/spaces/{spaceId}/buildings/{buildingId}/summary
```

**Who:** Any active space member.

**Use for:** Builder header and building overview — name, code, totals, status breakdown.

**Response example (flattened counts):**

```json
{
  "success": true,
  "data": {
    "buildingId": "uuid",
    "name": "Sunrise PG",
    "code": "SUN-01",
    "spaceId": "uuid",
    "floors": 3,
    "units": 0,
    "rooms": 30,
    "beds": 90,
    "available": 90,
    "occupied": 0,
    "reserved": 0,
    "maintenance": 0,
    "blocked": 0
  }
}
```

Status fields aggregate **rooms + beds + units** in the building.

### Progressive lists (no unified tree API)

List GETs default to **`view=summary`**: lightweight items in `PagedResponse`. Legacy full arrays: `view=full`.

| Node | Load with | Row fields |
|------|-----------|------------|
| Building (PG/HOSTEL) | `GET .../buildings/{buildingId}/floors` | `name`, `roomCount`, `bedCount`, `available`, `occupied` |
| Building (CO_LIVING/RENTAL) | `GET .../buildings/{buildingId}/units` | `name`, `roomCount`, `bedCount`, `status` |
| Floor | `GET .../floors/{floorId}/rooms` | `name`, `roomType`, `bedCount`, `availableBeds`, `occupiedBeds` |
| Unit (Co-Living) | `GET .../units/{unitId}/rooms` | same as floor rooms |
| Room | `GET .../rooms/{roomId}/beds` | `label`, `status` |

Query params: `query`, `page`, `size`, `sort`. Refresh `summary` + affected list keys after bulk/duplicate/mutations.

### Builder header UI (suggested)

```
Sunrise PG · SUN-01
3 floors · 30 rooms · 90 beds
Available: 90 · Occupied: 0
```

---

## 4.2C — Duplicate

All duplicate endpoints require **OWNER** or **MANAGER**. All are transactional (all-or-nothing).

### Duplicate building

```
POST /api/v1/spaces/{spaceId}/buildings/{buildingId}/duplicate
```

**Request:**

```json
{
  "targetBuildingName": "Building B",
  "targetBuildingCode": "BLD-B"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `targetBuildingName` | Yes | Must be unique among active buildings in space |
| `targetBuildingCode` | No | Optional code on new building |

**Clones (by space type):**

| Space type | Cloned structure |
|------------|------------------|
| PG / HOSTEL | Floors → rooms → beds (same numbers/names as source) |
| CO_LIVING | Units → rooms → beds |
| RENTAL | Units only |

**Response (HTTP 201):**

```json
{
  "success": true,
  "message": "Building duplicated successfully",
  "data": {
    "buildingId": "new-uuid",
    "name": "Building B",
    "code": "BLD-B",
    "floorsCreated": 3,
    "unitsCreated": 0,
    "roomsCreated": 30,
    "bedsCreated": 90
  }
}
```

**UI:** Confirm dialog with source name → target name. On success, navigate to new building in builder or refresh list.

---

### Duplicate floor

```
POST /api/v1/spaces/{spaceId}/buildings/{buildingId}/floors/{floorId}/duplicate
```

**PG / HOSTEL only.**

**Request:**

```json
{
  "targetFloorNumber": 2,
  "targetName": "Floor 2",
  "renumberRooms": true
}
```

| Field | Required | Default | Notes |
|-------|----------|---------|-------|
| `targetFloorNumber` | Yes | — | Must not conflict with existing floor on building |
| `targetName` | No | Auto (`Floor N` / `Ground Floor`) | Display name |
| `renumberRooms` | No | `true` | If true, room numbers use PG formula for target floor |

**Response:**

```json
{
  "success": true,
  "data": {
    "floorId": "uuid",
    "floorNumber": 2,
    "roomsCreated": 10,
    "bedsCreated": 30
  }
}
```

---

### Duplicate room

```
POST /api/v1/spaces/{spaceId}/rooms/{roomId}/duplicate
```

**Request:**

```json
{
  "targetRoomNumber": "102"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `targetRoomNumber` | No | Omit → server picks next free on same floor/unit |

Clones room on **same parent** (floor or unit) with all active beds.

**Response:**

```json
{
  "success": true,
  "data": {
    "roomId": "uuid",
    "roomNumber": "102",
    "bedsCreated": 3
  }
}
```

---

## 4.2D — Bulk operations

All bulk endpoints require **OWNER** or **MANAGER**. Single transaction — partial failure rolls back entirely.

### Bulk create units

```
POST /api/v1/spaces/{spaceId}/buildings/{buildingId}/units/bulk
```

**CO_LIVING and RENTAL only.**

**Request:**

```json
{
  "count": 25,
  "startUnitNumber": "101",
  "defaultStatus": "AVAILABLE"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `count` | Yes | 1–50 |
| `startUnitNumber` | No | Server allocates next free if omitted |
| `defaultStatus` | No | Default `AVAILABLE` |

**Response:**

```json
{
  "success": true,
  "data": {
    "unitsCreated": 25,
    "unitIds": ["uuid", "..."]
  }
}
```

Server sets `name` to `Unit 101`, `Unit 102`, …

---

### Bulk create rooms

**Under floor (PG / HOSTEL):**

```
POST /api/v1/spaces/{spaceId}/floors/{floorId}/rooms/bulk
```

**Under unit (CO_LIVING):**

```
POST /api/v1/spaces/{spaceId}/units/{unitId}/rooms/bulk
```

**Request:**

```json
{
  "count": 5,
  "startRoomNumber": "201",
  "roomType": "SHARED",
  "capacity": 3,
  "bedsPerRoom": 3,
  "defaultStatus": "AVAILABLE"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `count` | Yes | 1–50 |
| `startRoomNumber` | No | PG: numeric block; Co-Living: letter block if provided |
| `roomType` | Yes | `PRIVATE` \| `SHARED` \| `DORMITORY` |
| `capacity` | Yes | ≥ 1 |
| `bedsPerRoom` | Yes | ≥ 0; if > 0 creates beds per room in same transaction |
| `defaultStatus` | No | Default `AVAILABLE` |

**Not allowed:** bulk rooms with `bedsPerRoom > 0` on **RENTAL** spaces (400).

**Response:**

```json
{
  "success": true,
  "data": {
    "roomsCreated": 5,
    "bedsCreated": 15,
    "roomIds": ["uuid", "..."]
  }
}
```

---

### Bulk create beds

```
POST /api/v1/spaces/{spaceId}/rooms/{roomId}/beds/bulk
```

**PG, HOSTEL, CO_LIVING only** (not Rental).

**Request:**

```json
{
  "count": 3,
  "labelStyle": "ALPHA"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `count` | Yes | ≥ 1 |
| `labelStyle` | Yes | `ALPHA` or `NUMERIC` |

Labels append **after** existing beds in the room (no overwrite).

**Response:**

```json
{
  "success": true,
  "data": {
    "bedsCreated": 3,
    "bedIds": ["uuid", "..."]
  }
}
```

---

## Complete Phase 4.2 endpoint reference

| Method | Path | Response `data` | HTTP |
|--------|------|-----------------|------|
| `POST` | `.../accommodation/setup/preview` | `AccommodationSetupPreviewResponse` | 200 |
| `POST` | `.../accommodation/setup` | `AccommodationSetupResultResponse` | 201 / 200 |
| `GET` | `.../buildings/{buildingId}/summary` | `BuildingSummaryResponse` | 200 |
| `POST` | `.../buildings/{buildingId}/duplicate` | `DuplicateBuildingResponse` | 201 |
| `POST` | `.../buildings/{buildingId}/units/bulk` | `BulkCreateUnitsResponse` | 201 |
| `POST` | `.../buildings/{buildingId}/floors/{floorId}/duplicate` | `DuplicateFloorResponse` | 201 |
| `POST` | `.../rooms/{roomId}/duplicate` | `DuplicateRoomResponse` | 201 |
| `POST` | `.../floors/{floorId}/rooms/bulk` | `BulkCreateRoomsResponse` | 201 |
| `POST` | `.../units/{unitId}/rooms/bulk` | `BulkCreateRoomsResponse` | 201 |
| `POST` | `.../rooms/{roomId}/beds/bulk` | `BulkCreateBedsResponse` | 201 |

Path prefix: `/api/v1/spaces/{spaceId}`

---

## Screen checklist for frontend

| Screen / component | APIs to wire |
|--------------------|--------------|
| `AccommodationHomeScreen` | `GET .../buildings`; CTAs to wizard / manual |
| `QuickSetupWizardScreen` | preview + execute; store `Idempotency-Key` in wizard state |
| `AccommodationBuilderScreen` | `GET .../summary`; `listFloors` / `listUnits` / `listRooms*` / `listBeds` (`view=summary`) |
| Building overview screen | `getBuildingSummary` + paginated floor/unit list |
| Floor / unit detail screens | `listRoomsByFloor` / `listRoomsByUnit` |
| Room detail screen | `listBeds` |
| Duplicate building sheet | `POST .../buildings/{id}/duplicate` |
| Duplicate floor sheet | `POST .../floors/{id}/duplicate` |
| Duplicate room action | `POST .../rooms/{id}/duplicate` |
| Bulk units modal | `POST .../units/bulk` |
| Bulk rooms modal | `POST .../floors/{id}/rooms/bulk` or `.../units/{id}/rooms/bulk` |
| Bulk beds modal | `POST .../rooms/{id}/beds/bulk` |

---

## Error handling patterns

```typescript
async function apiPost<T>(url: string, body: unknown, extraHeaders?: Record<string, string>): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { ...authHeaders(), ...extraHeaders },
    body: JSON.stringify(body),
  });
  const json: ApiResponse<T> = await res.json();
  if (!json.success) {
    throw new Error(json.message ?? `Request failed (${res.status})`);
  }
  return json.data!;
}
```

| Scenario | UX |
|----------|-----|
| Setup validation on preview | Inline on wizard step; do not call execute |
| Setup execute 400 | Alert with `message`; keep wizard open |
| Duplicate number conflict | Show backend message; let user change target number/name |
| Bulk exceeds limit | Validate client-side before POST; show limit hint |
| 403 on orchestration | Hide action for role; or show “Owner/Manager only” |
| Network error on execute | Retry with **same** `Idempotency-Key` |

---

## Frontend implementation prompt (copy to AI / ticket)

> Integrate Acomi Accommodation Phase 4.2 for space `{spaceId}` with `spaceType` from current space context.
>
> 1. **Quick Setup wizard** — multi-step form per space type; step 4 calls `POST .../accommodation/setup/preview`; step 5 calls `POST .../accommodation/setup` with header `Idempotency-Key: uuid`. Do not send room/floor/unit/bed numbers — server generates all. Show preview totals, expandable `sample` tree, and `warnings`. On success navigate to builder with returned `buildingId`.
>
> 2. **Manual Builder / Building overview** — `GET .../summary` for header. Expand with `listFloors` / `listUnits` / `listRooms*` / `listBeds` (`view=summary`, paginated). No tree API. See `accommodation-lazy-loading-ui-integration.md`.
>
> 3. **Duplicate** — building (new name/code), floor (`targetFloorNumber`, `renumberRooms`), room (optional `targetRoomNumber`). OWNER/MANAGER only.
>
> 4. **Bulk** — units on building (max 50), rooms on floor or unit (max 50, optional beds per room), beds on room (`ALPHA`/`NUMERIC`). Respect Rental restrictions (units bulk only).
>
> 5. Use TypeScript types from `accommodation-phase-4.2-ui-integration.md`. Handle `ApiResponse` envelope and display `message` on errors.
>
> Reference: Phase 4.1 CRUD in `accommodation-ui-integration.md`.

---

## Related docs

- [accommodation-lazy-loading-ui-integration.md](./accommodation-lazy-loading-ui-integration.md) — Progressive loading, pagination, search
- [accommodation-ui-integration.md](./accommodation-ui-integration.md) — Phase 4.1 CRUD + shared enums
- [accommodation-flow-redesign.md](./accommodation-flow-redesign.md) — product UX spec
- [my-spaces-ui-integration.md](./my-spaces-ui-integration.md) — space context
- [development-roadmap.md](./development-roadmap.md) — Phase 4 roadmap
