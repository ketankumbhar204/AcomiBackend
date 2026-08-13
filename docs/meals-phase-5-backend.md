# Acomi — Meal Management Phase 5 (Backend Specification)

**Purpose:** Single source of truth for **server-side** Meal Management in Phase 5.  
**Audience:** Backend (Spring Boot) implementers.  
**Frontend contract:** React Native integrates via [meals-phase-5-ui-integration.md](./meals-phase-5-ui-integration.md).

**Related docs:**

- [development-roadmap.md](./development-roadmap.md) — Phase 5–7 roadmap
- [accommodation-occupancy-phase-4.3a-architecture.md](./accommodation-occupancy-phase-4.3a-architecture.md) — occupancy ↔ meal bridge
- [occupancy-phase-4.3b-backend.md](./occupancy-phase-4.3b-backend.md) — contract snapshots (`foodEnabled`, `foodChargeSnapshot`)
- [meals-phase-5-menu-library-architecture.md](./meals-phase-5-menu-library-architecture.md) — **library-first build order (Phase 5.1)**
- [permissions-backend-spec.md](./permissions-backend-spec.md) — role enforcement pattern

---

## 1. Executive summary

Acomi serves **PG, Hostel, Co-Living, Mess, and Rental** operators. The Meal module must work for:

| Business | People source | Accommodation |
|----------|---------------|---------------|
| PG / Hostel / Co-Living | TENANT, STAFF (residents) | Yes |
| Mess | CUSTOMER (subscribers, walk-ins) | No |
| Any space | Optional meal participants | May or may not apply |

**Core product insight:** Acomi’s meal USP is **availability-driven headcount** (menu → response → expected count → kitchen prep), not subscription-assumed eating. This works identically for PG and Mess via WhatsApp / app / link.

**Phase 5 builds:** participation, menu planning, poll-audience eligibility.  
**Phase 6 builds:** polls, responses, headcount (see §12 handoff).  
**Phase 7 builds:** meal commerce — entitlements, credit wallet, billing (see §11 — document only in Phase 5).

### Golden rules

1. **Participation ≠ billing.** Never store monthly/weekly/daily/per-meal charges in Phase 5.
2. **Headcount comes from availability responses (Phase 6), not from subscription or occupancy alone.**
3. **Member is the person anchor.** Do not create a parallel “Meal Subscriber” or “Meal Participant” person entity.
4. **Occupancy `foodEnabled` / `foodChargeSnapshot` stays unchanged** — PG accommodation contract snapshot only; not the meal billing engine.
5. **MESS spaces have no accommodation APIs** — meal APIs apply to all space types.

---

## 2. Architecture — three layers

```
┌─────────────────────────────────────────────────────────────────┐
│ Phase 5 — Participation & Menu                                  │
│   Member → MealParticipation (plan + status)                    │
│   Space → MealPlan catalog, MenuMaster, DailyMenu               │
│   Eligibility: who receives menus / is in poll audience         │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Phase 6 — Availability (USP)                                    │
│   DailyMenu → Poll → MealResponse (AVAILABLE | NOT_AVAILABLE)   │
│   Headcount = count(responses), NOT count(subscriptions)        │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Phase 7 — Meal Commerce (NOT Phase 5)                           │
│   MealEntitlement, CreditLedger, BillingModel, Invoices         │
│   Debit credits / charge per attended meal using Phase 6 data   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.1 What each layer answers

| Layer | Question | Phase |
|-------|----------|-------|
| **Participation** | Who is in the meal system? Which slots are they scoped for? | 5 |
| **Menu** | What is served on date X for breakfast/lunch/dinner? | 5 |
| **Eligibility** | Who should receive tomorrow’s menu / poll? | 5–6 |
| **Availability** | Who confirmed they are eating? | 6 |
| **Headcount** | How many meals to prepare per slot? | 6 |
| **Entitlement / billing** | What did they pay for? Credit balance? Invoice? | 7 |

### 2.2 Meal Plan ≠ Meal Entitlement

| Concept | Purpose | Phase |
|---------|---------|-------|
| **MealPlan** (catalog) | Slot scope: NONE, BREAKFAST, LUNCH, DINNER, FULL, CUSTOM | 5 |
| **MealParticipation** | Member’s active enrollment: plan + status + effective dates | 5 |
| **MealEntitlement** | Commercial rights: UNLIMITED, CREDIT_BASED (30 thaalis), PAY_PER_USE, etc. | 7 (document only now) |

A Mess customer with **30 meal credits** (Phase 7) still uses **MealParticipation** (Phase 5) for poll audience and slot scope. Credits are debited in Phase 7 when they respond AVAILABLE — not in Phase 5.

---

## 3. Domain model (Phase 5)

### 3.1 Entity overview

```
Space
  ├── MealPlan (catalog)
  ├── MealItem / MealCombo (menu master)
  ├── DailyMenu (date + mealType)
  │     └── DailyMenuOption (combo/item, sortOrder, isAvailable)
  └── (future) MealEntitlement — Phase 7

Member
  └── MealParticipation (0..1 active; history retained)
        ├── mealPlanId → MealPlan
        ├── customSlots? (when plan = CUSTOM)
        ├── status: ACTIVE | PAUSED | STOPPED
        ├── effectiveFrom, effectiveTo?
        ├── sourceOccupancyId? (nullable — audit when created from move-in)
        └── entitlementId? (nullable FK — reserved for Phase 7, always null in Phase 5)
