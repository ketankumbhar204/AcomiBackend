# Amico — Phase 5.2 Menu Planning (Backend-First Spec)

**Status:** Approved direction — Menu Planning is the primary Meals workflow  
**Supersedes:** Meals tab → Today's Menu as default operator entry (UI integration §4.1)  
**Prerequisite:** Phase 5.1 Menu Library complete  
**Related:** [meals-phase-5-backend.md](./meals-phase-5-backend.md) · [meals-phase-5-ui-integration.md](./meals-phase-5-ui-integration.md) · [meals-phase-5-menu-library-architecture.md](./meals-phase-5-menu-library-architecture.md) · [meals-phase-6-handoff.md](./meals-phase-6-handoff.md)

---

## 1. Executive summary

Menu Library (Phase 5.1) is complete. The next priority is **Menu Planning** — not Today's Menu — because operators primarily plan **future** meals (tomorrow's breakfast, next Sunday's special), not review what is already served today.

**Validated product direction:**

```
Meals tab  →  Menu Planning  (primary, managers)
           →  Menu Library   (secondary, setup)
           →  Participants    (secondary, enrollment)

Residents  →  Today's Menu   (read-only, dashboard / stack)
```

Phase 5.2 is split into four sub-phases:

| Sub-phase | Deliverable | Backend | Frontend |
|-----------|-------------|:-------:|:--------:|
| **5.2A** | Menu Planning hub (date + 3 slots + status) | Verify / extend daily menu APIs | New hub screen |
| **5.2B** | Copy menu / menu history | Copy endpoint + range query | Copy UX |
| **5.2C** | Eligibility summary on planning | Already spec'd | Display per slot |
| **5.2D** | Share preview (no WhatsApp) | Share preview endpoint | Preview screen |

**Golden rules (unchanged):**

1. **Eligible participants ≠ expected headcount.** Phase 5 shows eligibility only. Headcount = Phase 6 poll responses.
2. **Participation ≠ billing.** No credits, charges, or subscription amounts in Phase 5.
3. **Published menu gates sharing and future polls.** Draft menus are operator-only.
4. **Daily menus consume the library.** Planning selects combos/items from Menu Library — never free-text-only menus.

---

## 2. Gap analysis

### 2.1 Current state (frontend)

| Component | Status | Gap |
|-----------|--------|-----|
| `MenuLibraryScreen` | ✅ Complete | Should not be Meals tab default |
| `MealsHomeScreen` | 🔶 Stub | Re-exports `MenuLibraryScreen` — wrong entry point |
| `DailyMenuEditScreen` | 🔶 Built | Single-slot editor; not linked from Meals tab or dashboard |
| `DailyMenuTodayScreen` | 🔶 Built | Read-only today view; only linked from tenant dashboard |
| `DailyMenuSlotCard` | ✅ Ready | Reusable on planning hub |
| `mealsApi` | 🔶 Partial | Missing `getDailyMenusByDate`, `getDailyMenusRange`, copy, share preview |
| Dashboard meals actions | 🔶 Partial | Only Menu Library + Participants — no Plan Menu |
| Item picker in edit | 🔶 Weak | First 24 items as chips; should use `FoodItemMultiPicker` |

### 2.2 Current state (backend — per implementation notes)

| API | Status | Gap for 5.2 |
|-----|--------|-------------|
| `GET /daily-menus/today` | ✅ | Today-only; planning needs arbitrary date |
| `GET /daily-menus/{date}/{mealType}` | ✅ | Single slot — hub needs all 3 slots per date |
| `GET /daily-menus?from=&to=` | ✅ Spec'd | Confirm response shape supports history + status badges |
| `PUT /daily-menus/{date}/{mealType}` | ✅ | Must accept `entryType`, `comboId`, `itemId` |
| `POST .../publish` | ✅ | Per-slot publish |
| `DELETE .../draft` | ✅ | Draft-only delete |
| `GET /meals/eligibility-summary?date=` | ✅ | Must include `published` per slot |
| `GET /meals/eligible-participants` | ✅ | For share preview audience |
| `GET /daily-menus/{date}` | ⬜ **New** | Convenience: all slots for one date (mirrors `/today`) |
| `POST .../copy-from/{sourceDate}` | ⬜ **New** | Atomic copy for 5.2B (optional: client GET+PUT) |
| `GET /meals/share-preview` | ⬜ **New** | Formatted preview text for 5.2D (optional: client compose) |

