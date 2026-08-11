# Amico — Accommodation & Occupancy Phase 4.3a Architecture

**Status:** Approved architecture direction (pre-implementation)  
**Audience:** Product, Backend, React Native, QA  
**Prerequisites:** Phase 4.1 Structure, Phase 4.2 Builder, Phase 4.3 basic Occupancy (Allocate / Transfer / Vacate)  
**Supersedes:** Nothing — extends [accommodation-domain-model.md](./accommodation-domain-model.md) and [accommodation-occupancy-ui-integration.md](./accommodation-occupancy-ui-integration.md)

**Related documents:**

| Document | Scope |
|----------|-------|
| [accommodation-domain-model.md](./accommodation-domain-model.md) | Hierarchy, allocation targets, billing anchors |
| [accommodation-ui-integration.md](./accommodation-ui-integration.md) | Structure CRUD, `AccommodationStatus` |
| [accommodation-occupancy-ui-integration.md](./accommodation-occupancy-ui-integration.md) | Current occupancy APIs |
| [member-management-ui-integration.md](./member-management-ui-integration.md) | Member profile, deposit, documents |
| [development-roadmap.md](./development-roadmap.md) | Phase 4–6 sequencing |

---

## Table of contents

1. [Executive summary](#1-executive-summary)
2. [Current implementation baseline](#2-current-implementation-baseline)
3. [Architectural principles](#3-architectural-principles)
4. [Proposed enhancements — decisions](#4-proposed-enhancements--decisions)
5. [Occupancy lifecycle design](#5-occupancy-lifecycle-design)
6. [Bed-level occupancy model](#6-bed-level-occupancy-model)
7. [Accommodation status model](#7-accommodation-status-model)
8. [Financial modeling](#8-financial-modeling)
9. [Transfer pricing policy](#9-transfer-pricing-policy)
10. [Availability impact](#10-availability-impact)
11. [Meal management impact](#11-meal-management-impact)
12. [Future billing & payments impact](#12-future-billing--payments-impact)
13. [Accommodation metadata](#13-accommodation-metadata)
14. [Gender restrictions](#14-gender-restrictions)
15. [Additional requirements](#15-additional-requirements)
16. [Domain model changes](#16-domain-model-changes)
17. [API changes (directional)](#17-api-changes-directional)
18. [DTO changes (directional)](#18-dto-changes-directional)
19. [Risk assessment](#19-risk-assessment)
20. [Implementation phases](#20-implementation-phases)
21. [Decision log](#21-decision-log)
22. [Appendix A — Backend implementation prompt](#appendix-a--backend-implementation-prompt)

---

## 1. Executive summary

Amico has correctly separated **accommodation inventory** from **member occupancy**. Phase 4.3 delivered walk-in allocation (Allocate → Transfer → Vacate). Before Availability, Meals, and Billing, the platform must extend occupancy with:

- **Lifecycle:** Reserve → Move-In → Transfer → Vacate
- **Dates:** Move-in date, expected exit date
- **Operations:** Maintenance / Blocked inventory (not just occupancy-driven)
- **Snapshots:** Rent, deposit, food, and other charges captured at move-in (not full billing)
- **Metadata:** Amenities, rules, gender policy (space-level v1)

### Critical decisions

| Topic | Decision |
|-------|----------|
| Reservation entity | **No** — use `RESERVED` occupancy sub-state, not a separate table |
| Move-in vs allocate | **Separate actions** — Reserve (future) vs Move-In (active) |
| Pricing ownership | **Catalog on accommodation; contract snapshot on occupancy** |
| Status model | **Stored + synced** — `AccommodationStatus` on inventory; `OccupancyStatus` on allocation |
| Gender rules v1 | **Space-level only** |
| Amenities v1 | **Master list; room/unit primary edit level** |
| CO_LIVING ROOM allocation | **Keep but do not expand** — billing rules not finalized |

### Do not block on

Full invoicing, meal forecasting, payment collection, or vacancy ML. **Do** add lifecycle states and financial snapshots first.

---

## 2. Current implementation baseline

### 2.1 Backend (`amico-backend`)

| Area | Current state |
|------|---------------|
| `OccupancyStatus` | `ACTIVE`, `VACATED` only |
| `OccupancyHistoryEvent` | `ALLOCATED`, `TRANSFERRED`, `VACATED` |
| `AccommodationStatus` | `AVAILABLE`, `OCCUPIED`, `RESERVED`, `MAINTENANCE`, `BLOCKED` |
| Allocate | Creates `ACTIVE` occupancy immediately; target must be `AVAILABLE` |
| `AccommodationStatusSyncService` | Sets `OCCUPIED` / `AVAILABLE` on allocate/vacate; **does not use RESERVED** |
| `expectedCheckoutDate` | Exists on `OccupancyEntity` and allocate request |
| Financial snapshots | **Not implemented** |
| Reservation / Move-In | **Not implemented** |
| Gender / amenities / rules | **Not implemented** |

**Key files:**

- `occupancy/application/service/OccupancyService.java`
- `occupancy/application/service/OccupancyTargetService.java`
- `occupancy/application/service/AccommodationStatusSyncService.java`
- `occupancy/infrastructure/persistence/entity/OccupancyEntity.java`
- `occupancy/api/controller/OccupancyController.java`

### 2.2 Frontend (`Amico` React Native)

| Area | Current state |
|------|---------------|
| Member → accommodation | `MemberAccommodationSection` with allocate/transfer/vacate |
| Bed → tenant | `AccommodationOccupantSection` via `listOccupancies?bedId=` |
| Search-first picker | Implemented |
| Owner/Manager occupancy restrictions | Implemented on member profile |
| Reservation / Move-In UX | **Not implemented** |
| Financial snapshot UI | **Not implemented** |

---

## 3. Architectural principles

1. **Inventory ≠ Occupancy** — `AccommodationStatus` is operational inventory state; occupancy is the member allocation record ([domain model §2.4, §12.2](./accommodation-domain-model.md)).
2. **Bed is atomic** for PG/Hostel — room and unit are aggregation and alternate lease granularity.
3. **Occupancy is the billing contract anchor** — future invoices reference `occupancyId`, not `bedId` alone.
4. **Catalog vs snapshot** — accommodation holds rack/default rates; occupancy holds agreed contract values at move-in.
5. **Progressive disclosure** — defer building/floor/room gender and room-level rules until v2.
6. **No separate reservation table in v1** — reserved beds have `RESERVED` occupancy records.

---

## 4. Proposed enhancements — decisions

### 4.1 Mandatory before production (Phase 4.3a)

| # | Feature | Decision |
|---|---------|----------|
| 1 | Reservation support | `RESERVED` occupancy + inventory `RESERVED` sync |
| 2 | Move-In date | Mandatory; separate from reservation date |
| 3 | Expected exit date | Keep; unify naming to `expectedExitDate` (alias `expectedCheckoutDate` during migration) |
| 4 | Maintenance / Blocked | Manual ops on inventory; block allocation picker |
| 5 | Occupancy notes | On occupancy record |
| 6 | Member category | On member + copied to occupancy snapshot |
| 7 | Agreement signed | Boolean on occupancy |
| 8 | Bed-level occupant visibility | Extend list/filter for `RESERVED` and `ACTIVE` |

### 4.2 Design now, implement in Phase 4.3b

| # | Feature | Decision |
|---|---------|----------|
| 6 | Rent snapshot | On occupancy at move-in |
| 7 | Deposit snapshot | On occupancy at move-in |
| 8 | Food enrollment + charges | On occupancy snapshot |
| 9 | Additional charges | JSON array snapshot on occupancy |
| 10 | Transfer pricing policy | Keep / Apply new / Manual override |

### 4.3 Phase 4.4 (metadata)

| # | Feature | Decision |
|---|---------|----------|
| 10 | Amenities | Master list; space defaults; room/unit CRUD |
| 11 | Rules & policies | Space-level v1 |
| 12 | Bed numbering templates | Extend `BedLabelStyle` |
| 4 | Gender restrictions | Space-level v1 |

### 4.4 Defer (post-Availability / Billing)

| Feature | Reason |
|---------|--------|
| Separate Reservation entity | Over-engineering |
| Building/floor/room gender | Complexity |
| Room-level rules | Space-level sufficient for MVP |
| Bed-level amenities | Inherit from room |
| Full charge configuration engine | Billing module |
| Advance payment flag | Payments module |
| Invoice generation | Billing module |
| Meal forecasting automation | Meals module |

---

## 5. Occupancy lifecycle design

### 5.1 State machine

```
Inventory AVAILABLE
        │
        │ Reserve (future moveInDate)
        ▼
Occupancy RESERVED  +  Inventory RESERVED
        │
        ├── Cancel reservation ──► Occupancy ended + Inventory AVAILABLE
        │
        │ Move-In (on or after moveInDate)
        ▼
Occupancy ACTIVE  +  Inventory OCCUPIED
        │
        ├── Transfer ──► old VACATED, new ACTIVE on new target
        │
        └── Vacate ──► Occupancy VACATED + Inventory AVAILABLE

Parallel (no occupant):
Inventory AVAILABLE ◄──► MAINTENANCE / BLOCKED (manual ops)
```

### 5.2 Should reservation be a separate entity?

**No.** Use `OccupancyStatus.RESERVED` on the same `occupancies` table.

| Approach | Verdict |
|----------|---------|
| Separate `reservations` table | Rejected — duplicate sync, audit fragmentation |
| `RESERVED` occupancy | **Adopted** |
| Inventory `RESERVED` without occupancy | Rejected — loses member linkage |

### 5.3 Move-In vs allocation

| Action | Occupancy status | Inventory status | When |
|--------|------------------|------------------|------|
| **Walk-in / immediate** | `ACTIVE` | `OCCUPIED` | `moveInDate` ≤ today |
| **Reserve** | `RESERVED` | `RESERVED` | `moveInDate` > today |
| **Move-In** (promote) | `RESERVED` → `ACTIVE` | `RESERVED` → `OCCUPIED` | On move-in confirmation |
| **Cancel reservation** | End record (or `CANCELLED` sub-state) | `AVAILABLE` | Before move-in |
| **Vacate** | `VACATED` | `AVAILABLE` | Checkout |

### 5.4 History events (extend enum)

| Event | Trigger |
|-------|---------|
| `RESERVED` | Reservation created |
| `MOVE_IN` | RESERVED → ACTIVE |
| `ALLOCATED` | Direct ACTIVE (walk-in; keep for backward compatibility) |
| `TRANSFERRED` | Existing |
| `VACATED` | Existing |
| `RESERVATION_CANCELLED` | Reservation ended without move-in |

### 5.5 Member occupancy status

Extend `MemberOccupancyStatus`:

| Value | Meaning |
|-------|---------|
| `ALLOCATED` | Has `ACTIVE` occupancy |
| `RESERVED` | Has `RESERVED` occupancy only |
| `VACATED` | No active or reserved occupancy |

---

## 6. Bed-level occupancy model

### 6.1 Target type by space type (frozen)

| Space type | Primary target | Mandatory |
|------------|----------------|-----------|
| PG / HOSTEL | `BED` | Yes |
| CO_LIVING | `BED` (default) or `ROOM` | Room allocation rules **not finalized for billing** — keep implementation, do not expand |
| RENTAL | `UNIT` | Yes |

### 6.2 Per-bed mixed state example

```
Room 101
  Bed A → OCCUPIED  (ACTIVE occupancy — Tejas)
  Bed B → RESERVED  (RESERVED occupancy — Rahul, moveInDate = 20-Jun)
  Bed C → AVAILABLE
```

Room status: derived — partially occupied. Dashboards show per-bed truth.

### 6.3 Reservation at bed level

Reservation uses the same `targetType` as eventual move-in (`BED`, `ROOM`, or `UNIT` per space rules).

---

## 7. Accommodation status model

### 7.1 Stored vs computed

| Status | Storage | Source |
|--------|---------|--------|
| `MAINTENANCE`, `BLOCKED` | Stored on bed/room/unit | Manual operator action |
| `RESERVED`, `OCCUPIED` | Stored, synced from occupancy | `AccommodationStatusSyncService` |
| `AVAILABLE` | Stored | No blocking occupancy + no ops block |
| Dashboard rollups | Computed | Building summary API |

### 7.2 Status ownership

| Level | Role |
|-------|------|
| **Bed** | Source of truth for PG/Hostel |
| **Room** | Derived from beds + room-level occupancy |
| **Unit** | Derived from children + unit-level occupancy |

### 7.3 Allocation picker rules

| Action | Allowed target status |
|--------|----------------------|
| Reserve | `AVAILABLE` only |
| Move-In / Allocate | `AVAILABLE` only (reserve flow uses `RESERVED` bed after reservation) |
| Transfer | Target `AVAILABLE` only |
| Never pick | `OCCUPIED`, `RESERVED`, `MAINTENANCE`, `BLOCKED` |

---

## 8. Financial modeling

### 8.1 Two-layer pricing

```
ACCOMMODATION CATALOG (rack / default)
  bed.defaultRent = 8000
  bed.defaultDeposit = 10000
        │
        │ copied at move-in
        ▼
OCCUPANCY CONTRACT SNAPSHOT (immutable per stay)
  rentSnapshot = 8000
  depositSnapshot = 10000
  foodEnabled = true
  foodChargeSnapshot = 3000
  otherChargesSnapshot = [{ "code": "PARKING", "amount": 500 }]
        │
        │ consumed by
        ▼
BILLING MODULE (future)
  invoices, payments, revisions
```

### 8.2 Field ownership

| Field | Accommodation (catalog) | Occupancy (snapshot) |
|-------|----------------------|----------------------|
| Base rent | `defaultRent` | `rentSnapshot` |
| Deposit | `defaultDeposit` | `depositSnapshot` |
| Food enabled | — | `foodEnabled` |
| Food charge | Space default (optional) | `foodChargeSnapshot` |
| Parking, WiFi, etc. | — | `otherChargesSnapshot` (JSON) |
| Agreement signed | — | `agreementSigned` |
| Member category | Member profile | `memberCategory` (copy at move-in) |

### 8.3 Member deposit vs occupancy deposit

Member profile `depositAmount` / `depositPaid` (Phase 3) = **operational ledger summary**.  
Occupancy `depositSnapshot` = **this stay's agreed deposit**. Both coexist; Billing reconciles later.

---

## 9. Transfer pricing policy

**Default: Option B — Apply new accommodation catalog rent**, with explicit override.

| Option | Behavior |
|--------|----------|
| **A — Keep existing rent** | `rentSnapshot` unchanged on new occupancy |
| **B — Apply new rent** | **Default** — new target's `defaultRent` becomes new snapshot |
| **C — Manual override** | Manager enters custom amount |

Transfer request must include `rentPolicy: KEEP | APPLY_NEW | CUSTOM` and optional `customRent`.

---

## 10. Availability impact

| Module | Change |
|--------|--------|
| Occupancy dashboard | Add Reserved count, upcoming move-ins, expiring exits |
| Availability lists | Available / Reserved / Occupied / Maintenance / Blocked |
| Vacancy forecasting | Use `expectedExitDate` + reserved `moveInDate` |
| Building summary | Extend with `reservedBeds`, `upcomingMoveIns` |
| Search / picker | Filter by lifecycle-compatible status |

---

## 11. Meal management impact

| Concern | Link |
|---------|------|
| Food enrollment | `occupancy.foodEnabled` |
| Meal eligibility | ACTIVE occupancy + `foodEnabled = true` |
| Meal forecasting (future) | Count eligible occupancies by date |
| Meal billing (future) | `foodChargeSnapshot` as contract base |
| Transfer | Prompt keep/reset food enrollment |

**Do not** auto-create meal subscriptions in Phase 4.3 — store intent snapshot only.

---

## 12. Future billing & payments impact

| Charge | Phase 4.3b (snapshot) | Phase 5+ (billing) |
|--------|----------------------|-------------------|
| Rent | `rentSnapshot` | Monthly invoice from occupancy |
| Deposit | `depositSnapshot` | Collection, refund ledger |
| Food | `foodChargeSnapshot` | Meal plan invoices |
| Other | JSON snapshot | `ChargeType` catalog + line items |

Billing must reference **`occupancyId`** as contract anchor.

---

## 13. Accommodation metadata

### 13.1 Amenities hierarchy

```
Space defaults (template)
  └── Room amenities (primary edit level for PG)
  └── Unit amenities (rental / co-living)
  └── Bed inherits room (no bed CRUD in v1)
```

**Master list:** `AmenityMaster` with codes (`WIFI`, `AC`, `GEYSER`, `CUPBOARD`, `PARKING`, `BALCONY`, `STUDY_TABLE`).

### 13.2 Rules & policies

| Level | v1 | v2 |
|-------|----|----|
| Space | Yes | — |
| Room | Defer | Optional |

Examples: No Smoking, No Alcohol, Gate closes 11 PM, Visitor restrictions.

### 13.3 Bed numbering templates

Extend existing `BedLabelStyle`:

| Template | Enum / pattern |
|----------|----------------|
| A, B, C, D | `ALPHA` (exists) |
| 1, 2, 3, 4 | `NUMERIC` (exists) |
| L1, L2, U1, U2 | `BUNK` or custom (v2) |

Template applies at generation time only — do not retroactively rename beds.

---

## 14. Gender restrictions

### 14.1 v1 scope

**Space-level only:** `MALE` | `FEMALE` | `MIXED`

### 14.2 Validation

On Reserve, Move-In, Transfer:

1. Load `space.genderPolicy`
2. Load `member.gender` (new field on member)
3. Reject mismatch with clear error

### 14.3 Deferred

Building, floor, room overrides.

---

## 15. Additional requirements

| Area | Recommendation | Phase |
|------|----------------|-------|
| Occupancy notes | `remarks` exists; add structured notes or extend | 4.3a |
| Bed locking | Use `BLOCKED` status + reason field | 4.3a |
| Maintenance blocking | `MAINTENANCE` status + block picker | 4.3a |
| Advance payment received | Defer to Payments | 5+ |
| Member category | `STUDENT`, `WORKING_PROFESSIONAL`, `FAMILY`, `GUEST`, `INTERN` | 4.3a |
| Household / family allocation | Future `UNIT` multi-member | Defer |
| No-show handling | `RESERVATION_CANCELLED` + reason | 4.3a |

---

## 16. Domain model changes

### 16.1 `OccupancyEntity` — new / changed fields

| Field | Type | Notes |
|-------|------|-------|
| `status` | Extend enum | Add `RESERVED`; keep `ACTIVE`, `VACATED` |
| `reservedAt` | `LocalDateTime` | When reservation created |
| `moveInDate` | `LocalDate` | Planned or actual start date |
| `actualMoveInAt` | `LocalDateTime` | Set on move-in promotion |
| `expectedExitDate` | `LocalDate` | Rename from `expectedCheckoutDate` (migrate) |
| `memberCategory` | Enum | Snapshot at move-in |
| `agreementSigned` | `boolean` | |
| `rentSnapshot` | `BigDecimal` | Phase 4.3b |
| `depositSnapshot` | `BigDecimal` | Phase 4.3b |
| `foodEnabled` | `boolean` | Phase 4.3b |
| `foodChargeSnapshot` | `BigDecimal` | Phase 4.3b |
| `otherChargesSnapshot` | JSON / separate table | Phase 4.3b |
| `notes` | `TEXT` | Or keep `remarks` and add `internalNotes` |

### 16.2 Accommodation catalog fields (Phase 4.3b / 4.4)

| Entity | New fields |
|--------|------------|
| `BedEntity` | `defaultRent`, `defaultDeposit` (optional) |
| `RoomEntity` | `defaultRent`, `amenities[]` |
| `UnitEntity` | `defaultRent`, `defaultDeposit`, `amenities[]` |
| `SpaceEntity` | `genderPolicy`, `defaultAmenities[]`, `defaultRules[]` |
| `MemberEntity` | `gender`, `memberCategory` |

### 16.3 New entities (Phase 4.4)

| Entity | Purpose |
|--------|---------|
| `AmenityMaster` | Code, label, category |
| `PolicyRuleMaster` | Code, label |
| `SpaceAmenity` / `RoomAmenity` | Join tables |

---

## 17. API changes (directional)

Base: `/api/v1/spaces/{spaceId}`

### 17.1 Phase 4.3a — Lifecycle

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/occupancies/reserve` | Create RESERVED occupancy |
| `POST` | `/occupancies/{id}/move-in` | Promote RESERVED → ACTIVE (or direct if walk-in) |
| `POST` | `/occupancies/{id}/cancel-reservation` | Cancel RESERVED |
| `POST` | `/occupancies` | **Extend** — support `mode: WALK_IN` (ACTIVE) vs deprecated immediate-only |
| `GET` | `/occupancies` | Filter by `status=RESERVED` |
| `POST` | `/beds/{id}/block` | Set BLOCKED (or use accommodation status update) |
| `POST` | `/beds/{id}/maintenance` | Set MAINTENANCE |

### 17.2 Phase 4.3b — Financial snapshots

| Change | Description |
|--------|-------------|
| Allocate / Move-In request | Add snapshot fields |
| Transfer request | Add `rentPolicy`, `customRent`, food fields |
| Occupancy response | Return all snapshot fields |
| Accommodation GET | Return `defaultRent`, `defaultDeposit` |

### 17.3 Phase 4.4 — Metadata

| Method | Path | Description |
|--------|------|-------------|
| `GET/PUT` | `/settings/accommodation` | Space gender, defaults |
| `GET/POST/PUT/DELETE` | `/amenities` | Master list (admin) |
| `PUT` | `/rooms/{id}/amenities` | Room amenities |
| `PUT` | `/units/{id}/amenities` | Unit amenities |

---

## 18. DTO changes (directional)

### 18.1 `ReserveOccupancyRequest`

```json
{
  "memberId": "uuid",
  "targetType": "BED",
  "bedId": "uuid",
  "moveInDate": "2026-06-20",
  "expectedExitDate": "2026-12-31",
  "memberCategory": "STUDENT",
  "agreementSigned": false,
  "remarks": "Booking for June intake"
}
```

### 18.2 `MoveInOccupancyRequest` (Phase 4.3b adds financial fields)

```json
{
  "actualMoveInDate": "2026-06-20",
  "rentSnapshot": 8000,
  "depositSnapshot": 10000,
  "foodEnabled": true,
  "foodChargeSnapshot": 3000,
  "otherChargesSnapshot": [
    { "code": "PARKING", "label": "Parking", "amount": 500 }
  ],
  "agreementSigned": true,
  "remarks": "Walk-in completed"
}
```

### 18.3 `TransferOccupancyRequest` (extend)

```json
{
  "targetType": "BED",
  "bedId": "uuid",
  "rentPolicy": "APPLY_NEW",
  "customRent": null,
  "foodEnabled": true,
  "remarks": "Moved to premium bed"
}
```

### 18.4 `OccupancyResponse` (extend)

Add: `status`, `reservedAt`, `moveInDate`, `actualMoveInAt`, `expectedExitDate`, `memberCategory`, `agreementSigned`, snapshot fields, `memberName` (exists).

---

## 19. Risk assessment

### 19.1 Mistakes that cause major refactors

| Mistake | Consequence |
|---------|-------------|
| Rent only on bed entity | Cannot honor old rent; billing breaks |
| `AccommodationStatus` as occupancy | Loses member audit |
| Separate reservation table | Duplicate sync |
| Immediate-only allocate | Cannot model admissions pipeline |
| Member deposit = occupancy deposit | Refund disputes |
| Full billing in move-in API | Scope creep |

### 19.2 Over-engineering risks

- Full charge engine before Billing
- Policy inheritance resolver
- Per-bed amenities CRUD
- Agreement document workflow before Documents module

---

## 20. Implementation phases

### Phase 4.3a — Occupancy lifecycle hardening (NEXT)

1. `OccupancyStatus.RESERVED`
2. Reserve / Move-In / Cancel-reservation endpoints
3. `moveInDate`, `actualMoveInAt`, `expectedExitDate`
4. `AccommodationStatusSyncService` — RESERVED sync
5. Maintenance / Blocked manual status on inventory
6. `MemberOccupancyStatus.RESERVED`
7. History events: RESERVED, MOVE_IN, RESERVATION_CANCELLED
8. Member category + agreement signed on occupancy
9. Gender policy on space + member gender (validation only)
10. Tests + update `docs/api-reference.md`

**Exit criteria:** Operator can reserve, move in, transfer, vacate, block bed, see reserved occupant on bed detail.

### Phase 4.3b — Financial snapshots

1. Catalog fields on bed/room/unit
2. Snapshot fields on occupancy
3. Move-In captures snapshots
4. Transfer pricing policy
5. Read-only financial summary in APIs

### Phase 4.4 — Accommodation metadata

1. Amenity master + room/unit assignment
2. Space rules
3. Quick Setup amenity bundles
4. Bed label template extensions

### Phase 4.5 — Availability engine (precursor to roadmap Phase 6)

1. Availability lists and dashboard
2. Vacancy forecasting from exit dates
3. Upcoming move-ins / move-outs

### Phase 5+ — Meals, Billing, Payments

Consume occupancy snapshots; no redesign of occupancy core.

---

## 21. Decision log

| Date | Decision |
|------|----------|
| 2026-06 | Reservation = RESERVED occupancy, not separate entity |
| 2026-06 | Move-In is separate action from Reserve |
| 2026-06 | Pricing: catalog on accommodation, snapshot on occupancy |
| 2026-06 | Transfer default rent policy: APPLY_NEW |
| 2026-06 | Gender policy: space-level v1 only |
| 2026-06 | Amenities: room-level primary; bed inherits |
| 2026-06 | CO_LIVING ROOM allocation: keep, do not expand until billing review |

---

## Appendix A — Backend implementation prompt

Copy everything inside the block below into Cursor (or your AI agent) when working in the **backend repository** (`amico-backend`).

---

### BACKEND PROMPT START

```markdown
# Amico Backend — Phase 4.3a/4.3b Occupancy Enhancements

You are a Senior Backend Architect implementing approved architecture in the Amico Spring Boot backend.

**Repository:** `amico-backend` (Java / Spring Boot)
**Architecture source of truth:** Frontend repo doc `docs/accommodation-occupancy-phase-4.3a-architecture.md` and `docs/accommodation-domain-model.md`

## Current baseline (do not break)

- Occupancy module exists under `com.amico.amico_backend.occupancy`
- `OccupancyStatus`: ACTIVE, VACATED
- `OccupancyHistoryEvent`: ALLOCATED, TRANSFERRED, VACATED
- `AccommodationStatus`: AVAILABLE, OCCUPIED, RESERVED, MAINTENANCE, BLOCKED
- `POST /api/v1/spaces/{spaceId}/occupancies` — immediate ACTIVE allocation
- `OccupancyTargetService.assertAllocatable()` — only AVAILABLE targets allowed today
- `AccommodationStatusSyncService` — syncs OCCUPIED/AVAILABLE only
- `expectedCheckoutDate` on OccupancyEntity
- Space types: PG/HOSTEL → BED only; CO_LIVING → BED or ROOM; RENTAL → UNIT only
- CO_LIVING ROOM allocation: keep but add code comment that billing rules are not finalized

## Non-negotiable architecture rules

1. **Do NOT create a separate reservations table.** Use `OccupancyStatus.RESERVED` on `occupancies`.
2. **AccommodationStatus ≠ OccupancyStatus.** Inventory status is stored on bed/room/unit and synced by a service.
3. **Bed is the atomic slot** for PG/HOSTEL. Room/Unit are alternate allocation targets per space type.
4. **Pricing:** `defaultRent`/`defaultDeposit` on accommodation entities (catalog). `rentSnapshot`/`depositSnapshot` on occupancy (contract). Never overwrite snapshots when catalog changes.
5. **Billing anchor = occupancyId** (design for future; no invoice generation in this phase).
6. **Backward compatibility:** existing `POST /occupancies` must continue to work as walk-in ACTIVE allocation (or alias to move-in with today's date).

## Phase 4.3a — Implement first (lifecycle)

### 1. Extend enums

**OccupancyStatus** — add:
- `RESERVED`

**OccupancyHistoryEvent** — add:
- `RESERVED`
- `MOVE_IN`
- `RESERVATION_CANCELLED`
(keep ALLOCATED for backward compatibility on walk-in)

**MemberOccupancyStatus** — add:
- `RESERVED` (member has reserved but not active occupancy)

**MemberGender** (new enum on member):
- `MALE`, `FEMALE`, `OTHER`, `UNSPECIFIED`

**GenderPolicy** (new enum on space):
- `MALE`, `FEMALE`, `MIXED`

**MemberCategory** (new enum):
- `STUDENT`, `WORKING_PROFESSIONAL`, `FAMILY`, `GUEST`, `INTERN`

### 2. Extend OccupancyEntity

Add columns:
- `reservedAt` (LocalDateTime, nullable)
- `moveInDate` (LocalDate, not null for new records)
- `actualMoveInAt` (LocalDateTime, nullable — set on move-in)
- `expectedExitDate` (LocalDate, nullable) — migrate data from `expectedCheckoutDate`
- `memberCategory` (enum, nullable)
- `agreementSigned` (boolean, default false)

Keep `expectedCheckoutDate` temporarily as deprecated alias or migrate in Flyway/Liquibase script.

### 3. New service methods (OccupancyService)

Implement transactional methods:

#### `reserve(spaceId, callerId, ReserveOccupancyRequest)`
- Validate member has no ACTIVE or RESERVED occupancy in space
- Validate target is AVAILABLE (not MAINTENANCE, BLOCKED, OCCUPIED, RESERVED)
- Validate `moveInDate` >= today (or allow today)
- Validate gender policy if space has genderPolicy and member has gender
- Create occupancy with status RESERVED, set reservedAt = now
- Set member MemberOccupancyStatus.RESERVED
- Sync accommodation target to RESERVED
- Write history event RESERVED

#### `moveIn(spaceId, occupancyId, callerId, MoveInOccupancyRequest)`
- Load RESERVED occupancy (or support walk-in promotion)
- Validate moveInDate reached (or allow early move-in with manager override flag if needed)
- Set status ACTIVE, actualMoveInAt = now
- Set member MemberOccupancyStatus.ALLOCATED
- Sync accommodation target to OCCUPIED
- Write history event MOVE_IN
- Phase 4.3b: capture financial snapshots from request (or pre-fill from catalog)

#### `cancelReservation(spaceId, occupancyId, callerId, CancelReservationRequest)`
- Only for RESERVED
- Set occupancy to VACATED (or add CANCELLED if preferred — document choice)
- Release inventory to AVAILABLE
- Update member status to VACATED if no other reserved/active
- Write history RESERVATION_CANCELLED

#### Update existing `allocate()`
- Treat as **walk-in move-in**: ACTIVE immediately, moveInDate = today, actualMoveInAt = now
- Write ALLOCATED history event (keep existing behavior)

#### Update `transfer()` and `vacate()`
- Handle RESERVED targets in sync service
- Re-validate gender on transfer

### 4. Update AccommodationStatusSyncService

| Occupancy status on target | Bed/Room/Unit status |
|--------------------------|-------------------|
| RESERVED exists | RESERVED |
| ACTIVE exists | OCCUPIED |
| Neither | AVAILABLE (unless MAINTENANCE/BLOCKED manually set) |

**Important:** MAINTENANCE and BLOCKED are operator-set and must NOT be overwritten by sync unless explicitly cleared.

Add method: `setMaintenance(spaceId, targetType, targetId)` and `setBlocked(...)` or use existing accommodation update endpoints with validation that no ACTIVE/RESERVED occupancy exists.

### 5. Update OccupancyTargetService

- `assertAllocatable()` — split into:
  - `assertReservable(status)` — AVAILABLE only
  - `assertMoveInTarget(status)` — RESERVED (for same member) or AVAILABLE
- Block allocation to MAINTENANCE/BLOCKED always

### 6. New / updated API endpoints

| Method | Path | Auth |
|--------|------|------|
| POST | `/occupancies/reserve` | OWNER, MANAGER |
| POST | `/occupancies/{id}/move-in` | OWNER, MANAGER |
| POST | `/occupancies/{id}/cancel-reservation` | OWNER, MANAGER |
| GET | `/occupancies?status=RESERVED` | OWNER, MANAGER, STAFF |

Keep existing endpoints working.

### 7. Update MemberOccupancyListResponse / CurrentOccupancySummaryResponse

- Include RESERVED occupancy in `currentOccupancy` or add `reservedOccupancy` field
- Member detail endpoint: expose `occupancyStatus` = ALLOCATED | RESERVED | VACATED

### 8. Space + Member fields

**SpaceEntity:**
- `genderPolicy` (enum, nullable — null means no restriction)

**MemberEntity:**
- `gender` (enum, nullable)
- `memberCategory` (enum, nullable)

**Validation service:** `GenderPolicyValidator.validate(space, member)`

### 9. Database migration

Create migration script:
- Add new columns to `occupancies`
- Add enums / columns to `members`, `spaces`
- Migrate `expected_checkout_date` → `expected_exit_date`
- Backfill existing ACTIVE occupancies: `move_in_date = allocated_at::date`, `actual_move_in_at = allocated_at`

### 10. Tests (mandatory)

Add/update tests in `OccupancyServiceTest` (or equivalent):
- Reserve bed → status RESERVED, bed RESERVED, member RESERVED
- Move-in → ACTIVE, bed OCCUPIED
- Cancel reservation → bed AVAILABLE
- Cannot reserve OCCUPIED bed
- Cannot move-in before moveInDate (if rule enforced)
- Walk-in allocate still works (ACTIVE)
- Transfer from ACTIVE bed to AVAILABLE bed
- Gender mismatch rejected
- MAINTENANCE bed cannot be reserved
- Building summary counts include reserved beds

## Phase 4.3b — Implement after 4.3a (financial snapshots)

### 1. Accommodation catalog fields

Add to BedEntity, RoomEntity, UnitEntity (as appropriate):
- `defaultRent` (BigDecimal, nullable)
- `defaultDeposit` (BigDecimal, nullable)

Expose in GET responses and allow PUT on accommodation update DTOs.

### 2. Occupancy snapshot fields

Add to OccupancyEntity:
- `rentSnapshot` (BigDecimal)
- `depositSnapshot` (BigDecimal)
- `foodEnabled` (boolean)
- `foodChargeSnapshot` (BigDecimal, nullable)
- `otherChargesSnapshot` (JSON column or separate `occupancy_charge_lines` table)

### 3. MoveInOccupancyRequest / AllocateOccupancyRequest

- Accept snapshot fields
- If omitted, pre-fill from target's defaultRent/defaultDeposit and space food defaults
- Snapshots are **immutable** after move-in (transfer creates new occupancy with new snapshots)

### 4. TransferOccupancyRequest

Add:
- `rentPolicy`: KEEP | APPLY_NEW | CUSTOM
- `customRent` (when CUSTOM)
- `foodEnabled`, `foodChargeSnapshot` (optional reset)

Transfer logic:
- CREATE new occupancy with new snapshots per policy
- VACATE old occupancy
- Default rentPolicy = APPLY_NEW

### 5. API documentation

Update OpenAPI annotations and frontend `docs/api-reference.md` companion.

## Phase 4.4 — Metadata (separate PR)

Defer unless explicitly requested:
- AmenityMaster, PolicyRuleMaster
- Room/Unit amenity join tables
- Space settings endpoints

## Implementation constraints

- Follow existing package structure and patterns (entity, repository, service, controller, dto)
- Use existing `BusinessException`, `ResourceNotFoundException`
- Use `@Transactional` on all occupancy mutations
- Preserve `OccupancyAccessService` permission checks
- Update `AccommodationOccupancyPort` if occupancy history checks affected
- Do not implement invoice/payment/meal modules
- Add code comment on CO_LIVING ROOM in `OccupancyTargetService`:
  `// Room allocation rules are not finalized. Keep implementation; do not expand. Future billing review required.`

## Deliverables

1. Flyway/Liquibase migration(s)
2. Updated enums, entities, DTOs, services, controllers
3. Updated `AccommodationStatusSyncService`
4. Unit + integration tests
5. Brief `docs/occupancy-phase-4.3a-backend.md` in backend repo summarizing API changes

## Verification checklist

- [ ] Reserve → bed shows RESERVED, list occupancies returns RESERVED
- [ ] Move-in → bed shows OCCUPIED, member ALLOCATED
- [ ] Cancel → bed AVAILABLE
- [ ] Walk-in allocate (existing API) still works
- [ ] Transfer + vacate work with RESERVED/ACTIVE sync
- [ ] GET bed detail can resolve occupant for ACTIVE and RESERVED
- [ ] Gender validation works when configured
- [ ] MAINTENANCE/BLOCKED beds reject reserve/allocate
- [ ] All tests pass
```

### BACKEND PROMPT END

---

## Appendix B — Frontend follow-up (reference only)

After backend Phase 4.3a ships, update React Native:

1. Reserve / Move-In / Cancel flows in `OccupancyTargetPickerModal`
2. `MemberAccommodationSection` — show RESERVED state
3. `AccommodationOccupantSection` — show reserved occupant
4. i18n keys for lifecycle states
5. `docs/accommodation-occupancy-ui-integration.md` — new endpoints

Frontend prompt should be generated separately after backend API is merged.

---

*End of document.*
