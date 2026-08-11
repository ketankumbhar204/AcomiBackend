# Phase 5.1 — Menu Library Deactivate APIs (Backend Prompt)

Copy this into the **amico-backend** Cursor chat after Phase 5.1 list/create APIs are working.

Full spec: `docs/meals-phase-5-backend.md` §8.3.1

---

## Task

Implement **soft deactivate (remove/hide)** for menu library entities. The React Native app already calls these endpoints from `src/api/mealsApi.ts`.

**No hard DELETE.** All success responses: **`204 No Content`**, empty body.

---

## Endpoints

Base: `/api/v1/spaces/{spaceId}`  
Auth: JWT + `canManageMeals` (OWNER/MANAGER only)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/food-categories/{categoryId}/deactivate` | Hide global category for space OR deactivate space category |
| `POST` | `/food-items/{itemId}/deactivate` | Hide global item for space OR deactivate space item |
| `POST` | `/meal-combos/{comboId}/deactivate` | Soft-deactivate space combo |

### Request

No body. Example:

```http
POST /api/v1/spaces/0cbc920b-db9f-467b-9fb7-dfa5db614c77/food-items/9ab5833e-23ed-43cb-b753-305731ef6909/deactivate
Authorization: Bearer <token>
```

### Response

```http
HTTP/1.1 204 No Content
```

### Errors (standard `ApiResponse` envelope)

| Status | When |
|--------|------|
| `403` | STAFF/TENANT/CUSTOMER or not space member |
| `404` | categoryId/itemId/comboId not found for this space |
| `409` | *(optional)* combo referenced by published daily menu — or cascade anyway in v1 |

---

## Business rules

### Category deactivate

| Category scope | Action |
|----------------|--------|
| `GLOBAL` | Upsert `space_food_category_settings (space_id, category_id, is_enabled=false)` |
| `SPACE` (same space) | Set `food_category.is_active = false`; cascade deactivate all active items in that category for the space |

After hide, `GET /food-categories` must **exclude** hidden global categories for that space.

### Item deactivate

| Item scope | Action |
|------------|--------|
| `GLOBAL` | Upsert `space_food_item_settings (space_id, item_id, is_enabled=false)` |
| `SPACE` (same space) | Set `food_item.is_active = false` |

After hide, `GET /food-items` must exclude hidden/inactive items.

### Combo deactivate

Combos are space-scoped in v1.

- Set `meal_combo.is_active = false`
- Keep `meal_combo_item` rows (history)

---

## Migration

```sql
-- meal/V11__space_food_category_settings.sql
CREATE TABLE space_food_category_settings (
  space_id    UUID NOT NULL REFERENCES space(id),
  category_id UUID NOT NULL REFERENCES food_category(id),
  is_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (space_id, category_id)
);
```

Ensure `space_food_item_settings` exists from V3.

---

## List query changes

**GET /food-categories**

```sql
-- Pseudocode
SELECT c.*, count(i) AS item_count
FROM food_category c
LEFT JOIN food_item i ON ...
WHERE c.is_active = true
  AND (
    (c.scope = 'GLOBAL' AND c.space_id IS NULL
      AND NOT EXISTS (
        SELECT 1 FROM space_food_category_settings s
        WHERE s.space_id = :spaceId AND s.category_id = c.id AND s.is_enabled = false
      ))
    OR (c.scope = 'SPACE' AND c.space_id = :spaceId)
  )
ORDER BY c.sort_order, c.name
```

**GET /food-items** — exclude items where `space_food_item_settings.is_enabled = false` for global items; exclude `is_active = false` for space items.

**GET /meal-combos** — `WHERE is_active = true AND space_id = :spaceId`

---

## Integration tests

1. Deactivate global item → hidden in space A list; still visible in space B
2. Deactivate space custom item → removed from list
3. Deactivate global category → category + its items hidden in merged list for space
4. Deactivate space category → category gone; custom items in category inactive
5. Deactivate combo → excluded from GET meal-combos
6. STAFF role → 403 on all three POST deactivate routes

---

## Frontend verification

After deploy, in the app (Menu Library):

1. Long-press category → Remove → category disappears after reload
2. Tap item → Remove → item disappears
3. Combo preview → Remove combo → combo disappears

App methods: `mealsApi.deactivateFoodCategory`, `deactivateFoodItem`, `deactivateMealCombo`.