### 2.3 Domain model — no new entities for 5.2A–D

Existing entities are sufficient:

```
DailyMenu (spaceId, menuDate, mealType, status, publishedAt, notes)
  └── DailyMenuEntry (entryType COMBO|ITEM, comboId?, itemId?, label, sortOrder, isAvailable)
```

Menu history **is** `daily_menus` queried by date range. No history table. No weekly template entity.

---

## 3. Menu Planning as primary workflow — review

### 3.1 Why planning-first (not today-first)

| Operator task | Frequency | Best entry |
|---------------|-----------|------------|
| Plan tomorrow's lunch | Daily | Menu Planning (date = tomorrow) |
| Plan Sunday special | Weekly | Menu Planning (date = Sunday) |
| Fix tonight's dinner draft | Occasional | Menu Planning (date = today) |
| Check what's served now | Occasional | Today's Menu (read-only) |
| Manage catalog | Setup | Menu Library |

**Conclusion:** Menu Planning should be the **Meals tab default for OWNER/MANAGER**. Today's Menu remains available for STAFF/TENANT/CUSTOMER and as a secondary link for managers.

### 3.2 Default date behavior

| Role | Meals tab default | Default date |
|------|-------------------|--------------|
| OWNER / MANAGER | Menu Planning | **Tomorrow** (operators plan ahead) |
| STAFF | Today's Menu (read-only) | Today |
| TENANT / CUSTOMER | Today's Menu (read-only) | Today |

Managers can switch date to today, any future date, or recent past (for copy/reference).

### 3.3 Slot status indicators

Each slot on the planning hub shows one of three states:

| Status | Icon / label | Meaning |
|--------|--------------|---------|
| **Not planned** | ○ Not planned | No `DailyMenu` row for `(date, mealType)` |
| **Draft** | ⚠ Draft | Row exists, `status = DRAFT` |
| **Published** | ✓ Published | `status = PUBLISHED`, `publishedAt` set |

**Day-level summary (optional header):**

```
18 Jun 2026 — 2/3 published · 1 draft
```

Derived client-side from the 3 slot responses — no new backend field.

### 3.4 Draft vs publish semantics

| Action | Behavior |
|--------|----------|
| Save Draft | `PUT` upsert with `status` remaining/implied DRAFT |
| Publish (per slot) | `POST .../publish` → DRAFT → PUBLISHED |
| Publish All (UX) | Client calls publish on each draft slot sequentially |
| Edit published menu | **MVP:** allow `PUT` on published → stays PUBLISHED (in-place update). Alternative (stricter): edit reverts to DRAFT — pick one and document. **Recommended MVP:** in-place update without revert; re-publish not required for label/entry changes. |
| Delete | Only DRAFT slots; `DELETE` returns 204 |

**Resident visibility:** `GET /daily-menus/today` returns **PUBLISHED slots only** (confirm backend filters drafts).

---

## 4. Recommended screen structure

### 4.1 Menu Planning hub (`MenuPlanningScreen`) — Phase 5.2A

Primary Meals tab for managers.

```
┌─────────────────────────────────────────┐
│ Menu Planning                           │
│ Plan meals from your menu library       │
├─────────────────────────────────────────┤
│ Date  ◀  18 Jun 2026  ▶   [Today][+1]  │
│ Status: 2 published · 1 draft           │
├─────────────────────────────────────────┤
│ Breakfast          ✓ Published          │
│   Poha · Tea                            │
│   [Edit]                                │
├─────────────────────────────────────────┤
│ Lunch              ⚠ Draft              │
│   Standard Lunch Thali                  │
│   [Edit] [Publish]                      │
├─────────────────────────────────────────┤
│ Dinner             ○ Not planned        │
│   [Add combo / items]                   │
├─────────────────────────────────────────┤
│ Eligible participants (18 Jun)          │
│   Breakfast 42 · Lunch 48 · Dinner 45   │
│   (not expected headcount)              │
├─────────────────────────────────────────┤
│ Quick links                             │
│   Menu Library · Participants           │
│   Today's Menu (read-only)              │
└─────────────────────────────────────────┘
```

