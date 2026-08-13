# Acomi — Accommodation Domain Model

**Status:** Approved architecture decision  
**Decision:** Option C — Composable hierarchy with optional Unit container  
**Effective from:** Pre–Phase 4.3 Occupancy  
**Supersedes:** Space-type–exclusive hierarchy rules in [accommodation-ui-integration.md](./accommodation-ui-integration.md) §Structure by space type (product rules only; CRUD APIs remain valid)

This document is the **source of truth** for:

- Accommodation (Phase 4.1–4.2, complete)
- Occupancy (Phase 4.3+)
- Availability (Phase 4.3+)
- Billing (future)
- Complaints (future)
- Maintenance (future)
- Reporting (future)

**Related documents:**

| Document | Scope |
|----------|-------|
| [development-roadmap.md](./development-roadmap.md) | Phase 4+ roadmap, occupancy, availability, metrics |
| [accommodation-ui-integration.md](./accommodation-ui-integration.md) | Phase 4.1 CRUD, enums, permissions |
| [accommodation-phase-4.2-ui-integration.md](./accommodation-phase-4.2-ui-integration.md) | Quick Setup, Bulk, Duplicate |
| [accommodation-flow-redesign.md](./accommodation-flow-redesign.md) | Dual-entry UX (Quick Setup + Manual Builder) |
| [accommodation-lazy-loading-ui-integration.md](./accommodation-lazy-loading-ui-integration.md) | Progressive list loading, building summary |
| [accommodation-lifecycle-ui-integration.md](./accommodation-lifecycle-ui-integration.md) | Deactivate, restore, cascade delete |
| [accommodation-phase-4.2.5-ui-integration.md](./accommodation-phase-4.2.5-ui-integration.md) | Phase 4.2.5 layout modes implementation |

---

## Table of contents

