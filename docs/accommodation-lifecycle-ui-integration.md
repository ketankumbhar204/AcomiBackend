# Acomi — Accommodation Lifecycle UI Integration

Frontend guide for **Deactivate**, **Restore**, and **Permanent Delete** on accommodation structure entities.

Use this after Quick Setup, Manual Builder, or Bulk operations when operators need to trim or correct generated inventory.

**Related docs:**

- [accommodation-ui-integration.md](./accommodation-ui-integration.md) — Phase 4.1 CRUD, enums, base URLs
- [accommodation-lazy-loading-ui-integration.md](./accommodation-lazy-loading-ui-integration.md) — List GETs return lightweight paginated items; invalidate list + summary keys after lifecycle actions
- [accommodation-phase-4.2-ui-integration.md](./accommodation-phase-4.2-ui-integration.md) — Quick Setup, Builder, Bulk
- [my-spaces-ui-integration.md](./my-spaces-ui-integration.md) — `spaceId`, `membershipRole`

---

## Breaking change (mobile / web)

**Old:** `DELETE /buildings/{id}` (and similar) performed soft-delete.

**New:** Lifecycle uses explicit `POST` actions. `DELETE` on accommodation resources is **removed**.

| Old (removed) | New |
|---------------|-----|
| `DELETE .../buildings/{buildingId}` | `POST .../buildings/{buildingId}/deactivate` |
| — | `POST .../buildings/{buildingId}/restore` |
| — | `POST .../buildings/{buildingId}/delete` |

Same pattern for floor, unit, room, bed.

---

## Three actions — when to use each

| Action | Purpose | Data | Typical use |
|--------|---------|------|-------------|
| **Deactivate** | Hide from lists; keep row in DB | `isActive = false` | Production entity, historical use |
| **Restore** | Show in lists again | `isActive = true` | Undo accidental deactivate |
| **Delete** | Remove row permanently | Row deleted | Quick Setup mistake, extra generated beds/rooms |

**UI rule:** Users should **rarely** see Delete. Show it only when `actions.canDelete === true` on the entity detail GET.

---

## Permissions

| Action | OWNER | MANAGER | Others |
|--------|-------|---------|--------|
| View structure (GET) | Yes | Yes | Yes (space member) |
| Edit (PUT) | Yes | Yes | No |
| Deactivate | Yes | No | No |
| Restore | Yes | No | No |
| Delete (permanent) | Yes | No | No |

Drive button visibility from `actions` on detail responses **and** `membershipRole` from `GET /api/v1/spaces/my`.

---

## Action metadata (no separate eligibility API)

Detail GET responses include `actions`. **Do not** call a separate `/deletion-eligibility` endpoint.

```typescript
export interface AccommodationActionMetadata {
  canEdit: boolean;
  canDeactivate: boolean;
  canRestore: boolean;
  canDelete: boolean;
  /** Set when owner views entity and permanent delete is blocked */
  deleteReason?: string | null;
}
```

### Example — building detail

```json
GET /api/v1/spaces/{spaceId}/buildings/{buildingId}

{
  "success": true,
  "data": {
    "buildingId": "8c1b6b59-21c5-4c54-b6c2-88a5b57e35ed",
    "spaceId": "04bef4a9-de63-4f28-9206-52c67d31dd74",
    "name": "Building B",
    "code": "A wing",
    "active": true,
    "createdAt": "2026-06-10T15:32:01",
    "updatedAt": "2026-06-10T15:32:01",
    "actions": {
      "canEdit": true,
      "canDeactivate": false,
      "canRestore": false,
      "canDelete": false,
      "deleteReason": "Cannot delete building because floors still exist."
    }
  }
}
```

### Which GET returns `actions`?

| Entity | Detail GET (includes `actions`) |
|--------|----------------------------------|
| Building | `GET .../buildings/{buildingId}` |
| Floor | `GET .../buildings/{buildingId}/floors/{floorId}` **or** `GET .../floors/{floorId}` |
| Unit | `GET .../buildings/{buildingId}/units/{unitId}` **or** `GET .../units/{unitId}` |
| Room | `GET .../rooms/{roomId}` |
| Bed | `GET .../rooms/{roomId}/beds/{bedId}` **or** `GET .../beds/{bedId}` |

List endpoints (`GET .../buildings`, `GET .../floors`, etc.) do **not** include `actions` — load detail when opening edit/lifecycle sheet.

---

## Endpoint reference

Path prefix: `/api/v1/spaces/{spaceId}`