Tap slot → `DailyMenuEditScreen` (existing, enhanced).

### 4.2 Slot editor (`DailyMenuEditScreen`) — enhance existing

Single-slot deep edit. Opened from planning hub.

```
Plan Lunch — 18 Jun 2026                    [Draft]

Planned entries
  [COMBO] Standard Lunch Thali        ✕
  [ITEM]  Green Salad                 ✕

+ Add combo     (chip rail from library)
+ Add items     (FoodItemMultiPicker — category grouped)

Kitchen notes
  [Extra salad today                    ]

[Copy from yesterday]  [Copy from date…]   ← 5.2B

[Save Draft]  [Publish]  [Preview share]    ← preview = 5.2D
```

### 4.3 Today's Menu (`DailyMenuTodayScreen`) — keep, demote

Read-only stack screen for residents and manager quick check. Not the Meals tab default.

### 4.4 Menu Library (`MenuLibraryScreen`) — keep as stack

Accessible from planning hub quick links and dashboard. No longer the tab root.

### 4.5 Share Preview (`MenuSharePreviewScreen`) — Phase 5.2D

```
Share preview — 18 Jun 2026 · Lunch

┌─────────────────────────────────────────┐
│ 🍽 Sunrise Mess                          │
│ Tuesday, 18 Jun 2026 · Lunch            │
│                                         │
│ • Standard Lunch Thali                  │
│   Chapati · Dal Fry · Plain Rice        │
│                                         │
│ Eligible participants: 48               │
│ (Responses collected in a future update)│
└─────────────────────────────────────────┘

[Copy message]     (clipboard — no WhatsApp)
```

### 4.6 Menu History (`MenuHistoryScreen`) — Phase 5.2B (light)

List last 14 days with per-slot status badges. Tap day → Menu Planning hub for that date. Optional v1: skip dedicated screen; copy-from-date picker is enough.

---

## 5. Recommended navigation flow

### 5.1 Meals tab routing

```
SpaceTabNavigator → Meals
  │
  ├─ canManageMeals     → MenuPlanningScreen (default)
  ├─ canViewMeals only  → DailyMenuTodayScreen (default)
  └─ !canViewMeals      → PermissionDeniedScreen
```

### 5.2 Stack routes (MainNavigator)

| Route | Screen | Params | Access |
|-------|--------|--------|--------|
| `Meals` (tab) | `MenuPlanningScreen` or `DailyMenuTodayScreen` | `{ spaceId }` | role-based |
| `MenuLibrary` | `MenuLibraryScreen` | `{ spaceId }` | canViewMeals |
| `DailyMenuEdit` | `DailyMenuEditScreen` | `{ spaceId, menuDate, mealType }` | canManageMeals |
| `DailyMenuToday` | `DailyMenuTodayScreen` | `{ spaceId }` | canViewMeals |
| `MenuSharePreview` | `MenuSharePreviewScreen` | `{ spaceId, menuDate, mealType? }` | canManageMeals |
| `MealParticipantList` | existing | `{ spaceId }` | canViewMeals |
| `MealParticipationForm` | existing | `{ spaceId, ... }` | canManageMeals |

### 5.3 Dashboard quick actions (managers)

Replace current meals sheet:

1. **Plan menu** → `MenuPlanning` (tab) or stack with tomorrow's date
2. **Today's menu** → `DailyMenuToday`
3. **Menu library** → `MenuLibrary`
4. **Participants** → `MealParticipantList`

### 5.4 Operator daily workflow

```
1. Open Meals tab → Menu Planning (default date = tomorrow)
2. Review slot status badges (Breakfast ✓, Lunch ⚠, Dinner ○)
3. Tap Lunch → add combo/items from library → Save Draft
4. Publish Lunch
5. Copy yesterday's Breakfast if similar (5.2B)
6. Check eligible counts per slot (5.2C)
7. Preview share message for tomorrow's lunch (5.2D)
```

---

## 6. Backend API specification (Phase 5.2)

Base path: `/api/v1/spaces/{spaceId}`  
Envelope: `{ "success": true, "data": ... }`  
Auth: JWT + space membership; role checks per endpoint.