1. [Problem Statement](#1-problem-statement)
2. [Current Accommodation Model](#2-current-accommodation-model)
3. [Option Analysis](#3-option-analysis)
4. [Final Architecture Decision](#4-final-architecture-decision)
5. [Property Layout Modes](#5-property-layout-modes)
6. [Internal Domain Hierarchy](#6-internal-domain-hierarchy)
7. [Visible UI Hierarchy](#7-visible-ui-hierarchy)
8. [Synthetic Unit Strategy](#8-synthetic-unit-strategy)
9. [Quick Setup Rules](#9-quick-setup-rules)
10. [Manual Builder Rules](#10-manual-builder-rules)
11. [Occupancy Allocation Targets](#11-occupancy-allocation-targets)
12. [Availability Rollups](#12-availability-rollups)
13. [Billing Anchors](#13-billing-anchors)
14. [Complaints Hierarchy](#14-complaints-hierarchy)
15. [Maintenance Hierarchy](#15-maintenance-hierarchy)
16. [Reporting Hierarchy](#16-reporting-hierarchy)
17. [Migration Strategy](#17-migration-strategy)
18. [API Implications](#18-api-implications)
19. [UI Implications](#19-ui-implications)
20. [Future Roadmap Impact](#20-future-roadmap-impact)

---

## 1. Problem Statement

### 1.1 Real-world property diversity

Acomi serves PG, Hostel, Co-Living, and Rental operators. In practice, inventory is not uniform:

| Operator mental model | Example |
|----------------------|---------|
| **Corridor PG / hostel** | Floor → numbered rooms (101, 102…) → beds |
| **Converted apartment PG** | Floor → Flat 101 → Bedroom A, Bedroom B, Hall → beds |
| **Co-Living** | Building → apartments/suites → shared/private rooms → beds |
| **Rental** | Building → flats leased as whole units |

Many PG owners operate **converted apartments** (Studio, 1 RK, 1 BHK, 2 BHK, 3 BHK). A single BHK flat contains multiple rooms; corridor PGs do not.

### 1.2 Failure of profile-exclusive hierarchies

The Phase 4.1–4.2 implementation uses **space-type profiles** that treat PG/HOSTEL and CO_LIVING/RENTAL as mutually exclusive trees:

| Space type | Implemented visible hierarchy |
|------------|------------------------------|
| PG / HOSTEL | Building → Floor → Room → Bed |
| CO_LIVING | Building → Unit → Room → Bed |
| RENTAL | Building → Unit |

For converted-apartment PG, the current PG profile forces:

```
Floor 1 → Bedroom A, Bedroom B, Hall   (as sibling “rooms”)
```

This **loses**:

- **Flat as a first-class container** — vacancy, billing, complaints, and maintenance at “Flat 101” level.
- **Grouping** of rooms belonging to one operated unit.
- **Natural numbering** — Flat 101 containing Room A/B vs corridor Room 101.

### 1.3 Downstream risk

Phase 4.3+ features depend on correct structure:

- Member allocation (bed / room / unit)
- Availability lists and dashboard occupancy %
- Billing at bed, room, or flat granularity
- Complaints and maintenance scoped to bed, room, flat, floor, or building

If hierarchy rules are frozen incorrectly before Occupancy ships, later modules inherit **semantic hacks** (e.g. treating a flat as a “room”) or require **expensive migration** of allocation history.

### 1.4 Key insight

The **Unit** entity already exists in the domain (`UnitResponse` includes optional `floorId`). PG/HOSTEL UI profiles **hide** units and attach rooms directly to floors. The gap is primarily **product rules, layout modes, and onboarding** — not a missing core entity.

---

## 2. Current Accommodation Model

### 2.1 Entities (Phase 4.1)

| Entity | Purpose | Key fields |
|--------|---------|------------|
| **Building** | Physical building | `buildingId`, `name`, optional `code`, `active` |
| **Floor** | Level inside building | `floorId`, `floorNumber`, `sortOrder`, `name`, `active` |
| **Unit** | Apartment, flat, suite, or independent unit | `unitId`, `unitNumber`, `name`, `status`, `active`, optional `floorId` |
| **Room** | Room inside floor or unit | `roomId`, `roomNumber`, `roomType`, `capacity`, `status`, `active` |
| **Bed** | Individual occupancy slot | `bedId`, `bedNumber`, `name`, `status`, `active` |

### 2.2 Room parent rules (today)

| Parent | Used by | API |
|--------|---------|-----|
| Floor | PG / HOSTEL | `POST/GET .../floors/{floorId}/rooms` |
| Unit | CO_LIVING | `POST/GET .../units/{unitId}/rooms` |

Rooms are children of **either** a floor **or** a unit — not both in current PG validation.

### 2.3 Space-type profiles (Phase 4.1–4.2)

| Space type | Visible hierarchy | Quick Setup generates | Occupancy target (roadmap) |
|------------|-------------------|----------------------|----------------------------|
| PG | Floor → Room → Bed | Floors × rooms × beds | Member → Bed |
| HOSTEL | Floor → Room → Bed | Floors × rooms × beds | Member → Bed |
| CO_LIVING | Unit → Room → Bed | Units × rooms × beds | Member → Bed or Room |
| RENTAL | Unit (leaf) | Units only | Member → Unit |
| MESS | Hidden | — | N/A |

### 2.4 Operational status vs occupancy

`AccommodationStatus` (`AVAILABLE`, `OCCUPIED`, `RESERVED`, `MAINTENANCE`, `BLOCKED`) on Unit, Room, and Bed is an **operational availability flag**. It is **not** the same as member occupancy (Phase 4.3). Occupancy will be a separate allocation record.

### 2.5 What Phase 4.2 delivered

- Quick Setup Wizard (transactional generate)
- Manual Builder (multi-screen hierarchy navigation)
- Duplicate floor/room, bulk units/rooms/beds
- Progressive lazy loading and building summary
- Lifecycle (deactivate, restore, cascade delete)

All of this is built on **profile-driven depth**, not a single universal tree.

---

## 3. Option Analysis

### 3.1 Option A — Keep Floor → Room → Bed for PG + enhanced room types

Add room categories (`STUDIO`, `RK`, `BHK_1`, `BHK_2`, `BHK_3`, etc.) on the Room entity.

| Pros | Cons |
|------|------|
| No migration; Quick Setup stays simple | Cannot model flat as a grouping container |
| Matches corridor PG mental model | Apartment PG, flat billing, flat complaints require hacks |
| Minimal UI change | Room type on a single room misrepresents “one BHK = multiple rooms” |
| | No vacant-flat or whole-flat lease semantics |

**Verdict:** Adequate for traditional corridor PG only. **Poor long-term** for converted apartments and cross-module features.

### 3.2 Option B — Universal Floor → Unit → Room → Bed for everyone

Mandatory five-level hierarchy for all space types.

| Pros | Cons |
|------|------|
| One tree; matches real-estate structure | Overwhelms simple PG owners |
| Strong for occupancy, billing, complaints | Quick Setup + navigation always +1 level |
| Aligns with existing Unit + `floorId` | Abandons Phase 4.2 simplicity optimizations |

**Verdict:** Architecturally clean. **Product-risky** if mandatory for all PG operators.

### 3.3 Option C — Composable hierarchy (chosen)

**Structurally:** Support Unit everywhere in the domain model.  
**Experientially:** Hide Unit for corridor PG via **Property Layout Modes**.  
**Operationally:** Use synthetic units where the UI does not expose the unit layer.

| Pros | Cons |
|------|------|
| Correct semantics for apartment PG and rental | Requires profile refactor and layout-mode product rules |
| Corridor PG UX unchanged | Backend validation paths must support floor+unit+room |
| One allocation/billing/complaints model | Migration planning for existing PG inventory |
| Freeze rules before Occupancy | |

**Verdict:** **Adopted.** Best balance of structural correctness and operator simplicity.

---

## 4. Final Architecture Decision

### 4.1 Decision summary

| Dimension | Decision |
|-----------|----------|
| **Structural model** | Building → Floor → Unit → Room → Bed (all levels are real domain entities where applicable) |
| **Unit visibility** | Controlled by **Property Layout Mode** — visible, hidden, or synthetic |
| **Space type** | Sets **default layout mode**, not hard navigation walls |
| **Occupancy** | Allocation attaches via `targetType`: `BED` \| `ROOM` \| `UNIT` |
| **UI label** | Domain entity = **Unit**; user-facing label = **Apartment** where unit is visible |
| **Timing** | Freeze this document **before Phase 4.3 Occupancy** implementation begins |

### 4.2 Principles

1. **Progressive disclosure** — complexity scales with layout mode, not space type alone.
2. **Single inventory model** — no parallel “generated” or “virtual” accommodation entities.
3. **Bed remains atomic** for PG sharing — unit does not replace bed-level operations.
4. **Roll-up, not replacement** — availability and reporting aggregate upward through optional unit layer.
5. **Do not conflate shape with boundary** — `unitKind` (flat, BHK, studio) describes the **unit**; `roomType` (private, shared, dormitory) describes **sharing inside** a room.

### 4.3 What this does not change

- Phase 4.1 CRUD endpoints remain the foundation.
- Deactivation bottom-up order: Beds → Rooms → Units/Floors → Building.
- MESS spaces remain excluded from Accommodation.
- Permissions model (OWNER / MANAGER / TENANT / etc.) unchanged.

---

## 5. Property Layout Modes

Layout mode is a **per-building** (or per-building-section) product setting that controls which hierarchy levels are **visible** in UI and Quick Setup.

### 5.1 Layout mode definitions

| Layout mode | Default for space type | User sees |
|-------------|------------------------|-----------|
| `CORRIDOR_PG` | PG, HOSTEL | Building → Floor → Room → Bed |
| `APARTMENT_PG` | PG (when selected) | Building → Floor → **Apartment** → Room → Bed |
| `CO_LIVING` | CO_LIVING | Building → **Apartment** → Room → Bed |
| `RENTAL` | RENTAL | Building → **Apartment** |

> **Terminology:** The domain entity is **Unit**. In user-facing copy (labels, wizard steps, breadcrumbs), display **Apartment** when the unit layer is visible.

### 5.2 Unit visibility modes

| Mode | Meaning | Example |
|------|---------|---------|
| **Visible** | User creates, names, and navigates apartments | APARTMENT_PG, CO_LIVING, RENTAL |
| **Hidden** | Unit exists in data model; UI skips unit level | CORRIDOR_PG |
| **Synthetic** | System-created unit; not shown in UI; may be 1:1 with room or floor block | CORRIDOR_PG Quick Setup default |

### 5.3 Mixed buildings

A single building may support **per-floor layout** in a future enhancement:

- Floor 1: corridor rooms (CORRIDOR_PG segment)
- Floor 2: converted flats (APARTMENT_PG segment)

v1 recommendation: **one layout mode per building**. Mixed-mode is a v2 product extension documented here for forward compatibility.

### 5.4 Layout mode selection

| Entry point | When |
|-------------|------|
| Quick Setup — first step after building name | “What type of layout is this building?” |
| Manual Builder — building settings | Change layout mode (with migration warnings if structure exists) |
| Space type default | Pre-select mode; user can override for PG |

---

## 6. Internal Domain Hierarchy

### 6.1 Canonical tree

```
Building
  └── Floor (optional for single-storey blocks; typical for multi-floor PG)
        └── Unit (optional; required for apartment/rental semantics)
              └── Room (optional for RENTAL leaf; required for bed-based sharing)
                    └── Bed (optional; required for PG/hostel/co-living bed sharing)
```

Every entity is a **real persisted record**. There is no shadow inventory layer.

### 6.2 Parent-child rules

| Child | Valid parents | Notes |
|-------|---------------|-------|
| Floor | Building | Always `buildingId` |
| Unit | Building; optionally scoped to Floor via `floorId` | CO_LIVING may omit floor; APARTMENT_PG sets `floorId` |
| Room | Floor **or** Unit | **New rule:** In APARTMENT_PG, room parent is **Unit** (unit has `floorId`). In CORRIDOR_PG, room parent is **Floor** (synthetic unit optional). |
| Bed | Room | Always `roomId` |

### 6.3 Unit kinds (future product field)

`unitKind` describes the **shape or classification** of an apartment — not sharing layout:

| Value | Meaning |
|-------|---------|
| `SINGLE_ROOM` | Single room operated as unit |
| `STUDIO` | Studio apartment |
| `RK` | 1 RK |
| `BHK_1` | 1 BHK |
| `BHK_2` | 2 BHK |
| `BHK_3` | 3 BHK |
| `FLAT` | Generic flat / apartment |
| `DORMITORY` | Dorm-style unit (hostel wing) |
| `SUITE` | Suite |

`unitKind` is **optional metadata** for display, filtering, and reporting. It does not replace the Unit entity.

### 6.4 Room types (unchanged)

`roomType`: `PRIVATE` | `SHARED` | `DORMITORY` — describes sharing layout **inside** a room. Required on room create.

### 6.5 Capacity

- **Room `capacity`** — max occupants (integer ≥ 1); used for room-level allocation and capacity rollups.
- **Bed** — atomic slot; one occupant per bed in standard PG sharing.

---

## 7. Visible UI Hierarchy

What the operator sees depends on layout mode. Internal Unit records may exist even when hidden.

### 7.1 CORRIDOR_PG

```
Building
  → Floor
    → Room
      → Bed
```

Unit layer: **hidden or synthetic**. Navigation depth: 4 levels to bed.

### 7.2 APARTMENT_PG

```
Building
  → Floor
    → Apartment
      → Room
        → Bed
```

Unit layer: **visible**, labeled “Apartment”. Navigation depth: 5 levels to bed.

**Example — converted flat:**

```
Building A
  └── Floor 1
        └── Apartment 101
              ├── Bedroom A → Bed A, Bed B
              ├── Bedroom B → Bed A
              └── Hall      → Bed A
```

### 7.3 CO_LIVING

```
Building
  → Apartment
    → Room
      → Bed
```

Floor optional (single-storey co-living may omit floor in UI). Unit layer: **visible**.

### 7.4 RENTAL

```
Building
  → Apartment
```

Rooms and beds may be added in a later phase for partial-flat or room-rental expansion. v1 leaf = apartment.

### 7.5 Breadcrumbs and navigation

Manual Builder uses **two levels per screen** (parent list → child list). Layout mode determines which parent type appears at each depth:

| Screen depth | CORRIDOR_PG | APARTMENT_PG | CO_LIVING | RENTAL |
|--------------|-------------|--------------|-----------|--------|
| Building overview | Floors | Floors | Apartments | Apartments |
| Parent detail | Rooms (under floor) | Apartments (under floor) → then Rooms | Rooms (under apartment) | Apartment detail |
| Room detail | Beds | Beds | Beds | — |

Navigable breadcrumb trails must include apartment segment when visible.

---

## 8. Synthetic Unit Strategy

Synthetic units allow CORRIDOR_PG to use the **same internal model** without burdening operators with an extra visible level.

### 8.1 When to create synthetic units

| Scenario | Strategy |
|----------|----------|
| CORRIDOR_PG Quick Setup | Auto-create **one synthetic unit per room** OR **one per floor** (implementation choice; prefer per-room for cleaner future promotion to APARTMENT_PG) |
| CORRIDOR_PG manual room add | Create synthetic unit on first room under floor if none exists |
| Promotion to APARTMENT_PG | Merge synthetic units into named apartments; re-parent rooms |

### 8.2 Synthetic unit properties

| Property | Value |
|----------|-------|
| `name` | System-generated (e.g. `Room 101 Container`, `_synthetic_101`) |
| `unitNumber` | Derived from room number or floor index |
| `floorId` | Set when unit is floor-scoped |
| `visible` | `false` in UI profile — never shown in lists or breadcrumbs |
| `synthetic` | `true` flag (product/backend metadata) |

### 8.3 Rules

1. Synthetic units are **never** shown in CORRIDOR_PG UI.
2. Synthetic units **may** hold occupancy allocation only at bed level (not at unit level).
3. Duplicate floor operations clone synthetic unit structure transparently.
4. Converting a corridor room to apartment layout **promotes** synthetic unit to visible apartment or merges rooms into a new named apartment.
5. Billing, complaints, and maintenance **default to bed/room** for synthetic units unless operator explicitly promotes structure.

### 8.4 Why synthetic instead of room-only internal model

Keeping Unit in the internal tree even for corridor PG ensures:

- One code path for list APIs, rollups, and migration.
- Clean promotion to APARTMENT_PG without re-parenting beds.
- Consistent allocation `targetType` enum across all layout modes.

---

## 9. Quick Setup Rules

Quick Setup generates complete building structure in one transactional job. Rules vary by layout mode.

### 9.1 CORRIDOR_PG / HOSTEL (unchanged UX)

**Wizard steps:** Building → Floors → Rooms per floor → Beds per room → Preview → Generate.

| Input | Constraint |
|-------|------------|
| Floors | 1–20 |
| Rooms per floor | ≥ 1 |
| Beds per room | ≥ 1 |
| Total beds | ≤ 500 (warn at 400) |
| Default room type | `SHARED` (PG), `DORMITORY` (HOSTEL) |

**Internal behavior:** Create synthetic unit per room (or per floor). User does not see apartment step.

**Generated example:**

```
Sunrise PG
├── Floor 1
│   ├── Room 101 — Bed A, B, C
│   └── Room 102 — Bed A, B, C
└── Floor 2
    └── Room 201 …
```

### 9.2 APARTMENT_PG

**Wizard steps:** Building → Floors → **Apartments per floor** → Rooms per apartment → Beds per room → Preview → Generate.

| Input | Notes |
|-------|-------|
| Apartments per floor | Count of flats on each floor |
| Rooms per apartment | e.g. 2 bedrooms + 1 hall = 3 |
| Optional `unitKind` default | `FLAT` or `BHK_*` |
| Beds per room | ≥ 1 |

**Generated example:**

```
Building A — Floor 1
├── Apartment 101
│   ├── Bedroom A — Bed A, B
│   ├── Bedroom B — Bed A
│   └── Hall — Bed A
└── Apartment 102
    └── …
```

### 9.3 CO_LIVING (unchanged UX)

**Wizard steps:** Building → Apartments → Rooms per apartment → Beds per room → Preview → Generate.

No floor step unless building is multi-storey (future: optional floor grouping).

### 9.4 RENTAL (unchanged UX)

**Wizard steps:** Building → Apartments → Preview → Generate.

No floors, rooms, or beds in v1.

### 9.5 Cross-cutting Quick Setup rules

1. **Server-side numbering** — client does not send floor names, room numbers, or bed labels on execute (see [accommodation-phase-4.2-ui-integration.md](./accommodation-phase-4.2-ui-integration.md)).
2. **Preview mandatory** before persist.
3. **Idempotency-Key** on execute.
4. **Layout mode** stored on building at setup time.
5. **Space type** must match space; layout mode must be valid for space type (PG may use CORRIDOR_PG or APARTMENT_PG).

---

## 10. Manual Builder Rules

Manual Builder is the day-2 workspace for exceptions, edits, duplicate, and bulk operations.

### 10.1 Navigation pattern

- **Two levels per screen** — parent list, tap row → child list.
- **No full-tree single screen** (Phase 4.2 implementation direction).
- Depth follows layout mode (see §7).

### 10.2 Progressive disclosure

| Action | Behavior |
|--------|----------|
| Default PG building | CORRIDOR_PG — no apartment level |
| “This floor has apartments” | Enable APARTMENT_PG segment on floor; show apartment list |
| Add apartment | Bulk or single create under floor |
| Add room | Parent = floor (CORRIDOR_PG) or apartment (APARTMENT_PG / CO_LIVING) |
| Add bed | Parent = room |

### 10.3 Duplicate operations

| Operation | CORRIDOR_PG | APARTMENT_PG |
|-----------|-------------|--------------|
| Duplicate floor | Clone rooms + beds (+ synthetic units) | Clone apartments + rooms + beds |
| Duplicate apartment | N/A | Clone rooms + beds to new apartment number |
| Duplicate room | Clone beds on same parent | Same |
| Bulk rooms | Parent = floor | Parent = apartment |
| Bulk apartments | N/A | Parent = floor |

### 10.4 Lifecycle

Existing cascade delete and deactivation rules apply. When apartment is visible:

- Deactivate apartment blocked while active rooms exist.
- Delete apartment cascades to rooms and beds when `actions.canDelete` allows.

### 10.5 Search and filters

Builder local search filters within current building context. Future global search includes apartment number, room number, member name (post-occupancy).

---

## 11. Occupancy Allocation Targets

**Frozen before Phase 4.3.** Occupancy is modeled as an allocation record, not as a field on bed/room/unit alone.

### 11.1 Allocation model (conceptual)

```
Allocation {
  memberId
  targetType: BED | ROOM | UNIT
  targetId
  allocatedAt
  vacatedAt?
  previousAllocationId?   // transfer chain
}
```

### 11.2 Target type by scenario

| Scenario | targetType | Example |
|----------|------------|---------|
| PG shared bed | `BED` | Member → Bed A in Room 101 |
| Hostel dorm bed | `BED` | Member → Bed C in Dormitory room |
| Co-Living private room | `ROOM` | Member → Room B in Apartment 101 |
| Co-Living shared (bed) | `BED` | Member → Bed 1 in shared room |
| Rental whole flat | `UNIT` | Member → Apartment 101 |
| Apartment PG (full flat lease) | `UNIT` | Member → Apartment 101 (all beds in flat) |
| Apartment PG (per bed) | `BED` | Member → Bed in Bedroom A |

### 11.3 Default targets by layout mode

| Layout mode | Primary target | Secondary target |
|-------------|----------------|------------------|
| CORRIDOR_PG | `BED` | — |
| APARTMENT_PG | `BED` (typical PG) | `UNIT` (full-flat lease) |
| CO_LIVING | `BED` or `ROOM` | — |
| RENTAL | `UNIT` | `ROOM` (future) |

Space type informs UI defaults; **targetType is explicit** on each allocation.

### 11.4 Transfers

| Transfer | Supported |
|----------|-----------|
| Bed → Bed | Yes |
| Room → Room | Yes (co-living, room-level lease) |
| Unit → Unit | Yes (rental, full-flat PG) |
| Bed → Room | Promote allocation granularity (policy) |
| Unit → Bed | Demote / partial vacate (policy) |

Transfer history preserves `previousAllocationId` for audit.

### 11.5 Vacate

Vacate sets `vacatedAt`, triggers availability recalculation, and updates operational status per policy (bed/room/unit may return to `AVAILABLE`).

### 11.6 Constraints on structure changes

When occupancy exists (per [accommodation-lifecycle-ui-integration.md](./accommodation-lifecycle-ui-integration.md)):

- Block delete of occupied beds.
- Block cascade delete when occupancy history exists in subtree.
- Duplicate floor/apartment with occupied beds — block or require vacate first.

---

## 12. Availability Rollups

### 12.1 Source of truth

| Granularity | Source |
|-------------|--------|
| Bed availability | Bed status + bed-level allocation |
| Room availability | Room capacity vs occupied count; room status |
| Apartment availability | Derived from children (all beds occupied → apartment full) |
| Floor / building | Sum of descendants |

### 12.2 Operational status vs occupancy

| Concept | Mechanism |
|---------|-----------|
| **Operational status** | `AccommodationStatus` on unit/room/bed — maintenance, blocked, reserved |
| **Occupancy** | Allocation records — member assigned to bed/room/unit |
| **Available for allocation** | Operational status allows + capacity not exhausted |

A bed may be `AVAILABLE` but in a `MAINTENANCE` room — roll-up rules must respect parent status.

### 12.3 Roll-up hierarchy

```
Bed → Room → Unit (if present) → Floor (if present) → Building
```

### 12.4 Dashboard metrics (roadmap)

| Metric | Roll-up path |
|--------|--------------|
| Available beds | Bed level |
| Occupied beds | Bed level |
| Vacant rooms | Room capacity |
| Vacant apartments | Unit with no occupant at unit level and not all beds full (policy) |
| Occupancy % | Configurable: beds (PG) or units (rental) |

Building summary API (Phase 4.2) extends with optional `unitCount`, `vacantUnits`, per-apartment aggregates when layout mode exposes units.

### 12.5 CORRIDOR_PG without visible units

Roll-ups skip visible apartment layer but may use synthetic unit internally for consistent aggregation. User-facing dashboards show floor → room → bed.

---

## 13. Billing Anchors

Billing is out of Phase 4 scope but must align with allocation targets.

### 13.1 Lease granularity

| Lease type | Billing anchor | targetType |
|------------|----------------|------------|
| Per bed (PG) | Bed | `BED` |
| Per room (co-living) | Room | `ROOM` |
| Entire apartment (rental, full-flat PG) | Unit (Apartment) | `UNIT` |

### 13.2 One model

A single billing line item references:

```
billingAnchorType: BED | ROOM | UNIT
billingAnchorId: UUID
```

Rent amount, deposit, and invoice period attach to the anchor. Member allocation may match billing anchor or differ (e.g. company lease on unit, individual beds occupied).

### 13.3 Without unit in CORRIDOR_PG

Per-bed billing works unchanged. **Entire-flat billing** requires visible unit (APARTMENT_PG) or explicit `UNIT` allocation on synthetic unit (not recommended for UX — promote to APARTMENT_PG instead).

---

## 14. Complaints Hierarchy

Complaints attach to a **target scope** in the accommodation tree.

### 14.1 Target scopes

| Scope | targetType | Example |
|-------|------------|---------|
| Bed | `BED` | Broken mattress |
| Room | `ROOM` | AC not working in Bedroom A |
| Apartment | `UNIT` | Water leak affecting whole flat |
| Floor | `FLOOR` | Lift not working on Floor 2 |
| Building | `BUILDING` | Power backup failure |

### 14.2 Layout mode impact

| Layout mode | Flat-level complaint |
|-------------|---------------------|
| CORRIDOR_PG | File against room or building; no natural flat scope |
| APARTMENT_PG | File against apartment — **clean** |
| CO_LIVING | File against apartment or room |
| RENTAL | File against apartment |

### 14.3 Room categories are insufficient

Tagging rooms with `BHK_1` does not link Bedroom A, Bedroom B, and Hall under one complaint. **Unit provides the grouping key.**

---

## 15. Maintenance Hierarchy

Maintenance work orders use the same target scope model as complaints.

### 15.1 Examples

| Work | Target |
|------|--------|
| Replace bed frame | Bed |
| Paint room | Room |
| Plumbing — whole flat | Apartment (Unit) |
| Floor corridor lighting | Floor |
| Building generator service | Building |

### 15.2 Status cascade

Setting apartment to `MAINTENANCE` may cascade operational status to child rooms and beds (policy: block new allocations, show in availability).

### 15.3 Blocking allocation

Beds and rooms in `MAINTENANCE` or `BLOCKED` status are excluded from allocation picker (Phase 4.3).

---

## 16. Reporting Hierarchy

Reports aggregate along the same roll-up path as availability.

### 16.1 Report dimensions

| Report | Primary dimension | Optional drill-down |
|--------|-------------------|---------------------|
| Occupancy | Beds (PG), Units (rental) | Floor → Apartment → Room → Bed |
| Revenue | Billing anchor type | By apartment, room, bed |
| Vacancy | Vacant beds / vacant apartments | Per floor, per building |
| Property structure | Building | Full tree to bed |

### 16.2 Layout mode in reports

- CORRIDOR_PG reports: building → floor → room → bed.
- APARTMENT_PG reports: building → floor → apartment → room → bed.
- Filters always include apartment when layout mode exposes it.

### 16.3 Synthetic units in reports

Synthetic units are **excluded** from user-facing report dimensions. Roll-ups jump floor → room for CORRIDOR_PG.

---

## 17. Migration Strategy

### 17.1 Timing

| When | Action |
|------|--------|
| **Now (pre–4.3)** | Freeze domain rules in this document; implement layout modes |
| **Phase 4.2.5 / early 4.3** | Backend validation + UI profile refactor |
| **Before first production occupancy** | Complete data migration for existing PG inventory |
| **After occupancy live** | Migration cost rises — avoid hierarchy changes |

### 17.2 Existing PG/HOSTEL inventory

| Approach | Effort | Description |
|----------|--------|-------------|
| **Synthetic unit backfill** | Low | For each floor-room, create synthetic unit; re-parent room under unit; set `layoutMode = CORRIDOR_PG` |
| **Semantic apartment migration** | High | Owner names flats; rooms grouped manually |

**Recommended:** Synthetic backfill for existing data; owners opt into APARTMENT_PG when ready.

### 17.3 Existing CO_LIVING / RENTAL

Minimal change. Confirm `layoutMode` set. Optional `floorId` on units when multi-storey co-living adds floors.

### 17.4 Building metadata

Add to building (conceptual):

```
layoutMode: CORRIDOR_PG | APARTMENT_PG | CO_LIVING | RENTAL
```

Default from `spaceType` on create; user override in Quick Setup.

### 17.5 What does not require migration

- Bed records
- Room types and capacity
- Lifecycle history (pre-occupancy)
- Building summary counts (may add unit aggregates)

### 17.6 If postponed

| Risk | Impact |
|------|--------|
| Apartment PG mis-modeled | Flat-level features ship as hacks |
| Occupancy on wrong anchor | Transfer and audit confusion |
| Billing per flat | Requires retrofit |
| Complaints/maintenance | Multi-room flat tickets fragmented |

---

## 18. API Implications

No new entities required. API surface evolves to support layout modes and floor-scoped units.

### 18.1 Existing endpoints (retain)

| Endpoint family | Status |
|-----------------|--------|
| `/buildings`, `/floors`, `/units`, `/rooms`, `/beds` CRUD | Keep |
| Lifecycle POST (`deactivate`, `restore`, `delete`) | Keep |
| Quick Setup, Duplicate, Bulk | Extend for APARTMENT_PG |
| Building summary GET | Extend with unit aggregates |
| Paginated list GETs (`view=summary`) | Keep |

### 18.2 New or extended endpoints (conceptual)

| Need | Endpoint / change |
|------|-------------------|
| List apartments on floor | `GET .../floors/{floorId}/units` |
| Create room under apartment on floor | `POST .../units/{unitId}/rooms` (unit has `floorId`) |
| Building layout mode | `layoutMode` on building GET/PUT |
| Unit synthetic flag | `synthetic: boolean` on unit response (filter from UI lists) |
| Promote layout mode | `POST .../buildings/{buildingId}/promote-layout` (optional v2) |

### 18.3 Validation rule changes

| Rule | Today | After |
|------|-------|-------|
| PG room parent | Floor only | Floor (CORRIDOR_PG) **or** Unit with `floorId` (APARTMENT_PG) |
| PG units | Not exposed | Allowed with `floorId`; hidden when synthetic |
| List units for PG building | Not used | Return only non-synthetic units, or filter by `synthetic=false` |
| CO_LIVING floor | Optional | `floorId` on unit when multi-storey |

### 18.4 Quick Setup API

Extend setup config:

| Layout mode | Config shape |
|-------------|--------------|
| CORRIDOR_PG | Existing `PgHostelSetupConfig` |
| APARTMENT_PG | `floors` + `apartmentsPerFloor` + `roomsPerApartment` + `bedsPerRoom` |
| CO_LIVING | Existing `CoLivingSetupConfig` |
| RENTAL | Existing `RentalSetupConfig` |

Internal: CORRIDOR_PG execute creates synthetic units.

### 18.5 Summary and list items

Extend list item DTOs:

- `FloorListItem`: `apartmentCount`, `roomCount`, `bedCount`
- `UnitListItem`: `roomCount`, `bedCount`, `unitKind`, `synthetic`
- Building summary: `units` totals when layout exposes apartments

### 18.6 Occupancy APIs (Phase 4.3 — preview)

```
POST .../allocations        { memberId, targetType, targetId }
POST .../allocations/{id}/transfer   { newTargetType, newTargetId }
POST .../allocations/{id}/vacate     { vacatedAt, reason? }
GET  .../allocations?targetType=&targetId=
```

Allocation service validates target exists, is active, not in maintenance, and matches layout-mode policy.

---

## 19. UI Implications

### 19.1 AccommodationUiProfile refactor

Replace mutually exclusive `showFloors` / `showUnits` with **composable depth** driven by `layoutMode`:

| layoutMode | Overview children | Parent for rooms | Show apartment in trail |
|------------|-------------------|------------------|-------------------------|
| CORRIDOR_PG | Floors | Floor | No |
| APARTMENT_PG | Floors | Apartment (under floor) | Yes |
| CO_LIVING | Apartments | Apartment | Yes |
| RENTAL | Apartments | — | Yes |

### 19.2 Screen map

| Screen | CORRIDOR_PG | APARTMENT_PG |
|--------|-------------|--------------|
| Building overview | Floor list | Floor list |
| Floor detail | Room list | **Apartment list** |
| Apartment detail | — | Room list |
| Room detail | Bed list | Bed list |

### 19.3 Quick Setup Wizard

- Add layout mode selection for PG (corridor vs apartment).
- APARTMENT_PG: add apartments-per-floor step.
- Copy strings: use “Apartment” not “Unit” in UI.

### 19.4 Manual Builder

- Breadcrumb: `Building › Floor › Apartment › Room › Bed` when applicable.
- FAB actions context-aware (add apartment vs add room).
- Bulk modals: parent picker respects layout mode.
- Building settings: layout mode display + change (with warning).

### 19.5 Synthetic unit filtering

All list hooks and screens filter `synthetic === true` units from visible lists. CRUD against synthetic units is internal/admin only.

### 19.6 Occupancy UI (Phase 4.3)

| Node | Actions |
|------|---------|
| Bed | Allocate, Vacate, Transfer |
| Room | Allocate (room-level), View occupants |
| Apartment | Allocate (unit-level), Vacate all, View occupants |

Picker respects layout mode — apartment picker only when `targetType = UNIT` is allowed.

### 19.7 Documentation updates

After implementation, update:

- [accommodation-ui-integration.md](./accommodation-ui-integration.md) — structure tables
- [accommodation-phase-4.2-ui-integration.md](./accommodation-phase-4.2-ui-integration.md) — Quick Setup configs
- [accommodation-flow-redesign.md](./accommodation-flow-redesign.md) — §4 domain model, §6 wizard steps
- [development-roadmap.md](./development-roadmap.md) — space type rules

---

## 20. Future Roadmap Impact

### 20.1 Phase 4.3 — Occupancy

- Implement `Allocation` with frozen `targetType` enum.
- Builder node actions on bed, room, apartment.
- Block structure delete/duplicate when allocations exist.
- **Prerequisite:** Layout modes + synthetic unit strategy implemented or documented in backend.

### 20.2 Phase 4.3+ — Availability

- Available/occupied beds, rooms, vacant apartments lists.
- Dashboard occupancy % with layout-aware rollups.
- Filters by floor, apartment, room, status.

### 20.3 Phase 5+ — Billing

- Billing anchor = allocation target.
- Per-bed, per-room, per-apartment rent on one model.
- Invoice generation from occupancy period.

### 20.4 Future — Complaints & Maintenance

- Shared `AccommodationTarget { type, id }` for tickets.
- Apartment-level cascade status.
- SLA and assignment by scope.

### 20.5 Future — Reporting

- Unified property tree in exports.
- Layout-aware dimensions.
- Revenue by apartment (rental) vs by bed (PG).

### 20.6 Future — Enhancements

| Enhancement | Dependency on Option C |
|-------------|------------------------|
| Mixed layout per building (per floor) | Unit under floor |
| RENTAL room-level lease | Unit → Room hierarchy |
| Global accommodation search | Consistent parent chain |
| CSV import | Layout mode + unit rules |
| Household / family allocation | Unit-level `targetType` |

### 20.7 Mess spaces

Unchanged. Accommodation module hidden. Members, meals, billing remain separate modules.

---

## Appendix A — Decision log

| Date | Decision |
|------|----------|
| 2026-06 | Adopt Option C: composable hierarchy with optional Unit |
| 2026-06 | Freeze allocation `targetType`: BED \| ROOM \| UNIT before Phase 4.3 |
| 2026-06 | UI label “Apartment” for visible Unit layer |
| 2026-06 | CORRIDOR_PG uses synthetic units internally |
| 2026-06 | `unitKind` and `synthetic` as future metadata fields |

## Appendix B — Glossary

| Term | Meaning |
|------|---------|
| **Unit** | Domain entity: apartment, flat, suite, or independent operable container |
| **Apartment** | User-facing label for Unit when visible |
| **Layout mode** | Per-building setting controlling visible hierarchy depth |
| **Synthetic unit** | System-created unit hidden from UI; structural placeholder |
| **Allocation target** | Bed, Room, or Unit to which a member is assigned |
| **Billing anchor** | Entity on which rent/charges are calculated |
| **Operational status** | AVAILABLE / OCCUPIED / RESERVED / MAINTENANCE / BLOCKED on inventory |

---

*End of document.*
