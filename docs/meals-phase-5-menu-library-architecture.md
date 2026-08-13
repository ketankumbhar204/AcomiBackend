# Acomi — Menu Library Architecture (Phase 5.1)

**Status:** Approved product direction  
**Supersedes:** Menu-first daily planning in initial Phase 5 UI sketch

**Related:** [meals-phase-5-backend.md](./meals-phase-5-backend.md) · [meals-phase-5-ui-integration.md](./meals-phase-5-ui-integration.md)

---

## 1. Problem

Without a **Menu Library**, operators re-type the same items every day:

```
Dal · Rice · Chapati · Dal · Rice · Chapati …
```

Daily menu planning must **consume** a library — not replace it.

---

## 2. Build order (revised Phase 5)

| Phase | Deliverable | Depends on |
|-------|-------------|------------|
| **5.1** | Menu Library — categories, items, combos | — |
| **5.2** | Daily menu planning (select combo / items from library) | 5.1 |
| **5.3** | Participant enrollment | — (can test menus before participants) |
| **5.4** | Eligibility summary | 5.3 |
| **5.5** | Occupancy → participation bridge | 5.3 |
| **5.6** | Phase 6 poll prep | 5.2, 5.4 |

**Do not ship daily menu planning before the library exists.**

---

## 3. Two-layer catalog

### 3.1 Global catalog (platform)

Read-only defaults shipped with Acomi (~50–100 common Indian items).

- Seeded once in Flyway / bootstrap data
- Same for all spaces
- Operators can **use** but not **delete** global rows

### 3.2 Space catalog (per space)

Space-specific additions and overrides.

- Custom items (`Jain Lunch`, `Mess Special Thali`)
- Custom combos (`Sunday Special`)
- Deactivate global items for this space (hide without deleting global row)

```
Global Catalog          Space Catalog
├── Chapati             ├── Mess Special Thali (combo)
├── Dal Fry             └── Jain Lunch (combo)
├── Plain Rice
└── … (~50–100 items)
```

---

## 4. Domain model

### 4.1 FoodCategory

| Field | Notes |
|-------|-------|
| `categoryId` | UUID |
| `name` | e.g. Breads, Rice, Dal |
| `sortOrder` | UI order |
| `scope` | `GLOBAL` \| `SPACE` |
| `spaceId` | null for global |
| `isActive` | Soft deactivate |

**Initial global categories:**

Breads · Rice · Dal · Sabzi · Breakfast · Paratha · South Indian · Snacks · Beverages · Desserts · Salads · Extras

### 4.2 FoodItem

| Field | Notes |
|-------|-------|
| `itemId` | UUID |
| `categoryId` | FK |
| `name` | e.g. Dal Fry, Chapati |
| `scope` | `GLOBAL` \| `SPACE` |
| `spaceId` | null for global |
| `isActive` | |
| `isCustom` | true when space-created |

**Operations (space operator):**

- Use global items (default)
- Add custom item
- Edit custom item (space scope only)
- Deactivate item (space-level hide for global; soft delete for space items)

### 4.3 MealCombo (Thali)

| Field | Notes |
|-------|-------|
| `comboId` | UUID |
| `name` | e.g. Standard Lunch Thali |
| `description` | optional |
| `scope` | `GLOBAL` \| `SPACE` |
| `spaceId` | |
| `isActive` | |

**MealComboItem** (join):

| Field | Notes |
|-------|-------|
| `comboId` | |
| `itemId` | FK → FoodItem |
| `sortOrder` | |

Example — **Standard Lunch Thali:** Chapati, Dal Fry, Plain Rice, Salad

Example — **Sunday Special:** Veg Biryani, Raita, Gulab Jamun

Combos are **always space-created** in v1 (global combo templates optional later).

---

## 5. Space creation seed

When a space is created with type **MESS** (and optionally PG/HOSTEL offering food):

1. Copy global categories + items into space **visibility** (or reference global IDs — prefer reference + `space_food_item_overrides` for deactivate)
2. Pre-create 2–3 sample combos (optional): Standard Lunch Thali, Dal Rice Combo
3. Owner can plan menus **immediately** without typing Chapati/Dal/Rice

**Implementation options:**

| Approach | Pros | Cons |
|----------|------|------|
| **A. Reference global IDs** | No duplication | Need override table for deactivate |
| **B. Copy on space create** | Simple queries | Duplication on global update |

**Recommended:** Reference global IDs + `space_food_item_settings (spaceId, itemId, isEnabled)`.

---

## 6. Daily menu planning (Phase 5.2)

After library exists:

```
Daily Menu (date + BREAKFAST | LUNCH | DINNER)
  └── entries (either):
        type COMBO  → comboId
        type ITEM   → itemId (multiple)
```

**UI copy (not "options"):**

```
Breakfast
  + Add Combo
  + Add Items

Lunch
  Standard Lunch Thali  (combo)

Dinner
  Dal Rice Combo        (combo)
```

Then **Publish**.

---

## 7. API outline (Phase 5.1)

Base: `/api/v1/spaces/{spaceId}`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/food-categories` | List global + space categories |
| POST | `/food-categories` | Create space category |
| GET | `/food-items?categoryId=` | List items (merged global + space) |
| POST | `/food-items` | Add custom space item |
| PUT | `/food-items/{id}` | Edit space item |
| POST | `/food-items/{id}/deactivate` | Hide item for space |
| GET | `/meal-combos` | List combos |
| POST | `/meal-combos` | Create combo with itemIds |
| PUT | `/meal-combos/{id}` | Update combo |
| POST | `/meal-combos/{id}/deactivate` | Soft deactivate |

Daily menu APIs remain Phase 5.2.

---

## 8. UI screens (Phase 5.1)

**Meals tab home → Menu Library hub**

1. **Categories** — browse / add space categories  
2. **Items** — browse by category, add custom, deactivate  
3. **Combos** — create thalis from items  

Daily menu / today's menu → **Phase 5.2** (hidden or gated until library has items).

Participant enrollment → **Phase 5.3**.

---

## 9. Out of scope (Phase 5.1)

- Daily menu publish
- Participant enrollment
- Eligibility / headcount
- Payment / credits
- 500-item mega-catalog — ship ~50–100 global, grow via custom items

---

## 10. Changelog

| Date | Change |
|------|--------|
| 2026-06 | Menu Library first; global + space catalog; revised Phase 5 order |