### 6.1 Phase 5.2A — verify / extend daily menu APIs

#### 6.1.1 GET `/daily-menus/{date}` — **NEW convenience endpoint**

Returns all meal slots for a calendar date (0–3 menus). Mirrors `/daily-menus/today` but for any date.

**Auth:** `canViewMeals`

**Path params:** `date` — ISO `YYYY-MM-DD` in space timezone context (store/query as date, not timestamp).

**Response:** `DailyMenuResponse[]` — same shape as `/today`. Missing slots are omitted (client treats as "Not planned").

```json
{
  "success": true,
  "data": [
    {
      "dailyMenuId": "uuid",
      "menuDate": "2026-06-18",
      "mealType": "BREAKFAST",
      "status": "PUBLISHED",
      "publishedAt": "2026-06-17T18:30:00Z",
      "notes": null,
      "options": [
        {
          "optionId": "uuid",
          "entryType": "ITEM",
          "comboId": null,
          "itemId": "uuid-poha",
          "label": "Poha",
          "sortOrder": 1,
          "isAvailable": true
        },
        {
          "optionId": "uuid",
          "entryType": "ITEM",
          "comboId": null,
          "itemId": "uuid-tea",
          "label": "Tea",
          "sortOrder": 2,
          "isAvailable": true
        }
      ]
    },
    {
      "dailyMenuId": "uuid",
      "menuDate": "2026-06-18",
      "mealType": "LUNCH",
      "status": "DRAFT",
      "publishedAt": null,
      "notes": "Extra salad",
      "options": [
        {
          "optionId": "uuid",
          "entryType": "COMBO",
          "comboId": "uuid-thali",
          "itemId": null,
          "label": "Standard Lunch Thali",
          "sortOrder": 1,
          "isAvailable": true
        }
      ]
    }
  ]
}
```

**Rules:**

- Include DRAFT and PUBLISHED menus for managers.
- For `canViewMeals` without `canManageMeals` (STAFF/TENANT/CUSTOMER): return **PUBLISHED only** (same rule as `/today`).
- Resolve combo entries: optionally expand `items[]` names on GET for display (from `meal_combo_item` join) — if not on menu response, client resolves via combo library cache.

#### 6.1.2 GET `/daily-menus/today` — verify behavior

- Returns only **PUBLISHED** menus for today's date.
- Empty array if nothing published.

#### 6.1.3 GET `/daily-menus?from=&to=` — verify for history (5.2B)

**Query params:** `from`, `to` — ISO dates inclusive.

**Response:** `DailyMenuResponse[]` flat list, all slots in range. Used for menu history list and copy-source picker.

**Recommended max range:** 31 days (return `400` if exceeded).

**Optional enhancement:** include lightweight summary per date:

```json
{
  "date": "2026-06-17",
  "breakfastStatus": "PUBLISHED",
  "lunchStatus": "PUBLISHED",
  "dinnerStatus": "DRAFT"
}
```

If not added, client derives summary from flat list.

#### 6.1.4 PUT `/daily-menus/{date}/{mealType}` — verify upsert

**Auth:** `canManageMeals`

**Body:**

```json
{
  "options": [
    {
      "entryType": "COMBO",
      "comboId": "uuid",
      "itemId": null,
      "label": "Standard Lunch Thali",
      "sortOrder": 1,
      "isAvailable": true
    },
    {
      "entryType": "ITEM",
      "comboId": null,
      "itemId": "uuid-tea",
      "label": "Tea",
      "sortOrder": 2,
      "isAvailable": true
    }
  ],
  "notes": "Optional kitchen note"
}
```

**Validation:**

- At least one option with `isAvailable: true` required to publish (not necessarily to save draft — allow empty draft).
- `comboId` must reference active combo in space.
- `itemId` must reference active (non-hidden) food item for space.
- `entryType = COMBO` → `comboId` required, `itemId` null.
- `entryType = ITEM` → `itemId` required, `comboId` null.
- Replace-all semantics on upsert: sent `options[]` replaces existing entries.

**Response:** `DailyMenuResponse` with `status: DRAFT` if new; unchanged status if updating published (MVP in-place edit).

#### 6.1.5 POST `/daily-menus/{date}/{mealType}/publish`

