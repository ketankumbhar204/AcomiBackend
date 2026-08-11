# Amico — Meal Management UI Integration (Phase 5)

Frontend guide for **meal participation**, **menu master**, **daily menu planning**, and **Meals dashboard MVP**.

**Prerequisites:**

- [membership-ui-integration.md](./membership-ui-integration.md) — members list, roles
- [member-management-ui-integration.md](./member-management-ui-integration.md) — member detail
- [permissions-backend-spec.md](./permissions-backend-spec.md) — role gating pattern
- [meals-phase-5-backend.md](./meals-phase-5-backend.md) — backend source of truth

**Backend spec location for handoff:** Copy `docs/meals-phase-5-backend.md` to the backend repo before implementation.

---

## 1. Product principles (UI must reflect)

1. **Participation ≠ payment.** No charge amount fields in Phase 5 UI.
2. **Same meal flow for PG and Mess** — menu planning, participant list, future polls (Phase 6).
3. **Headcount comes from availability responses (Phase 6), not subscription count.** Phase 5 shows **eligible participants** only, not "expected meals = subscribers."
4. **Member is the person** — enroll TENANT (PG), CUSTOMER (Mess), or STAFF; no separate "subscriber" screen.
5. **Occupancy food fields stay on contract UI** — do not merge into meal participation forms except move-in suggestion bridge.

---

## 2. Space type behavior

| Space type | Accommodation tab | Meals tab | Typical participants |
|------------|:-----------------:|:---------:|---------------------|
| PG | ✅ | ✅ | TENANT, STAFF |
| HOSTEL | ✅ | ✅ | TENANT, STAFF |
| CO_LIVING | ✅ | ✅ | TENANT, STAFF |
| RENTAL | ✅ | ✅ (if offered) | TENANT |
| MESS | ❌ hidden | ✅ primary | CUSTOMER, STAFF |

Use `isAccommodationApplicable(spaceType)` (existing) + new `canViewMeals` / `canManageMeals` from permissions.

---

## 3. Permissions (UI)

Extend `SpacePermissionsResponse` / `deriveSpacePermissions` fallback:

| Flag | OWNER | MANAGER | STAFF | TENANT | CUSTOMER |
|------|:-----:|:-------:|:-----:|:------:|:--------:|
| `canManageMeals` | ✅ | ✅ | ❌ | ❌ | ❌ |
| `canViewMeals` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `canManageMealParticipation` | ✅ | ✅ | ❌ | ❌ | ❌ |

### UI gating

| Surface | Condition |
|---------|-----------|
| Meals tab | `canViewMeals` |
| Plan / edit menu, enroll participant | `canManageMeals` |
| Dashboard Meals quick actions | `canManageMeals` |
| View own participation on profile | TENANT/CUSTOMER linked member |
| Today's menu (read-only) | All roles with `canViewMeals` |

Add `src/utils/mealPermissions.ts` mirroring backend policy.

---

## 4. Navigation & screens

### 4.1 Replace Meals tab placeholder

Current: `SpaceTabNavigator` → `ScreenPlaceholder` for Meals.

**Phase 5 Meals tab (MVP):**

```
MealsHomeScreen
  ├── Today's Menu (default section)
  ├── Quick links: Plan Menu | Participants | Menu Library
  └── Eligible count summary per slot (from eligibility-summary API)
```

### 4.2 Screen list

| Screen | Route (suggested) | Access |
|--------|-------------------|--------|
| Meals Home | `Meals` tab | canViewMeals |
| Today's Menu | `DailyMenuToday` | canViewMeals |
| Plan Daily Menu | `DailyMenuEdit` `{ date, mealType }` | canManageMeals |
| Menu Library (combos) | `MealComboList` / `MealComboForm` | manage / view |
| Meal Participants | `MealParticipantList` | canViewMeals |
| Enroll Participant | `MealParticipationForm` | canManageMeals |
| Member Meals section | Section on `MemberDetailsScreen` | view / manage |

### 4.3 Dashboard integration

Enable the stubbed **Meals** module card on `DashboardScreen` (mirror Residents pattern):

**Quick actions bottom sheet (canManageMeals):**

1. View today's menu → Meals tab or DailyMenuToday
2. Plan today's menu → DailyMenuEdit (pick slot)
3. Meal participants → MealParticipantList
4. Add participant → member picker + MealParticipationForm

**Metrics:**

- Replace placeholder "Today's Meals: 42" with eligibility-summary total or "Menu published" status.
- Do not label as "subscribers eating" — use "Eligible participants" until Phase 6 headcount exists.

**TENANT / CUSTOMER dashboard:**

- Show today's menu card (read-only).
- Link to own meal participation on member profile if linked.

---

## 5. API integration

Base: `/api/v1/spaces/{spaceId}`

See [meals-phase-5-backend.md](./meals-phase-5-backend.md) §8 for full contract.

### 5.1 TypeScript types (add to `src/api/types.ts`)