All lifecycle calls: `POST` → **`204 No Content`** (empty body, no `ApiResponse` envelope).

Headers:

```
Authorization: Bearer <accessToken>
```

### Building

| Action | Path |
|--------|------|
| Deactivate | `POST .../buildings/{buildingId}/deactivate` |
| Restore | `POST .../buildings/{buildingId}/restore` |
| Delete | `POST .../buildings/{buildingId}/delete` |

### Floor

Flat (preferred in Builder when you only have `floorId`):

| Action | Path |
|--------|------|
| Detail | `GET .../floors/{floorId}` |
| Deactivate | `POST .../floors/{floorId}/deactivate` |
| Restore | `POST .../floors/{floorId}/restore` |
| Delete | `POST .../floors/{floorId}/delete` |

Nested (same behavior):

| Action | Path |
|--------|------|
| Deactivate | `POST .../buildings/{buildingId}/floors/{floorId}/deactivate` |
| Restore | `POST .../buildings/{buildingId}/floors/{floorId}/restore` |
| Delete | `POST .../buildings/{buildingId}/floors/{floorId}/delete` |

### Unit

| Action | Flat path |
|--------|-----------|
| Detail | `GET .../units/{unitId}` |
| Deactivate | `POST .../units/{unitId}/deactivate` |
| Restore | `POST .../units/{unitId}/restore` |
| Delete | `POST .../units/{unitId}/delete` |

Nested alias: `POST .../buildings/{buildingId}/units/{unitId}/...`

### Room

| Action | Path |
|--------|------|
| Deactivate | `POST .../rooms/{roomId}/deactivate` |
| Restore | `POST .../rooms/{roomId}/restore` |
| Delete | `POST .../rooms/{roomId}/delete` |

### Bed

| Action | Flat path |
|--------|-----------|
| Detail | `GET .../beds/{bedId}` |
| Deactivate | `POST .../beds/{bedId}/deactivate` |
| Restore | `POST .../beds/{bedId}/restore` |
| Delete | `POST .../beds/{bedId}/delete` |

Nested alias: `POST .../rooms/{roomId}/beds/{bedId}/...`

---

## TypeScript types

```typescript
export interface AccommodationActionMetadata {
  canEdit: boolean;
  canDeactivate: boolean;
  canRestore: boolean;
  canDelete: boolean;
  deleteReason?: string | null;
}

export interface BuildingResponse {
  buildingId: string;
  spaceId: string;
  name: string;
  code?: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  actions?: AccommodationActionMetadata; // present on detail GET only
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
  actions?: AccommodationActionMetadata;
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
  actions?: AccommodationActionMetadata;
}

export interface RoomResponse {
  roomId: string;
  floorId?: string | null;
  unitId?: string | null;
  buildingId?: string | null;
  name: string;
  roomNumber: string;
  roomType: RoomType;
  capacity: number;
  status: AccommodationStatus;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  actions?: AccommodationActionMetadata;
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
  actions?: AccommodationActionMetadata;
}
```

---

## UI — Manual Builder action sheet

### Button visibility (from `actions`)

```typescript
function LifecycleActions({ actions, role }: { actions: AccommodationActionMetadata; role: MembershipRole }) {
  const isOwner = role === 'OWNER';

  return (
    <>
      {actions.canEdit && <Button title="Edit" />}
      {isOwner && actions.canDeactivate && <Button title="Deactivate" variant="warning" />}
      {isOwner && actions.canRestore && <Button title="Restore" variant="secondary" />}
      {isOwner && actions.canDelete && <Button title="Delete permanently" variant="destructive" />}
      {/* Optional: owner-only hint when delete blocked */}
      {isOwner && !actions.canDelete && actions.deleteReason && (
        <Text variant="caption">{actions.deleteReason}</Text>
      )}
    </>
  );
}
```

**Do not** show Delete when `canDelete` is false — even if the user is OWNER.

### Confirm dialogs

| Action | Suggested copy |
|--------|----------------|
| Deactivate | "Hide {name} from lists? History is preserved. You can restore it later." |
| Restore | "Restore {name} and show it in lists again?" |
| Delete | "Permanently remove {name}? This cannot be undone." |

### After success (`204`)

| Action | UI behavior |
|--------|-------------|
| Deactivate | Remove from list; pop detail screen or show "Deactivated" state |
| Restore | Refresh list; entity reappears |
| Delete | Remove from list; navigate back to parent (floor / room / building) |

Invalidate React Query keys for parent lists, e.g. `['beds', spaceId, roomId]`, `['rooms', spaceId, floorId]`.