```

### 3.2 MealPlan (space-scoped catalog)

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID | PK |
| `spaceId` | UUID | FK → Space |
| `code` | Enum | See §4.1 |
| `name` | String | Display, e.g. "Full Meals", "Lunch Only" |
| `breakfastIncluded` | boolean | For CUSTOM templates; derived for preset codes |
| `lunchIncluded` | boolean | |
| `dinnerIncluded` | boolean | |
| `isActive` | boolean | Soft deactivate |
| `sortOrder` | int | UI ordering |
| `createdAt`, `updatedAt` | timestamptz | Audit |

**Preset plans** seeded per space on first meal setup:

| code | breakfast | lunch | dinner |
|------|:---------:|:-----:|:------:|
| `NONE` | — | — | — |
| `BREAKFAST` | ✓ | — | — |
| `LUNCH` | — | ✓ | — |
| `DINNER` | — | — | ✓ |
| `FULL` | ✓ | ✓ | ✓ |
| `CUSTOM` | configurable | configurable | configurable |

### 3.3 MealParticipation

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID | PK |
| `spaceId` | UUID | FK |
| `memberId` | UUID | FK → Member (required) |
| `mealPlanId` | UUID | FK → MealPlan |
| `status` | Enum | ACTIVE, PAUSED, STOPPED |
| `effectiveFrom` | date | Required |
| `effectiveTo` | date | Nullable — open-ended if null |
| `sourceOccupancyId` | UUID | Nullable — set when created from occupancy bridge |
| `entitlementId` | UUID | Nullable — **always null in Phase 5** |
| `stoppedAt` | timestamptz | Set when status → STOPPED |
| `createdAt`, `updatedAt` | timestamptz | Audit |

**Rules:**

- One **ACTIVE** participation per `(spaceId, memberId)` at a time.
- Status changes append history row (see §3.6) — no hard deletes.
- `Member.status` must be `ACTIVE` for participation to be eligible (not VACATED/SUSPENDED/BLACKLISTED).
- PAUSED excludes member from poll audience and eligibility counts.

### 3.4 Menu master — MealCombo / MealItem

| Entity | Purpose |
|--------|---------|
| `MealItem` | Single dish, e.g. "Dal", "Rice", "Salad" |
| `MealCombo` | Named combo/thali, e.g. "Dal Rice Thali", "Paneer Combo" |
| `MealComboItem` | Join: combo → items (optional detail) |

Both scoped to `spaceId`. Soft-deactivate via `isActive`.

### 3.5 DailyMenu

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID | PK |
| `spaceId` | UUID | FK |
| `menuDate` | date | Calendar date in space timezone |
| `mealType` | Enum | BREAKFAST, LUNCH, DINNER |
| `status` | Enum | DRAFT, PUBLISHED |
| `publishedAt` | timestamptz | Nullable until published |
| `notes` | String | Optional kitchen note |

**Unique constraint:** `(spaceId, menuDate, mealType)`.

**DailyMenuOption:**

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID | PK |
| `dailyMenuId` | UUID | FK |
| `comboId` | UUID | FK → MealCombo (nullable if item-only) |
| `label` | String | Override display |
| `sortOrder` | int | |
| `isAvailable` | boolean | "Not Available" option support |

### 3.6 MealParticipationHistory (audit)

| Field | Type |
|-------|------|
| `id` | UUID |
| `participationId` | UUID |
| `action` | CREATED, PLAN_CHANGED, STATUS_CHANGED, STOPPED |
| `oldValue`, `newValue` | JSON or string |
| `changedBy` | UUID (user) |
| `changedAt` | timestamptz |

---

## 4. Enums

### 4.1 MealPlanCode

```
NONE | BREAKFAST | LUNCH | DINNER | FULL | CUSTOM
```

### 4.2 MealParticipationStatus

```
ACTIVE | PAUSED | STOPPED
```

### 4.3 MealType

```
BREAKFAST | LUNCH | DINNER
```

### 4.4 DailyMenuStatus

```
DRAFT | PUBLISHED
```

### 4.5 Future — MealEntitlementType (Phase 7 — do not implement)

Document for forward compatibility:

```
UNLIMITED | CREDIT_BASED | FIXED_MEAL_COUNT | PAY_PER_USE | HYBRID | CUSTOM
```

**Credit-based example (Phase 7):**

- Plan: 30 meal credits / month
- Consumption: lunch = 1 credit, dinner = 1 credit, breakfast = 0.5 credit (space-configurable)
- Balance: single **meal credit balance** — not separate lunch/dinner balances
- Carry-forward and expiry: ledger rules in Phase 7

---

## 5. Eligibility engine

### 5.1 Poll audience eligibility (Phase 5)

A member is **eligible for poll audience** on `(spaceId, date, mealType)` when:

```
member.status = ACTIVE
AND participation.status = ACTIVE
AND participation.effectiveFrom <= date
AND (participation.effectiveTo IS NULL OR participation.effectiveTo >= date)
AND mealPlanCovers(participation.mealPlan, mealType)
```

**`mealPlanCovers`:** FULL → all; BREAKFAST/LUNCH/DINNER → that slot; CUSTOM → check flags; NONE → never (unless operator overrides — defer override to Phase 5.1+ if needed).

### 5.2 Headcount (Phase 6 — not Phase 5)

```
headcount(date, mealType) = COUNT(meal_responses WHERE response = AVAILABLE)
```

**Never:**

```
headcount ≠ COUNT(active participations)
headcount ≠ COUNT(occupancies WHERE foodEnabled = true)
```

Phase 5 exposes **eligible participant count** as a **ceiling** for planning UI only (`GET .../meals/eligibility-summary`).

### 5.3 Space-type defaults for audience

| Space type | Typical participants | Notes |
|------------|---------------------|-------|
| PG / HOSTEL / CO_LIVING | TENANT, STAFF with ACTIVE participation | Occupancy not required for eligibility |
| MESS | CUSTOMER (and STAFF) with ACTIVE participation | Walk-in: participation with plan NONE or slot CUSTOM |
| RENTAL | Optional — same model if operator offers meals | |

---

## 6. Occupancy bridge (non-breaking)

### 6.1 Keep existing contract snapshots

Do **not** remove or repurpose:

- `occupancies.foodEnabled`
- `occupancies.foodChargeSnapshot`
- `occupancies.foodIncludedInRent`
- `spaces.foodIncludedInRent`
- `spaces.defaultFoodCharge`

**Semantic (unchanged):** PG accommodation contract at move-in. Billing hint for Phase 7.

### 6.2 Move-in / allocate suggestion

When occupancy becomes ACTIVE and (`foodEnabled = true` OR `foodIncludedInRent = true`):

1. If member has no ACTIVE participation → **suggest** creating one with plan `FULL`.
2. API may accept optional `createMealParticipation: true` on move-in/allocate payload (Phase 5.5).
3. Set `sourceOccupancyId` on created participation.

### 6.3 Transfer

On transfer, prompt policy (API flags):

| Option | Behavior |
|--------|----------|
| `KEEP` | Participation unchanged |
| `UPDATE` | New plan in request |
| `STOP` | Set participation STOPPED |

### 6.4 Vacate

Space-configurable default (future space setting): `STOP_ON_VACATE` | `PAUSE_ON_VACATE` | `NO_CHANGE`. Phase 5.5: implement `STOP_ON_VACATE` as default for PG.

### 6.5 Backfill

One-time job: ACTIVE occupancies with `foodEnabled = true` and no participation → create FULL participation with `sourceOccupancyId`.

---

## 7. Permissions

Extend `SpacePermissionsResponse` on `GET /spaces/my`:

| Flag | OWNER | MANAGER | STAFF | TENANT | CUSTOMER |
|------|:-----:|:-------:|:-----:|:------:|:--------:|
| `canManageMeals` | ✅ | ✅ | ❌ | ❌ | ❌ |
| `canViewMeals` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `canManageMealParticipation` | ✅ | ✅ | ❌ | ❌ | ❌ |
| `canViewOwnMealParticipation` | ✅ | ✅ | ✅ | ✅ own | ✅ own |

### 7.1 Access service

```
MealAccessService
  requireViewMeals(membership)
  requireManageMeals(membership)        → OWNER, MANAGER
  requireManageParticipation(membership)
  requireViewOwnParticipation(membership, memberId)  → ops roles OR linked member