**Auth:** `canManageMeals`

- Requires at least one available option.
- Sets `status = PUBLISHED`, `publishedAt = now()`.
- Returns updated `DailyMenuResponse`.
- `409` if already published with no changes needed (or idempotent 200 — pick one).

#### 6.1.6 DELETE `/daily-menus/{date}/{mealType}`

**Auth:** `canManageMeals`

- Allowed only when `status = DRAFT`.
- `409` or `403` if PUBLISHED.
- Returns `204 No Content`.

---

### 6.2 Phase 5.2B — copy menu

#### Option A (recommended): POST copy endpoint

**POST** `/daily-menus/{targetDate}/{mealType}/copy-from/{sourceDate}`

**Auth:** `canManageMeals`

**Body (optional):**

```json
{
  "publish": false
}
```

**Behavior:**

1. Load source menu for `(sourceDate, mealType)`. `404` if source missing.
2. If target already exists as PUBLISHED → `409` unless `force: true` in body (default: reject overwrite of published).
3. Upsert target as **DRAFT** copying entries (comboId, itemId, label, sortOrder, isAvailable) and notes.
4. Do not copy `dailyMenuId`, `publishedAt`.
5. Return target `DailyMenuResponse`.

**Response:** `200` + `DailyMenuResponse`

#### Option B (acceptable MVP): client-side copy

Client: `GET source` → `PUT target`. No new endpoint. Less atomic but sufficient for v1.

**Recommendation:** implement Option A on backend for correctness; frontend can fall back to Option B during development.

#### Copy yesterday shortcut

Client computes `sourceDate = targetDate - 1 day`. Same endpoint.

---

### 6.3 Phase 5.2C — eligibility on planning screen

#### GET `/meals/eligibility-summary?date=YYYY-MM-DD`

**Auth:** `canViewMeals`

**Already spec'd.** Verify response includes:

```json
{
  "date": "2026-06-18",
  "slots": [
    {
      "mealType": "BREAKFAST",
      "eligibleCount": 42,
      "pausedCount": 3,
      "published": true,
      "byPlan": [
        { "mealPlanCode": "FULL", "count": 30 },
        { "mealPlanCode": "LUNCH", "count": 0 }
      ]
    },
    {
      "mealType": "LUNCH",
      "eligibleCount": 48,
      "pausedCount": 2,
      "published": false
    },
    {
      "mealType": "DINNER",
      "eligibleCount": 45,
      "pausedCount": 1,
      "published": true
    }
  ]
}
```

**Rules:**

- `eligibleCount` = ACTIVE members with ACTIVE participation covering that meal type on that date.
- `published` = whether daily menu for that slot is PUBLISHED (join `daily_menus`).
- **Never** label this as headcount or expected meals.

#### GET `/meals/eligible-participants?date=&mealType=`

**Auth:** `canManageMeals`

Used by share preview and future WhatsApp audience. Returns `{ memberId, memberName, mobileNumber, mealPlanCode, mealPlanName }[]`.

---

### 6.4 Phase 5.2D — share preview (no send)

#### GET `/meals/share-preview?date=&mealType=`

**Auth:** `canManageMeals`

**Query params:**

- `date` — required
- `mealType` — optional; omit for all published slots that day

**Rules:**

- Only include **PUBLISHED** slots. Draft slots omitted or listed as "Not published".
- Compose human-readable message server-side for consistency across app and future WhatsApp integration.

**Response:**

```json
{
  "spaceName": "Sunrise Mess",
  "menuDate": "2026-06-18",
  "mealType": "LUNCH",
  "dailyMenuId": "uuid",
  "status": "PUBLISHED",
  "eligibleCount": 48,
  "messageText": "🍽 Sunrise Mess\nTuesday, 18 Jun 2026 · Lunch\n\n• Standard Lunch Thali\n  Chapati · Dal Fry · Plain Rice\n\nEligible participants: 48",
  "slots": [
    {
      "mealType": "LUNCH",
      "dailyMenuId": "uuid",
      "lines": [
        { "entryType": "COMBO", "label": "Standard Lunch Thali", "detail": "Chapati · Dal Fry · Plain Rice" }
      ],
      "notes": "Extra salad today",
      "eligibleCount": 48
    }
  ]
}
```