---

## API client helpers

```typescript
const BASE = '/api/v1/spaces';

async function postLifecycle(url: string): Promise<void> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { Authorization: `Bearer ${getAccessToken()}` },
  });
  if (res.status === 204) return;
  const json = await res.json().catch(() => null);
  throw new Error(json?.message ?? `Request failed (${res.status})`);
}

export const accommodationLifecycle = {
  deactivateBuilding: (spaceId: string, buildingId: string) =>
    postLifecycle(`${BASE}/${spaceId}/buildings/${buildingId}/deactivate`),
  restoreBuilding: (spaceId: string, buildingId: string) =>
    postLifecycle(`${BASE}/${spaceId}/buildings/${buildingId}/restore`),
  deleteBuilding: (spaceId: string, buildingId: string) =>
    postLifecycle(`${BASE}/${spaceId}/buildings/${buildingId}/delete`),

  deactivateFloor: (spaceId: string, floorId: string) =>
    postLifecycle(`${BASE}/${spaceId}/floors/${floorId}/deactivate`),
  restoreFloor: (spaceId: string, floorId: string) =>
    postLifecycle(`${BASE}/${spaceId}/floors/${floorId}/restore`),
  deleteFloor: (spaceId: string, floorId: string) =>
    postLifecycle(`${BASE}/${spaceId}/floors/${floorId}/delete`),

  deactivateUnit: (spaceId: string, unitId: string) =>
    postLifecycle(`${BASE}/${spaceId}/units/${unitId}/deactivate`),
  restoreUnit: (spaceId: string, unitId: string) =>
    postLifecycle(`${BASE}/${spaceId}/units/${unitId}/restore`),
  deleteUnit: (spaceId: string, unitId: string) =>
    postLifecycle(`${BASE}/${spaceId}/units/${unitId}/delete`),

  deactivateRoom: (spaceId: string, roomId: string) =>
    postLifecycle(`${BASE}/${spaceId}/rooms/${roomId}/deactivate`),
  restoreRoom: (spaceId: string, roomId: string) =>
    postLifecycle(`${BASE}/${spaceId}/rooms/${roomId}/restore`),
  deleteRoom: (spaceId: string, roomId: string) =>
    postLifecycle(`${BASE}/${spaceId}/rooms/${roomId}/delete`),

  deactivateBed: (spaceId: string, bedId: string) =>
    postLifecycle(`${BASE}/${spaceId}/beds/${bedId}/deactivate`),
  restoreBed: (spaceId: string, bedId: string) =>
    postLifecycle(`${BASE}/${spaceId}/beds/${bedId}/restore`),
  deleteBed: (spaceId: string, bedId: string) =>
    postLifecycle(`${BASE}/${spaceId}/beds/${bedId}/delete`),
};
```

---

## Error handling

Lifecycle failures return `400` with `ApiResponse` envelope:

```json
{
  "success": false,
  "message": "Cannot deactivate room while active beds exist. Deactivate beds first.",
  "data": null
}
```

Show `message` verbatim in a toast or alert.

### Common messages

| Message (examples) | Meaning | UI hint |
|--------------------|---------|---------|
| `Cannot deactivate room while active beds exist...` | Children still active | Deactivate beds first |
| `Cannot delete Building Building A because occupancy history exists within the accommodation structure. Deactivate instead.` | Subtree has history | Show Deactivate only |
| `Cannot delete bed because it is currently occupied.` | Status or future occupancy | Use Deactivate instead |
| `Cannot delete bed because occupancy history exists.` | Was allocated before | Deactivate only |
| `Only the space owner can perform this action` | Wrong role | Hide action for non-owners |

### Deactivate vs delete rules

| Entity | Deactivate blocked when | Delete blocked when |
|--------|-------------------------|---------------------|
| Building | Active floors **or** active units | Any node in subtree has occupancy, history, or external refs |
| Floor | Active rooms | Subtree (rooms + beds) not clean |
| Unit | Active rooms | Subtree (rooms + beds) not clean |
| Room | Active beds | Room or its beds not clean |
| Bed | — | Bed occupied / history / external refs |

Children **do not** block cascade delete — they are deleted with the parent when the subtree is clean.

---

## Smart cascade delete (no manual child order)

Users **never** need to delete beds before rooms, or rooms before floors. The backend validates the **entire subtree** and cascade-deletes in one transaction.