```

**MESS spaces:** meal APIs enabled; accommodation APIs remain gated (existing behavior).

---

## 8. API specification (Phase 5)

Base path: `/api/v1/spaces/{spaceId}`

### 8.1 Meal plans (catalog)

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `GET` | `/meal-plans` | canViewMeals | List active plans |
| `POST` | `/meal-plans` | canManageMeals | Create CUSTOM plan |
| `PUT` | `/meal-plans/{planId}` | canManageMeals | Update (not preset codes) |

### 8.2 Meal participation

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `GET` | `/meal-participations` | canViewMeals | List with filters: `status`, `mealPlanCode`, `search` |
| `GET` | `/meal-participations/{id}` | canViewMeals / own | Detail |
| `POST` | `/meal-participations` | canManageMeals | Enroll member |
| `PUT` | `/meal-participations/{id}` | canManageMeals | Change plan or status |
| `POST` | `/meal-participations/{id}/pause` | canManageMeals | → PAUSED |
| `POST` | `/meal-participations/{id}/resume` | canManageMeals | → ACTIVE |
| `POST` | `/meal-participations/{id}/stop` | canManageMeals | → STOPPED |
| `GET` | `/members/{memberId}/meal-participation` | canViewMeals / own | Current participation + history summary |

**Create request:**

```json
{
  "memberId": "uuid",
  "mealPlanId": "uuid",
  "effectiveFrom": "2026-07-01",
  "effectiveTo": null
}
```

**Update request:**

```json
{
  "mealPlanId": "uuid",
  "status": "PAUSED"
}
```

### 8.3 Menu library (Phase 5.1 — implement first)

See [meals-phase-5-menu-library-architecture.md](./meals-phase-5-menu-library-architecture.md).

#### Food categories

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `GET` | `/food-categories` | canViewMeals | Global + space categories; include `itemCount` |
| `POST` | `/food-categories` | canManageMeals | Create space-scoped category |
| `POST` | `/food-categories/{categoryId}/deactivate` | canManageMeals | Hide global category for space or remove space category — see §8.3.1 |

**Response:**

```json
{
  "categoryId": "uuid",
  "name": "Breads",
  "sortOrder": 1,
  "scope": "GLOBAL",
  "isActive": true,
  "itemCount": 8
}
```

#### Food items

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `GET` | `/food-items?categoryId=` | canViewMeals | Merged global (minus space-disabled) + space custom items |
| `POST` | `/food-items` | canManageMeals | Create custom space item |
| `PUT` | `/food-items/{itemId}` | canManageMeals | Edit space item only (403 on global) |
| `POST` | `/food-items/{itemId}/deactivate` | canManageMeals | Hide global item for space or deactivate space item |

**Create request:**

```json
{ "categoryId": "uuid", "name": "Mess Special Dal" }
```

**Response:**

```json
{
  "itemId": "uuid",
  "categoryId": "uuid",
  "categoryName": "Dal",
  "name": "Dal Fry",
  "scope": "GLOBAL",
  "isCustom": false,
  "isActive": true
}
```

#### Meal combos

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `GET` | `/meal-combos` | canViewMeals | List combos with nested items |
| `POST` | `/meal-combos` | canManageMeals | Create combo |
| `PUT` | `/meal-combos/{comboId}` | canManageMeals | Update |
| `POST` | `/meal-combos/{comboId}/deactivate` | canManageMeals | Soft deactivate |

**Create request:**

```json
{
  "name": "Standard Lunch Thali",
  "description": "Daily lunch combo",
  "itemIds": ["uuid-chapati", "uuid-dal-fry", "uuid-plain-rice"]
}
```

**Response:**

```json
{
  "comboId": "uuid",
  "name": "Standard Lunch Thali",
  "description": "Daily lunch combo",
  "scope": "SPACE",
  "isActive": true,
  "items": [{ "itemId": "uuid", "name": "Chapati" }]
}
```

#### 8.3.1 Deactivate / remove (Menu Library — required for mobile)

Mobile uses **soft deactivate** only (no hard DELETE). All three endpoints return **`204 No Content`** on success.

**Rules (mirror global vs space catalog):**

| Entity | `scope = GLOBAL` | `scope = SPACE` |
|--------|------------------|-----------------|
| **Category** | Upsert `space_food_category_settings.is_enabled = false` for `(spaceId, categoryId)` | Set `food_category.is_active = false`; cascade deactivate active items in category for that space |
| **Item** | Upsert `space_food_item_settings.is_enabled = false` | Set `food_item.is_active = false` |
| **Combo** | N/A (combos are space-scoped in v1) | Set `meal_combo.is_active = false`; keep join rows for history |

**Endpoints (base `/api/v1/spaces/{spaceId}`):**

| Method | Path | Auth | Request body | Success | Errors |
|--------|------|------|--------------|---------|--------|
| `POST` | `/food-categories/{categoryId}/deactivate` | canManageMeals | *(empty)* | `204` | `404` unknown category; `403` not member |
| `POST` | `/food-items/{itemId}/deactivate` | canManageMeals | *(empty)* | `204` | `404` unknown item |
| `POST` | `/meal-combos/{comboId}/deactivate` | canManageMeals | *(empty)* | `204` | `404` unknown combo |

**No response body** on success. After deactivate, list endpoints (`GET /food-categories`, `/food-items`, `/meal-combos`) must **omit** hidden/inactive rows for that space.

**Verification (curl examples):**

```bash
# Hide global item "Chapati" for space
curl -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/spaces/$SPACE_ID/food-items/$CHAPATI_ID/deactivate"