```typescript
export type MealPlanCode =
  | 'NONE'
  | 'BREAKFAST'
  | 'LUNCH'
  | 'DINNER'
  | 'FULL'
  | 'CUSTOM';

export type MealParticipationStatus = 'ACTIVE' | 'PAUSED' | 'STOPPED';

export type MealType = 'BREAKFAST' | 'LUNCH' | 'DINNER';

export type DailyMenuStatus = 'DRAFT' | 'PUBLISHED';

export interface MealPlanResponse {
  mealPlanId: UUID;
  code: MealPlanCode;
  name: string;
  breakfastIncluded: boolean;
  lunchIncluded: boolean;
  dinnerIncluded: boolean;
  isActive: boolean;
}

export interface MealParticipationResponse {
  participationId: UUID;
  memberId: UUID;
  memberName: string;
  memberRole: MembershipRole;
  mealPlanId: UUID;
  mealPlanCode: MealPlanCode;
  mealPlanName: string;
  status: MealParticipationStatus;
  effectiveFrom: string;
  effectiveTo?: string | null;
  sourceOccupancyId?: UUID | null;
}

export interface MealComboResponse {
  comboId: UUID;
  name: string;
  description?: string | null;
  isActive: boolean;
}

export interface DailyMenuOptionResponse {
  optionId: UUID;
  comboId?: UUID | null;
  label: string;
  sortOrder: number;
  isAvailable: boolean;
}

export interface DailyMenuResponse {
  dailyMenuId: UUID;
  menuDate: string;
  mealType: MealType;
  status: DailyMenuStatus;
  publishedAt?: string | null;
  notes?: string | null;
  options: DailyMenuOptionResponse[];
}

export interface MealEligibilitySummaryResponse {
  date: string;
  slots: Array<{
    mealType: MealType;
    eligibleCount: number;
    published: boolean;
  }>;
}

// Extend MemberDetailsResponse
export interface MemberMealParticipationSummary {
  participationId: UUID;
  mealPlanCode: MealPlanCode;
  mealPlanName: string;
  status: MealParticipationStatus;
  effectiveFrom: string;
  effectiveTo?: string | null;
}
```

Extend `SpacePermissionsResponse`:

```typescript
canManageMeals?: boolean;
canViewMeals?: boolean;
canManageMealParticipation?: boolean;
```

Extend `MemberDetailsResponse`:

```typescript
mealParticipation?: MemberMealParticipationSummary | null;
```

### 5.2 API module (add `src/api/mealsApi.ts`)

| Function | Endpoint |
|----------|----------|
| `getMealPlans(spaceId)` | GET `/meal-plans` |
| `getMealParticipations(spaceId, params?)` | GET `/meal-participations` |
| `createMealParticipation(spaceId, body)` | POST `/meal-participations` |
| `updateMealParticipation(spaceId, id, body)` | PUT `/meal-participations/{id}` |
| `pauseMealParticipation` / `resume` / `stop` | POST `.../pause` etc. |
| `getMealCombos(spaceId)` | GET `/meal-combos` |
| `getDailyMenusToday(spaceId)` | GET `/daily-menus/today` |
| `upsertDailyMenu(spaceId, date, mealType, body)` | PUT `/daily-menus/{date}/{mealType}` |
| `publishDailyMenu(spaceId, date, mealType)` | POST `.../publish` |
| `getEligibilitySummary(spaceId, date?)` | GET `/meals/eligibility-summary` |

---

## 6. UX flows

### 6.1 Enroll Mess customer

1. Members → Add Member (role CUSTOMER) — existing flow
2. Member Details → Meals section → Enroll
3. Pick plan: FULL / LUNCH / DINNER / CUSTOM
4. effectiveFrom = today
5. No price field

### 6.2 Enroll PG tenant (from occupancy bridge)

1. OccupancyWizard move-in with foodEnabled → review step shows checkbox: "Enroll in meals (Full plan)" pre-checked
2. On submit: occupancy API + optional participation create (backend flag or separate call)
3. Member Details → Meals section shows participation

### 6.3 Plan today's lunch (Mess or PG)

1. Meals tab → Plan Menu → pick LUNCH
2. Add combos from library or type labels
3. Save draft → Publish
4. Published menu visible on Today's Menu

### 6.4 Participant list

- Filter: ACTIVE / PAUSED / STOPPED
- Badge: plan code + member role
- Actions (manage): Pause, Resume, Stop, Change plan

**Do not show:** credit balance, amount owed, subscription expiry (Phase 7).

---

## 7. i18n keys (suggested)

Add under `meals.*` in `en.json`:

- `meals.title`, `meals.todayMenu`, `meals.planMenu`
- `meals.participants`, `meals.eligibleCount`
- `meals.plan.full`, `meals.plan.lunch`, etc.
- `meals.status.active`, `meals.status.paused`, `meals.status.stopped`
- `meals.enroll`, `meals.noParticipation`
- `dashboard.quickActions.meals` — remove "Coming soon" when shipped

---

## 8. Phase 6 UI prep (do not build yet)

Reserve navigation hooks for:

- Share menu (WhatsApp) — uses eligible participants list
- Availability poll responses per slot
- Headcount display: "Expected Lunch: 35" from responses API

Phase 5 UI copy should say **"Eligible participants"** not **"Expected meals"** until Phase 6.

---

## 9. Phase 7 UI (document only)