**Alternative:** client composes from `GET /daily-menus/{date}` + `GET /meals/eligibility-summary`. Backend endpoint preferred for Phase 6 WhatsApp parity.

**Out of scope:** WhatsApp send, poll creation, response links.

---

## 7. Database — no new migrations for 5.2A

Existing tables (from V37/V42):

```sql
daily_menus (
  id, space_id, menu_date, meal_type, status, published_at, notes,
  created_at, updated_at,
  UNIQUE (space_id, menu_date, meal_type)
)

daily_menu_entries (
  id, daily_menu_id, entry_type, combo_id, item_id,
  label, sort_order, is_available
)
```

Copy and share preview are service-layer operations on existing rows.

---

## 8. Permissions (unchanged)

| Endpoint group | canViewMeals | canManageMeals |
|----------------|:------------:|:--------------:|
| GET daily menus (published only) | ✅ | — |
| GET daily menus (include drafts) | — | ✅ |
| PUT / publish / delete / copy | — | ✅ |
| GET eligibility-summary | ✅ | — |
| GET eligible-participants | — | ✅ |
| GET share-preview | — | ✅ |

---

## 9. Implementation order

### Phase 5.2A — Menu Planning (backend first, then UI)

**Backend tasks:**

1. Verify `daily_menu_entries` supports `entry_type`, `combo_id`, `item_id`.
2. Implement / verify `GET /daily-menus/{date}` (all slots for date).
3. Verify `PUT` upsert with COMBO + ITEM entries and library validation.
4. Verify publish / delete-draft semantics.
5. Verify `/today` returns published-only.
6. Integration tests: create draft → publish → resident GET today sees menu; manager GET by date sees draft + published.

**Frontend tasks (after backend):**

1. Create `MenuPlanningScreen` with date picker + 3 slot cards + status badges.
2. Replace `MealsHomeScreen` stub — managers → planning; others → today.
3. Wire slot tap → `DailyMenuEditScreen`.
4. Enhance edit screen with `FoodItemMultiPicker`.
5. Update dashboard quick actions.
6. Add `mealsApi.getDailyMenusByDate(spaceId, date)`.

**Exit criteria:** Manager plans and publishes tomorrow's lunch from Meals tab in under 2 minutes.

---

### Phase 5.2B — Copy menu / history

**Backend:** `POST .../copy-from/{sourceDate}` + verify range query.

**Frontend:** Copy yesterday / copy from date on edit screen; optional history list.

**Exit criteria:** Copy 17 Jun lunch → 18 Jun lunch as draft in one action.

---

### Phase 5.2C — Eligibility on planning

**Backend:** Verify eligibility-summary `published` flag and date param.

**Frontend:** Eligibility strip on planning hub; per-slot counts on edit screen footer.

**Exit criteria:** Operator sees "Lunch: 48 eligible participants" on planning screen — never "expected meals".

---

### Phase 5.2D — Share preview

**Backend:** `GET /meals/share-preview`.

**Frontend:** Preview screen + copy to clipboard; entry from planning hub and edit screen.

**Exit criteria:** Operator copies WhatsApp-ready text without sending.

---

## 10. Frontend API additions (`mealsApi.ts`)

| Function | Endpoint | Phase |
|----------|----------|-------|
| `getDailyMenusByDate(spaceId, date)` | GET `/daily-menus/{date}` | 5.2A |
| `getDailyMenusRange(spaceId, from, to)` | GET `/daily-menus?from=&to=` | 5.2B |
| `copyDailyMenu(spaceId, targetDate, mealType, sourceDate)` | POST `.../copy-from/{sourceDate}` | 5.2B |
| `getSharePreview(spaceId, date, mealType?)` | GET `/meals/share-preview` | 5.2D |

Existing functions used unchanged: `upsertDailyMenu`, `publishDailyMenu`, `deleteDailyMenu`, `getEligibilitySummary`, `getEligibleParticipants`.

---

## 11. Testing checklist

### 5.2A

- [ ] GET `/daily-menus/2026-06-18` returns 0–3 menus with correct status
- [ ] TENANT GET same date returns published slots only
- [ ] PUT creates draft with COMBO + ITEM entries
- [ ] PUT rejects invalid/deactivated comboId or itemId
- [ ] POST publish sets `publishedAt`
- [ ] DELETE works on draft, fails on published
- [ ] GET `/daily-menus/today` excludes drafts