# Remove space custom category
curl -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/spaces/$SPACE_ID/food-categories/$CATEGORY_ID/deactivate"

# Remove combo
curl -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/spaces/$SPACE_ID/meal-combos/$COMBO_ID/deactivate"

# Confirm hidden — item should not appear
curl -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/spaces/$SPACE_ID/food-items?categoryId=$CATEGORY_ID"
```

**New table (migration `V11__space_food_category_settings.sql`):**

| Column | Type | Notes |
|--------|------|-------|
| space_id | UUID | PK part |
| category_id | UUID | PK part — FK global category |
| is_enabled | BOOLEAN | `false` = hidden for space |
| updated_at | TIMESTAMPTZ | |

Unique `(space_id, category_id)`. Same pattern as `space_food_item_settings` (V3).

**Service pseudocode:**

```java
deactivateCategory(spaceId, categoryId):
  cat = findCategory(categoryId)
  if cat.scope == GLOBAL:
    upsert space_food_category_settings(spaceId, categoryId, enabled=false)
  else if cat.spaceId == spaceId:
    cat.isActive = false
    deactivateAllActiveItemsInCategory(spaceId, categoryId)
  else: throw 404

deactivateItem(spaceId, itemId):
  item = findItem(itemId)
  if item.scope == GLOBAL:
    upsert space_food_item_settings(spaceId, itemId, enabled=false)
  else if item.spaceId == spaceId:
    item.isActive = false
  else: throw 404

deactivateCombo(spaceId, comboId):
  combo = findCombo(spaceId, comboId) // combos always space-scoped
  combo.isActive = false
