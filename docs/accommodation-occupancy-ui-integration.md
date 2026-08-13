# Acomi — Occupancy Management UI Integration (Phase 4.3)

Frontend guide for **Allocate**, **Transfer**, **Vacate**, and **occupancy history**.

**Prerequisites:** [accommodation-ui-integration.md](./accommodation-ui-integration.md), [membership-ui-integration.md](./membership-ui-integration.md)

---

## Allocation rules by space type

| Space type | Allowed `targetType` | Target IDs |
|------------|---------------------|------------|
| PG / HOSTEL | `BED` | `bedId` required |
| CO_LIVING | `BED` or `ROOM` | `bedId` or `roomId` |
| RENTAL | `UNIT` | `unitId` required |

---

## Member fields

`MemberDetailsResponse` now includes:

- `occupancyStatus`: `ALLOCATED` | `VACATED`
- `currentOccupancy`: summary when allocated (building/floor/unit/room/bed names)

---

## Endpoints

Base: `/api/v1/spaces/{spaceId}`

| Method | Path | Role |
|--------|------|------|
| `POST` | `/occupancies` | OWNER, MANAGER |
| `POST` | `/occupancies/{occupancyId}/transfer` | OWNER, MANAGER |
| `POST` | `/occupancies/{occupancyId}/vacate` | OWNER, MANAGER |
| `GET` | `/occupancies/{occupancyId}` | OWNER, MANAGER, STAFF, TENANT (own) |
| `GET` | `/occupancies?status=&memberId=&buildingId=&floorId=&unitId=&roomId=&bedId=&targetType=` | OWNER, MANAGER, STAFF |
| `GET` | `/members/{memberId}/occupancies` | OWNER, MANAGER, STAFF, TENANT (own) |

---

## Allocate request

```json
{
  "memberId": "uuid",
  "targetType": "BED",
  "bedId": "uuid",
  "expectedCheckoutDate": "2026-12-31",
  "remarks": "New admission"
}
```

---

## Transfer request

```json
{
  "targetType": "BED",
  "bedId": "uuid",
  "remarks": "Moved to corner bed"
}
```

Closes the current occupancy and creates a new active one (transactional).

---

## Vacate request

```json
{
  "remarks": "Checkout completed"
}
```

---

## Building summary availability

`GET .../buildings/{buildingId}/summary` includes flattened fields:

- `availableBeds`, `occupiedBeds`
- `availableRooms`, `occupiedRooms`
- `availableUnits`, `occupiedUnits`

Bed/room/unit `AccommodationStatus` is updated automatically on allocate/vacate/transfer.

---

## Delete restrictions

Permanent delete remains blocked when occupancy history exists on any node in the subtree (unchanged lifecycle rules).

---

## Related docs

- [accommodation-lifecycle-ui-integration.md](./accommodation-lifecycle-ui-integration.md)
- [accommodation-lazy-loading-ui-integration.md](./accommodation-lazy-loading-ui-integration.md)
- [member-management-ui-integration.md](./member-management-ui-integration.md)