### 5.2B

- [ ] Copy source → target creates draft with same entries
- [ ] Copy to existing published target returns 409
- [ ] Range query returns 14 days for history UI

### 5.2C

- [ ] Eligibility summary respects meal plan slot coverage
- [ ] PAUSED participations excluded from count
- [ ] `published` flag matches daily menu status

### 5.2D

- [ ] Share preview includes only published slots
- [ ] Combo lines expand child item names
- [ ] Draft slot excluded from message text
- [ ] eligibleCount matches eligibility-summary for same date/slot

---

## 12. Out of scope (Phase 5.2)

- Weekly menu templates
- WhatsApp integration
- Availability polls and MealResponse
- Headcount / forecasting
- Meal credits, billing, subscriptions
- Bulk publish single API (client loops per slot)
- Menu consumption tracking

---

## 13. Changelog

| Date | Change |
|------|--------|
| 2026-06 | Menu Planning validated as primary workflow; backend-first spec for 5.2A–D |

---

## 14. Copy-paste backend implementation prompt

Copy the block below into the **backend repository** Cursor chat.

Also copy into backend `docs/`:

1. `meals-phase-5.2-menu-planning.md` (this file)
2. `meals-phase-5-backend.md`
3. `meals-phase-5-backend-implementation.md`
4. `permissions-backend-spec.md`