```

**Integration tests:**

1. Manager deactivates global item → GET items excludes it; other space still sees it
2. Manager deactivates space custom item → gone from list
3. Manager deactivates global category → category hidden; items in category hidden in merged list
4. Manager deactivates space category → category + its custom items inactive
5. Manager deactivates combo → GET meal-combos excludes it
6. STAFF/TENANT → `403` on all deactivate endpoints

### 8.4 Daily menus (Phase 5.2)

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `GET` | `/daily-menus?from=&to=` | canViewMeals | Range query |
| `GET` | `/daily-menus/today` | canViewMeals | Convenience: today all slots |
| `GET` | `/daily-menus/{date}/{mealType}` | canViewMeals | Single slot |
| `PUT` | `/daily-menus/{date}/{mealType}` | canManageMeals | Upsert draft |
| `POST` | `/daily-menus/{date}/{mealType}/publish` | canManageMeals | DRAFT → PUBLISHED |
| `DELETE` | `/daily-menus/{date}/{mealType}` | canManageMeals | Soft delete draft only |

**Upsert body:**

```json
{
  "options": [
    { "comboId": "uuid", "label": "Dal Rice Thali", "sortOrder": 1, "isAvailable": true },
    { "comboId": null, "label": "Not Available", "sortOrder": 99, "isAvailable": false }
  ],
  "notes": "Extra salad today"
}
```

### 8.5 Eligibility summary (Phase 5.4 — poll prep)

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `GET` | `/meals/eligibility-summary?date=` | canViewMeals | Per mealType: eligibleCount, pausedCount, byPlan breakdown |
| `GET` | `/meals/eligible-participants?date=&mealType=` | canManageMeals | Member list for WhatsApp share prep (Phase 6) |

**Response example:**

```json
{
  "date": "2026-07-15",
  "slots": [
    { "mealType": "BREAKFAST", "eligibleCount": 42, "published": true },
    { "mealType": "LUNCH", "eligibleCount": 48, "published": true },
    { "mealType": "DINNER", "eligibleCount": 45, "published": false }
  ]
}
```

### 8.6 Member details extension

Extend `GET /members/{memberId}` response:

```json
{
  "mealParticipation": {
    "participationId": "uuid",
    "mealPlanCode": "FULL",
    "mealPlanName": "Full Meals",
    "status": "ACTIVE",
    "effectiveFrom": "2026-07-01",
    "effectiveTo": null
  }
}
```

Null when not enrolled.

---

## 9. Implementation sequence

| Sub-phase | Deliverable |
|-----------|-------------|
| **5.0** | This spec approved; Flyway module `meal/` created |
| **5.1** | MealPlan seed, MealParticipation CRUD, permissions, member extension |
| **5.2** | MealCombo/MealItem menu master |
| **5.3** | DailyMenu upsert + publish + today endpoint |
| **5.4** | Eligibility summary + eligible participants list |
| **5.5** | Occupancy bridge (move-in suggest, vacate stop, backfill job) |
| **5.6** | Phase 6 handoff doc — response entity stub, no poll UI yet |

### Out of scope (Phase 5)

- MealEntitlement, credit wallet, ledger
- Charge amounts, billing models, invoices
- Availability polls and MealResponse CRUD (Phase 6)
- WhatsApp send integration (Phase 6)
- Weekly menu templates, menu history reports
- Meal consumption tracking, special requests
- Auto-debit credits on response

---

## 10. Migrations (outline)

> **Order follows library-first sequence.** See §9 and [meals-phase-5-menu-library-architecture.md](./meals-phase-5-menu-library-architecture.md).

| Migration | Description |
|-----------|-------------|
| `meal/V1__create_food_categories.sql` | FoodCategory (global + space scope) |
| `meal/V2__create_food_items.sql` | FoodItem |
| `meal/V3__create_space_food_item_settings.sql` | Per-space hide/disable overrides for global items |
| `meal/V4__seed_global_food_catalog.sql` | 12 categories + ~60–80 Indian items |
| `meal/V5__create_meal_combos.sql` | MealCombo + MealComboItem |
| `meal/V6__create_daily_menus.sql` | DailyMenu + entries (Phase 5.2) |
| `meal/V7__create_meal_plans.sql` | MealPlan catalog |
| `meal/V8__create_meal_participations.sql` | Participation + history |
| `meal/V9__seed_default_meal_plans.sql` | Per-space lazy seed or trigger |
| `meal/V10__mess_space_sample_combos.sql` | Optional: Standard Lunch Thali on MESS create |

Add nullable `entitlement_id` on participations for Phase 7 forward compatibility.

---

## 11. Phase 7 forward design (document only)

### 11.1 MealEntitlement (future)

| Field | Purpose |
|-------|---------|
| `type` | UNLIMITED, CREDIT_BASED, FIXED_MEAL_COUNT, PAY_PER_USE, HYBRID |
| `grantedCredits` | e.g. 30 thaalis |
| `validFrom`, `validTo` | Plan period |
| `carryForwardRule` | NONE, FULL, CAPPED(n) |
| `expiryRule` | END_OF_PERIOD, NEVER |

### 11.2 MealCreditLedger (future — append-only)

| Event | delta | Example |
|-------|-------|---------|
| GRANT | +30 | Monthly plan activation |
| CONSUME | -1 | Lunch AVAILABLE response |
| CARRY_FORWARD | +10 | Unused July → August |
| ADJUSTMENT | ±n | Manual correction |

**Consumption weight (space config, Phase 7):**

```
BREAKFAST → 0.5 credit
LUNCH     → 1.0 credit
DINNER    → 1.0 credit
```

### 11.3 Link to occupancy snapshot (PG)

Phase 7 may auto-create `UNLIMITED` or `MONTHLY_FIXED` entitlement from `foodChargeSnapshot` — **never** from Phase 5 participation APIs.

---

## 12. Phase 6 handoff (Availability)

Phase 6 adds:

```
MealPoll (dailyMenuId, pollOpensAt, pollClosesAt)
MealResponse (memberId, dailyMenuId, response: AVAILABLE | NOT_AVAILABLE | NO_RESPONSE, respondedAt, channel: APP | WHATSAPP)
```

Headcount API:

```
GET /spaces/{spaceId}/meals/headcount?date=
→ { breakfast: 42, lunch: 35, dinner: 40 }
```

Phase 5 must expose published daily menus and eligible participant contact list so Phase 6 can share menus via WhatsApp identically for PG and Mess.

---

## 13. Testing checklist

**Phase 5.1 — Menu Library**

- [ ] GET `/food-categories` returns 12 global categories with `itemCount`
- [ ] GET `/food-items` returns global seed for new MESS space
- [ ] POST custom item → `isCustom: true`, `scope: SPACE`
- [ ] Deactivate global item → hidden for space, visible for other spaces
- [ ] Create combo with `itemIds` → GET returns nested items
- [ ] STAFF can view library; cannot create combo
- [ ] TENANT/CUSTOMER can view library; cannot manage

**Phase 5.2–5.5**

- [ ] MESS space: create CUSTOMER participation, no occupancy APIs involved
- [ ] PG space: move-in with foodEnabled suggests FULL participation
- [ ] PAUSED participation excluded from eligibility-summary
- [ ] CUSTOM plan with lunch-only excluded from dinner eligibility
- [ ] TENANT can view own participation; cannot manage
- [ ] STAFF can view menus; cannot manage participation
- [ ] Daily menu unique per (space, date, mealType)
- [ ] Publish required before menu appears on `today` for participants
- [ ] Occupancy foodEnabled backfill creates participation
- [ ] No payment/credit fields in Phase 5 API responses

---

## 14. Copy-paste backend implementation prompt

Copy the block below into the **backend repository** Cursor chat (or save as `docs/meals-phase-5-backend-prompt.md`).

Also copy these spec files from the frontend repo into backend `docs/`:

1. `meals-phase-5-backend.md` (this file)
2. `meals-phase-5-menu-library-architecture.md`
3. `permissions-backend-spec.md`

```markdown
# Task: Implement Acomi Phase 5 — Meal Management (Backend)