Future screens — **no stubs in Phase 5:**

- Meal credits balance
- Entitlement plan picker (30 thaalis, unlimited, pay-per-use)
- Carry-forward / expiry display
- Invoice and payment history

---

## 10. Implementation sequence (UI)

| Step | Task |
|------|------|
| 1 | `mealPermissions.ts`, extend `deriveSpacePermissions` |
| 2 | API types + `mealsApi.ts` |
| 3 | MealsHomeScreen — replace placeholder tab |
| 4 | DailyMenuToday + DailyMenuEdit |
| 5 | MealComboList (menu library) |
| 6 | MealParticipantList + enrollment form |
| 7 | MemberDetailsScreen — Meals section |
| 8 | Dashboard — enable Meals card + eligibility metric |
| 9 | OccupancyWizard — meal enrollment suggestion on move-in |
| 10 | i18n |

---

## 11. Copy-paste UI implementation prompt

Paste this into **Cursor in the UI (React Native) repo** after backend Phase 5 APIs are available (or mock from spec):

```markdown
# Task: Implement Amico Phase 5 — Meal Management UI (React Native)

## Context

Amico React Native app. Phases 1–4 complete: auth, spaces, members, accommodation, occupancy, permissions, dashboard Residents module.

**Source of truth:**
- Backend API spec: `docs/meals-phase-5-backend.md`
- UI integration guide: `docs/meals-phase-5-ui-integration.md`

Read both before coding.

## Product rules (critical)

1. **Participation ≠ payment** — no charge/credit/billing fields in any Phase 5 screen.
2. **Member is the person anchor** — enroll TENANT (PG), CUSTOMER (Mess), STAFF via MealParticipation; no separate subscriber entity.
3. **Same meals UX for PG and Mess** — menu planning and participant list; accommodation tab stays hidden for MESS.
4. **Eligible participants ≠ expected headcount** — label counts as "Eligible participants" until Phase 6 availability responses exist. Never show "subscribers eating tomorrow."
5. **Occupancy foodEnabled/foodChargeSnapshot** — leave on contract terms UI only; meal enrollment is separate (optional bridge on move-in).

## Architecture

Three layers — implement Phase 5 UI only:
- Participation: plan (NONE/BREAKFAST/LUNCH/DINNER/FULL/CUSTOM) + status (ACTIVE/PAUSED/STOPPED)
- Menu: combo library + daily menu (draft/publish) + today's menu view
- Eligibility summary: per-slot eligible count from API

Do NOT build: polls, WhatsApp share, response collection, credit wallet, billing, invoices.

## Permissions

Add `src/utils/mealPermissions.ts` and extend `SpacePermissionsResponse` / `deriveSpacePermissions`:
- `canManageMeals` → OWNER, MANAGER
- `canViewMeals` → all active members

Gate Meals tab, dashboard card, and forms accordingly.

## Screens to implement

1. **MealsHomeScreen** — replace Meals tab placeholder in `SpaceTabNavigator`
2. **DailyMenuToday** — today's breakfast/lunch/dinner from `GET /daily-menus/today`
3. **DailyMenuEdit** — upsert + publish daily menu per slot
4. **MealComboList** + form — menu master library
5. **MealParticipantList** — filter by status, show plan + role badges
6. **MealParticipationForm** — enroll/change plan/pause/resume/stop
7. **MemberDetailsScreen** — new "Meals" section showing current participation
8. **DashboardScreen** — enable Meals module card (remove comingSoon); bottom sheet: view menu, plan menu, participants, add participant
9. **OccupancyWizard** — on move-in with foodEnabled, pre-check "Enroll in meals (Full plan)"

## API layer

Add types to `src/api/types.ts` and `src/api/mealsApi.ts` per meals-phase-5-ui-integration.md §5.

Extend `MemberDetailsResponse` with optional `mealParticipation` block.

## Space types

- MESS: Meals tab is primary; no accommodation flows
- PG/HOSTEL/CO_LIVING/RENTAL: Meals tab + existing accommodation

## i18n

Add `meals.*` keys to locale files.

## Patterns to follow

- Mirror Residents dashboard pattern (`ModuleActionCard` + bottom sheet)
- Mirror member management permissions (`useSpacePermissions`)
- Match existing API error handling and loading states
- Use existing `Screen`, form, and list components

## Out of scope

Availability polls, WhatsApp integration, headcount from responses, meal credits, entitlements, payment UI, weekly menu templates, menu history, consumption tracking.

## Deliverables

1. Navigation routes for meal screens
2. API integration with backend (or typed mocks if backend not ready)
3. Permissions gating
4. Dashboard + Member profile integration
5. OccupancyWizard meal enrollment suggestion
6. i18n strings

## Verification

- MESS space: CUSTOMER enrolled, daily menu published, no accommodation UI
- PG space: tenant enrolled via member profile or move-in bridge
- TENANT sees today's menu read-only; cannot manage
- STAFF sees menus; cannot manage participation
- No price/credit fields anywhere
```

---

## 12. Changelog

| Date | Change |
|------|--------|
| 2026-06 | Initial Phase 5 UI integration guide + implementation prompt |