```markdown
# Task: Implement Amico Phase 5.2 — Menu Planning (Backend)

## Context

Amico Spring Boot 3 / Java 17 modular monolith. **Phase 5.1 Menu Library is complete** (food categories, items, combos, global seed, deactivate APIs).

The React Native app is wired for meal APIs. Field names and paths must match frontend `src/api/types.ts` and `src/api/mealsApi.ts` exactly (`ApiResponse<T>` envelope).

**Product direction:** Operators plan **future** meals. Menu Planning (date + breakfast/lunch/dinner) is the primary workflow — not "Today's Menu" as the main surface.

**Spec:** `docs/meals-phase-5.2-menu-planning.md` (primary) · `docs/meals-phase-5-backend.md` · `docs/meals-phase-5-backend-implementation.md`

---

## Golden rules

1. **Eligible participants ≠ headcount.** Phase 5 exposes eligibility counts only. Headcount = Phase 6 poll responses.
2. **Daily menus consume the library.** Validate comboId/itemId against space catalog on upsert.
3. **Published menus gate resident view and share preview.** Drafts are manager-only.
4. **No billing, credits, polls, or WhatsApp** in this task.
5. **No new entities** unless strictly required — use existing `daily_menus` + `daily_menu_entries`.

---

## What already exists (verify first)

Per `meals-phase-5-backend-implementation.md`, these may already be implemented:

- `daily_menus`, `daily_menu_entries` (V37, V42)
- GET `/daily-menus/today`
- GET `/daily-menus/{date}/{mealType}`
- GET `/daily-menus?from=&to=`
- PUT `/daily-menus/{date}/{mealType}`
- POST `/daily-menus/{date}/{mealType}/publish`
- DELETE `/daily-menus/{date}/{mealType}` (draft only)
- GET `/meals/eligibility-summary?date=`
- GET `/meals/eligible-participants?date=&mealType=`

**Start by auditing existing DailyMenuService.** Fix gaps before adding new endpoints.

---

## Phase 5.2A — Menu Planning APIs (priority)

### 1. GET `/api/v1/spaces/{spaceId}/daily-menus/{date}`

New convenience endpoint. Returns `DailyMenuResponse[]` for all slots on that date (0–3 items).

- Managers (`canManageMeals`): include DRAFT + PUBLISHED
- Read-only roles (`canViewMeals` only): PUBLISHED slots only
- Missing slots omitted (not 404)

Response shape must match frontend `DailyMenuResponse`:

```typescript
{
  dailyMenuId, menuDate, mealType, status, publishedAt?, notes?,
  options: [{ optionId?, entryType?, comboId?, itemId?, label, sortOrder, isAvailable }]
}
```

### 2. Verify PUT upsert

Body uses `options[]` with:

- `entryType`: `COMBO` | `ITEM`
- `comboId` / `itemId` (nullable per type)
- `label`, `sortOrder`, `isAvailable`

Replace-all entries on upsert. Validate IDs against space library (active combo, active/non-hidden item).

Allow empty options for draft save. Require ≥1 available option to publish.

### 3. Verify publish / delete / today

- `POST .../publish` → DRAFT to PUBLISHED, set `publishedAt`
- `DELETE` → draft only, 204
- `GET /daily-menus/today` → **published only**

### 4. Published edit policy (MVP)

Allow PUT on published menu without reverting to draft (in-place update). Document in service.

---

## Phase 5.2B — Copy menu

### POST `/api/v1/spaces/{spaceId}/daily-menus/{targetDate}/{mealType}/copy-from/{sourceDate}`

- Auth: `canManageMeals`
- Load source menu; 404 if missing
- Upsert target as DRAFT with copied entries + notes
- Reject if target is PUBLISHED (409) unless optional `force: true` in body
- Return target `DailyMenuResponse`

Also verify `GET /daily-menus?from=&to=` supports 14-day history (max 31 days, 400 if exceeded).

---

## Phase 5.2C — Eligibility (verify)

`GET /meals/eligibility-summary?date=` must return:

```json
{
  "date": "2026-06-18",
  "slots": [
    { "mealType": "BREAKFAST", "eligibleCount": 42, "pausedCount": 3, "published": true, "byPlan": [...] }
  ]
}
```

- `eligibleCount`: ACTIVE member + ACTIVE participation + plan covers slot + date in range
- `published`: from daily_menus join
- PAUSED excluded

---

## Phase 5.2D — Share preview (no send)

### GET `/api/v1/spaces/{spaceId}/meals/share-preview?date=&mealType=`

- Auth: `canManageMeals`
- `mealType` optional — omit for all published slots that day
- Include only PUBLISHED menus
- Return structured payload + preformatted `messageText` for clipboard/WhatsApp prep

```json
{
  "spaceName": "...",
  "menuDate": "2026-06-18",
  "mealType": "LUNCH",
  "dailyMenuId": "uuid",
  "status": "PUBLISHED",
  "eligibleCount": 48,
  "messageText": "🍽 ...",
  "slots": [{ "mealType", "dailyMenuId", "lines": [{ "entryType", "label", "detail" }], "notes", "eligibleCount" }]
}
```

Expand COMBO lines with child item names from `meal_combo_item` join.

**Do NOT** implement WhatsApp send, poll open, or MealResponse.

---

## Permissions

Use existing `MealAccessService`:

- `requireViewMeals` — read published menus, eligibility summary
- `requireManageMeals` — upsert, publish, delete, copy, share preview, eligible participants list

Extend `SpacePermissionsResponse` on `GET /spaces/my` if not already present.

---

## Tests (required)

`DailyMenuServiceTest` / integration tests:

1. GET by date returns correct slot statuses for manager vs tenant
2. Upsert COMBO + ITEM entries; invalid comboId → 400
3. Publish requires available options
4. Today endpoint excludes drafts
5. Copy source → target draft; copy to published target → 409
6. Eligibility summary `published` flag accurate
7. Share preview excludes drafts; messageText includes combo detail
8. STAFF can view published; cannot upsert/publish/copy

---

## Response contract alignment

Match frontend types exactly:

- `dailyMenuId`, `menuDate`, `mealType`, `status`, `publishedAt`, `notes`, `options`
- Option fields: `optionId`, `entryType`, `comboId`, `itemId`, `label`, `sortOrder`, `isAvailable`
- DELETE and deactivate endpoints: `204 No Content`

---

## Deliverables

1. Audit + fix existing daily menu APIs (5.2A)
2. New GET `/daily-menus/{date}`
3. Copy endpoint (5.2B)
4. Verify eligibility summary (5.2C)
5. Share preview endpoint (5.2D)
6. Integration tests
7. Update `docs/meals-phase-5-backend-implementation.md` with any contract changes

## Out of scope

Weekly templates, WhatsApp, polls, MealResponse, headcount API, billing, credits, new migrations unless bug fix required.
```