## Context

Acomi is a **Spring Boot 3 / Java 17 modular monolith** (PostgreSQL, Flyway, JPA, JWT) for PG, Hostel, Co-Living, Mess, and Rental operators. Phases 1–4 are complete: auth, spaces, members, accommodation, occupancy, permissions.

The **React Native app is already wired** for meal APIs. Responses must match the contract in this spec and in frontend `src/api/types.ts` exactly (field names, paths, `ApiResponse<T>` envelope).

**Product USP (future):** availability-driven headcount — menu → poll → response → kitchen prep. Phase 5 builds library + daily menus + participation + eligibility. Polls/headcount = Phase 6. Billing/credits = Phase 7.

**Spec files:** `docs/meals-phase-5-backend.md`, `docs/meals-phase-5-menu-library-architecture.md`, `docs/permissions-backend-spec.md`

---

## Golden rules (non-negotiable)

1. **Participation ≠ billing.** No charge amounts, credits, wallet, or invoices in Phase 5.
2. **Member is the person anchor.** `MealParticipation` links to `Member`. Do NOT create a separate "Meal Subscriber" entity.
3. **Headcount ≠ subscription count.** Phase 5 exposes **eligible participant counts** only. Real headcount comes from availability responses in Phase 6.
4. **Do NOT change** occupancy contract fields (`foodEnabled`, `foodChargeSnapshot`, `foodIncludedInRent`) — PG accommodation snapshots for Phase 7 billing hints only.
5. **MESS spaces** have no accommodation APIs (existing guard). Meal APIs must work for all space types including MESS.
6. **Modular monolith only** — new `meal` module. No microservices.

---

## Implementation sequence (follow this order)

| Sub-phase | Deliverable | Priority |
|-----------|-------------|----------|
| **5.1** | Menu Library: FoodCategory, FoodItem, global seed (~60–80 items), space custom items, MealCombo, MESS space setup, meal permissions | **START HERE** |
| **5.2** | DailyMenu upsert/publish/today — select combos/items from library | After 5.1 |
| **5.3** | MealPlan seed, MealParticipation CRUD, member extension | After 5.2 or parallel |
| **5.4** | Eligibility summary + eligible participants list | After 5.3 |
| **5.5** | Occupancy bridge (move-in suggest, vacate stop, backfill) | After 5.3 |
| **5.6** | Phase 6 handoff stub (MealPoll/MealResponse documented, not exposed) | Last |

**Do not ship daily menu planning before the menu library exists.**

---

## Phase 5.1 — Menu Library (implement first)

### Problem

Without a library, operators re-type "Dal, Rice, Chapati" every day. Daily menus must **consume** the library, not replace it.

### Two-layer catalog

```
Global Catalog (platform, read-only)     Space Catalog (per space)
├── Chapati                                ├── Standard Lunch Thali (combo)
├── Dal Fry                                ├── Sunday Special (combo)
├── Plain Rice                             └── Jain Lunch (custom item)
└── ~60–80 seeded items
```

**Recommended:** Reference global item IDs + `space_food_item_settings` override table for per-space hide.

- Global rows: `scope = GLOBAL`, `space_id IS NULL`
- Space rows: `scope = SPACE`, `space_id = {spaceId}`
- Space cannot delete global rows; can **deactivate/hide** via override table

### Entities

#### `food_category`

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| name | VARCHAR | e.g. Breads, Rice, Dal |
| sort_order | INT | UI order |
| scope | ENUM GLOBAL, SPACE | |
| space_id | UUID FK nullable | null for global |
| is_active | BOOLEAN | |
| created_at, updated_at | TIMESTAMPTZ | |

**Global seed categories (12):**
Breads, Rice, Dal, Sabzi, Breakfast, Paratha, South Indian, Snacks, Beverages, Desserts, Salads, Extras

#### `food_item`

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| category_id | UUID FK | |
| name | VARCHAR | |
| scope | ENUM GLOBAL, SPACE | |
| space_id | UUID FK nullable | |
| is_active | BOOLEAN | |
| is_custom | BOOLEAN | true when space-created |
| created_at, updated_at | TIMESTAMPTZ | |

**Space operator ops:** use global items · add custom item · edit space item only · deactivate (global → override; space → is_active=false)

#### `space_food_item_settings`

| Column | Type | Notes |
|--------|------|-------|
| space_id | UUID | PK part |
| item_id | UUID | PK part |
| is_enabled | BOOLEAN | false = hidden for space |
| updated_at | TIMESTAMPTZ | |

Unique: `(space_id, item_id)`

