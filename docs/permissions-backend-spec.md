# Acomi — Permissions Backend Specification (Phase 4.5)

**Purpose:** Single source of truth for **server-side** role enforcement across Acomi modules.  
**Audience:** Backend (Spring Boot) implementers.  
**Frontend contract:** React Native already mirrors these rules in `src/utils/*Permissions.ts` — backend must enforce the same policy and return consistent `403` responses.

**Related docs:**

- [development-roadmap.md](./development-roadmap.md) — Phase 4.5 permissions polish
- [accommodation-ui-integration.md](./accommodation-ui-integration.md) — structure APIs
- [accommodation-occupancy-ui-integration.md](./accommodation-occupancy-ui-integration.md) — occupancy APIs
- [membership-ui-integration.md](./membership-ui-integration.md) — member & invitation APIs
- [member-management-ui-integration.md](./member-management-ui-integration.md) — member profile APIs

---

## 1. Goals

1. **Enforce permissions on every write and sensitive read** — never rely on UI hiding alone.
2. **Centralize role logic** — one policy layer, not copy-pasted `if (role == OWNER)` in controllers.
3. **Scope TENANT reads to own data** — tenants see their stay, not the full inventory.
4. **Deny CUSTOMER accommodation access** — mess/customer roles must not browse structure or occupancies.
5. **Preserve OWNER-only destructive actions** — deactivate, restore, permanent delete, remove member.
6. **Return predictable errors** — `403 Forbidden` with stable message keys for the mobile app.

---

## 2. Core concepts

### 2.1 SpaceMembership (access role)

Every API call under `/api/v1/spaces/{spaceId}/...` must resolve:

| Input | Source |
|-------|--------|
| `callerUserId` | JWT subject |
| `spaceId` | path variable |
| `membershipRole` | `SpaceMembership` where `userId = callerUserId`, `spaceId = spaceId`, `status = ACTIVE` |
| `membershipId` | same row |

If no active membership → `403` (not `404`).

**MembershipRole enum (frozen):**

```
OWNER | MANAGER | TENANT | CUSTOMER | STAFF
```

### 2.2 Member record (operational profile)

- A **Member** row is the PG/hostel operational profile (deposit, documents, occupancy).
- `Member.userId` may link to a `User` (app login) or be null (offline tenant).
- **Member.role** is copied from membership on create; used to decide if someone can be allocated a bed.

**Resident roles** (eligible for occupancy): `TENANT`, `CUSTOMER`, `STAFF`  
**Non-resident roles** (never allocated): `OWNER`, `MANAGER`

### 2.3 Viewer role vs subject member role

| Check | Example |
|-------|---------|
| **Viewer role** | Can the *caller* open accommodation structure? |
| **Subject member role** | Can *this member* be move-in / transfer / vacate target? |

Occupancy **write** requires viewer `OWNER | MANAGER` **and** subject member in resident roles.

### 2.4 Space type gate

Accommodation APIs apply only when `space.type` ∈ `{ PG, HOSTEL, CO_LIVING, RENTAL }`.  
For `MESS` → `403` or dedicated `404` with message `Accommodation not applicable for MESS spaces`.

---

## 3. Role definitions (product policy)

| Role | Summary |
|------|---------|
| **OWNER** | Full control — structure lifecycle delete/deactivate, occupancy, members, invitations |
| **MANAGER** | Day-to-day operations — structure create/edit, occupancy, members; **no** deactivate/delete/remove |
| **STAFF** | Operational read — view structure inventory and all occupancies; **no** writes |
| **TENANT** | Resident self-service read — **own** occupancy/allocation only; **no** structure browse |
| **CUSTOMER** | **No** accommodation access (mess subscriber / non-resident) |

---

## 4. Recommended backend architecture

### 4.1 Services (modular monolith)

```
SpaceMembershipResolver     → resolve caller membership or throw 403
AccommodationAccessService  → structure read/write/deactivate
OccupancyAccessService      → occupancy read/write + own-scope for TENANT
MemberAccessService         → member CRUD (may already exist)
InvitationAccessService     → invite/cancel
```

Each controller/service calls the access service **before** business logic.

### 4.2 Enforcement pattern

```java
// Example
public BuildingResponse createBuilding(UUID spaceId, UUID callerId, CreateBuildingRequest req) {
    var membership = membershipResolver.requireActive(spaceId, callerId);
    accommodationAccess.requireManageStructure(membership);
    return buildingService.create(spaceId, req);
}
```

### 4.3 Error contract

