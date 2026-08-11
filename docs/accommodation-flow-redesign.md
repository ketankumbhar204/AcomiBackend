# Amico — Accommodation Flow Redesign

Product and technical specification for redesigning **Phase 4 Accommodation Management** UX.

> **Status:** Approved direction for implementation planning  
> **Supersedes (UX only):** Drill-down list navigation as the **primary** accommodation experience described in [accommodation-ui-integration.md](./accommodation-ui-integration.md)  
> **Does not supersede:** Domain model, entity APIs, space-type rules, permissions, or deactivate hierarchy  
> **Roadmap context:** [development-roadmap.md](./development-roadmap.md) — Phase 4 Property Structure → Occupancy → Availability → Dashboard

---

## Table of contents

1. [Executive summary](#1-executive-summary)
2. [Problem statement](#2-problem-statement)
3. [Goals and non-goals](#3-goals-and-non-goals)
4. [Domain model (unchanged)](#4-domain-model-unchanged)
5. [Dual-entry product model](#5-dual-entry-product-model)
6. [Approach A — Quick Setup Wizard](#6-approach-a--quick-setup-wizard)
7. [Approach B — Manual Builder](#7-approach-b--manual-builder)
8. [Relationship to current Phase 4.1 UI](#8-relationship-to-current-phase-41-ui)
9. [Naming and numbering conventions](#9-naming-and-numbering-conventions)
10. [Mobile UI specification](#10-mobile-ui-specification)
11. [Permissions](#11-permissions)
12. [Backend architecture](#12-backend-architecture)
13. [API specification (conceptual)](#13-api-specification-conceptual)
14. [Duplication and bulk operations](#14-duplication-and-bulk-operations)
15. [Occupancy and future phases](#15-occupancy-and-future-phases)
16. [Implementation roadmap](#16-implementation-roadmap)
17. [Risks and mitigations](#17-risks-and-mitigations)
18. [Acceptance criteria](#18-acceptance-criteria)
19. [Open questions](#19-open-questions)
20. [Related documents](#20-related-documents)

---

## 1. Executive summary

Amico’s accommodation **data model** (Building → Floor/Unit → Room → Bed) is correct for PG, Hostel, Co-Living, and Rental operations and aligns with the Phase 4 roadmap.

The **current UI** (separate list screens and forms per entity, deep stack navigation) is optimized for browsing and CRUD purity, not for how owners **describe** or **provision** property inventory. A typical PG (3 floors × 10 rooms × 3 beds) requires **~124 separate create actions** and hundreds of taps.

**Recommendation:** Adopt a **dual-entry** accommodation experience:

| Entry | Role | Primary users |
|-------|------|----------------|
| **Quick Setup Wizard** | Describe property → generate full structure in one flow | New spaces, new buildings, symmetric layouts |
| **Manual Builder** | Single-screen accordion tree; duplicate, bulk add, inline edit | Day-2 management, exceptions, power users |

**Drill-down list stacks** (Buildings → Floors → Rooms → Beds) are **demoted** to secondary/deep-link paths, not removed from the codebase immediately.

**Backend:** Keep existing per-entity CRUD. Add a **structure setup** orchestration layer (transactional endpoint) for Quick Setup and bulk/duplicate commands for the Manual Builder.

---

## 2. Problem statement

### 2.1 Current flow (Phase 4.1 implemented)

```
Accommodation (tab)
  → Buildings list
    → Floors / Units list
      → Rooms list
        → Beds list
          → Detail / Form (per entity)
```

### 2.2 Operator pain

| Property size | Example creates | Estimated taps |
|---------------|-----------------|----------------|
| Small PG | 2 floors × 8 rooms × 2 beds | ~50+ |
| Medium PG | 3 × 10 × 3 | ~124 |
| Large hostel | 5 × 20 × 4 | ~505 |

Each create requires: navigate → FAB → form (often **name + number**) → save → back → repeat.

### 2.3 Mental model mismatch

Owners say:

> “Sunrise PG, 3 floors, 10 rooms per floor, triple sharing.”

They do not say:

> “Create Floor entity, then Room entity 101, then Bed entities A, B, C…”

### 2.4 Downstream impact

Phase 4.2+ features depend on accurate structure:

- Allocate member → bed/room/unit
- Available beds / vacant units
- Dashboard occupancy %

If setup is skipped or wrong, **occupancy and metrics ship on bad data**.

---

## 3. Goals and non-goals

### 3.1 Goals

- Reduce first-time structure setup from **hours** to **minutes** for typical properties.
- Provide **one-screen building management** (Manual Builder) without deep navigation.
- Support **symmetric layouts** via Quick Setup and **asymmetric layouts** via Builder.
- **Auto-derive display names** from numbers (single visible field per level where possible).
- Reuse **existing entity APIs and domain rules** — no parallel inventory model.
- Prepare for Phase 4.2 occupancy without redesigning structure UX again.

### 3.2 Non-goals (this redesign)

- Occupancy (allocate / transfer / vacate) — Phase 4.2+
- Availability list screens — Phase 4.2+
- Dashboard metrics — Phase 4.2+
- CSV / Excel import — later enhancement
- Rent, billing, meals — out of Phase 4 scope per roadmap
- Mess accommodation — tab remains hidden

---

## 4. Domain model (unchanged)

### 4.1 Space-type hierarchies

| Space type | Structure | Occupancy target (roadmap) |
|------------|-----------|----------------------------|
| **PG** | Building → Floor → Room → Bed | Member → Bed |
| **HOSTEL** | Building → Floor → Room → Bed | Member → Bed |
| **CO_LIVING** | Building → Unit → Room → Bed | Member → Bed or Room |
| **RENTAL** | Building → Unit | Member → Unit |
| **MESS** | N/A | N/A |

### 4.2 Entities and key fields

| Entity | Identifier fields | Display | Status |
|--------|-------------------|---------|--------|
| Building | `buildingId`, optional `code` | `name` | `active` |
| Floor | `floorId`, `floorNumber`, `sortOrder` | `name` | `active` |
| Unit | `unitId`, `unitNumber` | `name` | `status`, `active` |
| Room | `roomId`, `roomNumber`, `roomType`, `capacity` | `name` | `status`, `active` |
| Bed | `bedId`, `bedNumber` | `name` | `status`, `active` |

Reference: [accommodation-ui-integration.md](./accommodation-ui-integration.md), [development-roadmap.md](./development-roadmap.md).

### 4.3 Deactivation rules (unchanged)

Deactivate children before parents (bottom-up):

```
Beds → Rooms → Floors / Units → Building
```

OWNER only for DELETE/deactivate. Show backend `400` message verbatim.

---

## 5. Dual-entry product model

### 5.1 Accommodation Home — two entry points

```
┌─────────────────────────────────────────────────────────┐
│  Accommodation                                           │
├─────────────────────────────────────────────────────────┤
│  [ Quick Setup ]          ← primary when empty / add    │
│  [ Open Manual Builder ]  ← secondary / after setup     │
├─────────────────────────────────────────────────────────┤
│  Building cards (when exist)                           │
│    Sunrise PG — 90 beds · 12 available (future)        │
│    → tap opens Manual Builder                          │
└─────────────────────────────────────────────────────────┘
```

### 5.2 When to use which path

| Scenario | Recommended path |
|----------|------------------|
| First property setup | **Quick Setup** |
| Add second building (symmetric) | **Quick Setup** |
| Add one room to one floor | **Manual Builder** (inline +) |
| Copy Floor 1 layout to Floor 2 | **Manual Builder** (duplicate floor) |
| Rename / status change / deactivate | **Manual Builder** node menu |
| Odd layout (floor 2 has fewer rooms) | **Manual Builder** |
| Rental: 25 identical units | **Quick Setup** |

### 5.3 Primary vs secondary (product decision)

| | Decision |
|--|----------|
| **Onboarding** | Quick Setup is **primary** CTA on empty state |
| **Ongoing management** | Manual Builder is **primary** workspace per building |
| **Drill-down lists** | **Secondary** — deprecated as default; keep for compatibility / deep links |

---

## 6. Approach A — Quick Setup Wizard

### 6.1 Purpose

Fast onboarding: user describes property dimensions → system generates complete hierarchy.

### 6.2 Wizard shell

- **Presentation:** Full-screen modal stack or dedicated wizard navigator (4–5 steps).
- **Progress:** Step indicator + back/next.
- **Final step:** Preview + confirm (no silent generate).
- **Post-success:** Navigate to **Manual Builder** for generated building (expanded tree), show success toast with totals.

### 6.3 PG / HOSTEL — steps and fields

#### Step 1 — Building

| Field | Required | Default | Notes |
|-------|----------|---------|-------|
| Building name | Yes | — | e.g. `Sunrise PG` |
| Building code | No | — | e.g. `BLD-A` |

#### Step 2 — Floors

| Field | Required | Default | Notes |
|-------|----------|---------|-------|
| Number of floors | Yes | — | Integer ≥ 1 |
| Include ground floor | No | Yes | If yes, floor numbers start at 0; else start at 1 |
| Same room count per floor | Yes (v1) | Yes | v2: per-floor overrides |

#### Step 3 — Rooms and beds

| Field | Required | Default | Notes |
|-------|----------|---------|-------|
| Rooms per floor | Yes | — | Integer ≥ 1 |
| Beds per room | Yes | — | Integer ≥ 1 |
| Default room type | Yes | `SHARED` (PG), `DORMITORY` (HOSTEL) | User must confirm; no hidden default on first visit |
| Capacity per room | Yes | Same as beds per room | Must be ≥ 1 |

#### Step 4 — Preview

Display computed summary:

```
Building: Sunrise PG
Floors: 3
Rooms per floor: 10
Beds per room: 3
─────────────────
Total rooms: 30
Total beds: 90
```

Expandable sample tree (first floor snippet):

```
Floor 1 (Ground)
  Room 101 — Bed A, B, C
  Room 102 — Bed A, B, C
  …
```

#### Step 5 — Generate

- Show progress: `Creating floor 2 of 3…`
- Disable double-submit (idempotency key).
- On success: `90 beds ready in Sunrise PG`.

### 6.4 PG / HOSTEL — generated structure example

**Input:** Sunrise PG, 3 floors, 10 rooms/floor, 3 beds/room

```
Sunrise PG
├── Floor 1 (floorNumber: 0 or 1 per toggle)
│   ├── Room 101 — Bed A, Bed B, Bed C
│   ├── Room 102 — Bed A, Bed B, Bed C
│   └── … Room 110
├── Floor 2
│   └── Room 201 … 210
└── Floor 3
    └── Room 301 … 310
```

### 6.5 CO_LIVING — steps and fields

#### Step 1 — Building

Same as PG.

#### Step 2 — Units

| Field | Required | Default |
|-------|----------|---------|
| Number of units | Yes | — |
| Unit numbering start | No | `101` |
| Unit numbering step | No | `1` |

#### Step 3 — Rooms and beds

| Field | Required | Default |
|-------|----------|---------|
| Rooms per unit | Yes | — |
| Beds per room | Yes | — |
| Default room type | Yes | `PRIVATE` typical |
| Capacity per room | Yes | = beds per room |

#### Step 4 — Preview & generate

```
Building: CoLive Tower
Units: 10
Rooms per unit: 3
Beds per room: 2
─────────────────
Total rooms: 30
Total beds: 60
```

**Generated example:**

```
CoLive Tower
├── Unit 101
│   ├── Room A — Bed 1, Bed 2
│   ├── Room B — Bed 1, Bed 2
│   └── Room C — Bed 1, Bed 2
├── Unit 102
└── … Unit 110
```

### 6.6 RENTAL — steps and fields

#### Step 1 — Building

| Field | Required |
|-------|----------|
| Building name | Yes |
| Building code | No |

#### Step 2 — Units

| Field | Required | Default |
|-------|----------|---------|
| Number of units | Yes | — |
| Unit numbering start | No | `101` |
| Default unit status | No | `AVAILABLE` |

No floors, rooms, or beds in v1.

#### Step 3 — Preview & generate

```
Building: Rental Block A
Units: 25
─────────────────
Unit 101 … Unit 125
```

### 6.7 Quick Setup — UX rules

1. **Never ask for separate `name` and `number`** on wizard steps — derive names (see [§9](#9-naming-and-numbering-conventions)).
2. **Preview is mandatory** before persist.
3. **Show total bed/unit count** prominently.
4. **Hostel** uses same wizard as PG with copy and default room type `DORMITORY`.
5. **MESS** — wizard not shown (no Accommodation tab).
6. **Failure** — if partial failure (client batch), show what succeeded and offer retry; prefer server transactional setup to avoid this.

---

## 7. Approach B — Manual Builder

### 7.1 Purpose

Advanced editing and custom structures on **one screen per building** — no Floors → Rooms → Beds navigation stack.

### 7.2 Builder layout

```
┌─────────────────────────────────────────────────────────┐
│ ← Sunrise PG                    [Edit building] [⋮]      │
│ 3 floors · 30 rooms · 90 beds · 90 available (future)   │
│ [ Search rooms, beds… ]                                  │
├─────────────────────────────────────────────────────────┤
│ ▼ Floor 1 — Ground          [Duplicate] [+ Room] [⋮]    │
│   ▼ Room 101  SHARED · 3 beds    [Duplicate] [⋮]      │
│       Bed A   AVAILABLE                                  │
│       Bed B   AVAILABLE                                  │
│       Bed C   AVAILABLE                                  │
│       [ + Add bed ]  [ Add 3 beds ]                      │
│   ▶ Room 102 …                                           │
│   [ + Add room ]  [ Add 10 rooms ]                       │
├─────────────────────────────────────────────────────────┤
│ ▶ Floor 2                                                │
│ ▶ Floor 3                                                │
├─────────────────────────────────────────────────────────┤
│ [ + Add floor ]                                          │
└─────────────────────────────────────────────────────────┘
```

**CO_LIVING:** Replace Floor with Unit.  
**RENTAL:** Units only; no room/bed nodes.

### 7.3 UI pattern

| Pattern | Usage |
|---------|--------|
| **Accordion** | Primary — expand/collapse floor/unit → room → bed |
| **Nested cards** | Visual grouping; indent levels |
| **Bottom sheet / modal** | Edit entity, bulk add, duplicate confirm |
| **Context menu (⋮)** | Edit, Duplicate, Deactivate (owner), Change status |
| **Sticky search** | Filter visible nodes within building |

Avoid desktop-only “builder canvas” in v1.

### 7.4 Inline actions

| Level | Actions |
|-------|---------|
| **Building** | Edit name/code, Quick add wing (wizard), Deactivate (owner) |
| **Floor / Unit** | + Add child, Duplicate, Bulk add rooms (floor), Deactivate |
| **Room** | + Add bed, Duplicate room, Bulk add beds, Edit type/capacity/status |
| **Bed** | Edit, Change status, Deactivate |

### 7.5 Duplicate operations

#### Duplicate floor (PG/Hostel)

- **Input:** Source floor ID, target floor number/name.
- **Output:** New floor with cloned room and bed structure.
- **Renumbering:** Rooms on new floor use next floor digit pattern (101→201) or user-confirmed pattern.
- **Confirm:** Preview room/bed count before API call.

#### Duplicate room

- **Input:** Source room ID.
- **Output:** New room on same parent with cloned beds.
- **Renumbering:** Next free `roomNumber` on parent.

#### Bulk add rooms

- **Input:** Parent (floor or unit), count N, optional start number.
- **Output:** N rooms with default type, capacity, beds per room (optional).

#### Bulk add beds

- **Input:** Room ID (or “all rooms on floor”), count K.
- **Output:** Beds A…K or 1…K per convention.

### 7.6 Manual single-entity add (within builder)

Equivalent to current forms but **modal**, not stack screen:

- **Save** — close modal, refresh node
- **Save & add another** — clear form, same parent context (reduces taps for manual path)

### 7.7 Builder data loading

- On open: `GET .../buildings/{buildingId}/summary` only (flattened counts + status).
- **Progressive load** one level per screen — paginated lightweight list APIs (`view=summary`). See [accommodation-lazy-loading-ui-integration.md](./accommodation-lazy-loading-ui-integration.md).
- Do **not** hold the full building tree in React state.
- Cache per-level React Query keys; invalidate summary + affected lists on edit/deactivate/bulk/duplicate.

### 7.8 Rental builder

```
Rental Block A
├── Unit 101  AVAILABLE   [Edit] [⋮]
├── Unit 102  OCCUPIED
└── …
[ + Add unit ]  [ Add 10 units ]
```

No room/bed UI for Rental in Phase 4.1 redesign scope.

---

## 8. Relationship to current Phase 4.1 UI

### 8.1 What exists today (implemented)

| Component | Status |
|-----------|--------|
| Accommodation tab (hidden for MESS) | ✅ |
| Buildings list (`AccommodationHomeScreen`) | ✅ |
| Drill-down: `Floors`, `Units`, `AccommodationRooms`, `AccommodationBeds` | ✅ |
| Detail screens per entity | ✅ |
| Form screens (`*FormScreen`, create/edit mode) | ✅ |
| Per-entity CRUD API (`accommodationApi`) | ✅ |
| Client-side search on lists | ✅ |
| Permissions (view / create-edit / deactivate) | ✅ |

### 8.2 Migration plan

| Phase | UI change |
|-------|-------------|
| **4.1.5** | Empty-state CTAs; auto-naming on forms; “Save & add another” |
| **4.2a** | Quick Setup wizard |
| **4.2b** | Manual Builder replaces drill-down as default building entry |
| **4.2c** | Deprecate stack routes (keep for deep links, remove from primary flows) |
| **4.2d** | Backend setup + duplicate/bulk endpoints |

Drill-down screens may remain in codebase until Builder parity is verified.

### 8.3 Navigation after redesign

```
SpaceTabs
  └── Accommodation (tab)
        ├── Empty → Quick Setup CTA
        ├── Building list
        │     └── tap → Manual Builder (replaces Floors/Units entry)
        └── Quick Setup (FAB or header)

MainStack (secondary)
  ├── *Form modals may remain as stack screens initially
  ├── QuickSetupWizard (new)
  └── Legacy: Floors, Units, AccommodationRooms, AccommodationBeds (deprecated)
```

---

## 9. Naming and numbering conventions

### 9.1 Principle

**User enters one primary identifier; system sets `name` and number fields.**

### 9.2 Defaults

| Entity | User-facing field | `name` (auto) | Number field |
|--------|-------------------|---------------|--------------|
| Building | Building name | same | `code` optional |
| Floor | Floor label or number | `Ground Floor` / `Floor 1` | `floorNumber` |
| Unit | Unit number | `Unit {unitNumber}` | `unitNumber` |
| Room | Room number | `Room {roomNumber}` | `roomNumber` |
| Bed | Bed label | `Bed {bedNumber}` | `bedNumber` |

### 9.3 PG room numbering (Quick Setup)

| Floor | Room numbers (10 rooms, start 101) |
|-------|-------------------------------------|
| Floor 1 | 101–110 |
| Floor 2 | 201–210 |
| Floor 3 | 301–310 |

**Formula (v1):** `roomNumber = (floorIndex * 100) + roomIndex`  
Configurable in v2.

### 9.4 Bed labels

| Beds per room | Labels |
|---------------|--------|
| 1–26 | A, B, C, … Z |
| > 26 | 1, 2, 3, … |

### 9.5 Co-living room labels

Per unit: `A`, `B`, `C` or `1`, `2`, `3` — pick one convention per space in settings (v2); default `A/B/C` in v1.

### 9.6 Advanced override

Manual Builder “Edit” modal may expose **display name** as optional advanced field without requiring it on create.

---

## 10. Mobile UI specification

### 10.1 Screen inventory (target state)

| Screen | Type | Description |
|--------|------|-------------|
| `AccommodationHomeScreen` | Tab | Building list + CTAs |
| `QuickSetupWizardScreen` | Stack/modal | Multi-step generator |
| `AccommodationBuilderScreen` | Stack | Per-building accordion tree |
| `AccommodationEntitySheet` | Modal | Edit/create single entity |
| `AccommodationBulkSheet` | Modal | Bulk rooms/beds |
| `AccommodationDuplicateSheet` | Modal | Duplicate floor/room preview |

Legacy screens retained but not linked from primary flows until deprecation.

### 10.2 Empty state copy (Accommodation Home)

**Title:** No property structure yet  
**Body:** Describe your building in a few steps — floors, rooms, and beds — or add manually.  
**Primary CTA:** Quick Setup  
**Secondary CTA:** Add building manually  

### 10.3 Building card (non-empty)

```
Sunrise PG
90 beds · 3 floors
[Manage →]
```

### 10.4 Loading and progress

| Operation | UX |
|-----------|-----|
| Quick Setup generate | Full-screen progress with counts |
| Builder expand node | Skeleton inline |
| Duplicate floor | Progress overlay + preview |

### 10.5 Error handling

| Case | UX |
|------|-----|
| Setup validation error | Inline on wizard step |
| Setup `400` | Alert with backend `message` |
| Deactivate blocked | Backend message + child-order hint |
| Partial batch failure | “Created 2 of 3 floors” + retry failed portion |

### 10.6 Accessibility

- Expand/collapse: min 44pt touch targets
- Status badges: color + text label (not color-only)
- Screen reader: announce level (Floor 1, Room 101, Bed A)

---

## 11. Permissions

Unchanged from [accommodation-ui-integration.md](./accommodation-ui-integration.md) and roadmap.

| Action | OWNER | MANAGER | TENANT / CUSTOMER / STAFF |
|--------|-------|---------|----------------------------|
| View structure | ✅ | ✅ | ✅ |
| Quick Setup | ✅ | ✅ | ❌ |
| Manual Builder edit | ✅ | ✅ | ❌ |
| Create / update entity | ✅ | ✅ | ❌ |
| Duplicate / bulk add | ✅ | ✅ | ❌ |
| Deactivate | ✅ | ❌ | ❌ |

Hide CTAs when role insufficient — do not show disabled deactivate to managers.

---

## 12. Backend architecture

### 12.1 Layers

```
┌──────────────────────────────────────────────┐
│  UI: Quick Setup Wizard / Manual Builder      │
└────────────────────┬─────────────────────────┘
                     │
┌────────────────────▼─────────────────────────┐
│  Orchestration                                │
│  • AccommodationSetupService                  │
│  • AccommodationDuplicateService              │
│  • AccommodationBulkService                   │
└────────────────────┬─────────────────────────┘
                     │
┌────────────────────▼─────────────────────────┐
│  Entity services (existing)                   │
│  Building, Floor, Unit, Room, Bed             │
└────────────────────┬─────────────────────────┘
                     │
┌────────────────────▼─────────────────────────┐
│  Persistence                                  │
└──────────────────────────────────────────────┘
```

### 12.2 Design principles

1. **Single domain model** — wizard and builder persist the same entities as manual CRUD.
2. **Setup is transactional** — all-or-nothing for Quick Setup (preferred).
3. **Idempotency** — `Idempotency-Key` header on setup to prevent duplicate buildings on double-tap.
4. **Space-type validation** — server rejects floors for RENTAL, beds for RENTAL, etc.
5. **Numbering server-side** — reduces collision risk vs client loops.
6. **Audit** — log setup job with counts and `changedBy` for support.

### 12.3 Client-only fallback (interim)

Before setup endpoint ships:

- Client orchestrates existing CRUD in nested loops.
- Show progress UI; on failure, report partial state.
- **Not recommended** for production properties > 50 beds.

---

## 13. API specification (conceptual)

> Existing per-entity endpoints remain documented in [accommodation-ui-integration.md](./accommodation-ui-integration.md).  
> Below are **new** orchestration contracts for implementation planning.

### 13.1 Quick Setup — preview (optional, recommended)

```
POST /api/v1/spaces/{spaceId}/accommodation/setup/preview
```

**Request body (PG/Hostel example):**

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

**Response:**

```json
{
  "success": true,
  "data": {
    "totals": {
      "floors": 3,
      "rooms": 30,
      "beds": 90
    },
    "sample": { },
    "warnings": []
  }
}
```

### 13.2 Quick Setup — execute

```
POST /api/v1/spaces/{spaceId}/accommodation/setup
Header: Idempotency-Key: <uuid>
```

**Request:** Same shape as preview.

**Response:**

```json
{
  "success": true,
  "data": {
    "buildingId": "uuid",
    "totals": {
      "floors": 3,
      "rooms": 30,
      "beds": 90
    }
  }
}
```

**CO_LIVING request shape:**

```json
{
  "spaceType": "CO_LIVING",
  "building": { "name": "CoLive Tower" },
  "units": {
    "count": 10,
    "startNumber": "101",
    "roomsPerUnit": 3,
    "bedsPerRoom": 2,
    "defaultRoomType": "PRIVATE",
    "capacityPerRoom": 2
  }
}
```

**RENTAL request shape:**

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

### 13.3 Building summary (builder header)

```
GET /api/v1/spaces/{spaceId}/buildings/{buildingId}/summary
```

**Response:**

```json
{
  "success": true,
  "data": {
    "buildingId": "uuid",
    "name": "Sunrise PG",
    "counts": {
      "floors": 3,
      "rooms": 30,
      "beds": 90,
      "units": 0
    },
    "statusCounts": {
      "AVAILABLE": 90,
      "OCCUPIED": 0
    }
  }
}
```

### 13.4 Progressive lists (implemented — no tree API)

**Do not** add a unified tree endpoint. Use separate cache-friendly list APIs:

| Screen level | Endpoint | Response |
|--------------|----------|----------|
| Building overview | `GET .../summary` + `GET .../floors` or `.../units` | Summary + `PagedResponse<FloorListItem>` or `UnitListItem` |
| Floor / unit detail | `GET .../floors/{id}/rooms` or `.../units/{id}/rooms` | `PagedResponse<RoomListItem>` |
| Room detail | `GET .../rooms/{id}/beds` | `PagedResponse<BedListItem>` |

Default `view=summary` (lightweight + paginated). Legacy full arrays: `view=full`.

Space search: `GET .../floors?query=`, `.../units?query=`, `.../rooms?query=`.

Full UI spec: [accommodation-lazy-loading-ui-integration.md](./accommodation-lazy-loading-ui-integration.md).

### 13.5 Duplicate floor

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

### 13.6 Duplicate room

```
POST /api/v1/spaces/{spaceId}/rooms/{roomId}/duplicate
```

```json
{
  "targetRoomNumber": "102"
}
```

### 13.7 Bulk create rooms

```
POST /api/v1/spaces/{spaceId}/floors/{floorId}/rooms/bulk
POST /api/v1/spaces/{spaceId}/units/{unitId}/rooms/bulk
```

```json
{
  "count": 10,
  "startRoomNumber": "101",
  "roomType": "SHARED",
  "capacity": 3,
  "bedsPerRoom": 3,
  "defaultStatus": "AVAILABLE"
}
```

### 13.8 Bulk create beds

```
POST /api/v1/spaces/{spaceId}/rooms/{roomId}/beds/bulk
```

```json
{
  "count": 3,
  "labelStyle": "ALPHA"
}
```

---

## 14. Duplication and bulk operations

### 14.1 Numbering conflict resolution

| Operation | Rule |
|-----------|------|
| Bulk rooms | Allocate next N free numbers on parent; fail preview if overlap |
| Duplicate room | Suggest next number; user can override in modal |
| Duplicate floor | Renumbers rooms to new floor prefix |
| Bulk beds | Append A,B,C after existing beds in room |

### 14.2 Occupancy constraints (Phase 4.2+)

| Operation | Rule when occupied |
|-----------|-------------------|
| Duplicate floor/room with occupied beds | Block or require vacate first |
| Bulk add beds | Allowed on occupied room if capacity allows |
| Deactivate | Existing child rules |

Document hooks now; enforce when occupancy ships.

### 14.3 Limits (recommended)

| Limit | Value | Reason |
|-------|-------|--------|
| Max beds per setup job | 500 | Mobile + DB transaction size |
| Max rooms per bulk | 50 | UI sanity |
| Max floors per setup | 20 | Typical PG bound |

Return `400` with clear message if exceeded.

---

## 15. Occupancy and future phases

### 15.1 Builder node actions (Phase 4.2)

Add to bed/room/unit nodes when occupancy APIs exist:

| Node | Future action |
|------|----------------|
| Bed | Allocate member, Vacate |
| Room | Allocate (co-living), View occupants |
| Unit | Allocate (rental), Vacate |

Structure redesign **must not** require another navigation paradigm for occupancy — actions attach to builder nodes.

### 15.2 Dashboard metrics (Phase 4.2+)

Accommodation Home building cards show:

- Total beds / units
- Occupied / available
- Occupancy %

Data from summary endpoint + occupancy service.

### 15.3 Search (roadmap)

Global accommodation search on home:

- Room number, unit number, member name (when allocated)

Builder local search is v1; global is v2.

---

## 16. Implementation roadmap

### Phase 4.1 (done)

- Entity CRUD, drill-down lists, permissions, MESS hidden, RENTAL shallow.

### Phase 4.1.5 — Quick wins (1–2 sprints)

| Item | UI | Backend |
|------|-----|---------|
| Empty-state dual CTA copy | ✅ | — |
| Auto-name from number on forms | ✅ | — |
| Save & add another on forms | ✅ | — |
| Building summary counts on cards | ✅ | Optional summary GET |

### Phase 4.2a — Quick Setup Wizard (2–3 sprints)

| Item | UI | Backend |
|------|-----|---------|
| Wizard screens per space type | ✅ | Client batch OR setup POST |
| Preview step | ✅ | setup/preview |
| Progress UI | ✅ | — |
| Land on Builder after success | ✅ | — |

### Phase 4.2b — Manual Builder (3–4 sprints)

| Item | UI | Backend |
|------|-----|---------|
| Accordion tree screen | ✅ | Reuse list APIs |
| Inline add / edit sheets | ✅ | Existing CRUD |
| Duplicate floor/room | ✅ | duplicate endpoints |
| Bulk rooms/beds | ✅ | bulk endpoints |
| Deprecate drill-down entry | ✅ | — |

### Phase 4.2c — Backend hardening (parallel)

| Item | Backend |
|------|---------|
| Transactional `accommodation/setup` | ✅ |
| Idempotency | ✅ |
| Numbering service | ✅ |
| Setup audit log | ✅ |

### Phase 4.3 — Occupancy on builder nodes

Allocate / transfer / vacate from builder; availability filters.

---

## 17. Risks and mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| User enters wrong counts in wizard | High | High | Preview, totals, confirm |
| Partial setup failure | Medium | High | Transactional endpoint |
| Builder performance with 200+ nodes | Medium | Medium | Lazy expand, virtualize lists |
| Two UX paths confuse users | Medium | Low | Clear empty-state hierarchy; hide legacy nav |
| Numbering collisions | Medium | Medium | Server-side allocation |
| Duplicate occupied structure | Low (until 4.2) | High | Block with message when occupancy exists |
| Scope creep (CSV, per-floor variance) | High | Medium | Strict v1 symmetry; v2 backlog |
| Manager runs setup incorrectly | Medium | Medium | Manager allowed per permissions; owner reviews |

---

## 18. Acceptance criteria

### 18.1 Quick Setup — PG

- [ ] Owner can create Sunrise PG (3×10×3) in **under 2 minutes** excluding API latency.
- [ ] Preview shows **90 beds** before confirm.
- [ ] Generated structure visible in Builder without manual navigation.
- [ ] All beds `AVAILABLE`, rooms have correct `roomType` and `capacity`.
- [ ] Double-tap Generate does not create duplicate buildings (idempotency).

### 18.2 Manual Builder

- [ ] User manages entire building on **one screen** without Floors/Rooms/Beds stack.
- [ ] Duplicate Floor 1 → Floor 2 copies all rooms and beds with renumbered rooms.
- [ ] Bulk add 10 rooms on a floor creates 101–110 (or next free block).
- [ ] Search finds Room 204 within building.
- [ ] OWNER can deactivate bed → room → floor → building with correct error messages.

### 18.3 Space types

- [ ] CO_LIVING wizard generates units → rooms → beds.
- [ ] RENTAL wizard generates units only; no room/bed UI.
- [ ] MESS has no Accommodation tab.

### 18.4 Permissions

- [ ] MANAGER can Quick Setup and edit; cannot deactivate.
- [ ] TENANT can view builder read-only (if accommodation view allowed).

### 18.5 Regression

- [ ] Existing per-entity CRUD APIs remain functional.
- [ ] Manual path still works for single building + single floor edge case.

---

## 19. Open questions

| # | Question | Proposed default |
|---|----------|------------------|
| 1 | Per-floor different room counts in v1? | No — v2 |
| 2 | Unified tree API vs reuse list APIs in builder? | Reuse lists in v1 |
| 3 | Wizard inside tab vs main stack? | Main stack modal |
| 4 | Alpha vs numeric bed labels default? | Alpha A–Z, then numeric |
| 5 | Keep drill-down routes permanently for deep links? | Yes, hidden from primary UX |
| 6 | Co-living allocate to room vs bed in UI? | Bed-first; room allocate in 4.2 |

---

## 20. Related documents

| Document | Relationship |
|----------|--------------|
| [development-roadmap.md](./development-roadmap.md) | Phase 4 scope, occupancy, availability, metrics |
| [accommodation-ui-integration.md](./accommodation-ui-integration.md) | Current entity APIs, types, permissions (still valid for CRUD) |
| [my-spaces-ui-integration.md](./my-spaces-ui-integration.md) | `spaceId`, `spaceType`, `membershipRole` |
| [auth-ui-integration.md](./auth-ui-integration.md) | JWT |
| [membership-ui-integration.md](./membership-ui-integration.md) | Members linked in Phase 4.2 occupancy |

---

## Appendix A — Side-by-side comparison

| Dimension | Current drill-down CRUD | Quick Setup + Manual Builder |
|-----------|-------------------------|------------------------------|
| First-time 90-bed PG | ~124 creates, hundreds of taps | 1 wizard, ~2 minutes |
| See whole building | No | Yes (builder) |
| Duplicate floor | Not supported | Supported |
| Bulk add 10 rooms | 10 separate flows | One modal |
| Custom odd layout | Possible but tedious | Builder inline edits |
| Backend | Entity CRUD only | CRUD + orchestration |
| Learning curve | Low per screen, high overall | Low onboarding, medium builder |
| Mobile fit | Poor at scale | Good |

## Appendix B — Wizard field matrix (quick reference)

| Field | PG/Hostel | Co-Living | Rental |
|-------|-----------|-----------|--------|
| Building name | ✅ | ✅ | ✅ |
| Building code | opt | opt | opt |
| Floor count | ✅ | — | — |
| Unit count | — | ✅ | ✅ |
| Rooms per floor | ✅ | — | — |
| Rooms per unit | — | ✅ | — |
| Beds per room | ✅ | ✅ | — |
| Default room type | ✅ | ✅ | — |
| Capacity per room | ✅ | ✅ | — |
| Preview totals | ✅ | ✅ | ✅ |

---

*Document version: 1.0 — for UI and backend implementation planning.*