#### `space_food_category_settings` (V11)

| Column | Type | Notes |
|--------|------|-------|
| space_id | UUID | PK part |
| category_id | UUID | PK part — global category FK |
| is_enabled | BOOLEAN | false = hidden for space |
| updated_at | TIMESTAMPTZ | |

Unique: `(space_id, category_id)`

#### `meal_combo` + `meal_combo_item`

Combos are **space-scoped in v1**. Join table: `combo_id`, `item_id`, `sort_order`.

### Global seed (~60–80 items — NOT 500)

Seed via Flyway `meal/V4__seed_global_food_catalog.sql`:

**Breads:** Chapati, Roti, Phulka, Tandoori Roti, Butter Roti, Naan, Butter Naan, Kulcha

**Rice:** Plain Rice, Jeera Rice, Masala Rice, Tomato Rice, Lemon Rice, Pulav, Veg Biryani

**Dal:** Dal Fry, Dal Tadka, Dal Makhani, Yellow Dal, Mixed Dal, Sambar

**Paratha:** Aloo Paratha, Onion Paratha, Methi Paratha, Paneer Paratha, Gobhi Paratha

**Breakfast:** Poha, Upma, Idli, Vada, Misal Pav, Pav Bhaji, Dhokla

**Sabzi:** Aloo Matar, Bhindi Masala, Mix Veg, Paneer Butter Masala, Chana Masala, Rajma

**South Indian:** Dosa, Masala Dosa, Uttapam, Medu Vada, Rasam, Coconut Chutney

**Snacks:** Samosa, Pakora, Kachori · **Beverages:** Tea, Coffee, Buttermilk, Lassi

**Desserts:** Gulab Jamun, Kheer, Jalebi · **Salads:** Green Salad, Cucumber Raita, Onion Salad · **Extras:** Papad, Pickle, Curd

### MESS space creation hook

When `spaceType = MESS`:

1. Global catalog visible immediately (reference model — no copy)
2. Optionally pre-create sample combos: **Standard Lunch Thali** (Chapati, Dal Fry, Plain Rice, Green Salad), **Dal Rice Combo** (Dal Fry, Plain Rice)
3. Lazy-init on first `GET /food-categories` is acceptable if create-hook is hard

### Phase 5.1 APIs

Base: `/api/v1/spaces/{spaceId}` · Envelope: `{ "success": true, "data": ... }`

| Method | Path | Role |
|--------|------|------|
| GET | `/food-categories` | canViewMeals |
| POST | `/food-categories` | canManageMeals |
| GET | `/food-items?categoryId=` | canViewMeals |
| POST | `/food-items` | canManageMeals |
| PUT | `/food-items/{itemId}` | canManageMeals (space items only) |
| POST | `/food-items/{itemId}/deactivate` | canManageMeals |
| GET | `/meal-combos` | canViewMeals |
| POST | `/meal-combos` | canManageMeals |
| PUT | `/meal-combos/{comboId}` | canManageMeals |
| POST | `/meal-combos/{comboId}/deactivate` | canManageMeals |

**FoodCategoryResponse:** `{ categoryId, name, sortOrder, scope, isActive, itemCount? }`

**FoodItemResponse:** `{ itemId, categoryId, categoryName?, name, scope, isCustom, isActive }`

**MealComboResponse:** `{ comboId, name, description?, scope?, isActive, items?: [{ itemId, name }] }`

**Create combo body:** `{ "name": "...", "description": "...", "itemIds": ["uuid", ...] }`

**Create item body:** `{ "categoryId": "uuid", "name": "..." }`

**List items logic:** global items minus space-disabled overrides UNION space custom items; filter by categoryId optional; only enabled/active.

---

## Phase 5.2 — Daily Menu Planning

Entities: `daily_menu` (unique space_id + menu_date + meal_type, status DRAFT|PUBLISHED) + `daily_menu_entry` (entry_type COMBO|ITEM, combo_id?, item_id?, label, sort_order, is_available).

| Method | Path | Role |
|--------|------|------|
| GET | `/daily-menus/today` | canViewMeals |
| GET | `/daily-menus/{date}/{mealType}` | canViewMeals |
| GET | `/daily-menus?from=&to=` | canViewMeals |
| PUT | `/daily-menus/{date}/{mealType}` | canManageMeals |
| POST | `/daily-menus/{date}/{mealType}/publish` | canManageMeals |
| DELETE | `/daily-menus/{date}/{mealType}` | canManageMeals (draft only) |

**Upsert body (frontend contract):**
```json
{
  "options": [
    { "comboId": "uuid", "label": "Standard Lunch Thali", "sortOrder": 1, "isAvailable": true }
  ],
  "notes": "Extra salad today"
}
```

Validate comboId/itemId belong to space library.

---

## Phase 5.3 — Participant Enrollment

**MealPlan** codes: NONE, BREAKFAST, LUNCH, DINNER, FULL, CUSTOM — seed per space.

**MealParticipation:** one ACTIVE per (space, member); status ACTIVE|PAUSED|STOPPED; effectiveFrom/To; sourceOccupancyId; entitlementId always null in Phase 5; append history on changes.

| Method | Path | Role |
|--------|------|------|
| GET/POST/PUT | `/meal-plans`, `/meal-participations` | per spec §8.1–8.2 |
| POST | `/meal-participations/{id}/pause\|resume\|stop` | canManageMeals |
| GET | `/members/{memberId}/meal-participation` | canViewMeals / own |

Extend `GET /members/{memberId}` with optional `mealParticipation` block (null when not enrolled).

---

## Phase 5.4 — Eligibility Summary

