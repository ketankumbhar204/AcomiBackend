# Phase 7 — Unified Dashboard & Payments Ledger

**Status:** 🔶 Partial (MVP + billing foundation)  
**Last updated:** June 2026  
**Audience:** Backend, React Native, Product, QA  
**Related:** [development-roadmap.md](./development-roadmap.md) · [api-reference.md](./api-reference.md) · [permissions-backend-spec.md](./permissions-backend-spec.md) · [meals-phase-6-handoff.md](./meals-phase-6-handoff.md)

---

## 1. Goal

One operational dashboard and one payments tab for **all space types**.

**Pay per meal spaces** use:

| Card | Meaning |
|------|---------|
| **Expected Charges** | What members owe this month |
| **Collected** | Amount received |
| **Pending** | `Expected − Collected` |

**Prepaid balance (meal balance) spaces** use:

| Card | Meaning |
|------|---------|
| **Balance sold** | Prepaid packs / top-ups sold this month |
| **Balance used** | Meals or amount consumed from balance |
| **Balance left** | Remaining prepaid balance (snapshot) |

Never derive expected charges from **published menus alone** — only from confirmed meal activity and/or occupancy contracts.

**Acomi billing standard:** two concepts only — pay when you eat, or eat from prepaid balance. Hybrid behaviour is automatic fallback when balance is zero, not a third billing type.

---

## 2. Implementation priority (recommended order)

| # | Track | Status | Outcome |
|---|--------|--------|---------|
| 1 | **Mess billing type foundation** | ✅ Shipped | `PAY_PER_MEAL` \| `PREPAID_BALANCE` + settings API + dashboard cards |
| 1b | **Member balance wallet** | ⬜ Next | Real sold/consumed/remaining; debit on poll confirm |
| 2 | **Delivery locations module** | 🔶 | Reliable headcount/delivery breakdown |
| 3 | **Meal payment workflow** | 🔶 | Remarks, history timeline, reminders |
| 4 | **Weekly menu planning** | ⬜ | Operator efficiency |
| 5 | **PG rent collection** | ⬜ | Record Payment → occupancy collected |
| 6 | **Reports & analytics** | ⬜ | Stable billing data first |

---

## 3. Acomi billing standard (two types)

### 3.1 Space-level billing settings

Space settings define the **default billing for new members** and shared prepaid configuration:

| Field | Purpose |
|-------|---------|
| `mealBillingType` | Default for members without an override |
| `prepaidBalanceUnit` | `MEALS` or `CURRENCY` for all prepaid members |
| `prepaidFallbackToPayPerMeal` | When a prepaid member's balance is zero, bill pay-per-meal automatically |

### 3.2 Per-member billing override

Each member can override the space default:

| Field | Purpose |
|-------|---------|
| `members.meal_billing_type` | `PAY_PER_MEAL` \| `PREPAID_BALANCE` \| `NULL` (inherit space default) |

**Mixed mess:** Member A on prepaid balance, Member B on pay per meal — same space, different billing models.

### 3.3 Billing types

| Value | Label | Operator mental model |
|-------|-------|----------------------|
| `PAY_PER_MEAL` | Pay per meal | Customer selects meal → amount generated → payment collected |
| `PREPAID_BALANCE` | Meal balance | Sell prepaid pack → consume on confirmed meal → show remaining |

**Default for existing spaces:** `PAY_PER_MEAL`. Existing members inherit the space default until explicitly overridden.

### 3.4 Prepaid balance settings

| Field | Purpose |
|-------|---------|
| `prepaidBalanceUnit` | `MEALS` (30 meals pack) or `CURRENCY` (₹3,000 recharge) |
| `prepaidFallbackToPayPerMeal` | When balance is zero, bill pay-per-meal automatically — **not** a separate `HYBRID` type |

### 3.5 What is intentionally NOT a billing type

| Rejected enum | Replaced by |
|---------------|-------------|
| `SUBSCRIPTION` | Prepaid balance (meal-count packs) |
| `MEAL_CREDITS` | Prepaid balance (currency unit) |
| `HYBRID` | `prepaidFallbackToPayPerMeal = true` |

### 3.6 Per-type rules

**Pay per meal**
- Expected: sum of confirmed meal slot amounts (`amountGenerated`)
- Collected: owner-approved poll payments (`paidAmount`)
- Pending: expected − collected
- Calculator: `PayPerMealBillingCalculator`

**Prepaid balance**
- Dashboard: balance sold / used / left (not expected/collected/pending for pure Mess spaces)
- Debit: on poll confirm (wallet — next phase)
- Overflow: when balance empty and fallback enabled → `PayPerMealBillingCalculator` for that member
- Calculator: `PrepaidBalanceBillingCalculator` (wallet aggregation TBD; placeholder zeros today)

---

## 4. Backend module

Package: `com.acomi.acomi_backend.dashboard` + `meal` (settings API)

| Component | Role |
|-----------|------|
| `SpaceDashboardController` | `GET /spaces/{id}/dashboard-summary` |
| `PaymentController` | `GET /spaces/{id}/payments/ledger` |
| `MealBillingSettingsController` | `GET/PUT /spaces/{id}/meal-billing-settings` |
| `MealBillingSettingsService` | Persist billing type (OWNER/MANAGER) |
| `SpaceBillingService` | Ledger rows + financial rollup |
| `PayPerMealBillingCalculator` | Meal activity expected/collected |
| `PrepaidBalanceBillingCalculator` | Prepaid summary + fallback overflow |
| `OccupancyBillingCalculator` | Rent + food expected from occupancy |

### Database (V59)

Columns on `spaces`:

- `meal_billing_type` — `PAY_PER_MEAL` \| `PREPAID_BALANCE`
- `prepaid_balance_unit` — `MEALS` \| `CURRENCY` (nullable)
- `prepaid_fallback_to_pay_per_meal` — boolean, default `true`

### Access control

| Endpoint | Roles |
|----------|-------|
| Dashboard summary | `OWNER`, `MANAGER`, `STAFF` |
| Payment ledger | `OWNER`, `MANAGER` |
| Meal billing settings GET | Anyone with `requireViewMeals` |
| Meal billing settings PUT | `OWNER`, `MANAGER` (`requireManageMeals`) |

---

## 5. Financial scenarios

| Scenario | Dashboard cards | `financial.source` |
|----------|-----------------|--------------------|
| Mess + pay per meal | Expected / Collected / Pending | `MEAL_ACTIVITY` |
| Mess + prepaid balance | Balance sold / used / left | `MEAL_ACTIVITY` |
| PG / Hostel / Co-living / Rental | Expected / Collected / Pending (rent) | `OCCUPANCY` |
| PG + meal participation | Rent expected + meal row (type-dependent) | `HYBRID` |

`financial.mealBillingType` and `financial.prepaidBalance` are included in dashboard-summary and ledger responses.

### Occupancy expected formula

Matches frontend `computeOccupancyMonthlyTotal()`:

- No rent snapshot → `null`
- `foodIncludedInRent` → rent only
- `foodEnabled = false` → rent only
- Else → rent + (`foodChargeSnapshot` ?? 0)

---

## 6. Delivery locations (Phase 5–6 — priority #2)

| Item | Status |
|------|--------|
| List + create + active toggle | ✅ |
| Address / landmark field | ⬜ |
| Edit + reorder + dashboard entry | ⬜ |

---

## 7. Meal payment workflow (priority #3)

### Delivered

Pay now / pay later, proof upload, owner approve/reject (`MealHeadcountPanel`, `MemberMealActivityDaySheet`).

### Remaining

Approval/rejection remarks, payment history timeline, reminders.

---

## 8. Dashboard summary response

`GET /api/v1/spaces/{spaceId}/dashboard-summary?month=YYYY-MM`

Financial block example (prepaid):

```json
{
  "mealBillingType": "PREPAID_BALANCE",
  "expectedCharges": null,
  "collected": null,
  "pending": null,
  "currencyCode": "INR",
  "source": "MEAL_ACTIVITY",
  "prepaidBalance": {
    "balanceSold": 0,
    "balanceConsumed": 0,
    "balanceRemaining": 0,
    "unit": "MEALS",
    "currencyCode": "INR"
  }
}
```

---

## 9. Payment ledger

`GET /api/v1/spaces/{spaceId}/payments/ledger?month=YYYY-MM`

Summary includes same `mealBillingType` and `prepaidBalance` as dashboard. Member rows unchanged for v1; per-member balance fields ship with wallet.

### PG rent collection (deferred)

```
POST /api/v1/spaces/{spaceId}/members/{memberId}/rent-payments
```

Body: `amount`, `paidAt`, `mode` (`CASH` | `UPI` | `BANK_TRANSFER`), `remarks`.

---

## 10. Implementation sequence

### ✅ Step 1 — Billing type foundation (shipped)

**Backend**
- `MealBillingType`, `PrepaidBalanceUnit` enums
- V59 migration on `spaces`
- `GET/PUT /meal-billing-settings`
- `PayPerMealBillingCalculator`, `PrepaidBalanceBillingCalculator`
- `SpaceBillingService` branches; `DashboardFinancialSummaryResponse` extended

**Frontend**
- `mealBillingApi.ts`, types, normalizers
- `MealBillingSettingsSection` on `EditSpaceScreen` (Mess owners)
- `DashboardFinancialSnapshot` prepaid cards

### ⬜ Step 2 — Member balance wallet (shipped)

1. `MemberMealBalance` + `member_meal_balance_ledger` (V60)
2. Debit on poll submit (`MemberMealBalanceService.syncPollDebit`)
3. `PrepaidBalanceBillingCalculator` wired to real aggregates
4. Per-member balance on ledger rows + member profile purchase UI
5. `POST /members/{id}/meal-balance/purchases` for owner top-ups

### Steps 3–7

Delivery locations polish → meal payment hardening → weekly planning → PG rent → reports.

---

## 11. Frontend integration

| Area | Files |
|------|-------|
| Billing settings API | `src/api/mealBillingApi.ts` |
| Settings UI | `src/components/settings/MealBillingSettingsSection.tsx`, `EditSpaceScreen.tsx` |
| Dashboard cards | `src/components/dashboard/DashboardFinancialSnapshot.tsx` |
| Normalization | `src/utils/normalizeDashboardSummary.ts` |
| Ledger / hooks | `src/hooks/usePaymentsLedger.ts`, `useSpaceDashboard.ts` |

---

## 12. Verification checklist

- [x] Mess space: billing type defaults to `PAY_PER_MEAL`
- [x] Settings API round-trips `PAY_PER_MEAL` and `PREPAID_BALANCE`
- [x] Pay per meal: expected from meal activity, collected from poll payments
- [x] Prepaid mess: dashboard shows balance sold/used/left cards
- [x] Prepaid mess: wallet records sold/consumed/remaining
- [ ] PG space: expected from occupancy, collected null
- [ ] Backend restart required after deploy for V59 migration

---

## 13. Tests

| Suite | Location |
|-------|----------|
| `PrepaidBalanceBillingCalculatorTest` | Backend |
| `MealBillingSettingsServiceTest` | Backend |
| `normalizeDashboardSummary.test.ts` | Frontend |
| `OccupancyBillingCalculatorTest` | Backend |