| User action | Backend deletes automatically |
|-------------|-------------------------------|
| Delete Bed | Bed only |
| Delete Room | Room + all beds in that room |
| Delete Floor | Floor + all rooms + all beds on that floor |
| Delete Unit | Unit + all rooms + all beds in that unit |
| Delete Building | Building + all floors/units/rooms/beds |

**When `canDelete` is true** on a parent, the user can tap Delete once — children are removed automatically.

**When blocked** (occupancy history, active occupancy, or external references anywhere in the subtree):

```
Cannot delete Building Building A because occupancy history exists within the accommodation structure. Deactivate instead.
```

Show Deactivate as the alternative. Do **not** tell users to "delete beds first."

### Setup cleanup workflow (Quick Setup / Bulk)

To remove an entire unused building after Quick Setup:

```
POST .../buildings/{buildingId}/delete
```

To trim one extra room (and its beds):

```
POST .../rooms/{roomId}/delete
```

Refresh building summary after cleanup: `GET .../buildings/{buildingId}/summary`.

---

## Screens checklist

| Screen / component | Work |
|--------------------|------|
| Building detail | Load `GET .../buildings/{id}`; wire Edit / Deactivate / Restore / Delete from `actions` |
| Floor detail | `GET .../floors/{floorId}` or nested GET |
| Unit detail | `GET .../units/{unitId}` |
| Room detail | `GET .../rooms/{roomId}` |
| Bed detail | `GET .../beds/{bedId}` |
| Manual Builder row menu | On "⋯" tap → fetch detail if needed → show allowed actions only |
| Quick Setup success | Navigate to Builder; user trims via delete on leaf beds first |
| Archived / inactive view (future) | `canRestore` when viewing deactivated entity by id |
| Manager role | Hide Deactivate, Restore, Delete entirely |

---

## Frontend implementation prompt (copy to mobile repo)

```
Integrate Acomi Accommodation lifecycle (deactivate, restore, permanent delete) for space {spaceId}.

BREAKING: Do not use DELETE on accommodation resources. Use POST lifecycle endpoints only.

1. DETAIL + ACTIONS
   - On Building / Floor / Unit / Room / Bed detail screens, use existing GET detail APIs.
   - Response includes `actions: { canEdit, canDeactivate, canRestore, canDelete, deleteReason }`.
   - Do NOT call a separate deletion-eligibility endpoint.

2. BUTTON RULES (OWNER only for lifecycle)
   - Show Edit when actions.canEdit (OWNER or MANAGER).
   - Show Deactivate when actions.canDeactivate.
   - Show Restore when actions.canRestore (inactive entity).
   - Show "Delete permanently" ONLY when actions.canDelete === true.
   - Never show Delete when canDelete is false. Optionally show deleteReason as hint text for owners.

3. API CALLS (all POST → 204 empty body)
   Building:  POST .../buildings/{buildingId}/deactivate|restore|delete
   Floor:     POST .../floors/{floorId}/deactivate|restore|delete
   Unit:      POST .../units/{unitId}/deactivate|restore|delete
   Room:      POST .../rooms/{roomId}/deactivate|restore|delete
   Bed:       POST .../beds/{bedId}/deactivate|restore|delete

4. CONFIRM DIALOGS
   - Deactivate: "Hide from lists? History preserved."
   - Restore: "Show in lists again?"
   - Delete: "Permanently remove? Cannot be undone."

5. AFTER SUCCESS
   - Deactivate/Delete: remove from list, invalidate parent queries, navigate back if needed.
   - Restore: refresh list.
   - Refresh GET .../buildings/{buildingId}/summary after bulk cleanup.

6. ERRORS
   - On 400, show response.message verbatim.
   - If message says "Deactivate instead", show Deactivate action — never ask user to delete children first.

7. CASCADE DELETE
   - deleteRoom / deleteFloor / deleteBuilding automatically remove all descendant beds and rooms in one POST.
   - User never deletes beds manually before deleting a room.
   - Example: remove entire unused building → POST .../buildings/{id}/delete once.

8. TYPES
   - Use AccommodationActionMetadata on all detail response types.
   - See docs/accommodation-lifecycle-ui-integration.md in acomi-backend repo.

Reference: accommodation-ui-integration.md (CRUD), accommodation-phase-4.2-ui-integration.md (Builder).
```

---

## Related docs

- [accommodation-ui-integration.md](./accommodation-ui-integration.md)
- [accommodation-phase-4.2-ui-integration.md](./accommodation-phase-4.2-ui-integration.md)
- [accommodation-navigation-ui-fix.md](./accommodation-navigation-ui-fix.md) — space/floor/room ID navigation