Eligible when: member ACTIVE + participation ACTIVE + date in range + mealPlanCovers(plan, mealType). PAUSED excluded.

| Method | Path | Role |
|--------|------|------|
| GET | `/meals/eligibility-summary?date=` | canViewMeals |
| GET | `/meals/eligible-participants?date=&mealType=` | canManageMeals |

Response: `{ date, slots: [{ mealType, eligibleCount, published }] }` — label **eligible participants**, never headcount.

---

## Phase 5.5 — Occupancy Bridge

Non-breaking: move-in with `foodEnabled` accepts optional `createMealParticipation: true` → FULL plan + sourceOccupancyId; vacate default STOP_ON_VACATE; backfill job for existing occupancies. Do NOT repurpose foodChargeSnapshot.

---

## Permissions

Extend `SpacePermissionsResponse` on `GET /spaces/my`:

| Flag | OWNER | MANAGER | STAFF | TENANT | CUSTOMER |
|------|:-----:|:-------:|:-----:|:------:|:--------:|
| canManageMeals | ✅ | ✅ | ❌ | ❌ | ❌ |
| canViewMeals | ✅ | ✅ | ✅ | ✅ | ✅ |
| canManageMealParticipation | ✅ | ✅ | ❌ | ❌ | ❌ |

`MealAccessService`: requireViewMeals, requireManageMeals, requireManageParticipation, requireViewOwnParticipation — follow `permissions-backend-spec.md`.

---

## Flyway migrations

```
meal/V1__create_food_categories.sql
meal/V2__create_food_items.sql
meal/V3__create_space_food_item_settings.sql
meal/V4__seed_global_food_catalog.sql
meal/V5__create_meal_combos.sql
meal/V6__create_daily_menus.sql
meal/V7__create_meal_plans.sql
meal/V8__create_meal_participations.sql
meal/V9__seed_default_meal_plans.sql
meal/V10__mess_space_sample_combos.sql   (optional)
meal/V11__space_food_category_settings.sql
```

Add controllers:

```
POST .../food-categories/{categoryId}/deactivate
POST .../food-items/{itemId}/deactivate      (implement if missing)
POST .../meal-combos/{comboId}/deactivate    (implement if missing)
```

Nullable `entitlement_id` on `meal_participation` for Phase 7.

---

## Package structure (suggested)

```
com.acomi.meal
  domain/ · repository/ · service/ · web/ · config/
  FoodCatalogService · MealComboService · DailyMenuService
  MealParticipationService · MealEligibilityService · MealAccessService
```

---

## Frontend contract (must not break)

React Native already calls:

```
GET  /api/v1/spaces/{spaceId}/food-categories
POST /api/v1/spaces/{spaceId}/food-categories
POST /api/v1/spaces/{spaceId}/food-categories/{categoryId}/deactivate
GET  /api/v1/spaces/{spaceId}/food-items?categoryId=
POST /api/v1/spaces/{spaceId}/food-items
PUT  /api/v1/spaces/{spaceId}/food-items/{itemId}
POST /api/v1/spaces/{spaceId}/food-items/{itemId}/deactivate
GET  /api/v1/spaces/{spaceId}/meal-combos
POST /api/v1/spaces/{spaceId}/meal-combos
PUT  /api/v1/spaces/{spaceId}/meal-combos/{comboId}
POST /api/v1/spaces/{spaceId}/meal-combos/{comboId}/deactivate
GET  /api/v1/spaces/{spaceId}/meal-plans
GET  /api/v1/spaces/{spaceId}/meal-participations
POST /api/v1/spaces/{spaceId}/meal-participations
PUT  /api/v1/spaces/{spaceId}/meal-participations/{id}
POST /api/v1/spaces/{spaceId}/meal-participations/{id}/pause|resume|stop
GET  /api/v1/spaces/{spaceId}/daily-menus/today
GET  /api/v1/spaces/{spaceId}/daily-menus/{date}/{mealType}
PUT  /api/v1/spaces/{spaceId}/daily-menus/{date}/{mealType}
POST /api/v1/spaces/{spaceId}/daily-menus/{date}/{mealType}/publish
GET  /api/v1/spaces/{spaceId}/meals/eligibility-summary?date=
```

Types: frontend `src/api/types.ts` — FoodCategoryResponse, FoodItemResponse, MealComboResponse, etc.

---

## Deliverables

1. Flyway migrations under `src/main/resources/db/migration/meal/`
2. JPA entities, repositories, services, REST controllers
3. Global food catalog seed (~60–80 items)
4. MESS space hook or lazy init + optional sample combos
5. Permission flags on `/spaces/my`
6. Integration tests per role (see spec §13)
7. `docs/meals-phase-5-backend-implementation.md` — endpoints, migrations, seed list

---

## Out of scope

MealEntitlement, credit wallet, ledger, invoices, availability polls, MealResponse, WhatsApp, weekly templates, menu history, 500-item catalog, headcount from subscriptions/occupancy, changing occupancy snapshot semantics.

---

## Success criteria

**After Phase 5.1:** Menu Library in app shows ~12 categories, ~60+ items, combo CRUD works — no load errors.

**After full Phase 5:** Library → daily menus → enroll participants → eligible counts — no billing or polls.
```

---

## 15. Changelog

| Date | Change |
|------|--------|
| 2026-06 | Initial Phase 5 backend spec — participation, menu, eligibility; Phase 7 entitlement documented only |
| 2026-06 | Library-first Phase 5.1 prompt; §8.3 food-categories/items APIs; revised migration order |
| 2026-06 | §8.3.1 deactivate APIs (category/item/combo); V11 space_food_category_settings; mobile remove UX |
