# Occupancy Phase 4.3b — Contract Snapshots (Backend)

Phase 4.3b captures **occupancy contract snapshots** when an occupancy becomes **ACTIVE**. This is not billing, payments, or acacomig — only immutable agreed terms for future modules.

## Principles

| Rule | Implementation |
|------|----------------|
| No pricing at reservation | `reserve()` / `cancelReservation()` ignore financial fields |
| Snapshot at ACTIVE only | `moveIn()` and `allocate()` (walk-in today) |
| Catalog optional | `defaultRent` / `defaultDeposit` nullable on bed, room, unit |
| Rent required at ACTIVE | Resolved from request or catalog; error if still null |
| Deposit optional | Defaults to `0` |
| Immutability | No update API for snapshot fields after `pricingLockedAt` |
| Catalog changes | Never affect existing occupancies |

## Migrations

| Migration | Description |
|-----------|-------------|
| `accommodation/V27__add_default_pricing_to_accommodation.sql` | `default_rent`, `default_deposit` on beds, rooms, units |
| `space/V28__add_default_food_charge.sql` | Optional `spaces.default_food_charge` for move-in prefill |
| `space/V31__add_food_included_in_rent.sql` | `spaces.food_included_in_rent` — mandatory food bundled in rent |
| `occupancy/V29__add_occupancy_contract_snapshots.sql` | Snapshot columns on `occupancies` |
| `occupancy/V30__create_occupancy_charge_snapshots.sql` | Additional charge lines |
| `occupancy/V32__add_food_included_in_rent_to_occupancy.sql` | `occupancies.food_included_in_rent` on snapshot |

## Catalog fields (accommodation)

Optional on **Bed**, **Room**, **Unit** (via update APIs):

- `defaultRent`
- `defaultDeposit`

Exposed in GET responses. Not required at create time.

## Space food policy

On **Space** (`GET /spaces/{id}`, `PUT /spaces/{id}`):

| Field | Type | Default | Meaning |
|-------|------|---------|---------|
| `foodIncludedInRent` | boolean | `false` | Food is mandatory and included in monthly rent — no separate food charge line |
| `defaultFoodCharge` | decimal, nullable | `null` | Prefill for separate food charge when `foodIncludedInRent` is `false` |

When `foodIncludedInRent` is `true`:

- Activation requests must **not** require `foodChargeSnapshot`
- Server sets `foodEnabled = true`, `foodChargeSnapshot = null`, `foodIncludedInRent = true` on the occupancy snapshot
- Monthly billing total for rent+food equals `rentSnapshot` only

When `foodIncludedInRent` is `false` and client sends `foodEnabled = true`:

- Resolve `foodChargeSnapshot` from request, else `spaces.defaultFoodCharge`, else validation error if still null

## Occupancy snapshot fields

On **ACTIVE** occupancy (after move-in or allocate):

| Field | Required | Default |
|-------|----------|---------|
| `rentSnapshot` | Yes | From catalog or request |
| `depositSnapshot` | No | Catalog or `0` |
| `foodEnabled` | No | `true` when food included in rent; otherwise from request |
| `foodChargeSnapshot` | If food enabled and not bundled | Space `defaultFoodCharge` or request; must be null when bundled |
| `foodIncludedInRent` | No | Copied from space policy at activation |
| `otherCharges` | No | Child rows in `occupancy_charge_snapshots` |
| `pricingLockedAt` | Set automatically | Activation timestamp |

Legacy ACTIVE occupancies may have null `rentSnapshot` (pre-4.3b backfill).

## API changes

### Activation requests

**`POST /occupancies`** (allocate today) and **`POST /occupancies/{id}/move-in`** accept:

```json
{
  "rentSnapshot": 8000,
  "depositSnapshot": 10000,
  "foodEnabled": true,
  "foodChargeSnapshot": 2500,
  "otherCharges": [
    { "code": "PARKING", "label": "Parking", "amount": 500 }
  ]
}
```

All financial fields are optional in the JSON; server resolves:

1. Request value  
2. Target catalog default (rent/deposit)  
3. Deposit → `0`; rent → **error if missing**

**`POST /occupancies/reserve`** — no financial fields.

### Transfer

**`POST /occupancies/{id}/transfer`** adds:

```json
{
  "rentPolicy": "APPLY_NEW",
  "rentSnapshot": null,
  "depositSnapshot": null,
  "foodEnabled": true,
  "foodChargeSnapshot": 2500,
  "otherCharges": []
}
```

| `rentPolicy` | Behavior |
|--------------|----------|
| `APPLY_NEW` (default) | New target catalog + request overrides |
| `KEEP` | Copy rent/deposit/food/charges from previous occupancy |
| `CUSTOM` | `rentSnapshot` required in request |

Transfer creates a **new** occupancy row with a new snapshot (existing pattern).

### Responses

**`OccupancyResponse`** includes snapshot fields + `otherCharges[]`.

**`BedResponse` / `RoomResponse` / `UnitResponse`** include `defaultRent`, `defaultDeposit`.

## Charge codes

`OccupancyChargeCode`: `PARKING`, `LAUNDRY`, `ELECTRICITY`, `WIFI`, `MAINTENANCE`, `OTHER`

Max 10 lines per occupancy activation.

## Services

- `OccupancyPricingCatalogService` — read defaults from allocation target  
- `OccupancyContractSnapshotService` — resolve, validate, persist snapshots  
- `OccupancyService` — calls snapshot service on allocate, move-in, transfer only  

## Future billing compatibility

Billing module will read `occupancyId` + snapshot fields. Member profile `depositAmount` / `depositPaid` remain separate operational fields (Phase 3).

## Verification

- [ ] Reserve without rent/deposit succeeds  
- [ ] Move-in without catalog requires `rentSnapshot` in request  
- [ ] Move-in with catalog pre-fills rent/deposit  
- [ ] Deposit omitted → `0`  
- [ ] Catalog update does not change existing ACTIVE snapshot  
- [ ] Transfer APPLY_NEW uses new bed defaults  
- [ ] Transfer KEEP copies previous snapshot  
- [ ] Space with `foodIncludedInRent=true` activates without `foodChargeSnapshot`  
- [ ] Space with separate food requires charge or `defaultFoodCharge` when `foodEnabled=true`