| HTTP | When | Body example |
|------|------|----------------|
| `403` | Role or scope denied | `{ "message": "Only OWNER or MANAGER can perform this action", "code": "FORBIDDEN" }` |
| `403` | No membership | `{ "message": "You are not a member of this space", "code": "NOT_A_MEMBER" }` |
| `403` | Own-scope violation | `{ "message": "You can only view your own occupancy", "code": "OWN_SCOPE_ONLY" }` |

Use **stable English messages** — frontend maps known strings today.

### 4.4 Optional: expose capabilities to mobile (recommended)

Extend `MySpaceResponse` (from `GET /spaces/my`) with a computed block:

```json
{
  "spaceId": "...",
  "membershipRole": "MANAGER",
  "permissions": {
    "canViewAccommodation": true,
    "canManageAccommodation": true,
    "canDeactivateAccommodation": false,
    "canManageOccupancy": true,
    "canViewSpaceOccupancies": true,
    "canManageMembers": true,
    "canRemoveMember": false
  }
}
```

This lets the React Native app hide tabs without duplicating policy long-term.

---

## 5. Permission matrix — Accommodation structure

Applies to: buildings, floors, units, rooms, beds, builder summary, quick setup, duplicate, bulk.

| Action | OWNER | MANAGER | STAFF | TENANT | CUSTOMER |
|--------|:-----:|:-------:|:-----:|:------:|:--------:|
| View structure (all GET list/detail/summary) | ✅ | ✅ | ✅ | ❌ | ❌ |
| Create / Update (POST, PUT) | ✅ | ✅ | ❌ | ❌ | ❌ |
| Quick Setup execute | ✅ | ✅ | ❌ | ❌ | ❌ |
| Duplicate / Bulk create | ✅ | ✅ | ❌ | ❌ | ❌ |
| Deactivate / Restore / Delete (POST lifecycle) | ✅ | ❌ | ❌ | ❌ | ❌ |
| Allocation-target search `GET .../allocation-targets` | ✅ | ✅ | ❌ | ❌ | ❌ |

**Notes:**

- `GET .../buildings/{id}/summary` follows **view structure** row.
- Detail `actions.canEdit`, `actions.canDeactivate`, `actions.canDelete` in responses should reflect caller role (frontend uses these).
- Inline rename (`PUT` name field) follows **Create / Update**.

### Endpoints (structure) — enforce as above

| Method | Path pattern |
|--------|----------------|
| `GET` | `/spaces/{spaceId}/buildings/**` |
| `POST` | `/spaces/{spaceId}/buildings` |
| `PUT` | `/spaces/{spaceId}/buildings/{buildingId}` |
| `POST` | `/spaces/{spaceId}/buildings/{buildingId}/deactivate\|restore\|delete` |
| Same pattern | `/floors/**`, `/units/**`, `/rooms/**`, `/beds/**` |
| `POST` | `/spaces/{spaceId}/accommodation/quick-setup/**` |
| `POST` | `/spaces/{spaceId}/accommodation/**/duplicate` |
| `POST` | `/spaces/{spaceId}/accommodation/**/bulk-*` |
| `GET` | `/spaces/{spaceId}/accommodation/allocation-targets` |

---

## 6. Permission matrix — Occupancy

| Action | OWNER | MANAGER | STAFF | TENANT | CUSTOMER |
|--------|:-----:|:-------:|:-----:|:------:|:--------:|
| Allocate / Walk-in `POST /occupancies` | ✅ | ✅ | ❌ | ❌ | ❌ |
| Reserve `POST /occupancies/reserve` | ✅ | ✅ | ❌ | ❌ | ❌ |
| Move-in `POST /occupancies/{id}/move-in` | ✅ | ✅ | ❌ | ❌ | ❌ |
| Transfer `POST /occupancies/{id}/transfer` | ✅ | ✅ | ❌ | ❌ | ❌ |
| Vacate `POST /occupancies/{id}/vacate` | ✅ | ✅ | ❌ | ❌ | ❌ |
| Cancel reservation `POST /occupancies/{id}/cancel-reservation` | ✅ | ✅ | ❌ | ❌ | ❌ |
| List space occupancies `GET /occupancies` | ✅ | ✅ | ✅ | ❌ | ❌ |
| Get occupancy `GET /occupancies/{id}` | ✅ | ✅ | ✅ | ✅ own only | ❌ |
| Member occupancies `GET /members/{memberId}/occupancies` | ✅ | ✅ | ✅ | ✅ own member only | ❌ |
| Occupancy history | ✅ | ✅ | ✅ | ✅ own only | ❌ |

### Own-scope rules (TENANT)

`TENANT` may read occupancy data **only when**:

```
member.userId == callerUserId
```

Apply on:

