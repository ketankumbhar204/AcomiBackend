# Inventory Module — Backend Implementation

Aligned with the React Native Inventory Module and the Meal Library seeding pattern.

## Architecture

```
Space Created
    ↓
InventoryProfiles (config)
    ↓
InventorySeedService.seedDefaults(space)
    ↓
inventory_categories + inventory_items (+ optional supplier)
    ↓
Database
    ↓
REST /api/v1/spaces/{spaceId}/inventory/*
    ↓
Mobile inventoryApi (offline cache only)
```

Exactly once per space (`existsBySpaceId` guard). Also lazy-ensured on first inventory GET for older spaces created before this feature.

## Migration

| Version | Description |
|---------|-------------|
| **V92** | `inventory_categories`, `inventory_suppliers`, `inventory_items`, `inventory_transactions` |

Path: `src/main/resources/db/migration/inventory/V92__create_inventory_tables.sql`

## Profiles (config only)

`InventoryProfiles.forSpaceType(SpaceType)`:

| Space type | Profile | Default categories |
|------------|---------|--------------------|
| MESS | Food | Grains, Dairy, Vegetables, Oil, Spices |
| PG / HOSTEL / CO_LIVING | Asset | Furniture, Bedding, Cleaning, Electrical |
| RENTAL | Furniture | Furniture, Appliances, Laundry, Soft Furnishings, Keys |

Mobile `InventoryProfile` mirrors these for UI capabilities and form defaults — it does **not** seed.

## Space create hook

`SpaceService.createSpace` calls:

1. `mealSpaceSetupService.ensureSampleCombos(space)`
2. `mealPlanService.ensurePresetPlans(space.getId())`
3. **`inventorySeedService.seedDefaults(space)`**

## API endpoints

Base: `/api/v1/spaces/{spaceId}/inventory` — `ApiResponse<T>` envelope. Auth: bearer.

| Method | Path | Access |
|--------|------|--------|
| GET | `/dashboard` | OWNER / MANAGER / STAFF |
| GET / POST | `/categories` | view / manage |
| DELETE | `/categories/{categoryId}` | manage (non-default, empty) |
| GET / POST | `/items` | view / manage |
| GET / PUT / DELETE | `/items/{itemId}` | view / manage |
| POST | `/items/{itemId}/stock-moves` | manage |
| GET | `/transactions?itemId=` | view |
| GET / POST | `/suppliers` | view / manage |

### Create item body

```json
{
  "name": "Rice",
  "categoryId": "uuid",
  "unit": "KG",
  "openingStock": 10,
  "minimumStock": 5,
  "location": "Dry store",
  "supplierId": null,
  "purchasePrice": 55
}
```

### Stock move body

```json
{
  "type": "PURCHASE",
  "quantity": 20,
  "reason": "Weekly restock",
  "reference": "INV-102",
  "supplierId": "uuid",
  "amount": 1100,
  "setAbsoluteStock": null,
  "actorName": "You"
}
```

## Packages

```
com.countin.countin_backend.inventory
  api.controller.InventoryController
  api.dto.*
  application.catalog.InventoryProfiles
  application.service.InventoryAccessService
  application.service.InventorySeedService
  application.service.InventoryService
  domain.model.*
  infrastructure.persistence.entity.*
  infrastructure.persistence.repository.*
```

## Mobile contract

`src/api/inventoryApi.ts` calls the REST paths above. Method signatures keep `(spaceId, spaceType, …)` for UI compatibility; `spaceType` is unused on the wire (backend resolves profile from the space).

Offline: AsyncStorage cache key `@countin/inventory/cache/v2/{spaceId}` — populated after successful GETs; used only when the network fails.