- `GET /occupancies/{occupancyId}`
- `GET /occupancies?memberId=...` (reject if memberId not caller's linked member)
- `GET /members/{memberId}/occupancies` (reject if member not linked to caller)

Return `403` + `OWN_SCOPE_ONLY` when violated.

### Occupancy write validation (in addition to role)

| Rule | Enforcement |
|------|-------------|
| Caller role | `OWNER` or `MANAGER` |
| Subject member role | `TENANT`, `CUSTOMER`, or `STAFF` — reject `OWNER`/`MANAGER` as allocate target |
| Target status | `AVAILABLE` for allocate/reserve; `MAINTENANCE`/`BLOCKED` always rejected |
| Space type target | `BED` / `ROOM` / `UNIT` per space profile |

---

## 7. Permission matrix — Members & invitations

Already largely implemented; include for completeness.

| Action | OWNER | MANAGER | STAFF | TENANT | CUSTOMER |
|--------|:-----:|:-------:|:-----:|:------:|:--------:|
| View members list / detail | ✅ | ✅ | ✅ | ✅ | ✅ |
| Add / update member | ✅ | ✅ | ❌ | ❌ | ❌ |
| Remove member `DELETE /members/{id}` | ✅ | ❌ | ❌ | ❌ | ❌ |
| Update status / deposit / documents / notes | ✅ | ✅ | ❌ | ❌ | ❌ |
| Send / cancel invitation | ✅ | ✅ | ❌ | ❌ | ❌ |
| Accept invitation | ✅ if invited | ✅ if invited | ✅ if invited | ✅ if invited | ✅ if invited |

**Invariant:** `OWNER` role cannot be assigned via member create/update/invite APIs.

---

## 8. Permission matrix — Dashboard reads (future wiring)

When dashboard metrics APIs are added:

| Metric API (proposed) | OWNER | MANAGER | STAFF | TENANT | CUSTOMER |
|-----------------------|:-----:|:-------:|:-----:|:------:|:--------:|
| Occupancy %, vacant/reserved counts | ✅ | ✅ | ✅ | ❌ | ❌ |
| Meal counts | ✅ | ✅ | ✅ | ❌ | ❌ |
| Rent collected summary | ✅ | ✅ | ❌ | ❌ | ❌ |

Financial summaries should not be exposed to `STAFF` unless product policy changes.

---

## 9. Implementation checklist

### Phase A — Central access layer

- [ ] `SpaceMembershipResolver.requireActive(spaceId, userId)`
- [ ] `AccommodationAccessService` with methods:
  - `requireViewStructure(membership)`
  - `requireManageStructure(membership)`
  - `requireDeactivateStructure(membership)` → OWNER only
- [ ] `OccupancyAccessService` with methods:
  - `requireManageOccupancy(membership)` → OWNER, MANAGER
  - `requireViewSpaceOccupancies(membership)` → OWNER, MANAGER, STAFF
  - `requireViewMemberOccupancy(membership, memberId)` → ops roles OR own member
  - `assertSubjectIsResident(member)`

### Phase B — Wire controllers

- [ ] All accommodation structure controllers use `AccommodationAccessService`
- [ ] All occupancy controllers use `OccupancyAccessService` (extend existing `OccupancyAccessService` if present)
- [ ] MESS space type guard on accommodation module entry points
- [ ] Detail GET responses populate `actions` flags from caller role

### Phase C — TENANT / CUSTOMER hardening

- [ ] Deny structure GETs for TENANT and CUSTOMER
- [ ] Deny `GET /occupancies` list for TENANT and CUSTOMER
- [ ] Own-scope checks on occupancy GETs for TENANT
- [ ] Deny allocation-target search for non OWNER/MANAGER

### Phase D — Tests

- [ ] Parameterized tests per role for one endpoint per category (structure GET, structure POST, deactivate, allocate, list occupancies, own occupancy GET)
- [ ] TENANT reading another member's occupancy → 403
- [ ] MANAGER deactivate building → 403
- [ ] CUSTOMER structure GET → 403
- [ ] STAFF structure GET → 200; STAFF allocate → 403

### Phase E — Optional API enhancement

- [ ] Add `permissions` object to `MySpaceResponse`
- [ ] Document in `api-reference.md`

---

## 10. Frontend alignment (after backend ships)

React Native will update:

| File | Change |
|------|--------|
| `accommodationPermissions.ts` | `canViewAccommodation` → false for TENANT, CUSTOMER |
| `SpaceTabNavigator.tsx` | Hide Accommodation tab when `!canViewAccommodation` |
| `occupancyPermissions.ts` | Already aligned; verify own-scope error handling |
| `DashboardScreen.tsx` | Hide Residents card when `!canManageOccupancy` (already gated) |

---

## 11. Copy-paste backend implementation prompt

Use this prompt in the **backend repository** (Cursor / Claude / team handoff):

```markdown
# Task: Implement Acomi Phase 4.5 — Centralized Space Permissions (Backend)

## Context

Acomi is a Spring Boot 3 / Java 17 modular monolith for PG, Hostel, Co-Living, and Rental operations. Authentication is JWT (mobile OTP). Access is modeled via `SpaceMembership` with roles:

`OWNER | MANAGER | TENANT | CUSTOMER | STAFF`

The React Native frontend already implements UI permission helpers but **server enforcement is incomplete**. CUSTOMER and TENANT can currently hit accommodation APIs they should not access. TENANT occupancy reads need own-scope enforcement.

**Source of truth:** Copy the policy from the frontend repo doc `docs/permissions-backend-spec.md` (or the spec pasted below).

## Architecture requirements

1. Do NOT create microservices.
2. Add/extend access services in the accommodation and occupancy modules:
   - `SpaceMembershipResolver` — resolve active membership for `(spaceId, callerUserId)` or throw 403 `NOT_A_MEMBER`
   - `AccommodationAccessService` — structure view/manage/deactivate
   - `OccupancyAccessService` — occupancy view/manage + TENANT own-scope
3. Enforce in **service layer** (not only controllers). Controllers stay thin.
4. Preserve existing API paths and response DTOs. This is policy hardening, not an API redesign.
5. MESS `space.type` must reject accommodation module endpoints.
6. Return HTTP 403 with stable `message` strings matching existing app docs.

## Role policy to implement

### Accommodation structure (buildings, floors, units, rooms, beds, summary, quick-setup, duplicate, bulk, allocation-targets search)

| Action | OWNER | MANAGER | STAFF | TENANT | CUSTOMER |
|--------|-------|---------|-------|--------|----------|
| View (GET) | Y | Y | Y | N | N |
| Create/Update/QuickSetup/Duplicate/Bulk | Y | Y | N | N | N |
| Deactivate/Restore/Delete | Y | N | N | N | N |
| GET allocation-targets | Y | Y | N | N | N |

### Occupancy

| Action | OWNER | MANAGER | STAFF | TENANT | CUSTOMER |
|--------|-------|---------|-------|--------|----------|
| POST allocate, reserve, move-in, transfer, vacate, cancel-reservation | Y | Y | N | N | N |
| GET /occupancies (list) | Y | Y | Y | N | N |
| GET /occupancies/{id} | Y | Y | Y | own only | N |
| GET /members/{memberId}/occupancies | Y | Y | Y | own member only | N |

**Own-scope:** TENANT may read occupancy only when `member.userId == callerUserId`.

**Occupancy write extras:** reject allocate/reserve/transfer/vacate when subject member role is OWNER or MANAGER. Allow TENANT, CUSTOMER, STAFF as residents.

### Members (verify existing behavior)

- Add/update/invite: OWNER, MANAGER
- Remove member: OWNER only
- View: all active members

## Implementation steps

1. Audit existing permission checks in accommodation and occupancy controllers/services.
2. Create/refactor access services with explicit methods (`requireViewStructure`, `requireManageStructure`, `requireDeactivateStructure`, `requireManageOccupancy`, `requireViewSpaceOccupancies`, `requireViewMemberOccupancy`).
3. Apply checks to every endpoint listed in `permissions-backend-spec.md` sections 5–6.
4. Update detail GET `actions` flags (`canEdit`, `canDeactivate`, `canDelete`) based on caller role.
5. Add parameterized integration tests per role.
6. (Optional) Add `permissions` map to `MySpaceResponse` on `GET /spaces/my`.

## Testing (mandatory)

- MANAGER can POST building, cannot POST deactivate
- OWNER can POST deactivate
- STAFF can GET buildings, cannot POST building
- TENANT GET buildings → 403
- CUSTOMER GET buildings → 403
- TENANT GET own `/members/{id}/occupancies` → 200
- TENANT GET another member's occupancies → 403
- STAFF GET `/occupancies` → 200
- STAFF POST `/occupancies` → 403
- MESS space GET buildings → 403 or not-applicable error

## Deliverables

1. Access service classes + wiring
2. Flyway not required unless adding columns
3. Unit/integration tests
4. Brief `docs/permissions-phase-4.5-backend.md` summarizing changes
5. List of endpoints touched

Do not implement meal, payment, or dashboard metric APIs — permissions only.
```

---

## 12. Changelog

| Date | Change |
|------|--------|
| 2026-06 | Initial spec — Phase 4.5 backend permissions |
