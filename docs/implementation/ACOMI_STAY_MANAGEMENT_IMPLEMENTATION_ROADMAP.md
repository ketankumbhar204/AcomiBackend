# ACOMI Stay Management Implementation Roadmap

**Status:** Phases 1–6 remain ACCEPTED for their recorded scopes. Phase 7 is ACCEPTED for the overdue-stay alert. D-08 and D-10 remain `APPROVED`. D-05, D-14, D-16, D-17, and D-18 remain `APPROVED` to shipping rules in section 10. On 2026-11-03 master audit: backend targeted suite 89 PASS; web `tsc -b` PASS; owner-web W-* walks PASS; dump restore PASS. Android A-00 PASS; A-01–A-20 remain BLOCKED for walk-DB proof. Phase 8 `IN PROGRESS` / blocked. Section 43 OPEN. `V156`–`V158` not applied to `acomi_db`. Evidence: `K:\ACOMI-Work\audit-2026-11-03\`. On 2026-11-03 flowchart UX audit (tests NOT RUN): owner journeys mapped vs Comprehensive Tenant Stay Management Flowchart; dashboard Reserve wired to RESERVE; short-stay nights (D-03) + Until-leave callout; Monthly renewal labeled UI via extend API (D-15); checkout copy for vacate (D-17). Pricing-method radios not added (D-02). On 2026-11-03 unified Stay Management UX (tests NOT RUN): hierarchy single Allocate/Reserve routed to OccupancyWizard; stay_type + stay_details steps aligned on mobile+web; AllocationSuccessModal on wizard confirm. On 2026-11-03 UX finalization (tests NOT RUN): operation-specific success on Android+Web; bulk hierarchy reuses wizard business rules; OccupancyTargetPickerModal removed; locale coverage for new step/success strings. On 2026-11-03 stay-type-first + DAILY_RATE Until I Leave (v1.34) and cross–space-type parity helpers/tests (v1.35): mobile 30 / web 12 / OccupancyBillingCalculatorTest PASS; browser/device space-type matrix NOT RUN. Phase 8 remains IN PROGRESS; Section 43 OPEN. Evidence: `K:\ACOMI-Work\audit-2026-11-03\impl-audit\reports\STAY_MANAGEMENT_CROSS_SPACE_TYPE_IMPLEMENTATION.md`.
**Document version:** 1.35
**Date:** 2026-11-03
**Canonical path:** `docs/implementation/ACOMI_STAY_MANAGEMENT_IMPLEMENTATION_ROADMAP.md` in `acomi-backend`
**Related existing docs (do not replace):** `docs/occupancy-phase-4.3a-backend.md`, `docs/occupancy-phase-4.3b-backend.md`, `docs/accommodation-occupancy-phase-4.3a-architecture.md`, `docs/accommodation-domain-model.md`, `docs/payments-phase-7-dashboard.md`, `docs/development-roadmap.md`

This is the only stay-duration implementation contract. Do not create a second roadmap in the web app, mobile app, or public site. Those apps implement the phases defined here.

Evidence labels used below:

| Label | Meaning |
|---|---|
| **Verified** | Read from source, schema, or a migration on 2026-10-29. Tests named in the inspection sections were inspected, not executed. Phase 1 acceptance runs are recorded only in [section 18](#18-phase-1--existing-defects) and [section 36](#36-progress-tracker). |
| **Inferred** | Conclusion from that code. |
| **Proposed** | Implementation this roadmap recommends. Not in the product yet. |
| **Decision required** | Business rule not approved. |
| **Unknown** | Not established from the repository. |

---

## Contents

1. [Document control](#1-document-control)
2. [Operating rules](#2-operating-rules)
3. [Executive summary](#3-executive-summary)
4. [Scope and non-goals](#4-scope-and-non-goals)
5. [Repositories and evidence limits](#5-repositories-and-evidence-limits)
6. [Verified current architecture](#6-verified-current-architecture)
7. [Space-type compatibility](#7-space-type-compatibility)
8. [Existing defects](#8-existing-defects)
9. [Requirement IDs](#9-requirement-ids)
10. [Decisions register](#10-decisions-register)
11. [Occupancy lifecycle](#11-occupancy-lifecycle)
12. [Pricing and billing model](#12-pricing-and-billing-model)
13. [Ledger reconciliation](#13-ledger-reconciliation)
14. [Deposits, refunds, and adjustments](#14-deposits-refunds-and-adjustments)
15. [API contract inventory](#15-api-contract-inventory)
16. [Database and migration plan](#16-database-and-migration-plan)
17. [Phase 0](#17-phase-0--business-rule-approval-and-baseline)
18. [Phase 1](#18-phase-1--existing-defects)
19. [Phase 2](#19-phase-2--financial-foundations-and-charge-identity)
20. [Phase 3](#20-phase-3--extension-and-monthly-renewal)
21. [Phase 4](#21-phase-4--short-stay-creation-and-billing)
22. [Phase 5](#22-phase-5--date-range-availability)
23. [Phase 6](#23-phase-6--web-and-mobile-ui)
24. [Phase 7](#24-phase-7--operational-alerts)
25. [Phase 8](#25-phase-8--regression-rollout-and-release)
26. [Scenario matrix](#26-scenario-matrix)
27. [Security and authorization](#27-security-and-authorization)
28. [Invariants](#28-invariants)
29. [Testing strategy](#29-testing-strategy)
30. [Web and mobile parity](#30-web-and-mobile-parity)
31. [Observability](#31-observability)
32. [Rollout and rollback](#32-rollout-and-rollback)
33. [Release checklist](#33-release-checklist)
34. [Risks and unresolved questions](#34-risks-and-unresolved-questions)
35. [Evidence appendix](#35-evidence-appendix)
36. [Progress tracker](#36-progress-tracker)
37. [Consumer and impact map](#37-consumer-and-impact-map)
38. [Charge, settlement, and reconciliation model](#38-charge-settlement-and-reconciliation-model)
39. [Date, calendar, and timezone policy](#39-date-calendar-and-timezone-policy)
40. [Transactions, idempotency, and failure recovery](#40-transactions-idempotency-and-failure-recovery)
41. [Deployment compatibility and rollback](#41-deployment-compatibility-and-rollback)
42. [Observability and support](#42-observability-and-support)
43. [Phase readiness gates](#43-phase-readiness-gates)
44. [Consistency review](#44-consistency-review)
45. [Every phase must include mobile UI and cross-platform UX](#45-mandatory-requirement-every-phase-must-include-mobile-ui-and-cross-platform-ux-alignment)

---

## 1. Document control

| Version | Date | Change |
|---|---|---|
| 1.0 | 2026-10-29 | Initial roadmap from repository inspection. No application code, schema, or configuration was changed. No tests were executed. |
| 1.1 | 2026-10-29 | Added the consumer map, charge model, date policy, failure recovery, deployment order, observability, phase gates, and consistency review. Still documentation only. No tests were executed. |
| 1.2 | 2026-10-29 | Added section 45 so every phase must assess public site, web, and Android mobile UX in that phase. Backend evidence alone cannot close an end-user feature. No application code was changed. |
| 1.3 | 2026-10-29 | Defined the overdue-but-still-active extension date rule, short-stay and extension billing-period boundaries, and the section 45 completion gate. D-03, D-19, and D-26 stay `OPEN`. No application code was changed. No tests were executed. |
| 1.4 | 2026-10-29 | Documentation audit. Moved section 36 to its numbered place. Added a two-extension period example. Phase 3 extension behavior stays blocked while D-26, D-03, and D-19 are `OPEN`. No `btree_gist` check was recorded. No application code was changed. No tests were executed. |
| 1.5 | 2026-10-29 | D-24 approved: a room stay is allowed when a child bed is `MAINTENANCE` or `BLOCKED`. D-01 through D-23 and D-25 through D-26 stay `OPEN`. Phase 1 service changes are written. Tests were not executed. |
| 1.6 | 2026-10-29 | Phase 1 ACCEPTED for the authorized scope. Targeted occupancy tests, the owner web error check, and the earlier Android error tests are recorded. Follow-ups stay open and do not reopen that scope. D-24 stays `APPROVED`. D-01 through D-23 and D-25 through D-26 stay `OPEN`. Phases 0 and 2–8 stay unstarted. Documentation only. |
| 1.7 | 2026-10-29 | D-01, D-09, and D-23 approved. D-05 deferred to Phase 4 and left `OPEN`. Phase 2 ACCEPTED for the stay-kind column and the monthly-rent skip. No cross-month charge. Targeted tests recorded. Not a full-suite pass. |
| 1.8 | 2026-10-29 | D-11, D-12, D-13, D-15, D-22, D-26, D-03, and D-19 approved. Phase 3 ACCEPTED for extend, the short-stay extra, and monthly rent from a selected month. D-05 stays `OPEN`. No cross-month charge. `V157` was not applied. Targeted tests recorded. Not a full-suite pass. |
| 1.9 | 2026-10-29 | D-02, D-04, D-06, D-07, and D-25 approved. D-05 stays `OPEN` and deferred. Phase 4 ACCEPTED for a same-month short stay with one fixed total and one deposit. Scenario B is not implemented. `V158` was not applied. Targeted tests recorded. Not a full-suite pass. |
| 1.10 | 2026-10-29 | D-20 and D-21 approved. Phase 5 ACCEPTED for a locked half-open conflict query. Unique indexes remain. No `EXCLUDE` migration. `btree_gist` was not queried. Targeted unit tests recorded. Not a database race and not a full-suite pass. |
| 1.11 | 2026-10-30 | Disposable PostgreSQL race recorded on `acomi_phase5_race_0da8530c1339`, then that database was dropped. `acomi_db` was not migrated. Section 43 stays open because earlier phases are not complete. |
| 1.12 | 2026-10-30 | Phase 6 accepted for the parity recheck. Owner web occupancy mutations invalidate payment query keys. Android occupancy changes clear the owner payment-month cache for that space. Phase 7 stays blocked because D-10 and D-08 are open. Phase 8 was not started. |
| 1.13 | 2026-10-30 | D-10 and D-08 approved. Phase 7 adds `STAY_OVERDUE_ACTIVE` on the space business date, deduped per stay and day, without vacating. Checkout-today keeps the JVM date. No migration. `acomi_db` was not touched. Phase 8 was not started. |
| 1.14 | 2026-10-30 | Release-gate evidence pass. Section 33.1 maps each Section 33 and Section 43 item to the command that ran. Phase 8 stays `NOT STARTED`. Section 43 stays open. Disposable databases only. |
| 1.15 | 2026-10-30 | Service-posted ledger and Spring extension races on a disposable database. Web typecheck exit 0. Phase 8 stays `NOT STARTED`. Section 43 stays open. |
| 1.16 | 2026-10-30 | Section 33.3. Cross-month short extension stays rejected and the original ledger is unchanged. Rental unit sync and co-living room transfer KEEP ran through Spring on a disposable database. Web `npm test` and `tsc -b` exited 0. Android `tsc --noEmit` exited 2. No browser or device walk. `acomi_db` was not migrated. Phase 8 stays `NOT STARTED`. Section 43 stays open. |
| 1.17 | 2026-10-30 | Section 33.4. Short-stay extension races and a co-living room-to-bed KEEP transfer ran through Spring. Cross-month extension stays rejected. Android `tsc --noEmit` still exits 2, now 75 errors in 44 files. No browser or device walk. `acomi_db` was not migrated. Phase 8 stays `NOT STARTED`. Section 43 stays open. |
| 1.18 | 2026-10-30 | Section 33.5. Android `tsc --noEmit` now exits 0. The release-gate service test was rerun at 20 tests and the disposable V155-to-V158 test at 4 tests. Web `npm test`, the seven stay scripts, and `tsc -b` exited 0. No browser or device walk. `acomi_db` was not migrated. There is still no Flyway undo. Phase 8 stays `NOT STARTED`. Section 43 stays open. |
| 1.19 | 2026-10-30 | Section 33.6. Deployment runbook and owner-web/Android walkthroughs, from configuration and UI inspection. No migration, no backend startup, no browser session, and no device session. Phase 8 stays `NOT STARTED`. Section 43 stays open. |
| 1.20 | 2026-10-30 | Section 33.7. Executable owner-web and Android checklists. No browser session and no device session. `adb devices` listed no serial. Phase 8 stays `NOT STARTED`. Section 43 stays open. |
| 1.21 | 2026-10-30 | Section 33.8. Disposable backup and restore rehearsal on local PostgreSQL 17.10. Flyway was not run. `acomi_db` was not connected. Phase 8 stays `NOT STARTED`. Section 43 stays open. |
| 1.22 | 2026-10-30 | Section 33.9. Isolated database `acomi_stay_walk_fd1b26b24a1d` migrated to v158 and the backend started against it. No browser session and no Android device. Phase 8 stays `NOT STARTED`. Section 43 stays open. |
| 1.23 | 2026-10-30 | Section 33.10. Owner-web browser session against that database. A cross-month short extension was accepted and then fixed. Phase 8 stays `NOT STARTED`. Section 43 stays open. |
| 1.24 | 2026-10-30 | Section 33.11. Whole-stay extension tests passed. Occupied-bed and room-allocation limits were checked in the browser and through the allocate API. Paid short-stay row, transfer, and overdue alert were exercised. No Android device. Phase 8 stays `NOT STARTED`. Section 43 stays open. |
| 1.25 | 2026-11-02 | Section 33.12. Phase 8 verification on `acomi_stay_walk_fd1b26b24a1d`. ROOM search, allocate `moveInDate`, occupied-bed availability rejection, and member login restored. Co-living allocate no longer forces vacant-beds when ROOM is allowed. Android emulator blocked by disk space. D-05/D-14/D-16/D-17/D-18 stay `OPEN`. Phase 8 `IN PROGRESS` / blocked. Section 43 stays open. |
| 1.26 | 2026-11-02 | Decision register: D-05, D-14, D-16, D-17, D-18 marked `APPROVED` to shipping rules (reject cross-month short; allow unpaid extend; no invented late fee; no auto refund; whole rows). Added `Phase8DecisionRulesTest`. Agent tooling hit `SQLITE_FULL` on `C:`; shell/emulator/re-walk not re-run. Phase 8 still blocked. Section 43 stays open. |
| 1.27 | 2026-11-03 | Phase 8 continue after disk clear. Backend targeted suite green (75 tests). Owner-web W-04/09/10/11/17/19/20/21/22 PASS (`results-15.json`). Isolated restore previously PASS. AVD moved to `K:\AndroidAVD`; A-00 device attach PASS; A-01–A-20 BLOCKED (app ANR under Metro/debug). Phase 8 not `COMPLETE`. Section 43 open. |
| 1.28 | 2026-11-03 | Master audit Phases 0–8. Backend 89 PASS; web tsc PASS; owner-web walk15 PASS; restore 85/20 PASS; release APK built; Android A-00 PASS; A-01–A-20 BLOCKED (release→prod API; debug ANR). Traceability + final report under `K:\ACOMI-Work\audit-2026-11-03\`. Phase 8 not COMPLETE. Section 43 OPEN. |
| 1.29 | 2026-11-03 | Implementation audit (code only, tests NOT RUN). Mobile allocate moveInDate parity; short-stay same-day/cross-month client validation on mobile+web matching D-05 shipping messages. Phase 8 still IN PROGRESS. Section 43 OPEN. |
| 1.30 | 2026-11-03 | Flowchart UX audit + owner UI gaps (tests NOT RUN). Dashboard Reserve→RESERVE; short-stay nights; Until-leave callout; Monthly renewal labeled extend; checkout copy. D-02 pricing methods not invented. Phase 8 IN PROGRESS. Section 43 OPEN. |
| 1.31 | 2026-11-03 | Unified Stay Management UX (tests NOT RUN). All single Allocate/Reserve/Transfer entry points → OccupancyWizard (mobile+web); hierarchy modal retained for bulk only; stay_type/stay_details/contract/review/success step alignment. Phase 8 IN PROGRESS. Section 43 OPEN. |
| 1.32 | 2026-11-03 | UX consistency finalization (tests NOT RUN). Operation-specific success (Allocate/Reserve/Transfer) on Android+Web; bulk hierarchy reuses wizard submit/stay rules; OccupancyTargetPickerModal removed; locale keys for new steps/success. Phase 8 IN PROGRESS. Section 43 OPEN. |
| 1.33 | 2026-11-03 | Stay-level stay type (D-25 superseded). Allocate/Reserve/Transfer always offer Monthly/Short/Until I Leave without Edit Space flag. Backend no longer rejects SHORT for `short_stay_enabled=false`; column kept. Transfer accepts destination stayKind. Targeted backend + mobile unit tests PASS. Full matrix / device walk NOT RUN. Phase 8 IN PROGRESS. Section 43 OPEN. |
| 1.34 | 2026-11-03 | Stay-type-first wizard (Allocate/Reserve/Transfer). Short Stay review no longer shows monthly rent. Until I Leave uses additive `pricing_basis=DAILY_RATE` with owner-entered daily rent; legacy OPEN_ENDED (`pricing_basis` null) remains monthly. Reserve captures pricing snapshots without payments. Evidence: `K:\ACOMI-Work\audit-2026-11-03\impl-audit\reports\STAY_TYPE_FIRST_DAILY_OPEN_ENDED_IMPLEMENTATION.md`. Phase 8 IN PROGRESS. Section 43 OPEN. |
| 1.35 | 2026-11-03 | Cross–space-type Stay Management parity (PG/Hostel/Co-living/Rental/Corporate Stay). Shared stay helpers; CO_LIVING roomId skips target; Rental food toggle parity on Android; matrix unit tests. No SERVICED SpaceType (maps to CORPORATE_STAY/RENTAL). Evidence: `K:\ACOMI-Work\audit-2026-11-03\impl-audit\reports\STAY_MANAGEMENT_CROSS_SPACE_TYPE_IMPLEMENTATION.md`. Phase 8 IN PROGRESS. Section 43 OPEN. |

Update this table, the decisions register status column, and [section 36](#36-progress-tracker) at the end of every implementation phase. Record the PR or commit. Do not mark a phase complete because code was written.

---

## 2. Operating rules

These rules override convenience during implementation.

1. Implement one phase at a time.
2. Before a phase, reread that phase and the decisions it depends on, then re-read the current code. Line numbers in this file can drift.
3. Do not start a phase until its entry criteria pass and the previous phase's acceptance criteria pass.
4. After a phase, update status, test evidence, decisions, and deviations in this file.
5. A task is complete only when the named tests exist and their results are recorded. Inspecting a test is not a pass.
6. Leave behavior outside the phase unchanged.
7. Do not reinterpret legacy rows. A null stay kind remains a monthly stay. Do not infer Short Stay from old dates.
8. Do not drop `uq_occupancies_*` or `uq_space_payments_generated_period` until a replacement has failed a concurrent duplicate in a test.
9. Prefer additive, nullable columns and optional request fields.
10. The server calculates duration, availability, contract amounts, extension charges, deposits, and payment state. Clients may preview. Confirmation shows the stored server result.
11. Do not show a control the server cannot enforce. Short Stay stays hidden until Phase 2 skip logic and Phase 0 pricing rules exist.
12. Every phase assesses the public site, web app, and Android app in that same phase. See [section 45](#45-mandatory-requirement-every-phase-must-include-mobile-ui-and-cross-platform-ux-alignment). Phase 6 rechecks parity. It is not the first time mobile is allowed to change. Backend evidence alone cannot mark an end-user feature complete.
13. Do not vacate or release inventory because `expectedExitDate` has passed.
14. Do not rewrite a `PAID`, `UNDER_REVIEW`, or `PROOF_UPLOADED` payment row to represent a refund, a price change, or an extension.
15. Every implementation prompt names the phase, allowed areas, non-goals, tests, acceptance criteria, and rollback. Also name the section 45 platform tasks for that phase, including a written no-change justification where a platform is unchanged.

Future prompt shape:

```text
Phase: <n>
Requirements: <IDs>
Allowed areas: <paths>
Non-goals: <list>
Tests: <classes or new test names>
Acceptance: <checklist>
Rollback: <from this phase>
Platforms: backend, public website, web app, Android, alignment, preserved UI, tests — section 45. Write the no-change justification when a platform is unchanged.
Update: docs/implementation/ACOMI_STAY_MANAGEMENT_IMPLEMENTATION_ROADMAP.md tracker
```

---

## 3. Executive summary

**Verified.** ACOMI already has one occupancy row per stay, with allocate, reserve, move-in, cancel, transfer, and vacate. Monthly rent is `rentSnapshot`, billed until `vacatedAt`. A planned end date is stored and does not end the stay or the bill. Deposits are `DEPOSIT` / `SECURITY`. Web and mobile call the same occupancy routes.

**Inferred.** Short Stay and in-place extension are not safe to add as form fields on that model. `OccupancyBillingCalculator` treats `rentSnapshot` as a monthly amount. `PaymentMonthSnapshotService.needsMissingGeneratedRent` creates a `RENT` / `MONTHLY` row whenever that snapshot is positive and the member has no monthly rent row for the month. A fixed total stored in `rentSnapshot`, plus a `RENT` / `EXTRA` row for the same total, is counted twice in `SpaceBillingService.buildRow`.

**Proposed.** Keep one occupancy. Add a nullable stay kind. Exclude `SHORT` from monthly generation and from occupancy-derived ledger expectation. Represent a fixed total and a short-stay extension as non-monthly charges with the existing `billing_period_start` / `billing_period_end` columns. Extend by updating the same occupancy. Do not allow a second overlapping stay until Phase 5.

**Not ready to code past Phase 1** until the blocking decisions in [section 10](#10-decisions-register) are approved. Phase 1 (Co-living hierarchy and the web planned-end field) does not need those pricing decisions.

---

## 4. Scope and non-goals

### In scope, after the phase gates

- Monthly Stay, Short Stay, and Until I Leave on PG, Hostel, Co-living, Rental, and Corporate Stay.
- Extension of an existing stay and monthly renewal on the same occupancy.
- Date-range availability only in Phase 5.
- Web (`AcomiWeb`) and mobile (`AcomiMobile`) using the same API results.
- Mess left on meal billing only.

### Non-goals

This roadmap does not authorize:

- A second booking or occupancy table.
- Automatic checkout or inventory release when the planned date passes.
- Automatic refunds or unused-night credits.
- Partial payments without a real `paidAmount` column and ledger changes. Do not display `PARTIALLY_PAID` as a collected balance. **Verified:** `PaymentDueStatusCalculator` sets paid to the full amount or zero, and the comment states there is no partial column. `SpacePaymentService.markReceived` sets the whole row `PAID`.
- Cancellation fees or no-show fees.
- Tenant self-service extension.
- Public-site booking. **Verified:** `AcomiPublicWebsite` has marketing occupancy screenshots only. No allocate API was found there.
- Accommodation occupancy or accommodation rent for Mess.
- Rooms or beds added to Rental or Corporate Stay.
- Dividing monthly rent by 30, or by days in the month, to invent a nightly rate.
- Rewriting paid payment rows.
- Dropping unique indexes before replacement tests pass.
- Unrelated UI redesign.
- Breaking current clients that omit new fields.
- Production database changes or deploys while this document is being written.

---

## 5. Repositories and evidence limits

| Repository | Role for this work | Inspected on 2026-10-29 |
|---|---|---|
| `acomi-backend` | Occupancy, billing, payments, migrations | Yes. This file lives here. |
| `AcomiWeb` | Owner web wizard and inspector | Request builders and `OccupancyWizardPage.tsx` |
| `AcomiMobile` | Owner mobile wizard | `occupancyRules.ts`, `occupancyApi.ts`, wizard submit |
| `AcomiPublicWebsite` | Marketing | No allocation implementation found |

**Unknown / not done:** the inspection did not execute tests. Phase 1 later recorded the targeted runs in section 36. Those runs are not a full-suite pass. PostgreSQL version and whether `btree_gist` is installed were not queried. Production JVM timezone was not read. No feature-flag framework was found in application code.

Highest Flyway version file seen: `src/main/resources/db/migration/service/V153__service_contacts.sql`. Versions are global across folders. The next stay migration must use the next unused version at implementation time. Do not reuse V20, V23, V33, V71, V137, V138, or V153.

---

## 6. Verified current architecture

### 6.1 Concepts that must stay separate

| Concept | Current field or status | What it is not |
|---|---|---|
| Occupancy lifecycle | `OccupancyStatus`: `ACTIVE`, `RESERVED`, `VACATED` | Not the bed/unit badge by itself |
| Reservation | `RESERVED`, `reservedAt`, future `moveInDate` | Not a billed stay. `isBillableInMonth` returns false for `RESERVED` |
| Planned end | `expectedExitDate`. Deprecated `expectedCheckoutDate` is still written on allocate | Not checkout. Billing ignores it |
| Actual check-in | `actualMoveInAt`. Allocate sets it to now. Reserve leaves it null | Not `moveInDate` alone |
| Actual checkout | `vacatedAt`, set only in `OccupancyService.closeOccupancy` | Not `expectedExitDate` |
| Billing period | `BillingAmountCalculator.resolvePeriod`: inclusive dates inside a calendar month, end = `vacatedAt` date or month end | Not a night count |
| Payment obligation | `space_payments` row | Not `OccupancyChargeSnapshotEntity`. Those snapshots are not read by payment generation |
| Collection | `SpacePaymentStatus` on the whole row | Not a partial amount |
| Inventory | `AccommodationStatus` on bed, room, or unit: `AVAILABLE`, `OCCUPIED`, `RESERVED`, `MAINTENANCE`, `BLOCKED` | A future reservation must not be counted as occupied. Dashboard accommodation KPIs currently count bed statuses only (`SpaceDashboardSummaryService.buildAccommodationOperations`) |

### 6.2 Lifecycle operations

Controller: `OccupancyController`, base path `/api/v1/spaces/{spaceId}`.
Service: `OccupancyService`. Each command method is `@Transactional`. `createActivationPayments` is `@Transactional` with default `REQUIRED`, so it joins the occupancy transaction. **Inferred:** a payment failure rolls the occupancy back with it. There is no `@Version` on `BaseEntity` and no `PESSIMISTIC` lock in the occupancy package.

| Operation | Method | Verified behavior |
|---|---|---|
| Allocate | `allocate` | `ACTIVE`, `moveInDate` = `LocalDate.now()`, `actualMoveInAt` = now, optional end stored in both date columns, contract snapshot, meals bridge, activation payments |
| Reserve | `reserve` | `RESERVED`, move-in cannot be before today, `expectedExitDate` from the request, no contract, no payments, inventory `RESERVED` |
| Move in | `moveIn` | `RESERVED` to `ACTIVE`. Rejects a future move-in unless `allowEarlyMoveIn`. Then snapshot and activation payments |
| Cancel | `cancelReservation` | Only `RESERVED`. Sets `VACATED` and releases the target. No fee |
| Transfer | `transfer` | Vacates the current row, creates a new `ACTIVE` row, copies or replaces the snapshot via `TransferRentPolicy`. Does not call `createActivationPayments` |
| Vacate | `vacate` | `ACTIVE` only. Sets `vacatedAt` to now and releases the target |

History events allowed by `chk_occupancy_history_event_type` in `occupancy/V23__occupancy_lifecycle_phase_4_3a.sql`: `ALLOCATED`, `TRANSFERRED`, `VACATED`, `RESERVED`, `MOVE_IN`, `RESERVATION_CANCELLED`. `EXTENDED` will fail this check until a new migration alters it.

Target rules in `OccupancyTargetService.assertTargetTypeAllowed`:

- PG, Hostel: `BED` only.
- Co-living: `BED` or `ROOM`. `resolveRoom` comment: room allocation rules are not finalized.
- Rental, Corporate Stay: `UNIT` only.
- Mess: throws `Accommodation is not applicable for Mess spaces`.

`assertTargetNotHeld` rejects the target if any `ACTIVE` or `RESERVED` row exists for that bed id, room id, or unit id. Dates are not compared. `assertMemberHasNoCurrentOccupancy` rejects a second `ACTIVE` or `RESERVED` stay for the same member in the space.

Unique indexes (status, not dates):

- `uq_occupancies_member_active`, `uq_occupancies_member_reserved`
- `uq_occupancies_bed_active`, `uq_occupancies_bed_reserved`
- `uq_occupancies_room_active` / `_reserved` where `target_type = 'ROOM'` (`occupancy/V33__fix_room_unit_unique_indexes_by_target_type.sql`)
- `uq_occupancies_unit_active` / `_reserved` where `target_type = 'UNIT'`

### 6.3 Contract and money

`OccupancyContractSnapshotService` locks `rentSnapshot`, deposit, food flags, other charge snapshots, and `pricingLockedAt` at activation. Transfer policies: `KEEP`, `APPLY_NEW`, `CUSTOM`. Catalogue fields on bed, room, and unit are `defaultRent` and `defaultDeposit` only. No nightly or weekly catalogue column was found.

`OccupancyBillingCalculator.computeContractMonthlyTotal`: rent, plus food only when food is enabled and `foodIncludedInRent` is false. `resolveOccupancyEndDate` returns `vacatedAt` only.

`BillingAmountCalculator.prorateConfigured`: `monthly × billableDays / daysInMonth`, half-up to 2 decimals. `billableDays` is `ChronoUnit.DAYS.between(start, end) + 1`.

`SpacePaymentGenerationService`:

- Accommodation types are PG, Hostel, Co-living, Rental, Corporate Stay. Mess is excluded.
- Rent row: `RENT` / `MONTHLY`, upserted by space, member, month, type, category. Lookup omits `due_date`.
- Paid, under-review, and proof-uploaded rows are not updated.
- Deposit: `DEPOSIT` / `SECURITY` when snapshot is greater than zero.
- `needsMissingGeneratedRent` (`PaymentMonthSnapshotService`) calls `syncExpectedPayments` when a billable stay has a positive `rentSnapshot` and the member has no `RENT` / `MONTHLY` row that month.

`V137__manual_payment_description.sql` unique index `uq_space_payments_generated_period` is `(space_id, member_id, month, payment_type, payment_category, due_date)` for `RENT/MONTHLY`, `MEAL/MONTHLY`, `MEAL/DAILY`, and `DEPOSIT/SECURITY` only. `RENT/EXTRA` is not in that index. **Inferred:** two generated rows with different due dates make `findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory` throw because it returns `Optional` and does not include due date. Not executed.

`SpacePaymentCategory.DAILY` is used by meal-day proofs (`MealDaySpacePaymentBridge`), not by accommodation rent.

`SpaceBillingService.buildRow` expected amount:

1. Meal contribution when separate meal billing applies.
2. Live occupancy billing when a billable stay exists. If that is used, `RENT/MONTHLY` rows are not added again.
3. Else the sum of `RENT/MONTHLY` rows.
4. Plus `sumNonRecurringObligationAmount`, which includes every `RENT` row that is not `MONTHLY`, plus deposits and other non-meal types.

Collected amount is the sum of `PAID` rows. There is no `paidAmount` column on `SpacePaymentEntity`.

### 6.4 Clients

| Client | Allocate planned-end field | Evidence |
|---|---|---|
| Mobile | `expectedCheckoutDate` (and also sends `expectedExitDate`) | `AcomiMobile/src/utils/occupancyRules.ts` `buildAllocateRequest` |
| Web | `expectedExitDate` only | `AcomiWeb/src/modules/accommodation/utils/occupancyRules.ts` `buildAllocateRequest` |
| Backend allocate DTO | `expectedCheckoutDate` and `expectedExitDate`. Unequal values are rejected. The resolved date is written to both columns | `AllocateOccupancyRequest`, `OccupancyService.allocate` |

Reserve and move-in use `expectedExitDate` on both clients and on `ReserveOccupancyRequest` / `MoveInOccupancyRequest`. `OccupancyResponse.from` fills both response fields from `expectedExitDate`, then the deprecated column.

**Accepted in Phase 1.** `expectedExitDate` is a bound allocate field. Unequal dates are rejected in the service before save. Jackson is not configured to fail on unknown properties (`application.yml` has no `fail-on-unknown`). No controller test has verified JSON binding of `expectedExitDate`. That gap is a section 18 follow-up.

### 6.5 Legacy compatibility (required)

| Record | Required behavior until a later approved policy |
|---|---|
| Null stay kind | Monthly billing as today. Do not backfill to Short Stay |
| Null planned end | Open-ended monthly stay. Blocks the target until vacate |
| Planned end set, kind null | Still monthly. The date does not stop billing |
| `RESERVED` | No rent until move-in. Target stays reserved |
| `VACATED` | Historical. `vacatedAt` is the bill end |
| Transfer | Second occupancy row. Deposit payment stays on the old occupancy id. Summary amount comes from the active snapshot (`MemberDepositSummaryCalculator`) |
| Paid rent row | Not rewritten by later sync |
| Meal `DAILY` / `MONTHLY` rows | Untouched by stay work |

---

## 7. Space-type compatibility

| Space type | Inventory target | Stay eligibility | UI rule | Negative test |
|---|---|---|---|---|
| PG | Bed | Accommodation stays | Bed wizard and bed sheet. No room allocate | `assertTargetTypeAllowed` rejects `ROOM` and `UNIT` |
| Hostel | Bed | Same as PG | Same | Same |
| Co-living | Room or bed | Stays, after hierarchy rules in [DEF-001](#def-001-co-living-room-and-bed-are-not-mutually-exclusive) | Bed and room sheets | Room stay and a bed in that room cannot both exist. Two beds with no room stay can |
| Rental | Unit | Stays | Unit wizard and unit sheet. No room or bed counts added | Allocate with null bed succeeds. Room target rejected |
| Corporate Stay | Unit | Same as Rental. Space type, not `MemberCategory` | Unit sheet. Food may stay available. `MemberCategory` is `STUDENT`, `WORKING_PROFESSIONAL`, `FAMILY`, `GUEST`, `INTERN` | Same as Rental |
| Mess | None | No occupancy and no accommodation rent | No stay step | `syncExpectedPayments` writes no `RENT` row |

Food: if `foodIncludedInRent` is true, food is not added again in `computeContractMonthlyTotal`. Rental meal applicability is already false in meal policy. Do not change that in stay phases.

Backend enforcement is `OccupancyTargetService`. Web and mobile already branch on space type in `occupancyRules.ts` / accommodation profiles. Phases 4 and 6 must keep those branches. They must not add a shared screen that asks for a bed on a rental unit.

---

## 8. Existing defects

Fix in Phase 1. Do not bundle them with billing.

### DEF-001 Co-living room and bed are not mutually exclusive

**Accepted in Phase 1** on 2026-10-29 as a service-level check, including transfer exclusion. The bullets below are the pre-fix inspection. There is still no database exclusion constraint.

**Verified** at inspection.

- A bed stay copies `room` onto the occupancy (`OccupancyService.buildOccupancy`). `assertTargetNotHeld` for a room uses `existsByRoomIdAndStatus`, so a later room booking is rejected.
- A room stay has no `bed_id`. `assertTargetNotHeld` for a bed only checks that bed id. `AccommodationStatusSyncService.deriveBedStatus` only looks at occupancies of that bed. `markOccupied` for a room does not set each bed to `OCCUPIED`.
- `V33` room uniqueness applies only when `target_type = 'ROOM'`. A room row and a bed row can both be `ACTIVE`.
- Transfer from a bed to its own room runs `assertTargetNotHeld` before the bed stay is vacated, so the room check sees the current bed stay and rejects the move.
- Transfer from a room to a bed in that room can pass the bed check because the room stay has no bed id.
- Two beds in one room are allowed. That must remain allowed when there is no room-level stay.
- `resolveRoom` does not look at child beds that are `MAINTENANCE` or `BLOCKED`.

PG and Hostel cannot allocate a room, so this defect is Co-living-only. Rental units are unaffected.

### DEF-002 Web allocate drops the planned end

**Accepted in Phase 1** on 2026-10-29: the allocate DTO binds `expectedExitDate`. The pre-fix finding was that the field was not bound. Controller JSON binding remains a section 18 follow-up.

**Verified** at inspection from the DTO and request builders in [section 6.4](#64-clients). **Not executed** at runtime during that inspection.

### DEF-003 Positive `rentSnapshot` always feeds monthly rent

**Verified** from `OccupancyBillingCalculator` and `needsMissingGeneratedRent`. This is not a bug for current monthly stays. It becomes a double-charge defect if a short-stay total is stored in `rentSnapshot`. Phase 2 must close it before any Short Stay UI. Phase 1 must not change the formula.

### DEF-004 Stay-charges comment says beds; the query does not

**Verified.** `SpacePaymentRepository.findActiveStayCharges` comment says "beds that are currently occupied". The JPQL filters `o.status = ACTIVE` and `(month = :month OR paymentType = deposit)`. It does not require a bed. Phase 6 must confirm unit stays already appear. Do not "fix" this by filtering to beds.

---

## 9. Requirement IDs

| ID | Statement |
|---|---|
| STAY-001 | Stay arrangements are Monthly Stay, Short Stay, and Until I Leave. They are not new space types. |
| STAY-002 | Null stay kind means legacy monthly behavior. |
| STAY-003 | Planned end and actual checkout are different fields. |
| STAY-004 | Until I Leave has no planned end and still has a monthly contract. |
| BILL-001 | Do not change `prorateConfigured` unless a decision says so. |
| BILL-002 | Monthly bills end at `vacatedAt`, not at `expectedExitDate`. |
| BILL-003 | A Short Stay fixed total is not stored as the monthly `rentSnapshot` that generation reads. |
| BILL-004 | A charge is included once in the ledger expected total. |
| BILL-005 | `foodIncludedInRent` does not add food a second time. |
| EXT-001 | Extension updates the same occupancy id. |
| EXT-002 | Extension and renewal do not call `createActivationPayments`. |
| EXT-003 | A normal next monthly period is not also a `RENT/EXTRA` for those dates. |
| EXT-004 | A short-stay extension charge covers only the added dates. |
| EXT-005 | Retry with the same idempotency key does not insert a second charge. |
| EXT-006 | A `VACATED` stay cannot be extended. |
| INV-001 | A Co-living room stay conflicts with every bed in that room. A bed stay conflicts with a room stay. Two beds remain legal without a room stay. |
| INV-002 | Passing the planned end does not release inventory. |
| INV-003 | Status unique indexes stay until Phase 5 proves a replacement. |
| INV-004 | Mess cannot allocate or generate accommodation rent. |
| INV-005 | Rental and Corporate Stay stay unit-only. |
| API-001 | Allocate accepts `expectedCheckoutDate` and `expectedExitDate` as one planned end. |
| API-002 | New fields are optional. Omission means today's monthly allocate. |
| API-003 | Clients cannot override the server's stored short-stay total. |
| PAY-001 | Whole-row settlement only, until a paid-amount model exists. |
| PAY-002 | Paid rows are not rewritten. |
| PAY-003 | No second deposit on extension or renewal. |
| TEST-001 | Financial and concurrency scenarios assert database rows and ledger totals, not only HTTP status. |

---

## 10. Decisions register

Status values: `OPEN`, `APPROVED`, `REJECTED`. Approved decisions are D-01 through D-26 inclusive as of roadmap 1.26 (D-05 and D-14/D-16/D-17/D-18 closed on 2026-11-02 to shipping rules). Do not implement a phase whose blocking decisions are `OPEN`.

Proposed defaults are recommendations. They are not verified product rules.

| ID | Topic | Current evidence | Proposed default | Alternatives | Risks | Blocks | Status | Approval |
|---|---|---|---|---|---|---|---|---|
| D-01 | Stay kind values | Nullable `stay_kind` | `MONTHLY`, `SHORT`, `OPEN_ENDED`. Null = monthly | One boolean "open ended" | Null cannot mean both forgotten and open-ended once Short Stay exists | Phase 2 | APPROVED | 2026-10-29: product owner chose `MONTHLY`, `SHORT`, and `OPEN_ENDED`. Null stays legacy monthly. |
| D-02 | Short-stay price basis | Only monthly snapshot | Fixed total, nightly, or weekly. Each must be configured. No monthly÷30 | Daily as a synonym of nightly | Wrong totals | Phase 4 | APPROVED | 2026-10-29. Fixed total only. The owner enters `agreedTotal`. The server stores one `RENT/EXTRA`. No nightly or weekly rate. No monthly÷30. The server does not multiply a rate. |
| D-03 | 1 Nov–8 Nov duration | Inclusive calendar days only after vacate, for monthly proration | 7 nights for Short Stay display and availability. Do not change monthly proration | 8 inclusive dates | Mixing the two rebills monthly tenants | Phase 4 and 5 | APPROVED | 2026-10-29. Seven nights for Short Stay display, duration, and extension charges. Monthly billing and proration stay inclusive. |
| D-04 | Same-day stay | API allows any end date. No validator | Reject until a day price is approved | Allow and bill 1 inclusive day | Undefined amount | Phase 4 | APPROVED | 2026-10-29. Reject a stay whose check-in and checkout are the same calendar date. That is zero nights under the half-open period. A one-night stay checks out the next date. Do not bill one inclusive day. |
| D-05 | Cross-month fixed total | Two prorated monthly rows | One charge, included once. Reporting month is the check-in month unless finance chooses a split with a parent id | Put the full total in both months | Double count | Phase 2 and 4 | APPROVED | 2026-11-02 Phase 8: product chose the shipping rule — reject cross-month short create/extend (`SHORT_STAY_CROSSES_MONTH` / `EXTENSION_CROSSES_MONTH`). Scenario B one-charge attribution is not in this release. A later epic may revisit Scenario B without reopening double-count. |
| D-06 | Short stay end date | Optional on allocate | Required for `SHORT` | Optional | Open-ended "short" stay blocks forever | Phase 4 | APPROVED | 2026-10-29. A short stay requires a planned checkout. Monthly and open-ended stays stay optional. |
| D-07 | Short-stay deposit | Deposit snapshot creates `DEPOSIT/SECURITY` | Same rule: snapshot > 0 creates one deposit | No deposit on short stays | Inconsistent with monthly | Phase 4 | APPROVED | 2026-10-29. One `DEPOSIT/SECURITY` when the deposit snapshot is greater than zero. None when it is zero or absent. Not once per month. |
| D-08 | Cancellation and no-show fees | Cancel writes no money. `EXPIRED_RESERVATION` and `VACANT_RESERVED_BED` are enum values with no occupancy publisher | No fee and no auto-expiry. Leave those two types unused | Fee or expiry job | Frees a bed or charges without a rule | Phase 7 | APPROVED | 2026-10-30. No automatic reservation expiry, no-show notification, or reservation fee. Existing cancel behavior stays. |
| D-09 | Planned end vs monthly bill | `resolveOccupancyEndDate` uses `vacatedAt` only | Keep that | Clip at planned end | Underbilling if the tenant is still there | Phase 2 | APPROVED | 2026-10-29: product owner chose to keep billing until vacate. A planned end does not shorten the monthly bill. |
| D-10 | Overdue checkout | Stay stays `ACTIVE`. `MOVE_OUT_SCHEDULED_TODAY` fires only when the planned date is today on the JVM clock | Stay occupied. Alert after the space business date. Do not vacate | Auto-vacate | Double booking | Phase 7 | APPROVED | 2026-10-30. `STAY_OVERDUE_ACTIVE` when `ACTIVE`, `vacatedAt` is null, and `expectedExitDate` is before the space business date. Default zone `Asia/Kolkata`. One open alert per stay, recipient, and business date. Checkout-today is unchanged. |
| D-11 | Extension charge | No API | Short stay: one `RENT/EXTRA` for added dates. Monthly: no extra for a normal next month | Always an extra | February billed twice | Phase 3 | APPROVED | 2026-10-29. Short Stay uses the owner-entered `additionalAmount`. Monthly and open-ended extensions update the planned end and history only. No cross-month charge while D-05 is open. |
| D-12 | Free extension | No API | Date and history only. No zero-amount row. Generation already skips amount ≤ 0 | Zero row | Noise and uniqueness problems | Phase 3 | APPROVED | 2026-10-29. Absent or zero `additionalAmount` records the date and history only. |
| D-13 | Deposit on extend/renew | Generator inserts a deposit for a new month if activation runs | Never call activation. Do not insert `DEPOSIT/SECURITY` | New deposit | Duplicate cash demand | Phase 3 | APPROVED | 2026-10-29. Extend and renew do not rerun activation or insert another security deposit. A later allocation after vacate is a new stay. |
| D-14 | Extension while unpaid | Unpaid is `PENDING` | Allow, and show outstanding | Block | Operational only | Phase 3, can defer the block | APPROVED | 2026-11-02 Phase 8: allow extend while rows are `PENDING`. Do not add an unpaid gate. Outstanding remains visible on payment surfaces. |
| D-15 | Renewal price | One snapshot. Paid rows are frozen. Pending rows are overwritten on sync | New snapshot effective from a stated month. Do not change paid rows | Overwrite snapshot immediately | Pending historical months change | Phase 3 | APPROVED | 2026-10-29. New rent applies from the selected month. `PAID` rows stay. Earlier pending or rejected rows are not repriced. |
| D-16 | Late checkout money | Vacate reprorates a pending month. A paid row stays as paid | No invented amount. Owner enters a charge or a configured rate is applied as a new row | Auto-price from monthly÷nights | Invents money | Phase 3 and 7 | APPROVED | 2026-11-02 Phase 8: no invented late fee. Owner-entered `additionalAmount` on extend only. No monthly÷nights auto-price on vacate. Configured rate path not shipped. |
| D-17 | Early checkout refund | Vacate does not refund. `SpacePaymentService` rejects category `REFUND` on create | No automatic refund. Member `depositRefunded` remains the deposit path | Auto-credit | Rewrites cash or needs a new writer | Later than Phase 4 | APPROVED | 2026-11-02 Phase 8: no automatic refund on vacate. Create with category `REFUND` stays rejected. Deposit refunds stay on `depositRefunded`. |
| D-18 | Partial payment | No `paidAmount` | Whole rows only. Do not show partial as collected | Add `paidAmount` now | Large ledger change | Not in Phases 1–5 | APPROVED | 2026-11-02 Phase 8: whole rows only. Do not store or collect partial `paidAmount`. Derived `PARTIALLY_PAID` must remain unreachable under the current calculator. |
| D-19 | Availability interval | Not implemented for inventory ranges | Half-open `[start, end)` for Short Stay nights and for inventory overlap | Inclusive both ends | 8 Nov cannot be checkout and the next check-in | Phase 5 | APPROVED | 2026-10-29. Charge periods and the inventory conflict query use `[start, end)`. Checkout is free for the next check-in. No exclusion constraint. |
| D-20 | Reservation with no end | Allowed. Blocks the target via status | Keep blocking all future dates. No silent expiry | Require an end | Breaks current reserve calls that omit it | Phase 5 | APPROVED | 2026-10-29. A null planned end on `ACTIVE` or `RESERVED` is an open-ended hold and blocks every later date. No silent expiry. |
| D-21 | Same member, current stay plus future reservation | Member unique indexes forbid it | Keep one open stay per member. Do not drop the member unique indexes | Drop member unique indexes | Two identities | Phase 5 | APPROVED | 2026-10-29. `uq_occupancies_member_active` and `uq_occupancies_member_reserved` stay. A second open stay for one member is still rejected. |
| D-22 | Who may extend | `OccupancyAccessService.assertCanManageOccupancy`: OWNER or MANAGER | Same gate | Tenant | New permission | Phase 3 | APPROVED | 2026-10-29. OWNER and MANAGER only. No tenant extension. The public site stays listing and discovery. |
| D-23 | Legacy null kind | Bills as monthly | No backfill | Guess Short Stay from dates | Rebills history | Phase 2 | APPROVED | 2026-10-29: product owner chose no backfill. Null remains monthly. Do not guess Short Stay from old dates. |
| D-24 | Room vs maintenance bed | Room resolve ignores child maintenance | Room allocate fails if any child bed is `MAINTENANCE` or `BLOCKED` | Allow the room | Sells a room that contains a blocked bed | Phase 1 | APPROVED | 2026-10-29: product owner chose “allow the room”. A child bed in `MAINTENANCE` or `BLOCKED` does not reject a room stay. |
| D-25 | Which spaces may offer Short Stay | No per-space price basis | Hidden until that space has a saved basis | On for all types | Monthly rent misused as a nightly rate | Phase 4 | **SUPERSEDED 2026-11-03** | Originally APPROVED 2026-10-29 (space flag gate). **New product decision:** Monthly / Short / Until I Leave are chosen **per Allocate, Reserve, and Transfer**. `spaces.short_stay_enabled` is retained for backward compatibility only and **no longer controls eligibility**. Server no longer rejects SHORT solely because the flag is false. Mess still excluded from accommodation stay types. Edit Space no longer shows “Turn on Short Stay”. |
| D-26 | New planned checkout when the stay is still `ACTIVE` and the current planned end is before the space business date | Extend API compares the new end with the space business date | Accept a new end on or after the space business date. Reject a new end that is still before that date. Do not vacate | Require a date strictly after the business date, or allow any date after the old end even if it is still in the past | A still-past end leaves the stay overdue. A future-only rule blocks "leaving today" | Phase 3 | APPROVED | 2026-10-29. Rejection does not change the occupancy, vacate it, or release inventory. |

Blocking Phase 2: D-01, D-09, and D-23 are `APPROVED`. D-05 is `APPROVED` as reject-cross-month (no Scenario B charge). Phase 2 does not create a cross-month charge.
Phase 3 is accepted for the extend scope. D-11, D-12, D-13, D-14, D-15, D-16, D-22, D-26, D-03, and D-19 are `APPROVED`. Cross-month short extensions stay rejected. Unpaid extend is allowed. Late fees are not invented.
Blocking Phase 4: D-02, D-04, D-05, D-06, D-07, and D-25 are `APPROVED`. Cross-month short create stays rejected. Scenario B is not shipped.
Phase 5 is accepted for the locking conflict query. D-20 and D-21 are `APPROVED`. The current unique indexes remain. MIG-5 stays blocked because `btree_gist` was not queried.
D-17 and D-18 are `APPROVED` (no auto refund; whole rows only). D-08 and D-10 remain `APPROVED`.

---

## 11. Occupancy lifecycle

Authoritative transitions stay as they are, plus extension in Phase 3.

```text
Reserve --move-in--> ACTIVE --vacate--> VACATED
Reserve --cancel--> VACATED
Allocate -----------------> ACTIVE --vacate--> VACATED
ACTIVE --transfer--> VACATED, and a new ACTIVE row
ACTIVE --extend--> same ACTIVE row, new planned end
RESERVED --extend dates--> same RESERVED row (only if D-11 allows pre-arrival; default is no, Phase 3 excludes it)
```

**Proposed default for Phase 3:** extension is allowed only for `ACTIVE`. Pre-arrival date edits of a reservation are out of scope.

Invalid:

- Extend or vacate a `VACATED` row.
- Move in a non-reserved row.
- Cancel an `ACTIVE` row through the cancel endpoint.
- Allocate a member who already has `ACTIVE` or `RESERVED` in that space.
- Allocate Mess, or a disallowed target type.

Repeated vacate: `loadActiveOccupancy` rejects a non-active row. **Verified** by the status check. A double-submit that both pass the check is a race. Phase 3's row lock pattern should be reused if vacate races become part of extension tests. Do not change vacate in Phase 1.

---

## 12. Pricing and billing model

### 12.1 What exists

| Basis | Supported now | Where |
|---|---|---|
| Monthly catalogue or typed monthly amount | Yes | `defaultRent` or `rentSnapshot` |
| Food in rent vs separate food snapshot | Yes | `computeContractMonthlyTotal` |
| Other contract lines | Stored only | `OccupancyChargeSnapshotEntity`. Codes: `PARKING`, `LAUNDRY`, `ELECTRICITY`, `WIFI`, `MAINTENANCE`, `OTHER`. Not payment rows |
| Nightly, daily accommodation, weekly, fixed stay total | No | |
| Tax | Yes, from the space, inside `computeBilling` | |

### 12.2 Proposed charge identity

Do not add a second billing system.

| Stay | Rent obligation | Excluded from |
|---|---|---|
| `MONTHLY` or null or `OPEN_ENDED` | Existing `RENT/MONTHLY` from `rentSnapshot` | `RENT/EXTRA` for the same month's normal rent |
| `SHORT` | One non-monthly rent row for the agreed total. Period columns set to the stay dates. `rentSnapshot` must not be the input to `computeBilling` or `needsMissingGeneratedRent` | `RENT/MONTHLY` |
| Short extension | One `RENT/EXTRA` whose period is only the added dates | The original row, and monthly generation |

`RENT/EXTRA` is already a legal manual category (`SpacePaymentService.validateManualPaymentType`) and is already added by `sumNonRecurringObligationAmount`. **Proposed:** the extension service creates that row. It must set `billingPeriodStart` and `billingPeriodEnd`. Manual create today does not set those columns.

Uniqueness **proposed:** partial unique index on `(occupancy_id, payment_category, billing_period_start, billing_period_end)` where `payment_type = 'RENT'` and `payment_category = 'EXTRA'`. Idempotency key maps a retry to that row. Exact column types are Phase 2 design, not a migration in this document. The period values themselves are [section 12.4](#124-billing-period-boundaries). Do not write them while D-03 and D-19 are `OPEN`.

### 12.3 Scenarios (expected rows)

Amounts assume D-03 (7 nights) and D-07 (deposit when snapshot > 0) are approved. If they are not, do not code the amounts.

**Scenario A — fixed short stay.** Check-in 1 Nov, checkout 8 Nov, total ₹5,600, deposit ₹2,000.

| Row | Type / category | Amount | Status after create | Ledger |
|---|---|---|---|---|
| Stay charge | `RENT` / not `MONTHLY` (use `EXTRA` unless D-02 picks another existing category) | 5600 | `PENDING` until collected | Included once |
| Deposit | `DEPOSIT` / `SECURITY` | 2000 | `PENDING` | Included once as a non-rent obligation, matching today's deposit-in-expected behavior |
| Monthly rent | none | | | `needsMissingGeneratedRent` must not create one |

Inventory: target occupied from allocate until vacate. Not released on 8 Nov by the date alone.

**Scenario B — 28 Jan to 3 Feb, one fixed total.** One rent-like row whose amount is the agreed total. January and February generators create no `RENT/MONTHLY`. The ledger month that shows it is D-05. The other month's expected rent for this stay is zero. Deposit follows D-07 once, not once per month.

**Scenario C — extend a paid ₹5,600 stay to 10 Nov for ₹1,600.** Same occupancy id. Original row stays `PAID` at 5600. One new row, 1600. Periods follow [section 12.4](#124-billing-period-boundaries): original 1 Nov inclusive through 8 Nov exclusive, extension 8 Nov inclusive through 10 Nov exclusive. Those nights do not overlap. Deposit row count unchanged. Ledger expected adds 1600 once and does not add another 5600. This period shape waits on D-03 and D-19.

**Scenario C2 — second extension to 12 Nov for ₹800.** Same occupancy as Scenario C. First extension row stays 1600 for 8 Nov inclusive through 10 Nov exclusive. Second row is 800 for 10 Nov inclusive through 12 Nov exclusive. Nights are 1–7 Nov, then 8–9 Nov, then 10–11 Nov. No night is in two rows. Deposit still one row of 2000. If the original 5600 is `PAID` and the deposit and both extras are `PENDING`, expected charges are 10000 and collected is 5600. This shape waits on D-03 and D-19. It is not approved while they are `OPEN`.

**Scenario D — monthly stay extended from 31 Jan to 3 Feb.** No `RENT/EXTRA` for 1–3 Feb. February `RENT/MONTHLY` is the only February rent row, produced by the existing generator from `rentSnapshot`. January's paid or pending row is not replaced if it is `PAID`.

**Scenario E — October ₹10,000 paid, November becomes ₹11,000.** October row remains 10000 and `PAID`. November pending row is 11000. Snapshot effective-from is 1 Nov (D-15). Sync must not call upsert in a way that changes October.

**Scenario F — planned 8 Nov, vacate recorded 10 Nov, November already paid.** Paid row unchanged. No automatic extra. If the owner enters ₹1,600, that is Scenario C's charge, then vacate. If they vacate with no amount, the extra nights are unbilled. Do not invent the price. Inventory releases only at vacate.

Also required later, without new formulas:

| Case | Expected |
|---|---|
| Early checkout, month still `PENDING` | Existing proration may change that pending row. Do not change a `PAID` row. No automatic refund |
| Deposit increase before the security row is paid | `MemberDepositLedger.syncContractDeposit` already updates the open row and the active snapshot. Keep that path |
| Deposit increase after `PAID` | Do not edit the paid row. A further charge needs an explicit confirm. Out of scope until D-17/D-13 say otherwise |
| Proof uploaded / under review | Upsert already skips those statuses. Extension must not use upsert on the original row |
| Rejected monthly row | Upsert does not skip `REJECTED`, so a later sync can overwrite it. Do not route extension through that upsert |
| Transfer `KEEP` | New occupancy, no new deposit payment. Paid deposit row may still point at the vacated occupancy |
| Transfer `APPLY_NEW` or `CUSTOM` | Snapshot changes. Paid deposit row amount stays. Summary can show a higher contract than cash received. Document in the UI. Do not "fix" by rewriting the paid row |

### 12.4 Billing-period boundaries

**Verified for monthly rent.** `BillingAmountCalculator.resolvePeriod` documents an inclusive occupancy window. `billableDays` is `ChronoUnit.DAYS.between(periodStart, periodEnd) + 1`. `SpacePaymentGenerationService.applyBillingSnapshot` stores that start and that end on `billing_period_start` and `billing_period_end`. Both ends are inclusive calendar dates. `prorateConfigured` uses those inclusive days and `daysInMonth`. Do not change this for `RENT/MONTHLY`. A monthly tenant vacated on 8 Nov is still 8 inclusive days when the month starts on 1 Nov. Planned checkout does not clip that period (BILL-002).

**Approved for a Short Stay charge and for a short-stay extension `RENT/EXTRA`.** D-03 and D-19 are `APPROVED`. Phase 3 persists this convention on an extension extra. Phase 4 persists it on the original short-stay extra. Monthly `RENT/MONTHLY` stays inclusive.

**Stored on `RENT/EXTRA` only**, matching D-03 (1 Nov–8 Nov is 7 nights) and D-19 (half-open `[start, end)`):

| Column | Bound | Meaning |
|---|---|---|
| `billing_period_start` | Inclusive | First night of this charge |
| `billing_period_end` | Exclusive | Checkout date. Not itself a night of this charge |

The original charge and the extension charge meet at the old checkout date and do not share a night.

| Charge | Stay dates | `billing_period_start` | `billing_period_end` | Nights |
|---|---|---|---|---|
| Original short stay | Check-in 1 Nov, checkout 8 Nov | 1 Nov | 8 Nov | 1 Nov through 7 Nov |
| Extension | New checkout 10 Nov | 8 Nov | 10 Nov | 8 Nov and 9 Nov |

Ledger: the original amount is counted once. Each extension amount is counted once. `buildRow` must not add the original again because an extra row exists, and must not add a `RENT/MONTHLY` row for any of these periods. The unique index in section 12.2 treats different period pairs as different keys. A retry of the same extension period does not insert a second copy of that amount.

**Two extensions, same stay.** Check-in 1 Nov. First planned checkout 8 Nov. First extension checkout 10 Nov. Second extension checkout 12 Nov. Every date is in November, so this example does not depend on D-05.

Half-open nights are dates `d` with `billing_period_start <= d < billing_period_end`.

| Charge | Start, inclusive | End, exclusive | Nights charged | Amount | Status in this example |
|---|---|---|---|---|---|
| Original short stay | 1 Nov | 8 Nov | 1, 2, 3, 4, 5, 6, 7 Nov (7) | 5600 | `PAID` |
| First extension | 8 Nov | 10 Nov | 8, 9 Nov (2) | 1600 | `PENDING` |
| Second extension | 10 Nov | 12 Nov | 10, 11 Nov (2) | 800 | `PENDING` |
| Deposit | Not a night period | | | 2000 | `PENDING` |

Night check: 7 + 2 + 2 = 11 nights, which is `[1 Nov, 12 Nov)`. The first extension starts on the original exclusive end. The second starts on the first extension's exclusive end. No date falls in two ranges. 12 Nov is the checkout date and is not a charged night.

Ledger for this November example, each amount once:

- Expected charges = 5600 + 1600 + 800 + 2000 = 10000.
- Collected = 5600, the only `PAID` row in the example.
- Outstanding = 10000 − 5600 = 4400, which is 1600 + 800 + 2000.
- Do not add 5600 a second time. Do not price 800 or 1600 from monthly rent divided by 30 or by nights.

An overdue active stay uses the same exclusive added interval. Planned checkout 8 Nov, space business date 10 Nov, new checkout 12 Nov: the extension period is 8 Nov inclusive through 12 Nov exclusive. The original period stays 1 Nov inclusive through 8 Nov exclusive. The stay stays `ACTIVE`. The amount is the owner-entered `additionalAmount` (D-16 does not invent it). That comparison is D-26, which remains `OPEN`. D-11 still decides whether a monthly stay gets an extra row at all. A monthly extra is not given this exclusive period.

Monthly `RENT/MONTHLY` rows stay on the inclusive calculator above. Do not store a monthly period with an exclusive end. Do not divide monthly rent by 30 or by nights to fill an extension amount.

---

## 13. Ledger reconciliation

Authoritative calculator: `SpaceBillingService.buildRow`, persisted by `PaymentMonthSnapshotService.rebuildMonthUnlocked`.

For every new charge type, a test must assert:

1. The row exists with occupancy id, type, category, amount, status, due date, month, and period.
2. `buildRow.expectedCharges` equals the sum of intended obligations for that member and month (rent or fixed total once, deposit once, each extension once).
3. `buildRow.collected` equals the sum of `PAID` rows that the ledger includes.
4. A second call to generation does not insert another row.
5. The payment list (`GET /payments`), stay-charges list, and ledger do not each add the same rupee again on top of an already combined total. UI must not add ledger expected to the row list.
6. For a short stay plus extensions, the `billing_period_*` ranges do not share a night. Scenario C is 5600 + 1600, each once, plus the deposit once. Scenario C2 is 5600 + 1600 + 800 + 2000 = 10000 expected, and 5600 collected when only the original stay charge is `PAID`. Monthly proration fixtures stay on the inclusive formula and do not use these exclusive ends.

`GET /payments/stay-charges` is read-only and does not generate payments. Its query is any active occupancy, despite the bed comment (DEF-004).

`SpacePaymentResponse.paidAmount`, `outstandingAmount`, and `settlementStatus` are derived in `PaymentDueStatusCalculator`, not stored columns. Paid is the full `amount` when status is `PAID`, otherwise zero. `PARTIALLY_PAID` cannot occur with that formula. Web `occupantPaymentSummary.ts` and mobile `occupantPaymentSummary.ts` still map `settlementStatus === 'PARTIALLY_PAID'` and a paid-plus-outstanding pair to a partial label. Do not start populating that status. Phase 6 must keep the label unreachable unless D-18 adds a real paid-amount column. Screens that show expected, collected, pending, and under-review amounts take those numbers from the month snapshot or ledger (`FinancialSummaryWidget`, `PaymentsSummaryFilters`, mobile `DashboardFinancialSnapshot`, `MemberPaymentRow`, `memberDues.ts`). They must not add row totals on top of `expectedCharges`.

Contract charge snapshots are not ledger lines. If the UI shows them as due, Phase 6 must either create payment rows or label them as contract notes. Do not leave both interpretations.

---

## 14. Deposits, refunds, and adjustments

**Verified path**

- Create: `SpacePaymentGenerationService.syncDepositPayment` during activation.
- Manual second deposit: `SpacePaymentService` throws `DEPOSIT_ALREADY_EXISTS` unless `confirmDuplicateDeposit`.
- Summary: active or reserved `depositSnapshot` is the contract amount. `PAID` security rows are cash received. Vacated snapshots are not added on top (`MemberDepositSummaryCalculator`).
- Edit: `PUT /api/v1/spaces/{spaceId}/members/{memberId}/deposit` via `MemberMasterService.updateDeposit`. `validateDeposit` rejects paid above amount and refunded above paid.
- Refund category on create: rejected. Working refund record is `member.depositRefunded` plus member history `DEPOSIT_UPDATED`.
- Transfer: payment `occupancy_id` is not moved.

**Proposed for Phases 3 and 4**

- Extension and renewal never call activation and never insert `DEPOSIT/SECURITY`.
- A new allocate after a real vacate may create a deposit under the current generator. That is a new stay, not an extension. Do not change it in Phase 3.
- Initial release does not add a linked refund payment. Deposit refunds stay on the member field.
- Rent adjustments are new rows, never edits of `PAID` rows.

---

## 15. API contract inventory

Base prefix: `/api/v1/spaces/{spaceId}`. Authorization for occupancy commands: OWNER or MANAGER (`OccupancyAccessService`). Staff may view space occupancies. Customers may not (`assertCanViewSpaceOccupancies`).

| Method | Endpoint | Current purpose | Planned change | Compatibility | Tests |
|---|---|---|---|---|---|
| POST | `/occupancies` | Allocate today | Phase 1: accept `expectedExitDate` as an alias of `expectedCheckoutDate`. Phase 4: optional `stayKind`, `pricingBasis`. Server stores the total. Client-sent "total" is the agreed amount, not a second calculator | Omit new fields: today's monthly allocate | Both date field names. Null end. End before start |
| POST | `/occupancies/reserve` | Future hold, no rent | Phase 5 only: date conflict. No contract in Phase 4 | Existing body unchanged | Reserve still creates no rent row |
| POST | `/occupancies/{id}/move-in` | Promote reservation | Phase 4: short-stay snapshot and the non-monthly charge, not `RENT/MONTHLY` | Existing monthly move-in unchanged | Reserved stay still not billed before move-in |
| POST | `/occupancies/{id}/cancel-reservation` | Vacate a reservation, no fee | None until D-08 | Unchanged | Cancel writes no rent and no fee |
| POST | `/occupancies/{id}/transfer` | New occupancy | Phase 1: same-room transfer must not collide with itself | Response remains one occupancy, the new row | KEEP does not add a deposit row |
| POST | `/occupancies/{id}/vacate` | Actual checkout | None in Phase 1. Phase 3 must not let extension and vacate both commit | Unchanged | Second vacate fails |
| GET | `/occupancies/{id}` | Detail | Later: echo new nullable fields. Keep both end-date response fields | Old clients ignore unknown JSON | Response still contains `expectedExitDate` and `expectedCheckoutDate` |
| GET | `/occupancies` | List | None required for Phase 1 | Filters unchanged | |
| GET | `/members/{memberId}/occupancies` | History | None | Unchanged | |
| POST | `/occupancies/{id}/extend` | Active stay planned end | Implemented in Phase 3. Body: new end date, optional additional amount, idempotency key, and optional monthly rent with its effective month. Response: occupancy plus charge id, or null for a free extension. This route does not set `vacatedAt` | New route. Old clients unaffected | Scenario C, B9, B16, retry, vacated |
| GET | `/payments` | List rows. `sync=true` can generate | Phase 2 generation skip for `SHORT` | List shape unchanged | Short stay does not gain `RENT/MONTHLY` when sync runs |
| GET | `/payments/stay-charges` | Read-only active stay rows | Confirm unit rows remain. Do not filter to beds | Unchanged | Rental unit charge appears |
| GET | `/payments/summary` | Month KPIs, no sync | Includes extras only if the snapshot was rebuilt from the ledger | | Ledger test |
| GET | `/payments/ledger` | Member ledger | Must match [section 13](#13-ledger-reconciliation) | | |
| GET | `/payments/owner-month` | Owner month | Same snapshot source | | |
| POST | `/payments/sync` | Generate | Must honor the short-stay skip | | Retry does not duplicate |
| POST | `/payments` | Manual charge | Not the extension path. Extension uses the occupancy transaction | | |
| POST | `/payments/{id}/proof` | Submit proof | No stay change | | |
| POST | `/payments/{id}/review` | Approve or reject | No stay change | | |
| POST | `/payments/{id}/received` | Whole row `PAID`. Already `PAID` returns the row | Do not add a partial amount parameter in this roadmap | | Second call does not change the amount |
| GET | `/payments/{id}/timeline` | Payment history | Extension must not rewrite an old timeline entry | | |
| GET | `/payments/overdue` | Overdue rows | Space-local date via `PaymentDueStatusCalculator.businessDate` | | |
| PUT | `/members/{memberId}/deposit` | Deposit figures | Do not use this to fake an extension charge | | Refund above paid is rejected |

**Proposed extend request (not implemented):**

| Field | Type | Required | Rule |
|---|---|---|---|
| `expectedExitDate` | date | yes | [Valid new planned checkout](#valid-new-planned-checkout). The request does not vacate |
| `additionalAmount` | decimal | no | Absent or null means free extension (D-12). Must be ≥ 0. Server stores it. Server does not recompute it from monthly rent |
| `idempotencyKey` | string | yes | Same key and same occupancy returns the first result |

**Proposed error behavior:** 409 for inventory or unique conflicts, 422 or the project's existing `BusinessException` status for invalid transitions, 403 for role failures. Do not return raw database constraint text. Map unique violations in the occupancy transaction to a stable error code. An invalid new checkout date is that business error. It must not be reported as a vacate, and it must not change status or inventory.

#### Valid new planned checkout

**Approved and implemented for extend.** D-26 is `APPROVED`. The comparison below is what the extend route uses. It does not vacate.

The comparison date is the space business date already used for payment overdue: `PaymentDueStatusCalculator.businessDate`, from `MealPollCloseAtCalculator.nowInSpace`, space timezone, default `Asia/Kolkata`. That clock is verified for payments. Applying it to extend is proposed. It does not change `prorateConfigured` and it does not vacate. Occupancy notifications today still use JVM `LocalDate.now()`. Phase 7 may record that alert clock separately. If the alert clock and this business date ever differ around midnight, extension validation stays on the space business date unless a later decision says otherwise. D-10 stays `OPEN` and is not implemented here.

Let `start` be `moveInDate`, `currentEnd` the stored planned end, `newEnd` the request date, and `businessDate` the space business date. The stay must already be `ACTIVE`. `VACATED` and `RESERVED` fail (EXT-006). Pre-arrival extension stays out of scope.

| Case | Condition | Valid `newEnd` | Status after success | Status after rejection |
|---|---|---|---|---|
| Open end | `currentEnd` is null | Strictly after `businessDate`, and after `start` | `ACTIVE`. `vacatedAt` still null | Unchanged. No vacate |
| Planned end still ahead | `currentEnd` is on or after `businessDate` | Strictly after `currentEnd`, and after `start` | `ACTIVE`. Inventory still held | Unchanged. No vacate |
| Planned end already passed | `ACTIVE`, `vacatedAt` null, `currentEnd` before `businessDate` | On or after `businessDate`. That is also strictly after `currentEnd` | `ACTIVE`. Same occupancy id. Inventory still held. `vacatedAt` still null | Unchanged. No vacate |

Also reject `newEnd` null, `newEnd` before or equal to `start`, and `newEnd` equal to `currentEnd`. Same-day create stays D-04, which is `OPEN` and owned by Phase 4. This table does not approve a same-day stay.

Example for the overdue case. Space business date 10 Nov. Planned checkout 8 Nov. Stay `ACTIVE`, `vacatedAt` null. New checkout 10 Nov succeeds and the guest is still in occupancy. New checkout 9 Nov fails because it is still before the business date. New checkout 12 Nov succeeds. None of these calls set `vacatedAt` or release the bed, room, or unit.

Web and Android send `newEnd` as `YYYY-MM-DD`. They display the server error on the existing inspector or sheet. They do not add a vacate control to the extend action, and they do not treat a rejected date as checkout.

Old clients: unknown response fields are ignored. Missing request fields keep current behavior (API-002).

---

## 16. Database and migration plan

Do not write these migrations until the phase starts. At that time, pick the next version after the highest `V###` file. On 2026-10-29 the highest file seen was `V153__service_contacts.sql`.

| ID | Phase | Purpose | Objects | Data impact | Rollback | Verification |
|---|---|---|---|---|---|---|
| MIG-0 | 1 | None if hierarchy and DTO alias are code-only | | None | Code revert | Room/bed tests |
| MIG-1 | 2 | Stay kind, pricing basis, agreed total | Nullable columns on `occupancies`. No backfill | Existing rows stay null and mean monthly | Ignore the columns. Do not drop them after writes without a plan | Null kind still bills |
| MIG-2 | 2 | History event `EXTENDED` | Alter `chk_occupancy_history_event_type` | No row updates | Restore the V23 list only if no `EXTENDED` row exists. If rows exist, forward-fix only | Insert of `EXTENDED` succeeds. Old events still insert |
| MIG-3 | 2 | Extra-charge uniqueness | Partial unique index on occupancy + extra period | Rejects duplicate extras | Drop only that new index | Two identical periods fail. Two different periods succeed. `RENT/MONTHLY` unaffected |
| MIG-4 | 3 | Idempotency | Unique `(occupancy_id, idempotency_key)` on a small request table or column. Design at implementation. Do not invent a table if a unique key on the payment row is enough | None for old rows | Drop the new unique object | Retry test |
| MIG-5 | 5 | Range exclusion | PostgreSQL exclusion constraint. Requires verified server version and `btree_gist` if UUID + range is required | None until indexes are validated | Drop only the new constraint | Concurrent overlap test |

**Preconditions for MIG-5:** Phase 1 hierarchy is in production or in the same release behind tests. Current unique indexes still exist. No check of `CREATE EXTENSION btree_gist` was recorded for this roadmap. Do not treat the extension as available. If a later recorded check shows it is not allowed, Phase 5 stays on transactional locking plus a conflict query, and MIG-5 stays blocked. Do not pretend the constraint exists. Do not require an exclusion constraint before that check.

**Not allowed:** guessing Short Stay in a backfill. Deleting occupancies or payments as rollback. Editing `V23` or `V137` in place. Flyway `clean`.

Deployment order: backend migration and code before clients that send `stayKind`. Old clients keep working because fields are optional. Clients that show Short Stay must not ship before the skip logic is live.

---

## 17. Phase 0 — Business-rule approval and baseline

**Status:** `NOT STARTED`
**Goal:** Approve every decision that the next coding phase needs, and record a baseline. No application code.

**Scope:** This document's register. Optional notes in the PR that approves decisions. No Java, SQL, or UI.

**Entry:** This roadmap exists.
**Exit:** Every decision marked as blocking the upcoming phase is `APPROVED` or explicitly deferred in the register. Baseline commands and results are pasted into section 36. Section 45 platform notes for the public site, web app, and Android app are in the tracker. If tests cannot be run, record why. Do not write "passed" without a command log.

**Baseline to capture (do not treat existing tests as already green):**

- `OccupancyServiceTest`
- `OccupancyBillingCalculatorTest`
- `OccupancyContractSnapshotServiceTest`
- `AccommodationStatusSyncServiceTest`

**Exclusions:** Any product code.

### Platforms

Required by [section 45](#45-mandatory-requirement-every-phase-must-include-mobile-ui-and-cross-platform-ux-alignment). This phase still writes no product code. The evidence is the investigation, not a screen change.

- **Backend / database.** Record the current occupancy and payment baseline named above. No migration.
- **Public website.** Inspect the marketing occupancy sections. Expected finding: no allocate, reserve, extend, or checkout form. Write that justification in the tracker. Do not add a booking flow.
- **Web app.** Inventory the wizard, entity inspector, and payment screens that later phases will touch. No layout change.
- **Android.** Inventory `OccupancyWizardScreen`, the bed sheet, the unit sheet, and `occupancyRules.ts`. Record that allocate already sends `expectedCheckoutDate`. No layout change.
- **Alignment.** Note that web uses a full-page wizard and inspector, and Android uses a wizard plus bottom sheets. Do not plan identical screens.
- **Preserve existing UI.** No visual change in this phase.
- **Tests and evidence.** Baseline command log, or the reason it could not be run. Platform notes in section 36. "No UI" with no investigation is not an exit.

**Checklist**

- [ ] D-01, D-09, D-23 approved before Phase 2.
- [x] D-24 approved 2026-10-29: maintenance or blocked child beds do not block a room stay.
- [ ] Owners named in the register.
- [ ] Baseline test log attached or the environment limitation written down.

---

## 18. Phase 1 — Existing defects

**Status:** `ACCEPTED` for the authorized scope on 2026-10-29. This is not section 43 `COMPLETE`: rollback was not exercised, and the follow-ups below stay open without reopening this scope.
**Goal:** Close DEF-001 and DEF-002. Nothing else.

**Depends on:** D-24 is `APPROVED`. A room stay is allowed when a child bed is `MAINTENANCE` or `BLOCKED`. The mutual exclusion of room and bed does not need pricing decisions.

### Scope

**A. INV-001**

- Room-level `ACTIVE` or `RESERVED` blocks a new bed stay in that room.
- Any bed-level `ACTIVE` or `RESERVED` in the room blocks a new room stay.
- The stay being transferred does not block its own target. Exclude the current occupancy id from the check.
- Two beds in a room with no room-level stay still succeed.
- Child bed `MAINTENANCE` or `BLOCKED` does not reject a room stay. D-24 is `APPROVED` as allow the room. The room itself still cannot be allocated when the room status is `MAINTENANCE` or `BLOCKED`.
- Direct API and UI both use the service result. UI hiding is not the control.
- Do not mark every bed `OCCUPIED` because one bed is occupied. `deriveRoomStatus` already marks a room occupied only when every bookable bed is occupied, or when a room-target stay exists. Keep that.

**B. API-001**

- `AllocateOccupancyRequest` maps `expectedExitDate` and `expectedCheckoutDate` to one value.
- If both are sent and they differ, reject the request.
- Persist `expectedExitDate`. Keep writing the deprecated column only if existing readers still need it. Response already coalesces them.
- Mobile payloads that send `expectedCheckoutDate` keep working.

### Files to inspect before editing

- `occupancy/application/service/OccupancyService.java` (`assertTargetNotHeld`, `transfer`)
- `occupancy/application/service/OccupancyTargetService.java`
- `occupancy/application/service/AccommodationStatusSyncService.java`
- `occupancy/api/dto/request/AllocateOccupancyRequest.java`
- `AcomiWeb/src/modules/accommodation/utils/occupancyRules.ts`
- `AcomiMobile/src/utils/occupancyRules.ts`
- `OccupancyServiceTest`, `AccommodationStatusSyncServiceTest`

### Exclusions

Stay kind, charges, extension endpoint, date-range booking, index drops, monthly formula, Mess, rental room or bed fields. No new wizard step.

### Platforms

- **Backend / database.** INV-001 and API-001 only. No migration.
- **Public website.** Reconfirm there is no stay form. Record the justification. No change expected.
- **Web app.** The existing builder already sends `expectedExitDate`. The DTO now binds that field, so the wizard and inspector were not changed.
- **Android.** The existing builder already sends `expectedCheckoutDate` and the same `expectedExitDate`. It was not changed. Do not add a stay-kind step.
- **Alignment.** Both clients persist one planned end. Conflict copy for a room/bed rejection comes from the API on both. Layouts stay different.
- **Preserve existing UI.** Bed sheet, unit sheet, Mess screens, and the rental wizard's lack of a bed step stay as they are.
- **Tests and evidence.** Service and API rows below, plus web and mobile request-builder tests. Backend tests alone do not close DEF-002.

### Tests

| Layer | Cases |
|---|---|
| Service | Room then bed rejected. Bed then room rejected. Two beds allowed. Transfer bed to its room succeeds and leaves one active stay. Transfer room to a bed in that room leaves one active stay. PG room target still rejected. Rental unit with null bed still succeeds |
| API | Allocate with only `expectedExitDate` stores the date. Allocate with only `expectedCheckoutDate` stores the date. Both equal succeeds. Both different fails. Null end still succeeds |
| Web / mobile | Request builders were left unchanged. Web already sends `expectedExitDate`. Android already sends both fields with the same date |

### Acceptance

- DEF-001 and DEF-002 closed with the tests above.
- No new stay kind and no migration.
- Existing monthly allocate test behavior unchanged.

### Rollback

Revert the commits. No data migration.

### Entry

Phase 0 not required for the date alias. D-24 is `APPROVED` as allow the room.

### Exit

Recorded in section 36. The authorized scope is accepted. Rollback is not verified. Maintenance or blocked child beds do not reject a room stay, per D-24.

### Acceptance record

Accepted scope only:

- Co-living room/bed mutual exclusion and transfer exclusion.
- Allocation checkout-date alias compatibility.
- Owner web and Android occupancy error handling.

This is not a full-suite pass. It is not a claim that every platform workflow was integration-tested. Phases 2–8 stay `NOT STARTED`.

Backend Surefire on 2026-10-29, exit 0:

- `OccupancyServiceTest`: 22 passed.
- `OccupancyTargetServiceTest`: 4 passed.
- `AccommodationStatusSyncServiceTest`: 3 passed.
- Total: 29 tests, 0 failures, 0 errors.

Owner web `scripts/check-occupancy-wizard-errors.mjs` passed during the final review. Android `occupancyErrors.test.ts`: 4 passed in an earlier run the same day, and was not rerun during the final review.

The public site has marketing occupancy sections and no allocate API. Web and Android request builders were not changed. Error handling uses the existing wizard and sheet patterns.

### Follow-ups

These do not reopen the accepted scope. Do not implement them by starting a later phase.

1. Add controller or API coverage that JSON `expectedExitDate` binds. The passing service tests set Java fields.
2. Strengthen the transfer tests so they verify the current occupancy id is excluded from the overlap query. The passing transfer tests do not assert that call.
3. Add or identify monthly billing regression coverage. `OccupancyBillingCalculator` was not in the 29-test run and was not changed in Phase 1.
4. Rollback was not exercised. Rollback remains: revert the commits. There is no data migration.
5. Track this failure outside stay management. `InquiryPaymentConfigServiceTest` was run as 8 tests, 1 failure, 0 errors. `adminUpdate_rejectsNegativeContactViewLimitsWithoutChangingTheRow` expects `Website contact-view limit`. The service returns `View here daily limit must be 0 or greater.` Do not change that behavior or that test as stay-management work.
6. Keep the build and test recovery separate from the Phase 1 features: the Lombok processor version in `pom.xml`; removal of the duplicate `monthlyPriceBasis` field in `AdminCreateMessRegistrationRequest`; removal from `GlobalExceptionHandler` of a handler whose exception types do not exist. `ServiceContactExceptionHandler` still handles `DuplicateServiceContactException` for `ServiceContactController`. `InquiryPaymentConfigServiceTest` was updated so void `adminUpdate` is read back with `adminGet` and the Boolean getters. That edited test method was not the failing method in item 5.

D-24 stays `APPROVED`. D-01 through D-23 and D-25 through D-26 stay `OPEN`.

---

## 19. Phase 2 — Financial foundations and charge identity

**Status:** `ACCEPTED` for the authorized scope on 2026-10-29. This is not section 43 `COMPLETE`: rollback was not exercised, `V156` was not applied to a database in this run, and D-05 stays deferred.
**Goal:** Persist stay kind and make a `SHORT` stay invisible to monthly generation and to occupancy-derived expectation, without changing monthly proration.

**Depends on:** D-01, D-09, and D-23 are `APPROVED`. D-05 is deferred to Phase 4, so this phase does not attribute a cross-month charge. Phase 1 is `ACCEPTED`. Do not start Phase 4 until its own decisions are approved.

### Schema (proposed)

Nullable on `occupancies`:

- `stay_kind` — `MONTHLY`, `SHORT`, `OPEN_ENDED`, null.
- `pricing_basis` — values approved in D-02, null for legacy.
- `agreed_total` — decimal, null. Used for `SHORT` only. Never passed to `prorateConfigured`.

No backfill.

### Service rules

- `computeBilling` and `isBillableInMonth`: if `stay_kind = SHORT`, return not billable for monthly rent. Deposit creation for short stays is Phase 4, using the existing deposit method once, not monthly rent.
- `needsMissingGeneratedRent`: ignore `SHORT` stays. A positive `rentSnapshot` on a `SHORT` row must not heal a monthly row. Prefer leaving `rentSnapshot` null on short stays so a missed skip cannot bill a monthly rate. If activation still requires a snapshot (`RENT_SNAPSHOT_REQUIRED` in `OccupancyContractSnapshotService`), change that check so `SHORT` satisfies it with `agreed_total` instead. Do not put the total in `rentSnapshot`.
- `buildRow`: do not add occupancy monthly expected for `SHORT`. Do add the non-monthly rent row and the deposit row once.
- History check constraint includes `EXTENDED` before Phase 3 writes it. Phase 2 may add the constraint without writing the event.
- Uniqueness index for extra periods (MIG-3) can land here so Phase 3 has it.

### Files

- `OccupancyEntity`
- `OccupancyBillingCalculator` and its test
- `PaymentMonthSnapshotService.needsMissingGeneratedRent`
- `SpacePaymentGenerationService`
- `SpaceBillingService`
- `OccupancyContractSnapshotService` rent-required check
- New migration after the current max version

### Exclusions

A new Short Stay wizard step, the extension endpoint, dropping indexes, changing `prorateConfigured`, Mess meal math. The existing allocate screens stay. They are not redesigned here.

### Platforms

- **Backend / database.** Nullable columns and the `SHORT` billing skip in this phase's schema and service rules.
- **Public website.** No pricing or booking control. Write the investigation. No change expected.
- **Web app.** Do not add Short Stay. Confirm the current allocate body still works and no screen offers a short-stay total as monthly rent. Record that confirmation.
- **Android.** Do not add a Short Stay step. Confirm the current wizard still allocates without `stay_kind`, and that a screen which shows rent does not invent a monthly amount for a `SHORT` row with a null `rentSnapshot`. If no screen reads stay kind yet, write that finding and the request-builder regression.
- **Alignment.** Server remains the only place that skips monthly rent. Clients must not preview a short-stay total as `rentSnapshot`.
- **Preserve existing UI.** Monthly wizard steps, sheets, and Mess screens stay visually the same.
- **Tests and evidence.** Ledger fixtures in the acceptance list, plus the web and Android regression notes. A backend ledger test alone does not close this phase.

### Acceptance

- Fixture: null kind, rent 10000, November, no vacate. Ledger expected rent 10000. One monthly row. This matches current full-month behavior.
- Fixture: `SHORT`, `agreed_total` 5600, `rentSnapshot` null, November. Zero `RENT/MONTHLY`. Sync and snapshot heal still create zero monthly rent.
- Existing `OccupancyBillingCalculatorTest` cases for monthly food, reserved, and vacate proration still describe the same formulas.
- Paid rows in a monthly fixture are not updated.
- Existing unique indexes still present.

### Rollback

Stop reading the new columns (null means monthly). Do not drop columns that already hold `SHORT` without a data plan. If no `SHORT` rows exist, ignoring the columns is enough.

### Entry

Blocking decisions approved. Phase 1 tracker updated if that phase was done.

### Exit

Recorded in section 36. The authorized scope is accepted. The cross-month charge is not part of that scope.

### Acceptance record

Accepted scope only:

- Nullable `stay_kind` (`MONTHLY`, `SHORT`, `OPEN_ENDED`), `pricing_basis`, and `agreed_total`. Null kind stays monthly. No backfill.
- `SHORT` is not monthly-billable. A positive `rentSnapshot` on a `SHORT` row does not heal or generate `RENT/MONTHLY`. `agreed_total` is not passed into monthly proration.
- A planned end does not shorten a monthly bill. Monthly proration is unchanged.
- No Short Stay screen, no allocate field for stay kind, and no cross-month charge.

Not in this acceptance: MIG-3, a `pricing_basis` value list, writing `EXTENDED`, applying `V156` to a database, and rollback. `pricing_basis` is nullable and unconstrained because D-02 is `OPEN`. The history check now allows `EXTENDED` and this phase does not write that event.

This is not a full-suite pass. The ledger fixtures below are unit tests, not a posted database ledger.

Backend Surefire on 2026-10-29, exit 0, 80 tests, 0 failures, 0 errors:

- `OccupancyBillingCalculatorTest`: 16 passed. Includes null kind at 10000 for November with a planned end still billing 10000, `MONTHLY` and `OPEN_ENDED` at 10000, and `SHORT` billing nothing.
- `BillingAmountCalculatorTest`: 10 passed. Inclusive monthly proration was not changed.
- `SpacePaymentGenerationServiceTest`: 5 passed. A null-kind activation still creates rent and one deposit. `SHORT` creates neither. A `PAID` monthly row is not rewritten.
- `PaymentMonthSnapshotEnsureTest`: 5 passed. A `SHORT` row with a positive snapshot does not heal a monthly charge.
- `OccupancyContractSnapshotServiceTest`: 14 passed. `SHORT` keeps `agreed_total` out of `rentSnapshot`.
- `OccupancyServiceTest`: 23 passed. Transfer copies stay kind and does not turn it into monthly rent.
- `OccupancyTargetServiceTest`: 4 passed. `AccommodationStatusSyncServiceTest`: 3 passed.

Owner web `scripts/check-stay-kind-absent.mjs` passed. It also read the Android allocate builder and monthly-total helper. That is a source check, not a device run. The public site has no stay-kind field and no occupancy API.

D-24 stays `APPROVED`. D-02, D-03, D-04, D-05 through D-08, D-10 through D-22, and D-25 through D-26 stay `OPEN`.

---

## 20. Phase 3 — Extension and monthly renewal

**Status:** `ACCEPTED` for the authorized scope. Not section 43 `COMPLETE`.
**Goal:** One occupancy, one transaction, no second deposit, no duplicate monthly rent.

**Depends on:** Phase 2 exit, and D-11, D-12, D-13, D-15, D-22, D-26, D-03, and D-19. Those decisions are `APPROVED`. Monthly renewal keeps the existing inclusive `RENT/MONTHLY` formula. A short-stay extension stores the added half-open period on one `RENT/EXTRA` row. Inventory date ranges stay in Phase 5.

### Behavior

1. Load `ACTIVE` occupancy for this space. `VACATED` and `RESERVED` fail (EXT-006). Pre-arrival extension is out of scope.
2. Lock the row (`SELECT FOR UPDATE` or an equivalent version column added in this phase).
3. Validate the new end date with [Valid new planned checkout](#valid-new-planned-checkout). Use the space business date. If the stay is `ACTIVE` and the current planned end is already before that date, the new end must be on or after the business date. Rejection writes nothing and does not vacate. D-26 must be `APPROVED` before this check is coded.
4. If idempotency key already stored, return the first response. Do not insert again (EXT-005).
5. Monthly or open-ended stay moving into a month the generator will bill: update `expectedExitDate` and history only (EXT-003). Price change (D-15): set snapshot effective from the approved month. Do not update `PAID` rows.
6. Short stay: insert one `RENT/EXTRA` for `additionalAmount` covering only the added interval in [section 12.4](#124-billing-period-boundaries). The original period and the extension period do not share a night. Skip the insert when the amount is absent (free extension). Do not write those period columns until D-03 and D-19 are `APPROVED`. A monthly or open-ended extension does not get this extra for a normal next month (EXT-003).
7. Write history `EXTENDED` with old date, new date, actor, and amount in remarks or structured columns already on `occupancy_history`. Inspect `OccupancyHistoryEntity` before adding columns. Prefer existing remarks plus from/to target fields over a new history table.
8. Do not call `createActivationPayments` or `syncRentPayment` (EXT-002, PAY-003).
9. Commit occupancy, history, charge, and idempotency row together.
10. Vacate racing the extension: one winner. The loser sees the new status and writes nothing.
11. The API response includes the occupancy and the charge id so clients can refetch. Cache-key polish can be rechecked in Phase 6. The web and Android extend actions themselves ship in this phase, per section 45.

Concurrent different end dates: one commit, the other conflict. Do not last-write-wins two charges.

Under-review and paid original rows stay as they are. A rejected original monthly row is not the extension vehicle.

### Files

- `OccupancyController` new route, marked proposed until this phase
- `OccupancyService`
- `OccupancyHistoryEvent` and the check constraint
- `SpacePaymentGenerationService` must not be called
- A small writer for the extra row, setting period dates and occupancy id
- Web and mobile: one extend action on the active stay in this phase. The action calls the extend endpoint, not allocate. Phase 6 may recheck layout and cache keys. It is not the first implementation of the action.

### Platforms

- **Backend / database.** Extend route, history `EXTENDED`, one extra charge or none, idempotency, and the row lock. No second occupancy.
- **Public website.** No extend control. Write the investigation. No change expected.
- **Web app.** Add Extend on the active-stay inspector. Keep the inspector pattern. Do not rebuild it as a mobile sheet.
- **Android.** Add Extend on the active bed sheet and the active unit sheet. Use the bottom sheet already used for that target. Do not copy the web inspector.
- **Alignment.** Same route, same idempotency key, same error when the stay is vacated, and the same overdue-date rule from section 15. Amounts and period dates shown after success come from the response. Presentation follows each app. Neither client vacates from the extend action.
- **Preserve existing UI.** Allocate, reserve, transfer, and vacate flows stay. Mess gains no extend action. Rental uses the unit sheet only.
- **Tests and evidence.** Service scenarios below, including B9 and B16, plus a web test and an Android test that the action calls extend, shows the business-date error without vacating, and ignores a second tap while the request is in flight. Backend acceptance alone does not complete Extend Stay.

### Exclusions

Second occupancy, date-range double booking, automatic late-checkout price (D-16), refunds, Mess.

### Acceptance

- Scenario C: same id, one 1600 row, 5600 unchanged, one deposit, second retry with the same key does not insert. Original period and extension period do not share a night. Ledger adds 1600 once.
- Scenario C2: a second extension adds 800 for 10 Nov inclusive through 12 Nov exclusive only. Expected charges 10000. Collected 5600 when only the original stay charge is `PAID`. No night is billed twice. Not persisted while D-03 and D-19 are `OPEN`.
- Scenario B9: D-26, still `OPEN`. Planned end before the space business date, stay still `ACTIVE`. Extend to the business date or later only after D-26 is `APPROVED`. Same id. `vacatedAt` still null. Inventory still held. Do not treat the proposed rule as approved.
- Scenario B16: D-26, still `OPEN`. The same overdue stay with a new end still before the business date. Business error. No history, no charge, status still `ACTIVE`. No automatic vacate.
- Two concurrent extensions: one charge.
- Extension and vacate: one outcome, no partial charge. The extend path itself never vacates.
- Free extension: no zero row.
- Scenario D: one February `RENT/MONTHLY`, no extra for those days. Monthly period stays inclusive. `prorateConfigured` unchanged.
- Scenario E: October paid 10000 remains. November pending 11000.
- `VACATED` extend returns a business error and no row.

### Rollback

Disable the route. Leave historical `EXTENDED` rows and extra charges in place. Do not delete them.

### Acceptance record

Accepted scope only:

- `POST /occupancies/{id}/extend` for an `ACTIVE` stay. `OWNER` and `MANAGER` use the existing occupancy-management gate. `VACATED` and `RESERVED` fail. The stay stays `ACTIVE` unless a separate vacate runs.
- The new end is compared with the space business date. An overdue planned end may move to that date or later. A new end that is still before the business date is rejected and writes nothing.
- Short Stay: one `RENT/EXTRA` for a positive `additionalAmount`, period `[old end, new end)`, checkout excluded. 1 November through 8 November is seven nights. Absent or zero amount writes history only.
- A short-stay added interval that is not inside one calendar month is rejected. No cross-month charge and no reporting month. Monthly and open-ended extensions do not create an extra for a later month.
- A positive `additionalAmount` on a monthly, open-ended, or null-kind stay is rejected so the amount is not dropped silently.
- Monthly rent change stores `prior_rent_snapshot` and `rent_effective_month`. Earlier pending and rejected monthly rows are left as stored. `PAID`, under-review, and proof-uploaded rows stay unchanged. The effective month may update a pending row.
- Extend does not call activation or deposit sync. A replayed idempotency key returns the first charge id and does not insert again.
- Owner web Extend is on the unit inspector, the bed inspector, the bed dialog, and the member stay panel. Android Extend is on the bed and unit actions, the quick actions, the bed menu, and the member stay section. The public site has no extend control.
- Half-open periods apply to the extension charge. Status-based overlap is unchanged. Phase 5 still owns inventory date ranges.

Not in this acceptance: applying `V156` or `V157` to a database, a database race between two extensions or between extend and vacate, a posted ledger total, rollback, D-14 unpaid blocking, D-16 automatic late pricing, and Phase 4 short-stay creation.

This is not a full-suite pass.

Backend Surefire on 2026-10-29, exit 0, finished 2026-10-29T20:37:25+05:30, 85 tests, 0 failures, 0 errors:

- `OccupancyBillingCalculatorTest` 17
- `AccommodationStatusSyncServiceTest` 3
- `OccupancyContractSnapshotServiceTest` 14
- `OccupancyExtensionServiceTest` 17
- `OccupancyServiceTest` 23
- `OccupancyTargetServiceTest` 4
- `SpacePaymentGenerationServiceTest` 7

Web `scripts/check-extend-stay.mjs` passed. Android `src/utils/__tests__/extendStay.test.ts` passed, 2 tests. The extend screens were not exercised in a browser or on a device. A real PostgreSQL race was not run. The lock is `findByIdAndSpaceIdForUpdate` on extend and vacate.

---

## 21. Phase 4 — Short-stay creation and billing

**Status:** `ACCEPTED` for the authorized scope on 2026-10-29. This is not section 43 `COMPLETE`. Rollback was not exercised. `V156`, `V157`, and `V158` were not applied. D-05 stays `OPEN`. Scenario B is not implemented.
**Goal:** Create a short stay only when the space has turned Short Stay on, and store one owner-entered total.

**Depends on:** Phase 2 and Phase 3 exits. D-02, D-03, D-04, D-06, D-07, and D-25 are `APPROVED`. D-05 stays deferred.

### Behavior

- The owner enters `agreedTotal`. The server stores that amount as one `RENT/EXTRA`. It does not multiply a nightly or weekly rate and does not divide monthly rent by 30.
- Checkout is excluded. 1 November through 8 November is seven nights. The charge month is the check-in month because the stay is inside that month. That does not close D-05.
- No `RENT/MONTHLY` on short-stay activation or on the monthly heal path. `isBillableInMonth` stays false for `SHORT`.
- One `DEPOSIT/SECURITY` when the deposit snapshot is greater than zero. None when it is zero or absent.
- Planned checkout is required for `SHORT`. Monthly and open-ended stays keep an optional end.
- The same calendar date for check-in and checkout is rejected. A one-night stay checks out the next date.
- A short stay whose nights are not all in one calendar month is rejected. No cross-month charge.
- `short_stay_enabled` defaults to false. The server rejects `SHORT` while it is off. Omitted on update leaves the flag unchanged.
- Targets stay the existing ones. Mess is still rejected. A rental allocate still has a null bed.
- Allocate retry is the existing member unique index, which stops a second `ACTIVE` stay. Recovery is `GET` member occupancies. No allocate idempotency key was added. A second activation looks up the extra by occupancy and half-open period and does not insert another copy. Deposit uses the existing month upsert.
- Reserve stores the kind and total and does not create payments. Move-in creates the charge. Transfer copies the existing short stay and does not create another extra or deposit.

### UI gate

Short Stay is shown on the owner web wizard and the Android wizard only when `shortStayEnabled` is true. The server is the gate. Monthly and open-ended steps stay available when Short Stay is hidden. Mess has no stay step. The rental wizard still has no bed step. The review shows the agreed total as a preview before it is saved.

### Platforms

- **Backend / database.** `V158__space_short_stay_enabled.sql` adds `spaces.short_stay_enabled`, default false. It was not applied. The flag is on owner space details and update. It is not on public discover cards.
- **Public website.** No short-stay booking and no stay form. No product change.
- **Web app.** The space setting is on Edit Space. The wizard offers Monthly, Short stay, and Until I leave only after the flag is on.
- **Android.** The same setting and the same wizard choice. Not a device run.
- **Preserve existing UI.** A monthly allocate still omits `stayKind`. The monthly rent displays still use `rentSnapshot`.

### Exclusions

Scenario B, overlapping future stays, public booking, partial payments, auto refund, nightly and weekly rates, and a second prior-rent snapshot.

### Rent history

Only one `prior_rent_snapshot` is kept, with `rent_effective_month`. A second monthly rent change replaces that prior. Existing earlier pending, rejected, and paid rows stay as stored. A missing earlier month created later is priced from that single prior, so a second change can affect reconstruction of missing historical months. Short stays do not use `rentSnapshot` or the prior rent. Phase 4 does not depend on changing this, and the behavior was not changed.

### Acceptance record

Accepted scope only:

- Scenario A shape: check-in 1 Nov, checkout 8 Nov, agreed total 5600, deposit 2000. One `RENT/EXTRA` of 5600, period `[1 Nov, 8 Nov)`, seven nights, month `2026-11`. One `DEPOSIT/SECURITY` of 2000. No `RENT/MONTHLY`.
- A second activation of that period does not insert another extra. A paid deposit is not saved again.
- Same-day checkout writes nothing. A cross-month short stay writes nothing. A hidden space rejects `SHORT` before save. A missing checkout rejects before save. An agreed total on a monthly stay is rejected.
- A short reserve stores the kind and total and creates no payment.
- Monthly heal of a short stay still creates no monthly rent.
- Phase 3 extension tests still pass.

Not in this acceptance: Scenario B, applying `V156`, `V157`, or `V158`, a database race, a posted ledger, rollback, D-14, D-16, and Phase 5 date-range availability.

This is not a full-suite pass.

Backend Surefire on 2026-10-29, exit 0, finished 2026-10-29T22:45:25+05:30, 92 tests, 0 failures, 0 errors:

- `OccupancyBillingCalculatorTest` 17
- `OccupancyContractSnapshotServiceTest` 14
- `OccupancyExtensionServiceTest` 17
- `OccupancyServiceTest` 30
- `ShortStayRulesTest` 4
- `SpacePaymentGenerationServiceTest` 10

`OccupancyTargetServiceTest` and `AccommodationStatusSyncServiceTest` were not rerun. Those classes were not changed in this phase.

Web `scripts/check-short-stay.mjs` and `scripts/check-stay-kind-absent.mjs` passed. Android `src/utils/__tests__/shortStay.test.ts` and `extendStay.test.ts` passed, 5 tests. The new screens were not exercised in a browser or on a device. `V158` was not applied.

---

## 22. Phase 5 — Date-range availability

**Status:** `ACCEPTED` for the authorized locking-query scope on 2026-10-29. This is not section 43 `COMPLETE`. Step 2, the index replacement, was not approved and was not done. MIG-5 was not written. `btree_gist` was not queried. No migration was applied.
**Goal:** Non-overlapping stays on the same target, without weakening current protection early.

**Depends on:** Phase 1 hierarchy. D-03, D-19, D-20, and D-21 are `APPROVED`. Database extension availability is still unknown. Do not code `EXCLUDE` against an unverified server.

### Rules

- Availability range is half-open `[moveInDate, end)` if D-19 is approved.
- End is `expectedExitDate` when set. Null end on `ACTIVE` or `RESERVED` means unbounded and blocks all later dates (D-20, legacy).
- `VACATED` blocks only through `vacatedAt`, then stops.
- Cancelled reservations are `VACATED` and stop blocking.
- Extension's new range is checked in the same locked transaction. Conflict rolls back the extension.
- Co-living: a room range conflicts with every bed range in that room and the reverse (INV-001), not only equal target ids.
- Maintenance and blocked remain whole-target flags unless a later decision gives them dates. They are not ranges in this phase.
- Same member current plus future stay stays forbidden. D-21 is approved. Do not drop `uq_occupancies_member_active` or `uq_occupancies_member_reserved`.
- Keep status unique indexes until a test shows two concurrent overlapping inserts cannot both commit **and** a product decision allows two non-overlapping `ACTIVE`/`RESERVED` rows. Those two goals conflict with the current unique indexes. Sequence:

  1. Add the exclusion or locking check while the old indexes remain. Overlap tests pass. Non-overlapping second stays still fail because of the old indexes. That is expected.
  2. Only when a later product decision approves dropping the target unique indexes, replace "one open row per target" with a range constraint in a reviewed migration, after a concurrent test passes on PostgreSQL. D-21 does not approve that step. It was not done.

### Exclusions

Removing indexes in the same change as the first conflict query. Auto-expiry. A new public availability calendar.

### Platforms

- **Backend / database.** Range check while the current unique indexes remain. Index replacement waits for a later product decision and a PostgreSQL concurrency test. D-21 does not approve dropping the member indexes.
- **Public website.** No date-range search or booking. Write the investigation. No change expected.
- **Web app.** Show the server conflict on the existing allocate, reserve, and extend actions, using the current error pattern. Do not add a second calendar product in this phase.
- **Android.** Show the same conflict on the wizard and on the bed or unit sheet, using the current snackbar or inline error. The sheet stays a sheet.
- **Alignment.** One conflict code and one business meaning. Copy can follow each app's existing error pattern. Do not invent a different overlap rule per client.
- **Preserve existing UI.** Screens that already succeed for a legal stay stay in place. Mess and rental target rules stay as they are.
- **Tests and evidence.** Concurrent database cases below, plus web and Android tests that an overlap response is shown and does not clear the form as a success. A database test alone does not complete this phase.

### Acceptance

- Two overlapping inserts: one commits.
- Legacy null end still blocks a future reserve.
- After the approved index change only: a stay that ends before the next start both commit. A stay ending on the next start follows D-19.
- Room/bed overlap still rejected.
- Mess and rental unit rules unchanged.

### Rollback

Drop only a new constraint. No constraint was added, so there is nothing to drop. Old Flyway files were not edited. `V156`, `V157`, and `V158` were not applied.

### Acceptance record

Accepted scope only. Sequence step 1:

- Allocate, reserve, transfer, move-in, and extend call `StayRangeGuard` inside the existing transaction. Candidate rows are locked with `PESSIMISTIC_WRITE` and no join fetch. The status messages `Bed is not available`, `Room is not available`, and `Unit is not available` stay.
- The range is half-open `[moveInDate, end)`. A null planned end on `ACTIVE` or `RESERVED` blocks every later date. A `VACATED` stay blocks until `vacatedAt`'s date, and that date is free for the next stay. A cancelled reservation is `VACATED` and stops blocking once that date is reached. An empty range blocks nothing.
- A room range conflicts with a bed range in that room, and the reverse. Sibling beds in the same room do not conflict with each other.
- An overlapping extension throws `STAY_OVERLAP` before the new end and before any extra charge is saved. The stay stays `ACTIVE`. An idempotent extend replay returns before the range check.
- A transfer checks `[today, unbounded)` because the new row does not copy the previous planned end. The current stay is closed only after that check.
- Member unique indexes and the bed, room, and unit unique indexes remain. A second open row on the same target still fails those indexes. That is expected. Two non-overlapping `ACTIVE` or `RESERVED` rows on one target are still rejected.
- Owner web shows the server sentence on the existing wizard catch and on the extend dialog, and the wizard does not navigate on that error. Android maps the same sentence on the wizard, the bed or unit sheet, and the mutation hooks. The public website has no date-range search or booking. No product change there.
- Maintenance and blocked stay whole-target flags. Mess is still rejected. A rental allocate still has a null bed. Monthly inclusive proration and the short-stay fixed total were not changed.

Not in the 2026-10-29 acceptance: dropping any unique index, an `EXCLUDE` constraint, MIG-5, a browser run, a device run, a full-suite pass, and Phase 6.

On 2026-10-30, `Phase5StayConcurrencyIsolationTest` created `acomi_phase5_race_0da8530c1339`, applied the existing Flyway history only to that database, ran 5 tests, and dropped it. `acomi_db` was not a connection target. The catalog check after the drop still listed `acomi_db`. Surefire finished 2026-10-30T00:21, exit 0: 5 tests, 0 failures. Two overlapping active inserts and two overlapping reservations each left one row; the loser rolled back with unique-violation `23505`. An extension that lost to a reservation kept 8 Nov and wrote no charge. A reservation that lost to an extension left no reserved row. A vacated stay ending 8 Nov allowed a stay starting 8 Nov. A stay starting 7 Nov rolled back. The eight occupancy unique indexes were still present. `btree_gist` was not queried. This does not call `OccupancyExtensionService` through Spring, and it does not make Section 43 complete.

Surefire on 2026-10-29, finished 23:38:57+05:30, exit 0: 93 tests, 0 failures, 0 errors. `OccupancyBillingCalculatorTest` 17, `OccupancyExtensionServiceTest` 18, `OccupancyServiceTest` 31, `StayRangeGuardTest` 7, `ShortStayRulesTest` 4, `StayAvailabilityRulesTest` 6, `SpacePaymentGenerationServiceTest` 10. These are Mockito and pure unit tests. They do not prove that one of two overlapping inserts commits. Android `occupancyErrors.test.ts` passed, 5 tests. Web `scripts/check-stay-overlap-error.mjs` passed. The full web test script and a browser or device pass were not run.

---

## 23. Phase 6 — Web and mobile UI

**Status:** `ACCEPTED` for the parity recheck. Not section 43 complete.
**Goal:** Both clients send the same meaning and show server amounts.

Acceptance record, 2026-10-30. No new stay rule and no schema change. The drift found was cache scope. Owner web `useOccupancyMutations` already cleared the bed stay-charge index and occupancy, bed, and building queries. It did not invalidate `payments-summary`, `payments-members`, `payments-review`, `payments-history`, `payments-list`, `payment`, or `payment-timeline`. Those keys are now in the same success handler as the occupancy keys, together with `dashboard-summary` and `pending-actions`. Android `invalidateAfterOccupancyChange(spaceId)` still clears occupancy, accommodation, dashboard, and the stay-charge index, and now publishes a payment-month change so the owner and tenant month caches for that space drop. Extend still keeps the idempotency key created when the dialog opens. A failed submit does not mint a new key. Submit stays disabled while the request is pending. Short Stay remains behind `shortStayEnabled`. The wizard review labels the agreed total as a preview. Rental and Corporate Stay allowed targets stay `UNIT`. Mess is not an allowed stay target. Dashboard occupied beds still come from `AccommodationStatus.OCCUPIED` only. Reserved beds stay in `reservedBeds`. `PARTIALLY_PAID` stays a status label. It is not added as collected money. Public website occupancy sections remain marketing screenshots. They do not call allocate, reserve, extend, or vacate.

Web script `scripts/check-phase6-stay-refresh.mjs` passed, exit 0. Android `npx jest src/utils/__tests__/occupancyStayRefresh.test.ts --no-coverage` passed, 2 tests, 0 failures, exit 0. The full web `npm test` script, a browser walk, and a device walk were not run.

**Depends on:** The phase that created the API being live. This phase is the parity recheck. Extend, Short Stay, conflict copy, and the Phase 1 end-date field must already have their own web and Android tasks in those phases. This phase does not introduce them for the first time.

### Screens

| Surface | Repo paths to inspect | Change |
|---|---|---|
| Allocate / reserve / move-in / transfer / vacate wizard | `AcomiWeb/.../OccupancyWizardPage.tsx`, `AcomiMobile/.../OccupancyWizardScreen.tsx`, both `occupancyWizardSteps.ts` | Stay step only after Phase 4. The step is built in Phase 4. This row is the recheck. Fields depend on the selected kind |
| Review | Wizard review steps | Show server duration and stored amounts after the call, or a preview labeled as preview. The confirmation state uses the response |
| Active stay | Web `EntityInspector`, mobile bed/unit sheet | Extend action already required in Phase 3. This row rechecks that it calls extend, not allocate |
| Payments | Existing payment list and month screens | Refetch after extend and vacate. Do not add ledger expected on top of the row list |
| Dashboard | Bed counts on the backend summary | Do not count `RESERVED` as occupied. Do not add fake room counts for rental. Unit occupancy for rental is a separate existing client count (`useUnitLevelOccupancyCounts`). Do not "fix" the bed KPI by inventing beds |
| Mess | Meal screens | No stay controls |

### Requirements

- Web and mobile layouts may differ. Payloads and error codes may not.
- Do not show `PARTIALLY_PAID` as money collected.
- Loading, validation, and API failure states stay on the existing toast/snackbar patterns.
- Disable submit while the request is in flight. Also handle timeout by retrying with the same idempotency key on extend.
- Cache: invalidate occupancy and payment queries after success. Name the query keys in the PR. Do not assume one invalidation refreshes both.

### Acceptance

- Web `expectedExitDate` and mobile `expectedCheckoutDate` both persist (Phase 1, rechecked here).
- Short Stay hidden when the basis is absent.
- Displayed total equals the response field.
- After extension, payment list shows the new row without a manual reload of the whole app.
- Rental wizard has no bed step. Mess has no stay step.

### Platforms

- **Backend / database.** No new stay rule. Fix only a defect found while rechecking clients. Schema changes belong to the earlier phase that owns them.
- **Public website.** Confirm again that marketing pages do not allocate. Write the justification if still unchanged.
- **Web app.** Recheck wizard, inspector, payments, and dashboard against the table above. Repair drift. Keep the inspector and full-page wizard.
- **Android.** Recheck wizard, bed sheet, unit sheet, payments, and any dashboard entry the app already has. Repair drift. Keep bottom sheets.
- **Alignment.** Payloads and error codes match. Layouts do not have to match.
- **Preserve existing UI.** Do not restyle unrelated accommodation screens. Do not add Mess stay controls.
- **Tests and evidence.** Web and Android checks in the acceptance list. A backend-only retest does not complete this phase.

---

## 24. Phase 7 — Operational alerts

**Status:** `ACCEPTED` for the overdue-stay alert. Not section 43 complete.
**Goal:** Tell the owner. Do not move inventory.

Acceptance record, 2026-10-30. D-10 is `APPROVED`. An `ACTIVE` stay with `vacatedAt` null and `expectedExitDate` before the space business date publishes `STAY_OVERDUE_ACTIVE` to each owner and manager. The business date uses `MealPollCloseAtCalculator.zoneOf`, which defaults to `Asia/Kolkata`. The dedupe key is `STAY_OVERDUE_ACTIVE:{occupancyId}:{businessDate}:{userId}`. A repeat on the same business date finds the open row, and a concurrent insert is stopped by `uq_space_notifications_open_dedupe`. The insert runs in its own transaction. A duplicate or save failure is logged and does not vacate the stay. The next business date uses a new key. A previous day's open key is resolved by that key only. Checkout-today still uses JVM `LocalDate.now()` and the title "Move-out scheduled today". The overdue title is "Stay still active" and the action is "View stay". D-08 is `APPROVED` as no automatic expiry, no-show, or fee. `EXPIRED_RESERVATION` and `VACANT_RESERVED_BED` are not published. No schema change. The public website was not changed.

Surefire `OverdueStayAlertRulesTest`, `OccupancyNotificationSyncServiceTest`, and `OverdueStayAlertPublisherTest`: 20 tests, 0 failures, exit 0, finished 2026-10-30T01:21:58+05:30. These are unit tests. They do not open a database. Web `node scripts/check-overdue-stay-alert.mjs` passed. Android Jest `ownerOnlyNotifications.test.ts` and `notificationDeepLinks.push.test.ts`: 20 passed, 0 failed. No browser or device pass.

**Verified today**

- `OccupancyNotificationSyncService.syncSpace` publishes move-in scheduled today, reservation starting today, and move-out scheduled today (`expectedExitDate` equals JVM `LocalDate.now()` for `ACTIVE`).
- `OperationalNotificationScheduler` cron default `0 15 8 * * *`, flag `acomi.notifications.ops-scheduler.enabled`.
- Payment overdue uses `MealPollCloseAtCalculator.nowInSpace`, default timezone `Asia/Kolkata` (`space.timezone`, else that default).
- Occupancy "today" does not use that zone. **Inferred:** the two clocks can disagree around midnight. Unknown in production.
- `EXPIRED_RESERVATION` and `VACANT_RESERVED_BED` are enum values. No occupancy publisher was found outside the admin test catalog.

**Proposed**

- Keep checkout-today.
- Add an alert for `ACTIVE` and `expectedExitDate < businessDate` and `vacatedAt` null. Dedupe per occupancy and day. The job must not call vacate (INV-002, D-10).
- Do not add expiry, no-show, or fee notifications unless D-08 is approved.
- Pick one business date for new occupancy alerts. Prefer the space timezone already used for payments, and record the choice in the register. Do not change monthly proration as part of that choice.
- Notification failure must not roll back a committed extension. Inspect whether `onAllocationCreated` runs inside the occupancy transaction before adding work. If it does, a notification exception can roll back the stay. New alerts should follow the existing scheduler pattern, not a new outbox, unless that inspection shows the current pattern already loses events.

### Exclusions

Auto-vacate, new fee types, WhatsApp template copy beyond existing reminder kinds.

### Platforms

- **Backend / database.** New alert only. The job must not vacate or release a bed, room, or unit.
- **Public website.** No owner alert inbox. Write the investigation. No change expected.
- **Web app.** If the owner web app already shows operational notifications, show the new alert there and do not add a vacate button. If it does not show that class of alert, write the investigation and name the surface that does.
- **Android.** If the app already shows operational notifications, show the new alert without vacating. If it has no inbox for that type, write the investigation and the channel the owner actually uses. Do not skip the Android check because the job is server-side.
- **Alignment.** The alert means "planned end has passed and the stay is still active." It does not mean the stay ended. Copy may differ. The meaning may not.
- **Preserve existing UI.** Checkout-today behavior stays. Do not add a new fee control.
- **Tests and evidence.** Scheduler test that the stay stays `ACTIVE`, plus the web and Android display evidence or the written no-surface justification. A green scheduler test alone does not complete an owner-facing alert.

---

## 25. Phase 8 — Regression, rollout, and release

**Status:** `IN PROGRESS` (blocked for release — Android A-01–A-20 on walk DB)
**Goal:** Evidence for every invariant in [section 28](#28-invariants) before release.

Started for verification on 2026-11-02 against disposable database `acomi_stay_walk_fd1b26b24a1d` only. Continued 2026-11-03 after disk recovery. Decision rows D-05, D-14, D-16, D-17, and D-18 are `APPROVED` to shipping rules in section 10. Targeted backend suite and owner-web W-04/09/10/11/17/19/20/21/22 re-ran PASS. Dump/restore rehearsal PASS. Android A-00 attached on relocated AVD; A-01–A-20 BLOCKED (debug ANR). `V156` through `V158` are not applied to `acomi_db`. Production deploy remains unauthorized. Phase 8 is not `COMPLETE`. Evidence: `C:\Users\Lenovo\AppData\Local\Temp\acomi-stay-walk\evidence\PHASE8_VERIFY_STATUS.md`, `results-15.json`.

No new product behavior except fixes for failures found here.

Release is blocked until section 33 is checked with command output or a written environment limit. UI green is not enough for ledger or concurrency rows. Backend green is not enough for an end-user feature. Section 45 evidence is part of the release gate.

### Platforms

- **Backend / database.** Re-run the ledger, concurrency, and migration checks named in section 33.
- **Public website.** Smoke the marketing occupancy pages. Confirm they still do not allocate. Record the result.
- **Web app.** Walk allocate, reserve, extend, short stay, conflict, and payment refresh on the existing web patterns.
- **Android.** Walk the same business actions on the wizard and on the bed or unit sheets. Include a device or emulator pass for the flows this release turned on, or record why that pass could not be run and what remains unverified.
- **Alignment.** Compare payloads and displayed totals. Do not file a defect only because a sheet and an inspector look different.
- **Preserve existing UI.** Monthly allocate, Mess meals, and rental unit-only flows still behave as before this program.
- **Tests and evidence.** Section 33 checklist, including the section 45 rows. The release stays blocked while any included end-user feature has only backend evidence.

---

## 26. Scenario matrix

`Phase` is the first phase that must automate the row. Expected charge and ledger columns assume the proposed defaults. If a decision is still `OPEN`, the test asserts current behavior and is labeled baseline.

| ID | Scenario | Space | Initial | Action | Occupancy | Inventory | Charges | Ledger | Notify | Layer | Phase |
|---|---|---|---|---|---|---|---|---|---|---|---|
| A1 | Monthly, no end | All accommodation | None | Allocate, null end | `ACTIVE`, null end | Held | `RENT/MONTHLY` + deposit if > 0 | Full month | Allocation created | Service | Baseline |
| A2 | Monthly with end | All accommodation | None | Allocate with end | `ACTIVE`, end stored | Held past that date | Monthly until vacate | Ignores planned end | Move-out on that date only | Service | Baseline |
| A3 | Until I Leave | All accommodation | None | `OPEN_ENDED` or null | `ACTIVE`, null end | Held | Same as A1 | Same as A1 | Same | Service | 2 |
| A4 | Reserve, no move-in | All accommodation | None | Reserve | `RESERVED` | Reserved, not occupied | No rent | No occupancy rent | Reservation created | Service | Baseline |
| A5 | Move in | All accommodation | A4 | Move in | `ACTIVE` | Occupied | Activation payments | First month | Move-in completed | Service | Baseline |
| A6 | Cancel reserve | All accommodation | A4 | Cancel | `VACATED` | Released | No fee | Unchanged | Cancelled | Service | Baseline |
| A7 | Early checkout | All accommodation | Active, pending month | Vacate | `vacatedAt` set | Released | Pending month reprorated. Paid row unchanged | Matches rows | None new | Service | Baseline |
| A8 | Late checkout, paid | All accommodation | Paid through planned end | Vacate later | `vacatedAt` actual | Released at vacate | Paid unchanged. Extra only if entered | No invented amount | | Service | 3 |
| A9 | Planned end passed | All accommodation | End yesterday | No vacate | Still `ACTIVE` | Still held | Billing continues | Full open month | Today-only alert, then Phase 7 overdue | Service | 7 |
| A10 | Second vacate | All accommodation | Vacated | Vacate | Unchanged | Unchanged | None | | | API | Baseline |
| A11 | Transfer KEEP / APPLY_NEW / CUSTOM | Supported targets | Active | Transfer | Old vacated, new active | New target held | No new deposit payment | Snapshot matches policy. Paid deposit amount unchanged | Transfer event exists | Service | Baseline |
| A12 | End before start | All accommodation | None | Allocate | Reject | Unchanged | None | | | API | 1 |
| A13 | Same-day | All accommodation | None | Start = end | Reject if D-04 stands | | | | | API | 4 |
| A14 | Legacy null kind | All accommodation | Null column | Generate | Monthly | Held | `RENT/MONTHLY` | Same as A1 | | Service | 2 |
| B1 | Fixed 5600 + deposit 2000 | Where enabled | Basis saved | Short allocate 1–8 Nov | One stay | Occupied | 5600 non-monthly, 2000 deposit, no monthly | 7600 expected before collection, each once | | DB + ledger | 4 |
| B2 | Nightly / weekly | Where enabled | Basis saved | Allocate | Server total | Occupied | One stay charge | Once | | Service | 4 |
| B3 | No basis | Any | None | Send `SHORT` | Reject | | None | | | API | 4 |
| B4 | Cross-month fixed | Where enabled | Basis | 28 Jan–3 Feb | One stay | Occupied | One rent-like total | Once, month per D-05 | | Ledger | 4 |
| B5 | Extend +2 nights, 1600, original paid | Short | B1 paid | Extend | Same id, new end | Occupied | 5600 unchanged, one 1600 | Adds 1600 once | | DB | 3 |
| B6 | Extend across February | Monthly | January active | Extend to 3 Feb | Same id | Occupied | February monthly only | No extra for those days | | DB | 3 |
| B7 | Several extensions | Short | Scenario C | Second extend to 12 Nov for 800 | Same id | Occupied | 5600, 1600, and 800. Periods [1 Nov, 8 Nov), [8 Nov, 10 Nov), [10 Nov, 12 Nov). No shared night | Expected 10000 with deposit 2000. Collected 5600 if only the original is `PAID` | | DB | 3 |
| B8 | Free extension | Short or monthly | Active | No amount | Same id | Occupied | No zero row | Unchanged | | DB | 3 |
| B9 | Extend after planned end, still active | Any | A9. Planned end before the space business date. `ACTIVE`. `vacatedAt` null | Extend to the business date or later. D-26, still `OPEN` | Same id, still `ACTIVE`, `vacatedAt` null, new end on or after the business date | Still held | Short: one `EXTRA` for the added exclusive interval only, amount from the request. Monthly: per D-11, no extra for a normal next month. Original row unchanged. D-16 does not invent the amount | Original once. Extension once if a row exists | No vacate | Service | 3 |
| B10 | Extend after vacate | Any | Vacated | Extend | Reject | Released | None | | | API | 3 |
| B11 | Price after payment | Monthly | October paid | November price | Same id | | October unchanged | November pending is the new amount | | DB | 3 |
| B12 | Generator retry | Short | B1 | Sync twice | | | Still one stay charge | | | DB | 4 |
| B13 | Snapshot heal | Short | Positive snapshot mistaken | Heal | | | Still no `RENT/MONTHLY` | | | DB | 2 |
| B14 | Positive legacy snapshot on SHORT | Short | Bad data | Heal | | | No monthly row | | | DB | 2 |
| B15 | List, ledger, dashboard, member | Short | B1 | Read all four | | | | Same obligations, not summed twice in one UI | | API | 6 |
| B16 | Overdue stay, new end still in the past | Any | A9 | Extend to a date before the space business date. D-26, still `OPEN` | Reject. Still `ACTIVE`. `vacatedAt` null | Still held | None | Unchanged | No vacate | Service | 3 |
| C1 | Unpaid rent | Monthly | Pending | Read overdue | | | Status not paid | Outstanding = amount | Overdue if due date passed | Service | Baseline |
| C2 | Paid in full | Any | Pending | `received` | | | `PAID`, amount unchanged | Collected = amount | | API | Baseline |
| C3 | Proof / review | Any | Proof | Review | | | Status changes, amount unchanged | | Payment notifications | API | Baseline |
| C4 | Rejected | Monthly | Rejected | Sync | | | Document whether upsert rewrites it. Do not use this path for extensions | | | Service | 2 |
| C5 | Extend after paid | Short | C2 | Extend | Same id | | Original `PAID` | | | DB | 3 |
| C6 | Early checkout after paid | Monthly | Paid | Vacate | Vacated | Released | Paid amount identical | | | DB | Baseline |
| C7 | Partial 4000 of 10000 | Any | Pending | No API | | | Row stays fully outstanding | Do not show partial collected | | API | Out of scope |
| C8 | 6000 for two charges | Any | Two rows | No split | | | Both rows unchanged | | | API | Out of scope |
| C9 | Deposit paid | Any | Security pending | Receive | | | One security `PAID` | Deposit summary paid matches | | Service | Baseline |
| C10 | Deposit edit before pay | Any | Open security | PUT deposit | Snapshot and open row follow `syncContractDeposit` | | Paid rows not in this case | | | Service | Baseline |
| C11 | Deposit edit after pay | Any | Security `PAID` | PUT | Paid amount unchanged | | | Summary must not pretend the paid row grew | | Service | Baseline |
| C12 | Transfer with paid deposit | Any | Paid deposit | Transfer KEEP | New occupancy | | No second security row | Paid still counts. Contract from new snapshot | | DB | Baseline |
| C13 | Extend no deposit | Any | Deposit exists | Extend | | | Security count unchanged | | | DB | 3 |
| C14 | Renew no deposit | Monthly | Deposit exists | Next month | | | One security, one new monthly rent | | | DB | 3 |
| C15 | Partial deposit refund | Any | Paid | PUT refund ≤ paid | | | No `REFUND` row required | `depositRefunded` stored. History written | | API | Baseline |
| C16 | Create `REFUND` category | Any | | POST payment | | | 400, message that refund is not creatable | | | API | Baseline |
| C17 | Due date change, two monthly rows | Monthly | Two due dates | Upsert finder | | | Must not throw or must be handled. **Inferred risk, not executed** | | | DB | 2 |
| D1 | Two allocates, one target | Any | | Concurrent | One `ACTIVE` | | One rent activation | | | DB concurrent | Baseline index |
| D2 | Two overlapping reserves | Any | | Concurrent | One `RESERVED` | | | | | DB | 5 |
| D3 | Two non-overlapping future | Any | Phase 5 index change approved | | Both, if policy allows | | | | | DB | 5 |
| D4 | Two extends, same key | Any | | Concurrent | One end date | | One extra | | | DB | 3 |
| D5 | Two extends, different dates | Any | | Concurrent | One winner | | One extra | | | DB | 3 |
| D6 | Extend vs vacate | Any | | Concurrent | One status | Matches status | No charge if vacate wins | | | DB | 3 |
| D7 | Extend vs new reservation | Any | Phase 5 | | Extension rolls back on overlap | | No extra if conflict | | | DB | 5 |
| D8 | Crash after date, before charge | Any | | Failure | Transaction rolls both back | | No charge without the date | | | DB | 3 |
| D9 | Timeout after commit | Any | | Retry same key | Same id | | Still one charge | | | API | 3 |
| D10 | Notification failure | Any | | Scheduler error | Stay unchanged | | | | Log, no vacate | Job | 7 |
| E1 | Room then bed | Co-living | Room stay | Allocate bed | Reject | | | | | Service | 1 |
| E2 | Bed then room | Co-living | Bed stay | Allocate room | Reject | | | | | Service | 1 |
| E3 | Two beds | Co-living | None | Two bed allocates | Both active | | | | | Service | 1 |
| E4 | Room, one bed occupied | Co-living | One bed | Room allocate | Reject | | | | | Service | 1 |
| E5 | Maintenance bed | Co-living | Bed maintenance | Room allocate | Succeeds. D-24 allows the room | | | | | Service | 1 |
| E6 | Blocked bed | Co-living | Bed blocked | Room allocate | Succeeds. D-24 allows the room | | | | | Service | 1 |
| E7 | Bed to containing room | Co-living | Bed stay | Transfer | One active room stay | Room held, bed not still separately active | No second deposit | | | Service | 1 |
| E8 | Room to a bed | Co-living | Room stay | Transfer | One active bed stay | | No second deposit | | | Service | 1 |
| E9 | Rental unit, no bed | Rental | | Allocate | Unit target | | | | | Service | 1 |
| E10 | Corporate unit, no bed | Corporate Stay | | Allocate | Unit target | | | | | Service | 1 |
| E11 | PG room allocate | PG | | Allocate room | Reject | | | | | Service | 1 |
| E12 | Mess allocate | Mess | | Allocate | Reject | | No `RENT` from sync | | | Service | 2 |
| F1 | Web end date | Any | | Allocate `expectedExitDate` | End stored | | | | | API | 1 |
| F2 | Mobile end date | Any | | Allocate `expectedCheckoutDate` | End stored | | | | | API | 1 |
| F3 | Old client, no stay kind | Any | | Allocate | Monthly | | Monthly row | | | API | 2 |
| F4 | Display vs server | Short | | Review | | | | UI shows response total | | UI | 4, rechecked in 6 |
| F5 | Refresh after extend | Any | | Extend | | | List contains the new row after client refetch | | | UI | 3, rechecked in 6 |
| F6 | Refresh after vacate | Any | | Vacate | | Target available in a refetch | | | | UI | 6 |
| F7 | Auth expired mid-extend | Any | | 401 | No partial charge if the server rejected before commit | | | | | API | 3 |
| F8 | Old app, new server | Any | | Omit new fields | A1 behavior | | | | | API | 8 |

---

## 27. Security and authorization

**Verified:** occupancy mutations call `assertCanManageOccupancy`. The controller uses the authenticated user id, not a body user id.

Tests to add with the phase that touches the route:

- Customer and staff receive 403 on allocate, extend, transfer, and vacate.
- A manager of space A cannot extend an occupancy in space B (load by `spaceId` and occupancy id, which `loadActiveOccupancy` already scopes).
- A client cannot set another member's payment by posting an occupancy id from another space.
- Idempotency keys are scoped to the occupancy, not global, so one tenant's key cannot replay another's charge.
- Logs for conflicts include occupancy id and space id, not phone numbers, proof URLs, or full payment remarks.
- Constraint failures return a stable code, not SQL.

Audit: occupancy history for extend, member history for deposit edits, payment timeline for new charges (`PaymentTimelineEventType.CREATED` already used by generation). Do not rewrite old timeline rows.

---

## 28. Invariants

| # | Invariant | Enforcing layer |
|---|---|---|
| 1 | One `ACTIVE` or `RESERVED` stay per member per space | DB unique indexes + `assertMemberHasNoCurrentOccupancy` |
| 2 | No overlapping stays on a target | Status unique indexes, plus the Phase 5 locked half-open query for vacated and room/bed ranges. No exclusion constraint |
| 3 | Room stay and child bed stay do not both exist | Phase 1 service check, then a parent-aware constraint if added |
| 4 | Two beds in a shared room are legal without a room stay | Absence of a room-target row. Test E3 |
| 5 | Rental and Corporate Stay are units | `assertTargetTypeAllowed` |
| 6 | Mess creates no accommodation rent | `isAccommodationApplicable` |
| 7 | Short stay is not monthly rent | Phase 2 skip in calculator, generation, and heal |
| 8 | Fixed total counted once | Ledger test B1, B4, B15 |
| 9 | Extension keeps the occupancy id | Phase 3 service |
| 10 | Extension retry does not add a charge | Unique period + idempotency key |
| 11 | Monthly renewal does not duplicate the month | Do not write `RENT/EXTRA` for generator-owned dates |
| 12 | Extension and renewal do not add a deposit | Do not call activation |
| 13 | Paid rows are not rewritten | Existing upsert skip. Extension must not call it on those rows |
| 14 | Inventory releases on vacate only | `closeOccupancy` is the only `vacatedAt` writer. Jobs must not call it |
| 15 | Legacy null kind stays monthly | Phase 2 null branch |
| 16 | Expected totals match intended rows | `SpaceBillingService` test |
| 17 | Collected totals match `PAID` rows | Same |
| 18 | Old unique indexes remain until replacement tests pass | Migration review |
| 19 | Omitted new fields do not break old clients | API tests F3, F8 |
| 20 | Web and mobile show the same stored dates and amounts | API contract plus client tests F1, F2, F4 |

---

## 29. Testing strategy

**Inspected, not executed, when section 29 was first written:** `OccupancyServiceTest`, `OccupancyBillingCalculatorTest`, `OccupancyContractSnapshotServiceTest`, `AccommodationStatusSyncServiceTest`. Those four classes were executed on 2026-10-30. The result is in section 33.1. That run does not close the API, ledger, or D4–D6 rows.

New tests follow section 26. Minimum layers:

| Layer | Must assert |
|---|---|
| Unit | Date convention, proration unchanged, food-in-rent, short-stay skip |
| Service | Lifecycle, hierarchy, extension transaction |
| Repository / DB | Unique indexes, new partial unique, history check constraint |
| Concurrent | D1, D4, D5, D6. Two threads or two transactions |
| API | Status codes, error codes, both date field names, 403 |
| Ledger | `buildRow` numbers for A, C, D, E scenarios |
| Web | Request builder and one wizard test that the end field is the one the DTO binds |
| Mobile | `buildAllocateRequest` still sends `expectedCheckoutDate` |

Do not accept a test that only checks HTTP 200.

Financial extension assertions (TEST-001), all required for Phase 3 exit:

- Same occupancy id.
- Previous and new planned end recorded.
- One `EXTENDED` history row.
- One extension charge when an amount was sent.
- No second deposit.
- Original `PAID` amount unchanged.
- Same idempotency key does not insert again.
- Ledger expected matches the rows.
- Target still occupied.
- Conflict leaves the old end and writes no charge.
- B9 and B16 follow D-26, which remains `OPEN`. Do not treat the rule as approved. Overdue `ACTIVE` stay: a new end on or after the space business date keeps `vacatedAt` null. A new end before that date writes nothing and does not vacate.
- Scenario C periods do not share a night. Ledger equals 5600 + 1600 + deposit, each once.
- Scenario C2: expected 10000 and collected 5600 under the statuses in section 12.4. Monthly proration fixtures unchanged. Do not persist those periods while D-03 and D-19 are `OPEN`.

---

## 30. Web and mobile parity

| Topic | Web | Mobile | Server |
|---|---|---|---|
| Routes | `AcomiWeb/src/modules/accommodation/api/occupancyApi.ts` | `AcomiMobile/src/api/occupancyApi.ts` | `OccupancyController` |
| Allocate end field today | `expectedExitDate` only. Builder unchanged | `expectedCheckoutDate` and the same `expectedExitDate`. Builder unchanged | Both fields bind. Unequal values are rejected |
| Reserve / move-in end | `expectedExitDate` | `expectedExitDate` | Matches |
| Submit guard | Wizard disable while pending | `submitting` disables buttons | Not idempotent except payment `received` when already `PAID` |
| Math | Must not be the source of record | Same | `OccupancyBillingCalculator` and the future short-stay total |

Phase 6 rechecks payloads, error codes, and displayed amounts. It does not replace the mobile work required inside Phases 0 through 5 and Phase 7. [Section 45](#45-mandatory-requirement-every-phase-must-include-mobile-ui-and-cross-platform-ux-alignment) is the completion rule.

The public website has no allocate, reserve, or extend flow. It stays out of feature parity for those actions. Every phase still records that investigation. A missing note is not the same as "out of scope." The admin notification catalog remains a diagnostic list, not a stay editor.

---

## 31. Observability

Log at warn, with space id and occupancy id, without phone, email, or proof URLs:

- Allocation or reserve conflict (target held).
- Extension idempotency replay (info, not error).
- Extension and vacate race loser.
- Payment sync retry and any `IncorrectResultSizeDataAccessException` from the rent finder (DEF risk C17).
- Ledger rebuild where expected rent is zero while an active non-short stay has a positive snapshot (existing heal path).
- Deposit summary contract amount and paid security total diverging after transfer (info for support, not an automatic fix).
- Invalid date range rejections.
- Migration or new constraint failures.
- Notification scheduler failures per space.
- Count of `ACTIVE` stays whose planned end is before today (Phase 7), as a metric or log summary. This is not a vacate trigger.

User-facing copy stays in the existing i18n keys. Administrators use the error code plus occupancy id.

---

## 32. Rollout and rollback

No feature-flag framework was found. Do not invent a flag service. Use these gates instead:

| Phase | Ships as | Old clients | Disable | Data after rollback |
|---|---|---|---|---|
| 1 | Backend + web field | Mobile already sends the bound field | Revert deploy | No schema change |
| 2 | Backend migration, nullable | Omit fields, stay monthly | Ignore new columns in code | Null rows unchanged. `SHORT` rows must stay compatible with the skip or they will be billed monthly if the skip is removed |
| 3 | New route plus the web inspector action and the Android bed or unit sheet action in the same phase | Builds that were not updated in that phase do not call it | Remove route from the build | Keep charges and history. Do not delete |
| 4 | API plus the web wizard step and the Android wizard step in the same phase | Hidden if they do not send `SHORT` | Server rejects `SHORT` | Same as Phase 2. Do not convert rows back by guessing |
| 5 | Migration, plus conflict copy on the existing web and Android stay actions | No new public booking calendar | Drop new constraint only | Old unique indexes must still be present |
| 6 | Parity recheck of client work already required in earlier phases | Older app keeps the monthly wizard | Hide a step that this phase finds incorrect | Server remains source of truth |
| 7 | Scheduler | None | `acomi.notifications.ops-scheduler.enabled` already exists for the current job. A new alert should use the same flag or a new property defaulting off until verified | No occupancy writes |

Never delete occupancy or payment history to roll back.

The stay deployment sequence, the missing database backup, and the safeguards against the local datasource are in section 33.6. That section was written on 2026-10-30 and was not executed.

After release, watch: duplicate `RENT/MONTHLY` for a known short-stay member, deposit counts per member, extension unique-constraint warnings, and occupied counts that include reservations.

---

## 33. Release checklist

Record command, date, and result. Unrun items stay unchecked. The 2026-10-30 evidence pass checks a box only when that row is fully met. Partial rows stay open and are mapped in [section 33.1](#331-release-gate-evidence-2026-10-30).

- [ ] Phase 0 decisions that the release uses are `APPROVED`. D-05, D-14, D-16, D-17, and D-18 remain `OPEN` and unimplemented.
- [x] Backend unit and service tests named in section 29. Surefire 2026-10-30T08:50:41+05:30, 135 tests, 0 failures, plus `OccupancyTargetServiceTest` 4 tests at 2026-10-30T08:58:09+05:30. Not an HTTP API suite and not `mvn test` of the whole reactor.
- [ ] Migration applied on a PostgreSQL database that matches deployment, including constraint checks. Disposable upgrade only. `acomi_db` was not migrated.
- [x] API tests for old allocate bodies. `StayReleaseGateServiceTest.legacyAllocateBodyPersistsMonthlyStayAndRent` posted `expectedCheckoutDate` through `OccupancyController` and read the row back. 2026-10-30T10:55:28+05:30.
- [x] Web request-builder or wizard test for the end date. `node scripts/check-occupancy-wizard-errors.mjs` and the other stay scripts, exit 0. Source checks, not a browser walk.
- [x] Mobile request-builder test for the end date. Jest stay suites rerun 2026-10-30, 7 suites, 39 tests, exit 0. `shortStay.test.ts` asserts `expectedCheckoutDate`. Not a device walk.
- [ ] Ledger scenarios A, C, D, and E. Services posted a same-month short-stay extra and deposit, a monthly extension with no extra, and a paid current month left unchanged. A cross-month short extension is still rejected, and section 33.3 shows the original checkout, payments, and history unchanged. The added extra was not posted.
- [ ] Concurrent tests D1 and, if Phase 3 is included, D4–D6. D4–D6 and the short-stay extra races in section 33.4 call `OccupancyExtensionService`. D1 remains the earlier disposable insert race and was not rerun.
- [x] Monthly stay with null kind unchanged. `OccupancyBillingCalculatorTest.nullKindFullNovember_billsTheWholeMonthUntilVacate` is in the 17 passing calculator tests.
- [x] Reserve, move-in, cancel. Those methods are in the 31 passing `OccupancyServiceTest` cases.
- [x] Transfer KEEP does not add a deposit. `transferKeepDoesNotAddADepositAndDoesNotCopyThePlannedEnd` left one security row and a null planned end on the new stay.
- [x] Vacate does not clear a paid row. `vacateLeavesAPaidShortStayChargeUnchanged` marked the extra received through `SpacePaymentService` and read `PAID` 5600 after vacate.
- [x] Planned end in the past leaves the stay `ACTIVE`. Overdue-alert tests in the same Surefire run do not vacate.
- [x] Mess sync creates no `RENT`. `messSyncCreatesNoRent` called `SpacePaymentGenerationService.syncExpectedPayments` on a Mess space.
- [x] PG room allocate rejected. Hostel same. `OccupancyTargetServiceTest.resolve_pgAndHostelRejectRoomTarget`, 2026-10-30T08:58:09+05:30.
- [x] Co-living E1–E3 and E7–E8. E1–E3 remain the earlier `OccupancyServiceTest` results and were not rerun. Section 33.4 reran bed-to-room KEEP and added room-to-bed KEEP. Each leaves one deposit and a null planned end on the new stay.
- [x] Rental and Corporate Stay unit allocate with null bed. Corporate: `corporateUnitAllocateAppearsInStayCharges`. Rental: `rentalUnitAllocateSyncsThePersistedLedger`, section 33.3. Both target types are `UNIT`.
- [x] Dashboard occupied count excludes `RESERVED` beds. `dashboardCountsOccupiedAndReservedBedsSeparately` read occupied 1 and reserved 1 from `SpaceDashboardSummaryService`.
- [x] Stay-charges list still returns unit occupancies (DEF-004). `listActiveStayCharges` returned the corporate unit occupancy's payment.
- [x] Food included in rent is not a second food line. A food charge on a bundled stay was rejected and left no payment. The accepted stay has rent and deposit only, a null food snapshot, and no `MEAL` row.
- [x] Rollback note written for the phases in the release. See section 33.1. It records disposable reverse DDL only.
- [x] This document's tracker updated.
- [ ] Section 45 evidence exists for every phase in the release: public website, web app, and Android, or a written investigation and justification for each unchanged platform. Web `npx tsc -b` exited 0 and `npm test` exited 0. Android `npx tsc --noEmit` now exits 0. No browser or device walk. Public-site `src` still has no allocate or stay-kind API.
- [ ] No end-user feature in the release is marked complete from backend evidence alone. This pass did not mark one complete. The release row stays open until the browser and device walks exist.

### 33.1 Release-gate evidence, 2026-10-30

Phase 8 stays `NOT STARTED`. Section 43 stays open. D-10 alert semantics and D-08 no-expiry, no-show, and no-fee behavior were not changed. Scenario B, an unpaid-extend gate, an automatic late price, an automatic refund, and partial-payment collection were not implemented.

| Requirement | Status | Evidence this pass | Verification still required |
|---|---|---|---|
| Section 33 decisions the release uses | Open | Implemented rules use approved decisions. D-05, D-14, D-16, D-17, and D-18 stay `OPEN` | Recorded approval or an explicit deferral before any of those behaviors is coded |
| Section 29 unit and service tests | Passed | `.\mvnw.cmd "-Dtest=OccupancyServiceTest,OccupancyExtensionServiceTest,OccupancyBillingCalculatorTest,OccupancyContractSnapshotServiceTest,AccommodationStatusSyncServiceTest,StayRangeGuardTest,StayAvailabilityRulesTest,ShortStayRulesTest,SpacePaymentGenerationServiceTest,OccupancyNotificationSyncServiceTest,OverdueStayAlertRulesTest,OverdueStayAlertPublisherTest,Phase5StayConcurrencyIsolationTest" "-Dmaven.compiler.useIncrementalCompilation=false" test`. 135 tests, 0 failures, exit 0, finished 2026-10-30T08:50:41+05:30. `OccupancyTargetServiceTest`: 4 tests, 0 failures, exit 0, finished 2026-10-30T08:58:09+05:30 | Whole-reactor `mvn test` was not run. It would also execute tests that can migrate `acomi_db` |
| Deployment migration | Blocked | Not run against `acomi_db` | Apply `V156`–`V158` only on a database that matches deployment, outside this pass |
| Disposable upgrade | Passed | `ReleaseGatePersistenceTest`, 4 tests, 0 failures, exit 0, finished 2026-10-30T08:47:50+05:30. Database `acomi_release_gate_df61ed3782a8`. Flyway reached version 158 from a v155 baseline. PostgreSQL 17.10. `@AfterAll` dropped that database and asserted `acomi_db` remains in `pg_database` | Does not satisfy the deployment checkbox |
| Old allocate API | Partial | `OccupancyServiceTest` persists `expectedCheckoutDate` alone, `expectedExitDate` alone, and rejects unequal values | Controller or MockMvc test of the old JSON body |
| Web end-date builder | Passed as source check | Seven `node scripts/check-*.mjs` stay scripts, exit 0, including `check-occupancy-wizard-errors.mjs` | Browser walk of allocate, reserve, extend, short stay, conflict, and payment refresh |
| Android end-date builder | Passed as Jest | `npx jest` on six stay suites, 32 tests, 0 failures, exit 0. `shortStay.test.ts` expects `expectedCheckoutDate` `2026-11-08` | Device or emulator walk |
| Ledger A, C, D, E | Partial, schema only | Same disposable test inserted and read back: short stay `RENT/EXTRA` 5600 `PENDING` period `[2026-11-01, 2026-11-08)` plus `DEPOSIT/SECURITY` 2000, sum 7600, no `RENT/MONTHLY`; extension `EXTRA` 1600 `PENDING` `[2026-11-08, 2026-11-10)` after the first extra was marked `PAID`; one deposit; monthly stay has January and February `RENT/MONTHLY` and zero `EXTRA`; October 10000 `PAID` and November 11000 `PENDING` with `rent_effective_month` 2026-11-01. Duplicate extra period raises SQLState 23505 | Rows posted by `OccupancyService`, `OccupancyExtensionService`, and `SpaceBillingService.buildRow`. Scenario C2 was not inserted. Scenario B stays unimplemented |
| D1 concurrency | Passed on disposable DB | `Phase5StayConcurrencyIsolationTest`, 5 tests, 0 failures, database `acomi_phase5_race_c4e3d6ae1358`, inside the 135-test run. The test drops that name | Not a Spring service race |
| D4, D5, D6 | Not run | Unique indexes `uq_occupancy_history_extension_key` and `uq_space_payments_rent_extra_period` exist after `V157` on the disposable database | Two Spring transactions: same idempotency key, two different dates, and extend versus vacate |
| Notification dedupe | Passed on disposable DB | Two concurrent inserts of one open `STAY_OVERDUE_ACTIVE` key. One insert committed and one failed with SQLState 23505. Open-row count for that key was 1 | Repository unit tests are separate and are not this evidence |
| Null monthly kind | Passed | Calculator test named above, inside the 17 passing tests | None for this row |
| Reserve, move-in, cancel | Passed | `OccupancyServiceTest` methods in the 31 passing tests | No browser or device confirmation |
| Transfer KEEP deposit | Partial | `applyTransferSnapshot_keep_copiesFoodIncludedInRent` copies the rent snapshot. Transfer service tests do not assert a deposit payment count | A test that proves KEEP creates no new deposit payment |
| Vacate leaves a paid row | Partial | `syncExpectedPayments_doesNotUpdatePaidMonthlyRent` passed | Vacate against a `PAID` row |
| Past planned end stays `ACTIVE` | Passed | `OverdueStayAlertRulesTest` 7 and `OccupancyNotificationSyncServiceTest` 11, inside the 135. No vacate | None for the alert rule |
| Mess creates no rent | Not run | No Mess generation test in this pass | Generation test for a Mess space |
| PG and Hostel room allocate | Passed | `OccupancyTargetServiceTest`, 4 tests, 0 failures | None for this reject rule |
| Co-living E1–E3, E7–E8 | Partial | Room/bed reject, second bed, and both transfer directions passed in `OccupancyServiceTest` | Assert no second deposit on E7 and E8 |
| Rental and Corporate unit | Partial | `allocate_rentalUnit_withNoBed_succeeds` passed. `resolve_rentalAndCorporateStayRejectBedAndRoom` passed | Corporate unit allocate success |
| Dashboard occupied count | Not run | No test in this pass | Dashboard summary test that excludes `RESERVED` |
| DEF-004 stay charges | Not run | No `findActiveStayCharges` test in this pass | Query test that a unit occupancy is returned |
| Food included in rent | Partial | `applyActivationSnapshot_foodIncludedInRent_succeedsWithoutFoodCharge` passed | Persisted ledger with no second food line |
| Rollback | Passed on disposable DB only | Reverse DDL removed `stay_kind` and `short_stay_enabled` and restored the V23 history check without `EXTENDED`. The short occupancy row remained `ACTIVE`. Flyway has no undo scripts, so `flyway_schema_history` still listed 156–158 after the reverse DDL. The database was then dropped. `acomi_db` was not connected | Do not treat this as a deployment rollback |
| Section 43 decisions | Open | Approved decisions were preserved. Open decisions were not implemented | Dependencies cannot be `COMPLETE` while earlier phases are only `ACCEPTED` |
| Section 43 dependencies | Blocked | Phases 1–7 are `ACCEPTED` for recorded scopes, not section 43 `COMPLETE` | Each earlier phase must meet every section 43 gate |
| Section 43 schema and API | Partial | Disposable schema matches `V156`–`V158`. Old allocate fields pass in service tests | Deployment database and an API test |
| Section 43 automated tests | Partial | Named service tests and disposable D1 plus notification unique-index race ran | D4–D6 through Spring |
| Section 43 web and mobile | Partial | Web scripts exit 0. Android Jest exit 0. `npx tsc -b` on the web app exited 2. `npx tsc --noEmit` on Android exited 2. Public-site `src` has no `allocateMember`, `stayKind`, `agreedTotal`, or `/occupancies` | Browser walk and device or emulator walk. Stay files are not the only typecheck failures |
| Section 43 ledger | Blocked | See the ledger row. Unit tests are not ledger proof | Service-posted rows for A, C, D, and E |
| Section 43 acceptance | Open | Phase checkboxes in section 36 stay at their accepted scopes | Do not mark a phase `COMPLETE` from this pass |
| Section 43 rollback | Partial | Disposable reverse DDL only | Review against deployment rows after a deployment test |
| Section 45 journeys | Blocked | `adb devices` printed no attached device. No browser tool was available, so no owner workflow was clicked | Web journeys and Android device or emulator journeys |

Rollback note for the phases in this release: `V156`, `V157`, and `V158` are forward Flyway scripts. There is no Flyway undo. On `acomi_release_gate_df61ed3782a8` only, the test dropped `spaces.short_stay_enabled`, the V157 columns and unique indexes, and the V156 stay columns, then restored the pre-`EXTENDED` history check. The occupancy row survived. Deleting `flyway_schema_history` was not done. That reverse DDL must not be run on `acomi_db`.

### 33.2 Service ledger pass, 2026-10-30

Phase 8 stays `NOT STARTED`. Section 43 stays `OPEN`. The command was `.\mvnw.cmd "-Dtest=StayReleaseGateServiceTest" "-Dmaven.compiler.useIncrementalCompilation=false" test`. Exit 0. Tests run: 14, Failures: 0. Finished 2026-10-30T10:55:28+05:30. Database `acomi_release_gate_c3aa3b21fe16`. Spring Flyway migrated that database. `@AfterAll` dropped it and the test asserts `acomi_db` is still listed. This is not a Flyway undo and not a deployment migration.

| Criterion | Expected | Where | Test | Evidence | Result | Gap |
|---|---|---|---|---|---|---|
| Old allocate body | `expectedCheckoutDate` alone stays monthly | `OccupancyController.allocate` | `legacyAllocateBodyPersistsMonthlyStayAndRent` | Spring controller, persisted row | HTTP 201. `stay_kind` null. Both end columns match. One `RENT/MONTHLY` and one deposit | No browser confirmation |
| Short stay ledger | Agreed total and deposit, no monthly rent | `OccupancyService.allocate`, `SpacePaymentGenerationService` | `shortStayServiceWritesExtraAndDepositWithoutMonthlyRent` | Persisted ledger from the service | `RENT/EXTRA` 5600 `PENDING`, deposit 2000, zero monthly after a later sync | Checkout is the next day in the current month, not 1 Nov through 8 Nov. Scenario B stays unimplemented |
| Extension extra | One added-period extra, including after the original is paid | `OccupancyExtensionService` | `shortStayExtensionThatLeavesTheMonthWritesNoExtra` | Service rejection, then the original row | Cross-month extend throws. The original extra stays one row of 5600 | D-05 blocks the added interval. Scenario C was not posted |
| Monthly extension | No extra | `OccupancyExtensionService` | `monthlyExtensionWritesNoExtraAndReplayKeepsOneHistoryRow` | Persisted payments and history | Zero extras. One history row for the replayed key | None for the monthly rule |
| Paid history | A paid month stays, the effective month uses the new rent | `SpacePaymentService.markReceived`, `OccupancyExtensionService`, `syncExpectedPayments` | `rentChangeLeavesThePaidCurrentMonthRowUntouched` | Persisted ledger | Current month stays `PAID` at the amount the service had written. Next month is 11000 `PENDING`. Zero extras | The current month is prorated from today, so it is not the October 10000 fixture |
| D4 | Same idempotency key, one result | `OccupancyExtensionService.extend` | `concurrentSameKeyExtendCommitsOneHistoryRow` | PostgreSQL concurrency through Spring | Two threads, zero failures, one history row, zero extras | Monthly date change, not a short-stay extra |
| D5 | Different dates do not double-bill | `OccupancyExtensionService.extend` | `concurrentDifferentExtendsLeaveNoExtra` | PostgreSQL concurrency through Spring | At most one thread failed the date check. Final end is the later date. Zero extras. Stay stays `ACTIVE` | Two short-stay extras were not created |
| D6 | Extend versus vacate stays consistent | `OccupancyExtensionService` and `OccupancyService.vacate` | `concurrentExtendAndVacateLeaveAConsistentStayAndNoExtra` | PostgreSQL concurrency through Spring | Final status `VACATED`. At most one caller failed. Monthly rent remains. Zero extras | Not a short-stay extra race |
| Mess rent | Sync creates no rent | `SpacePaymentGenerationService.syncExpectedPayments` | `messSyncCreatesNoRent` | Persisted count | Zero `RENT` rows | None for this reject |
| Dashboard beds | Reserved beds are not occupied | `SpaceDashboardSummaryService` | `dashboardCountsOccupiedAndReservedBedsSeparately` | Service read after allocate and reserve | Occupied 1, reserved 1. Reserved bed status `RESERVED` | No browser dashboard |
| DEF-004 | Stay charges include a unit occupancy | `SpacePaymentService.listActiveStayCharges` | `corporateUnitAllocateAppearsInStayCharges` | Service list after corporate allocate | The unit occupancy id is in the list. Target type `UNIT` | Rental was not re-run |
| Transfer KEEP | No new deposit, planned end not copied | `OccupancyService.transfer` | `transferKeepDoesNotAddADepositAndDoesNotCopyThePlannedEnd` | Persisted payments and dates | One deposit. New end is null. Old stay `VACATED` | Co-living E7 and E8 were not re-run as room transfers |
| Vacate paid | Paid amount survives vacate | `SpacePaymentService.markReceived`, `OccupancyService.vacate` | `vacateLeavesAPaidShortStayChargeUnchanged` | Persisted ledger | Extra remains `PAID` 5600. Stay is `VACATED` | None for this row |
| Corporate unit | Allocate with a null bed | `OccupancyService.allocate` | `corporateUnitAllocateAppearsInStayCharges` | Persisted occupancy | `target_type` `UNIT` and one monthly rent | Rental unit allocate was not re-run |
| Food line | Bundled food is not a second payment | `OccupancyService.allocate`, `syncExpectedPayments` | `bundledFoodDoesNotAddASecondLedgerLine` | Persisted ledger | Food charge rejected and left no payment. Accepted stay has rent, deposit, null food snapshot, and zero `MEAL` | None for this row |
| Web typecheck | `tsc -b` | `K:\AcomiWeb` | `npx tsc -b --pretty false --incremental false` | Typecheck | Exit 0 | Not a browser walk |
| Web tests | Stay scripts and `npm test` | `K:\AcomiWeb` | Stay scripts, then `npm test` | Static checks | Seven stay scripts exited 0. `npm test` exited 1 in `scripts/check-space-dashboard-home.mjs` because the Android back helper no longer contains `goBackInStack` | That script was not changed. No browser walk |
| Android typecheck | `tsc --noEmit` | `K:\AcomiMobile` | `npx tsc --noEmit --pretty false --incremental false` | Typecheck | Exit 2. 149 errors in 83 files. None name the stay wizard, overdue link, stay-refresh fixture, `needsImport`, `prefilledTarget`, `queueMicrotask`, or `bedOccupancyMenuOptions` | Remaining errors are outside those files |
| Android Jest | Stay suites | `K:\AcomiMobile` | `npx jest` on seven stay suites | Jest | Exit 0. 7 suites, 39 tests | `adb devices` listed no device |
| Public site | No allocate API | `K:\AcomiPublicWebsite\src` | Source search on the previous pass | Static | No allocate or stay-kind API | No live page was opened |
| Deployment migration | `V156`–`V158` on the deployment database | Flyway | Not run | None | `acomi_db` was not connected | Still required before release |
| Section 43 | Every gate, including earlier phases `COMPLETE` | Sections 33, 43, 45 | This pass | Mixed | Open decisions stay unimplemented. Earlier phases stay `ACCEPTED` | Section 43 stays open. Phase 8 may not start |

### 33.3 Closure pass, 2026-10-30

Phase 8 stays `NOT STARTED`. Section 43 stays `OPEN`. Phases 1–7 stay `ACCEPTED` for the scopes already recorded. They are not section 43 `COMPLETE`. D-05, D-14, D-16, D-17, and D-18 stay `OPEN` and unimplemented. D-08 and D-10 were not changed.

Fixture rows in `StayReleaseGateServiceTest` are direct SQL: users, spaces, memberships, buildings, floors, rooms, beds, units, and members. Occupancy, payment, and history rows asserted below were written by `OccupancyController`, `OccupancyService`, `OccupancyExtensionService`, `SpacePaymentService`, or `SpacePaymentGenerationService`, then read back with JDBC. `ReleaseGatePersistenceTest` still inserts its ledger and notification rows with JDBC. That file is schema and migration evidence, not service-posted ledger evidence.

Spring races in this class, unchanged and still passing: `concurrentSameKeyExtendCommitsOneHistoryRow`, `concurrentDifferentExtendsLeaveNoExtra`, and `concurrentExtendAndVacateLeaveAConsistentStayAndNoExtra`. They call `OccupancyExtensionService.extend` and, for the last one, `OccupancyService.vacate`. The notification race in `ReleaseGatePersistenceTest` is two JDBC inserts, not a Spring service race. `Phase5StayConcurrencyIsolationTest` was not rerun.

| Command | Where | Exit | Result |
|---|---|---|---|
| `.\mvnw.cmd "-Dtest=StayReleaseGateServiceTest" "-Dmaven.compiler.useIncrementalCompilation=false" test` | `acomi-backend` | 0 | 16 tests, 0 failures, 0 errors. Finished 2026-10-30T11:41:21+05:30. Database `acomi_release_gate_467b59cbc4cc`. `@AfterAll` refused any other name, dropped that database, and asserted `acomi_db` is still listed. A run at 2026-10-30T11:30:03+05:30 on `acomi_release_gate_ced34d56eabe` failed the co-living transfer because a one-bed room was already `OCCUPIED`. That run is not the passing evidence. Its `@AfterAll` still drops only that disposable name |
| `.\mvnw.cmd "-Dtest=ReleaseGatePersistenceTest" "-Dmaven.compiler.useIncrementalCompilation=false" test` | `acomi-backend` | 0 | 4 tests, 0 failures. Finished 2026-10-30T11:44:26+05:30. Database `acomi_release_gate_ec4ede528558`. Flyway applied 155 migrations to v155, then v156, v157, and v158 on PostgreSQL 17.10. `@AfterAll` dropped that database and asserted `acomi_db` remains. The fourth test then drops the new columns with hand-written `ALTER` and `DROP` statements and restores the pre-`EXTENDED` history check. Flyway has no undo scripts. `flyway_schema_history` is not a down migration. This was not applied to `acomi_db` |
| `npx tsc --noEmit --pretty false --incremental false` | `AcomiMobile` | 2 | 112 errors in 60 files. Not a device walk |
| `npx jest` on the seven stay suites named below | `AcomiMobile` | 0 | 7 suites, 39 tests. Finished 2026-10-30T06:14:46Z |
| `npm test` | `AcomiWeb` | 0 | Static script chain, including `check-space-dashboard-home.mjs`. Finished 2026-10-30T06:00:31Z |
| Seven stay scripts listed below | `AcomiWeb` | 0 | Each script printed its success line |
| `npx tsc -b --pretty false --incremental false` | `AcomiWeb` | 0 | Finished 2026-10-30T06:02:00Z |
| `adb devices` | host | 0 | Header only. No device or emulator |
| Browser automation | none | not run | No browser or Playwright tool is available in this session |
| `V156`–`V158` on `acomi_db` | deployment | not run | No authorization and no deployment window. Disposable success does not close this gate |

The seven web scripts were `check-occupancy-wizard-errors.mjs`, `check-stay-kind-absent.mjs`, `check-extend-stay.mjs`, `check-short-stay.mjs`, `check-stay-overlap-error.mjs`, `check-phase6-stay-refresh.mjs`, and `check-overdue-stay-alert.mjs`.

`check-space-dashboard-home.mjs` now requires `goBackAboveSpaceTabs`, a call to `spaceTabHeaderBackAncestorIndex`, `navigators[index]?.goBack()`, and `spaceDashboardHeaderDestination('back'`. It also requires `spaceHeaderNav.ts` to skip `ancestor.type === 'tab'`. It rejects `goBackInStack`. `SpaceTabBackButton.tsx` was not changed. Header back still pops the nearest non-tab navigator that has a previous screen.

`shortStayExtensionThatLeavesTheMonthWritesNoExtra` calls `OccupancyExtensionService.extend` with a checkout in the next month and `additionalAmount` 1600. The service throws. After that rejection the stay is `ACTIVE`, both end columns are still the original checkout, `RENT/EXTRA` is one `PENDING` row of 5600, `DEPOSIT/SECURITY` is one `PENDING` row of 2000, monthly rent is zero, and the failed idempotency key has zero history rows. No extra was inserted.

`rentalUnitAllocateSyncsThePersistedLedger` calls `OccupancyService.allocate` for a rental unit and `SpacePaymentGenerationService.syncExpectedPayments` twice. The persisted row is `target_type` `UNIT`, one `RENT/MONTHLY` `PENDING`, one `DEPOSIT/SECURITY` of 5000 `PENDING`, and that occupancy is returned by `SpacePaymentService.listActiveStayCharges`.

`coLivingRoomTransferKeepLeavesOneDepositAndDoesNotCopyThePlannedEnd` allocates a bed through `OccupancyService`, then transfers to the containing room with `TransferRentPolicy.KEEP`. A second empty bed is created first so the stored room status stays `AVAILABLE`: one occupied bed marks a one-bed room occupied, and transfer then rejects that room. The new stay is `ROOM` and `ACTIVE`. Its planned end is null. Its deposit count is zero. The member still has one `DEPOSIT/SECURITY` row, on the vacated stay. The old stay is `VACATED`.

Android typecheck categories that remain, after the import-path, `absoluteFill`, and missing-symbol fixes in this pass. These were not forced green:

| Category | Count | Where it shows up |
|---|---|---|
| `string` is not assignable to `number` | 9 | `normalizeDashboardSummary.test.ts` |
| `string \| null` is not assignable to `string` | 7 | `MemberDepositTab.tsx`, `useCompleteProfile.ts` |
| `notPlanned` is not a known property | 5 | `dailyMenuDayStatus.test.ts` |
| `globalThis` has no index signature | 5 | `fileUploadService.ts`, `defaultSpaceContact.ts`, `secureRandom.ts` |
| `PrepaidBalanceUnit \| null` | 4 | `MemberMealSubscriptionActivityTimeline.tsx` |
| Navigation `getState` mismatch | 3 | `HeaderBackButton.tsx`, `SpaceTabBackButton.tsx` |
| No matching overload | 3 | scattered call sites |
| Filter option `.value` missing on a union | 3 | `DashboardBedInventoryFiltersBar.tsx` |
| Meal item fixtures missing fields | 6 | meal test objects |
| `headerBackVisible` / `nextRent` unknown | 4 | navigation options and bed pricing fixtures |
| `Uint8Array` not a `Blob`, proof-image unions, `UserResponse` unions, meal-poll casts | 2 each | upload, profile, and meal tests |
| Node builtins `child_process` and `process` | 2 | `hardcodedUiScan.test.ts` |
| Remaining single mismatches | the rest of 112 | meals, payments, dashboard, and profile types |

`SpaceTabBackButton.tsx` still has the navigation `getState` mismatch. That is a React Navigation type mismatch on the existing helper. It was not patched with a cast.

Public-site `src` was searched again for `allocateMember`, `stayKind`, `agreedTotal`, and `/occupancies`. No matches. No public page was opened and no public file was edited.

#### Evidence matrix

| Gate | Result | Owner and next action |
|---|---|---|
| Decisions the release uses | PASS for approved rows. D-05, D-14, D-16, D-17, D-18 stay OPEN | Product owner records approval or an explicit deferral before any of those behaviors is coded |
| Section 29 named tests | PASS on the earlier 135-test run. Not rerun here | Do not run whole-reactor `mvn test` while it can migrate `acomi_db` |
| Service ledger A, same-month short stay, monthly extension, paid month, vacate, mess, food, dashboard, DEF-004 | PASS | None for those rows |
| Scenario C added extra | FAIL against a posted extra. PASS as a rejection that leaves the original ledger unchanged | D-05 stays open. Do not invent a cross-month charge |
| Rental and corporate unit allocate | PASS | None for allocate and sync |
| Co-living bed-to-room KEEP | PASS | Room-to-bed ledger proof is still NOT RUN |
| Spring D4–D6 monthly races | PASS | A short-stay extra race is NOT RUN because D-05 rejects the only cross-month extra available today |
| Disposable V156–V158 upgrade | PASS on `acomi_release_gate_ec4ede528558` | Does not authorize a shared database |
| Flyway undo | NOT RUN. None exists | Do not describe the hand-written reverse DDL as an undo |
| Deployment of V156–V158 on `acomi_db` | BLOCKED | Needs explicit authorization and an approved deployment window. Backend start applies Flyway. Do not start that process against `acomi_db` for this gate |
| Web static checks and `tsc -b` | PASS | Not a browser walk |
| Android stay Jest | PASS | Not a device walk |
| Android `tsc --noEmit` | FAIL | 112 errors in 60 files, categorized above. Stay request builders are not in that list except the pre-existing `SpaceTabBackButton` navigation type |
| Browser owner walk | BLOCKED | Needs a browser automation environment, then allocate, short stay, charges, extend, vacate, transfer, and payment refresh |
| Android device or emulator walk | BLOCKED | `adb devices` attached nothing. Needs a device or emulator for the same owner flows |
| Public website | PASS as a source investigation. No live page | No product change. A live smoke is still NOT RUN |
| Section 43 dependencies | BLOCKED | Phases 1–7 are `ACCEPTED`, not `COMPLETE` |
| Section 43 overall | OPEN | Phase 8 may not start |

### 33.4 Short-stay races and room-to-bed transfer, 2026-10-30

Phase 8 stays `NOT STARTED`. Section 43 stays `OPEN`. Phases 1–7 stay `ACCEPTED` for their recorded scopes. D-05, D-14, D-16, D-17, and D-18 stay `OPEN`. D-08 and D-10 were not changed.

Fixture rows are still direct SQL: users, spaces, memberships, buildings, floors, rooms, beds, units, and members. Occupancy, payment, and history rows below were written by `OccupancyService`, `OccupancyExtensionService`, `AccommodationStatusSyncService.setMaintenance`, or the payment services, then read back with JDBC. `ReleaseGatePersistenceTest` still inserts its ledger rows with JDBC. Its notification race is two JDBC inserts, not a Spring service race.

| Command | Exit | Result |
|---|---|---|
| `.\mvnw.cmd "-Dtest=StayReleaseGateServiceTest" "-Dmaven.compiler.useIncrementalCompilation=false" test` | 0 | 20 tests, 0 failures, 0 errors, 122.1 s. Database `acomi_release_gate_5bb9acb5dc81`. `@AfterAll` dropped that name and asserted `acomi_db` remains. Finished 2026-10-30T12:45+05:30 |
| `.\mvnw.cmd "-Dtest=ReleaseGatePersistenceTest" "-Dmaven.compiler.useIncrementalCompilation=false" test` | 0 | 4 tests, 0 failures. Finished 2026-10-30T12:49:30+05:30. Database `acomi_release_gate_f7a5ee3f7ec9`. Flyway reached v158 from a v155 baseline on PostgreSQL 17.10, then the test dropped the new columns with hand-written SQL. That is not a Flyway undo. The database was dropped. `acomi_db` was not connected |
| `npx tsc --noEmit --pretty false --incremental false` in `AcomiMobile` | 2 | Before this pass: 112 errors in 60 files. After the type fixes below: 75 errors in 44 files |
| `npx jest` on the seven stay suites | 0 | 7 suites, 39 tests |
| `npm test` in `AcomiWeb` | 0 | Includes `check-space-dashboard-home.mjs`, which still requires `goBackAboveSpaceTabs` |
| Seven stay source checks in `AcomiWeb` | 0 | Wizard, stay-kind absent, extend, short stay, overlap, phase 6 refresh, overdue alert |
| `npx tsc -b --pretty false --incremental false` in `AcomiWeb` | 0 | |
| `adb devices` | 0 | No device attached during this pass |
| Browser automation | not run | No browser tool is available |
| V156–V158 on `acomi_db` | not run | Blocked. Startup Flyway is enabled and the local datasource defaults to `acomi_db` |

`concurrentSameKeyShortStayExtensionWritesOneExtra` extends a short stay by one night through `OccupancyExtensionService`. The added night stays in the checkout month, so D-05 does not reject it. Two threads and a later replay use the same idempotency key. One history row. One added `RENT/EXTRA` of 800 `PENDING`, period start equal to the original checkout and period end the next day. The original extra stays 5600 `PENDING`. Deposit stays one row of 2000 `PENDING`. Both end columns match the new checkout. The stay stays `ACTIVE`. No monthly rent.

`concurrentDifferentShortStayExtensionsLeaveOneAddedExtra` sends two different keys for that same new end. One thread fails the date check after the other commits. Exactly one added extra, one history row, and the original 5600 extra remains.

`concurrentCrossMonthShortStayExtensionsWriteNoExtra` sends two keys for `checkout.plusMonths(1)`. Both calls throw. A later repeat of the first key throws again and still writes no history. The original checkout, the 5600 `PENDING` extra, the 2000 `PENDING` deposit, and the active status are unchanged. No monthly rent.

`coLivingRoomToBedTransferKeepLeavesOneDepositOnTheVacatedStay` allocates a co-living room through `OccupancyService`. A maintenance bed rejects transfer and leaves the room stay, rent, and deposit unchanged. Another member cannot take the open bed while the room stay holds it, and that rejection writes no payment. Transfer onto the open bed with `TransferRentPolicy.KEEP` vacates the room stay, leaves its planned end and its one deposit and monthly rent in place, and opens a bed stay with a null planned end, no deposit, and no extra. The new stay has one `TRANSFERRED` history row.

Android type errors that remain, and why they were not forced closed: dashboard summary fixtures still pass numeric strings into `DashboardSummaryResponse` fields whose declared type is `number`, while `toNumber` already accepts `unknown`. Widening those response types would change the app contract, and a boundary cast was not added. The other remaining errors are meal fixtures, payment proof unions, navigation overloads, `headerBackVisible` on tab options, and profile unions. `headerBackTitleVisible` was not renamed, because that would change which option the navigator reads. Header back still uses the existing `goBackAboveSpaceTabs` behavior. Its parameter type now describes `getParent`, `getState`, and `goBack` only.

#### Evidence matrix

| Gate | Result | Next action |
|---|---|---|
| Approved decisions | PASS | None |
| D-05, D-14, D-16, D-17, D-18 unimplemented | PASS | Product owner approval before any of those rules is coded |
| Section 29 named unit tests | PASS — PREVIOUSLY VERIFIED, NOT RERUN | Do not run whole-reactor `mvn test` against `acomi_db` |
| Same-month short stay, monthly extension, paid month, vacate, mess, food, dashboard, rental and corporate allocate | PASS | Rerun inside the 20-test service class |
| Scenario C cross-month extra | PASS as a rejection. FAIL as a posted cross-month charge | D-05 stays open |
| Same-month one-night short extra, including same-key replay | PASS | None for that interval |
| Concurrent different keys, one added extra | PASS | None |
| Concurrent cross-month short extensions | PASS | None |
| Co-living bed-to-room KEEP | PASS | Rerun in the 20-test class |
| Co-living room-to-bed KEEP | PASS | None for the deposit and planned-end contract |
| Co-living E1–E3 | PASS — PREVIOUSLY VERIFIED, NOT RERUN | Earlier `OccupancyServiceTest` |
| Spring D4–D6 monthly races | PASS | Rerun in the 20-test class |
| D1 insert race | PASS — PREVIOUSLY VERIFIED, NOT RERUN | `Phase5StayConcurrencyIsolationTest` |
| Disposable V156–V158 upgrade and hand-written column removal | PASS on `acomi_release_gate_f7a5ee3f7ec9` | Not a Flyway undo and not a deployment |
| Flyway undo | NOT RUN | No undo scripts exist |
| V156–V158 on `acomi_db` | BLOCKED | Explicit authorization and a deployment window. Do not start the backend against `acomi_db` for this gate |
| Owner-web `npm test`, seven stay scripts, `tsc -b` | PASS | Not a browser walk |
| Android stay Jest | PASS | Not a device walk |
| Android `tsc --noEmit` | FAIL | 75 errors in 44 files remain. Do not close them with casts |
| Browser owner walk | BLOCKED | A browser automation tool, then the owner flows |
| Android device or emulator walk | BLOCKED | `adb devices` listed no device during this pass |
| Public website | PASS as a source search. Live page NOT RUN | No product change |
| Section 43 dependencies | BLOCKED | Phases 1–7 are `ACCEPTED`, not `COMPLETE` |
| Section 43 | OPEN | Phase 8 may not start |

### 33.5 Android typecheck closure, 2026-10-30

Phase 8 stays `NOT STARTED`. Section 43 stays `OPEN`. Phases 1–7 stay `ACCEPTED` for their recorded scopes. D-05, D-14, D-16, D-17, and D-18 stay `OPEN`. D-08 and D-10 were not changed. No new backend test was added. The service and migration classes already cover the release-gate rows from sections 33.2 through 33.4, including the cross-month rejection.

The Android typecheck at the start of this pass was the previous log: 75 errors in 44 files, exit 2. After the contract and fixture fixes below, `npx tsc --noEmit --pretty false --incremental false` in `AcomiMobile` exited 0 with 0 errors.

`goBackAboveSpaceTabs` was not changed. `headerBackTitleVisible` was removed from the stack header options because the installed native-stack types do not include it. `headerBackVisible: false` remains. Stack `headerLeftContainerStyle` and `headerRightContainerStyle` were removed for the same reason. Tab headers still set those container styles. Member-tab `headerBackVisible` was removed where `BottomTabNavigationOptions` does not declare it. Space tabs still install `headerLeft`.

Other type fixes that change what the user can see: the member-list avatar now passes `label`, so the initial is drawn. The enquiry success toast sets `tone: 'success'`. The renderer already treated a missing tone as success. The space-health pending count reads `totalCount`. The previous `.length` read was not a field of `PendingActionsSummary`, so that count had been undefined. Profile status has no `REJECTED` value, so that comparison was removed and the tone stays neutral. `Space.photoFileId` was added because `SpaceResponse` already returns it. Payment timeline events now include `REMINDER_SENT` and `REMINDER_FAILED`, matching the backend enum.

| Command | Exit | Result |
|---|---|---|
| `npx tsc --noEmit --pretty false --incremental false` in `AcomiMobile` | 0 | 0 errors. Previous log for this tree: 75 errors in 44 files, exit 2 |
| `npx jest` on the seven stay suites plus the fixture suites changed in this pass | 0 | 18 suites, 114 tests. The seven stay suites are included |
| `npm test` in `AcomiWeb` | 0 | Finished 2026-10-30T14:00:13+05:30. The chain includes the stay scripts and `check-space-dashboard-home.mjs` |
| Seven stay source checks in `AcomiWeb` | 0 | Wizard, stay-kind absent, extend, short stay, overlap, phase 6 refresh, overdue alert. Finished 2026-10-30T14:01:55+05:30 |
| `npx tsc -b --pretty false --incremental false` in `AcomiWeb` | 0 | Finished 2026-10-30T14:02:29+05:30 |
| `.\mvnw.cmd "-Dtest=StayReleaseGateServiceTest,ReleaseGatePersistenceTest" "-Dmaven.compiler.useIncrementalCompilation=false" test` | 0 | Finished 2026-10-30T14:02:23+05:30. Service test: 20 tests, 0 failures, 95.48 s, database `acomi_release_gate_0f467956f655`. Persistence test: 4 tests, 0 failures, 24.58 s, database `acomi_release_gate_0c49c648230b`. Each `@AfterAll` drops only that disposable name and fails if `acomi_db` is not still listed. Neither URL was `acomi_db` |
| `adb devices` | 0 | Header only. No device or emulator attached |
| Browser automation | not available | No browser tool is registered |
| Public-site `src` search for allocate, stay kind, agreed total, and occupancies | no matches | Not a live page |
| V156–V158 on `acomi_db` | not run | Blocked |

Service-written rows in `StayReleaseGateServiceTest` are still occupancy, payment, and history rows created by `OccupancyService`, `OccupancyExtensionService`, `AccommodationStatusSyncService`, and the payment services. Setup rows for users, spaces, rooms, beds, and members remain direct SQL. `ReleaseGatePersistenceTest` still inserts its ledger and notification rows with JDBC and then removes the V156–V158 columns with hand-written SQL. That removal is not a Flyway undo. `flyway_schema_history` still lists those versions after the hand-written drops. No undo script was added.

Deployment of V156–V158 stays blocked. The approved shape, which was not executed, is: take a backup of the target database, get an explicit authorization and a deployment window, start the backend so Flyway applies the existing classpath migrations, then confirm `flyway_schema_history` reaches v158 and `ddl-auto` validate succeeds. Local `application-local.yml` defaults `DB_NAME` to `acomi_db`, and Flyway runs on startup, so this gate must not be closed by starting the local profile. There is no feature-flag framework and no down migration. Recovery is restore from the backup. Do not describe the disposable hand-written column drops as that recovery.

#### Evidence matrix

| Gate | Result | Next action |
|---|---|---|
| Approved decisions | PASS | None |
| D-05, D-14, D-16, D-17, D-18 unimplemented | PASS | Product owner approval before any of those rules is coded |
| Section 29 named unit tests | PASS — PREVIOUSLY VERIFIED, NOT RERUN | Do not run whole-reactor `mvn test` against `acomi_db` |
| Service ledger, short-stay races, co-living transfers, cross-month rejection | PASS | Rerun in this pass: 20 tests, 0 failures, `acomi_release_gate_0f467956f655`, then dropped |
| Scenario C cross-month extra | PASS as a rejection. FAIL as a posted cross-month charge | D-05 stays open |
| D1 insert race | PASS — PREVIOUSLY VERIFIED, NOT RERUN | `Phase5StayConcurrencyIsolationTest` |
| Disposable V155-to-V158 upgrade and hand-written column removal | PASS | Rerun in this pass: 4 tests, 0 failures, `acomi_release_gate_0c49c648230b`, then dropped. Not a Flyway undo |
| Flyway undo | NOT RUN | No undo scripts exist |
| V156–V158 on `acomi_db` | BLOCKED | Authorization, backup, and a deployment window. Do not start the local backend for this gate |
| Owner-web `npm test`, seven stay scripts, `tsc -b` | PASS | Not a browser walk |
| Android stay Jest | PASS | 7 suites inside the 18-suite rerun. Not a device walk |
| Android `tsc --noEmit` | PASS | 0 errors, exit 0. Previous count 75 errors in 44 files |
| Browser owner walk | BLOCKED | A browser automation tool, then the owner flows |
| Android device or emulator walk | BLOCKED | `adb devices` listed no device |
| Public website | PASS as a source search. Live page NOT RUN | No product change |
| Section 43 dependencies | BLOCKED | Phases 1–7 are `ACCEPTED`, not `COMPLETE` |
| Section 43 | OPEN | Phase 8 may not start |

### 33.6 Deployment runbook and walkthroughs, 2026-10-30

Phase 8 stays `NOT STARTED`. Section 43 stays `OPEN`. Phases 1–7 stay `ACCEPTED` for their recorded scopes. D-05, D-14, D-16, D-17, and D-18 stay `OPEN` and unimplemented. D-08 and D-10 were not changed. No application behavior was changed. No migration was executed. The backend was not started.

This section is the stay-specific deployment runbook and the click-path checklist. It does not replace `AcomiMobile/docs/ACOMI-PRODUCTION-DEPLOYMENT-RUNBOOK.md`. The version 1.18 test results below were read from the saved Surefire reports and from section 33.5. They were not rerun.

#### Commands in this pass

| Command or read | Exit | Result |
|---|---|---|
| Read `target/surefire-reports/...StayReleaseGateServiceTest.txt` | n/a | Tests run: 20, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 95.48 s. Not a new execution |
| Read `target/surefire-reports/...ReleaseGatePersistenceTest.txt` | n/a | Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 24.58 s. Not a new execution |
| Read `application.yml`, `application-local.yml`, `application-prod.yml`, `V156`, `V157`, `V158` | n/a | Configuration recorded below. No process was started from those files |
| `adb devices` | 0 | `List of devices attached` and no serial |
| Browser tool search for browser, Playwright, and Puppeteer | n/a | No match. No page was opened |
| `mvn`, `npm test`, `tsc`, `spring-boot:run`, `psql`, Flyway | not run | Not required to verify the unchanged 1.18 results |

#### Baseline kept from section 33.5

| Check | Result |
|---|---|
| Android `npx tsc --noEmit --pretty false --incremental false` | PASS — PREVIOUSLY VERIFIED, NOT RERUN. 0 errors, exit 0. The earlier log was 75 errors in 44 files, exit 2 |
| Android `npx jest` | PASS — PREVIOUSLY VERIFIED, NOT RERUN. 18 suites, 114 tests, exit 0, including the seven stay suites |
| Owner-web `npm test` | PASS — PREVIOUSLY VERIFIED, NOT RERUN. Exit 0, finished 2026-10-30T14:00:13+05:30 |
| Owner-web seven stay source checks | PASS — PREVIOUSLY VERIFIED, NOT RERUN. Exit 0, finished 2026-10-30T14:01:55+05:30 |
| Owner-web `npx tsc -b --pretty false --incremental false` | PASS — PREVIOUSLY VERIFIED, NOT RERUN. Exit 0, finished 2026-10-30T14:02:29+05:30 |
| `StayReleaseGateServiceTest` | PASS — PREVIOUSLY VERIFIED, NOT RERUN. 20 tests, 0 failures, 95.48 s, database `acomi_release_gate_0f467956f655`, then dropped |
| `ReleaseGatePersistenceTest` | PASS — PREVIOUSLY VERIFIED, NOT RERUN. 4 tests, 0 failures, 24.58 s, database `acomi_release_gate_0c49c648230b`, then dropped |
| `acomi_db` migration | NOT RUN. The disposable `@AfterAll` methods asserted that `acomi_db` was still listed. They did not connect to it |

#### What V156, V157, and V158 do

Flyway location is `classpath:db/migration`. Versions are global, not per folder. `V155__create_service_contacts.sql` is the preceding version. A search for undo scripts under `db/migration` found none.

| Script | Effect |
|---|---|
| `occupancy/V156__occupancy_stay_kind.sql` | Adds nullable `occupancies.stay_kind VARCHAR(20)`, `pricing_basis VARCHAR(30)`, and `agreed_total DECIMAL(12,2)`. `stay_kind` must be null or one of `MONTHLY`, `SHORT`, `OPEN_ENDED`. `agreed_total` must be null or at least 0. `pricing_basis` has no allowed-value list. Replaces the history event check so `EXTENDED` is allowed alongside the existing event names. No backfill. Existing rows stay null and remain monthly |
| `occupancy/V157__stay_extension.sql` | Adds nullable `prior_rent_snapshot DECIMAL(12,2)` and `rent_effective_month DATE`. The snapshot must be null or at least 0. Adds nullable `occupancy_history.idempotency_key VARCHAR(64)` and `extension_charge_id UUID`. Creates unique index `uq_occupancy_history_extension_key` on `(occupancy_id, idempotency_key)` where the key is not null. Creates unique index `uq_space_payments_rent_extra_period` on `(occupancy_id, billing_period_start, billing_period_end)` for `RENT` / `EXTRA` rows that have an occupancy and both period bounds. No backfill. A duplicate extra period fails this migration |
| `space/V158__space_short_stay_enabled.sql` | Adds `spaces.short_stay_enabled BOOLEAN NOT NULL DEFAULT FALSE`. Existing spaces stay off. Do not backfill true |

These scripts do not drop `uq_occupancies_member_active`, `uq_occupancies_member_reserved`, `uq_occupancies_bed_active`, `uq_occupancies_bed_reserved`, `uq_occupancies_room_active`, `uq_occupancies_room_reserved`, `uq_occupancies_unit_active`, or `uq_occupancies_unit_reserved`. They do not add `btree_gist` or an `EXCLUDE` constraint.

#### Compatibility that must stay true

- An old client that omits `stayKind` remains a monthly stay.
- The server rejects `SHORT` while `short_stay_enabled` is false. Mess forces the flag off.
- A short stay uses one `RENT` / `EXTRA` for the agreed total and one `DEPOSIT` / `SECURITY` when the deposit snapshot is greater than 0. Extension and transfer do not create a second deposit.
- A short extension whose added nights fall in two calendar months is rejected with `EXTENSION_CROSSES_MONTH` and the sentence "A short-stay extension cannot cover nights in more than one calendar month." The original checkout, extra, deposit, and history stay unchanged. A one-night extension whose last night stays in the checkout month is allowed. D-05 stays open, so no cross-month charge is implemented.
- `KEEP` transfer copies the rent and food snapshots, does not call activation payment creation, and leaves the new stay without a copied planned end.
- `STAY_OVERDUE_ACTIVE` does not vacate, release inventory, or add a fee.

#### Safeguard: a person confirms the target before any migration

Flyway runs when the backend process starts. Starting the process is the migration. This document does not authorize that start.

Before anyone starts the backend, a person records all of the following in the deployment ticket and another person checks them:

1. The environment name: local, shared, or production.
2. The active Spring profile.
3. The resolved `DB_HOST`, `DB_PORT`, and `DB_NAME`. Record those three values. Do not record the password, a JWT secret, or an OTP key.
4. A written statement that those three values are the authorized target.
5. A written authorization and a deployment window.

Stop, and do not start the process, if any of these are true:

- The profile is `local`, or `SPRING_PROFILES_ACTIVE` is unset. The base file defaults the profile to `local`.
- `DB_NAME` is `acomi_db`, the host is localhost, or any of the three values is blank.
- The ticket has no authorization or no deployment window.
- The person has not yet taken and validated the database backup in the next subsection.

`application-local.yml` builds the URL as `jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:acomi_db}`. A start with no `DB_NAME` therefore migrates `acomi_db`. That same file sets `flyway.clean-disabled` to false, which overrides the base value of true. Do not run `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"`, and do not start the backend with the profile omitted, for this release.

`application-prod.yml` requires `DB_HOST`, `DB_USERNAME`, and `DB_PASSWORD` from the environment. Its URL defaults `DB_PORT` to 5432 and `DB_NAME` to `postgres`, and it adds `sslmode=require`. `flyway.clean-disabled` stays true. The default name `postgres` is not proof of the live database. The production env file was not opened in this pass, so the live host and database name are unknown here. A person must read them from the host env file and compare them with the ticket before start. Do not print the password.

This pass did not open a database connection, did not call Flyway, and did not start the backend.

#### Verify the target database before deployment

Run these reads only after the human check above has named the authorized database. They were not run in this pass. Do not run them against `acomi_db`, production, or a shared database until that authorization exists.

Confirm version 155 succeeded and versions 156, 157, and 158 are absent:

```sql
SELECT version, success, installed_on
FROM flyway_schema_history
WHERE version IN ('155', '156', '157', '158')
ORDER BY installed_rank;
```

Confirm no duplicate extra period would fail `uq_space_payments_rent_extra_period`:

```sql
SELECT occupancy_id, billing_period_start, billing_period_end, COUNT(*)
FROM space_payments
WHERE payment_type = 'RENT'
  AND payment_category = 'EXTRA'
  AND occupancy_id IS NOT NULL
  AND billing_period_start IS NOT NULL
  AND billing_period_end IS NOT NULL
GROUP BY occupancy_id, billing_period_start, billing_period_end
HAVING COUNT(*) > 1;
```

Stop if version 155 is missing or unsuccessful, if 156–158 are already present and this is supposed to be the first apply, or if the duplicate query returns any row. Do not repair a duplicate with manual SQL during the deploy.

#### Backup and backup validation

The production runbook's pre-deploy backup is a container and configuration snapshot under `/home/ubuntu/deploy-backups/pre-deploy-<timestamp>`. It stores `inspect.json`, `summary.txt`, a redacted key list, and a mode-600 copy of the env file. That snapshot is not a PostgreSQL dump and cannot restore occupancy or payment rows.

No `pg_dump` or database restore procedure is written in that runbook. That absence is a release blocker. The required backup, which has not been executed, is:

1. Take a logical dump of the database name confirmed in the human check.
2. Restore that dump onto a new disposable database whose name is neither `acomi_db`, nor the production database, nor a shared database.
3. On the restored copy only, record the maximum successful `flyway_schema_history` version and a row count for `occupancies`, `space_payments`, and `spaces`.
4. Compare those figures with the same read-only counts on the authorized target.
5. Keep the dump until the post-migration smoke checks pass. Record who took it, when, and where it is stored. Do not store it in git.

The container snapshot from the production runbook is still required for an image rollback. It does not replace steps 1–5. The dump command, the restore command, and a successful restore rehearsal are missing information. This pass did not invent them and did not run them.

#### Approved sequence

This sequence was not executed.

1. Complete the human target check and the read-only version and duplicate checks.
2. Complete the database dump and the disposable restore validation.
3. Take the production runbook's container and configuration snapshot.
4. Deploy the backend only, using the production profile and the confirmed env file. Do not use the local profile. Do not set `DB_NAME` to `acomi_db`. Do not call Flyway clean. Do not run manual SQL.
5. If Flyway or startup fails, stop. Do not continue to web or mobile.
6. Read `flyway_schema_history` and confirm versions 156, 157, and 158 have `success = true`. Confirm the health endpoint used by the production runbook, `https://api.acomi.in/actuator/health`, and the runbook's discover check.
7. Deploy owner web, then the public site, then mobile, in the production runbook's order. Clients that send `stayKind` ship only after step 6 succeeded. The public site has no stay change in this release.
8. Run the walkthrough in this section on the deployed environment. A source check is not this step.

`ddl-auto` is `validate`. A backend image older than the applied Flyway version can fail that validation. Do not roll the image backward after versions 156–158 have succeeded.

#### Recovery

There are no Flyway undo scripts. The hand-written column drops inside `ReleaseGatePersistenceTest` are not a recovery procedure and must not be run on the target. `flyway_schema_history` still lists the versions after those drops on a disposable database.

Recovery is restore of the logical dump taken before step 4, onto the same authorized database, followed by a start of the backend image that matches the restored schema. That restore has no approved command in the production runbook and was not rehearsed here. Until the dump and the disposable restore validation exist, this release has no recovery path.

#### Missing before any real deployment

- No recorded authorization for V156–V158 on a named database.
- No deployment window.
- The live production `DB_HOST` and `DB_NAME` were not read. The prod file's default database name is `postgres`, which is not evidence of the live name.
- No approved `pg_dump` command, no restore command, and no restore rehearsal.
- The production runbook was last verified on 2026-09-18 and does not record the current production Flyway version.
- No stay feature flag exists. Short Stay is withheld by `short_stay_enabled` defaulting to false and by the server rejecting `SHORT` while that flag is off.

#### Owner-web and Android walkthrough

Every row is `NOT RUN`. The paths were read from the owner-web router and the Android navigation types. No login was performed, no button was clicked, and no screenshot was captured.

Capture for each row: the surface (owner web or Android), the space id, the occupancy id when one exists, the visible dates and amounts, the success screen or the exact error text, and a screenshot. Do not capture passwords, tokens, or payment proofs. Do not treat this table as a passed gate.

| Step | What to do | Expected UI | Evidence | Status |
|---|---|---|---|---|
| Owner login and space access | Sign in as an owner. Open `/spaces/{spaceId}/dashboard` on the web. Open the same space's tabs on Android | The space dashboard loads for that owner. A member account does not gain operator actions | Screenshot of the dashboard after login, on each surface | NOT RUN |
| Legacy monthly allocation | Open `/spaces/{spaceId}/occupancy/wizard?mode=ALLOCATE` and the Android `OccupancyWizard`. Leave stay kind unset. Allocate a vacant bed, room, or unit | The stay appears on `/spaces/{spaceId}/occupancy?mode=active`. It is monthly. No short-stay extra is created | Screenshots of the wizard, the active list, and the payment list | NOT RUN |
| Short Stay creation and payment | On `/spaces/{spaceId}/edit`, or Android `EditSpaceScreen`, turn Short Stay on for a non-Mess space. Create a short stay with a planned checkout and an agreed total | The wizard offers Short Stay only after the flag is on. The payment list shows one `RENT` / `EXTRA` for the agreed total and one `DEPOSIT` / `SECURITY` when the deposit is greater than 0 | Screenshots of the switch, the wizard totals, and the payment rows | NOT RUN |
| Same-month extension | Extend that short stay by nights whose last night stays in the checkout month. Enter the additional amount | The checkout date moves forward. One new extra appears for the added interval. The original extra and the deposit remain one each | Before and after screenshots of the checkout date and the payment list | NOT RUN |
| Cross-month extension | Extend the same short stay so the added nights fall in two calendar months | The UI shows the rejection "A short-stay extension cannot cover nights in more than one calendar month." The checkout, extra, deposit, and history are unchanged | Screenshot of the error and a screenshot showing the unchanged checkout and payments | NOT RUN |
| Vacate and inventory | Vacate the stay from the occupancy actions. Open `/spaces/{spaceId}/bed-inventory` and the Android inventory screen | The stay is vacated. The bed, room, or unit is available again. A paid extra remains paid | Screenshots of the vacated stay and the inventory status | NOT RUN |
| Co-living transfer and deposit | Transfer an active co-living stay with rent policy `KEEP` | The old stay is vacated. The new stay is active, has no copied planned end, and keeps the deposit snapshot. The payment list still has one deposit | Screenshots of both stays and the payment list, including the deposit count | NOT RUN |
| Overdue notification and deep link | Use an active stay whose planned end is before the space business date. Open Notifications as an owner or manager, then as a member | The operator sees `STAY_OVERDUE_ACTIVE`. Opening it on the web goes to the active occupancy list. Opening it on Android goes to `DashboardOccupancyList` with `mode: 'active'`. A member does not see it. The stay stays active and inventory does not change | Screenshots of the operator notification, the destination screen, and the member list with the alert absent | NOT RUN |
| Owner-web and Android parity | Repeat the rows above on both surfaces | Dates, amounts, rejection text, deposit count, and overdue visibility match. Mess still has no Short Stay control | Paired screenshots for each row | NOT RUN |

Environment required for the web rows: a browser session signed in as an owner, plus a member account for the overdue row. No browser tool is registered in this session, so those rows are `BLOCKED` as well as `NOT RUN`.

Environment required for the Android rows: a device or emulator visible to `adb devices`, running an owner build. `adb devices` in this pass exited 0 and listed no serial, so those rows are `BLOCKED`.

#### Public website

The earlier public-site result was a source search of `AcomiPublicWebsite/src`. It found no `allocateMember`, `stayKind`, `agreedTotal`, or `/occupancies` use. That result is PASS — PREVIOUSLY VERIFIED, NOT RERUN, and it is not a live-page check.

The live check, which was not run, is to open `https://www.acomi.in`, open a listing, and open a property detail. The expected result is that those pages still have no allocate action, no stay kind, and no occupancy control. Evidence would be screenshots of the listing and the detail page. This pass did not open the site because no browser tool is available. Live-page verification is `NOT RUN` and `BLOCKED`. The public site was not changed.

#### Evidence matrix

| Gate | Result | Next action |
|---|---|---|
| Approved decisions, including D-08 and D-10 | PASS — PREVIOUSLY VERIFIED, NOT RERUN | None |
| D-05, D-14, D-16, D-17, D-18 unimplemented | PASS — PREVIOUSLY VERIFIED, NOT RERUN | Product-owner approval before any of those rules is coded |
| Android typecheck and Jest, owner-web tests and typecheck | PASS — PREVIOUSLY VERIFIED, NOT RERUN | Do not treat them as a browser or device walk |
| Service ledger, 20 tests, `acomi_release_gate_0f467956f655` | PASS — PREVIOUSLY VERIFIED, NOT RERUN | Disposable database already dropped |
| Persistence, 4 tests, `acomi_release_gate_0c49c648230b` | PASS — PREVIOUSLY VERIFIED, NOT RERUN | Hand-written reverse DDL is not a Flyway undo |
| Flyway undo scripts | NOT RUN | None exist under `db/migration` |
| Human confirmation of the target database | NOT RUN | Required before any backend start |
| Database dump and disposable restore validation | NOT RUN | Missing from the production runbook. Required before authorization |
| V156–V158 on an authorized database | BLOCKED | Authorization, window, confirmed host and database name, and a validated backup. Do not start the local profile |
| V156–V158 on `acomi_db` | NOT RUN | This pass did not connect to it and does not authorize it |
| Backend startup | NOT RUN | Not started in this pass |
| Owner-web browser walk | BLOCKED | No browser tool. Checklist above is not execution |
| Android device or emulator walk | BLOCKED | `adb devices` listed no serial |
| Public website source search | PASS — PREVIOUSLY VERIFIED, NOT RERUN | Not a live page |
| Public website live page | NOT RUN and BLOCKED | A browser that can open `https://www.acomi.in` |
| Section 43 dependencies | BLOCKED | Phases 1–7 are `ACCEPTED`, not `COMPLETE` |
| Section 43 | OPEN | Phase 8 may not start |
| Phase 8 | NOT STARTED | Deployment, browser, and device evidence are still absent |

### 33.7 Browser and Android walkthrough preparation, 2026-10-30

Phase 8 stays `NOT STARTED`. Section 43 stays `OPEN`. Phases 1–7 stay `ACCEPTED` for their recorded scopes. D-05, D-14, D-16, D-17, and D-18 stay `OPEN` and unimplemented. D-08 and D-10 were not changed. No application code, schema, configuration, or customer data was changed.

Section 33.6 named the walks and left them BLOCKED. This section adds the step-by-step checklists. A checklist is not a passed runtime gate.

| Checklist | Path | How to run it |
|---|---|---|
| Owner web | `docs/implementation/ACOMI_STAY_MANAGEMENT_OWNER_WEB_WALKTHROUGH.md` | Pass W-00, then W-01 through W-24 on an authorized non-production space. Record each screenshot in that file's result log. |
| Android | `docs/implementation/ACOMI_STAY_MANAGEMENT_ANDROID_WALKTHROUGH.md` | Pass A-00, then A-01 through A-20 on an attached device or an already running emulator. Record the device model, Android version, app version, and commit. |

The safety gate in both files requires a named authorized database that is not `acomi_db` and not production, a test-owned space, and an owner or manager of that space. The owner-web example env proxies `/api/v1` to the local Spring Boot process. The code fallback is `http://localhost:8080/api/v1`. A debug Android build uses `http://10.0.2.2:8080` on an emulator and `http://192.168.1.105:8080` on a physical device. A release build uses `https://api.acomi.in`. Starting the local backend would run Flyway against the local default database `acomi_db`. That start was not performed. No test account or test space was available.

Same-key extension replay is not a control on either screen. Each open creates a new `idempotencyKey` and does not display it. A second tap while the request is pending does not send another request. Replaying a captured key with a manual client is not part of this walk. That replay row is UNVERIFIED from the UI. The server overlap sentence, the same-day sentence, and the two cross-month sentences are copied from the server classes into the checklists so the expected text is exact. They were not produced by a click in this pass.

The header back destination after **View stay** is UNVERIFIED until a device records it. The operator destination of the action itself is `DashboardOccupancyList` with `mode` `active` on Android and `/spaces/{spaceId}/occupancy?mode=active` on the web.

#### Commands in this pass

| Command or read | Exit | Result |
|---|---|---|
| `adb devices` | 0 | `List of devices attached` and no serial. An emulator was not started |
| Browser tool search | n/a | No browser, Playwright, or Puppeteer tool is registered. No page was opened |
| Read owner-web routes, wizard, extend dialog, and Android screens | n/a | Labels and paths in the two checklists |
| `mvn`, `npm`, `tsc`, `jest`, `spring-boot:run`, Flyway, `psql` | not run | Not required. The section 33.5 results stay PASS — PREVIOUSLY VERIFIED, NOT RERUN |

#### Evidence matrix for this pass

| Gate | Result | Next action |
|---|---|---|
| Owner-web checklist prepared | PASS as a document | Execute W-00 through W-24 on an authorized test space |
| Owner-web browser execution | BLOCKED | Browser session, authorized non-production API, and a test owner |
| Android checklist prepared | PASS as a document | Execute A-00 through A-20 |
| Android device execution | BLOCKED | `adb devices` listed no serial. Do not use the production API to create stays |
| Same-key replay from the UI | UNVERIFIED | The screens do not expose the key. Do not send a manual replay |
| Back destination after View stay | UNVERIFIED | Record it on a device |
| Public website live page | NOT RUN and BLOCKED | Unchanged from section 33.6. No browser tool |
| Automated tests from section 33.5 | PASS — PREVIOUSLY VERIFIED, NOT RERUN | Not a browser or device walk |
| Defects found by execution | none | No walk was executed, so no defect was fixed |
| V156–V158 on `acomi_db` | NOT RUN | The backend was not started |
| Section 43 | OPEN | Browser, device, deployment, and earlier phase `COMPLETE` marks are still absent |
| Phase 8 | NOT STARTED | This section does not start it |

### 33.8 Disposable backup and restore rehearsal, 2026-10-30

Phase 8 stays `NOT STARTED`. Section 43 stays `OPEN`. D-05, D-14, D-16, D-17, and D-18 stay `OPEN`. D-08 and D-10 were not changed. No application code or configuration was changed. The backend was not started. Flyway was not run. `V156`, `V157`, and `V158` were not applied. `acomi_db` was not a connection target.

This section records one executed rehearsal of `pg_dump` and `pg_restore`. It is not a backup of `acomi_db`, production, or any shared database. It does not authorize the owner-web or Android walkthroughs. Those walks stay BLOCKED because this rehearsal database had no application schema and was dropped.

#### Infrastructure found

| Item | Evidence |
|---|---|
| Engine | PostgreSQL. Client and server both reported 17.10 |
| Client tools | `C:\Program Files\PostgreSQL\17\bin\psql.exe`, `pg_dump.exe`, and `pg_restore.exe` |
| Maintenance connection | Host `localhost`, port `5432`, user `postgres`, database `postgres`. `SELECT current_database()` returned `postgres` |
| Password | Read from the existing `ReleaseGateIsolatedDatabase` test helper. It was not printed. `PGPASSWORD` was removed before the script exited |
| Existing disposable pattern | `acomi_release_gate_[0-9a-f]{12}` and `acomi_phase5_race_[0-9a-f]{12}`, created from the maintenance database. This rehearsal used a separate prefix so it could not drop one of those databases |
| Production backup | Still the container and env snapshot in `AcomiMobile/docs/ACOMI-PRODUCTION-DEPLOYMENT-RUNBOOK.md`. No production database dump was taken |

#### Guardrails that ran

The script refused every database name except `acomi_backup_rehearsal_` plus 12 lowercase hex characters. It also refused `acomi_db`, `postgres`, `template0`, and `template1` as rehearsal targets. A connection was allowed only to the maintenance database `postgres` or to a name that had just passed that check. `CREATE DATABASE` ran only after `pg_database` showed the name was absent. `DROP DATABASE` ran only for the two names created in this run. The dump file was deleted. The shared-database check was `SELECT count(*) FROM pg_database WHERE datname = 'acomi_db'` on the maintenance connection. That query does not open `acomi_db`.

#### Commands and results

The password is omitted from every command below. The shell set `PGHOST=localhost`, `PGPORT=5432`, and `PGUSER=postgres`.

| Command | Exit | Result |
|---|---|---|
| `psql --version`, `pg_dump --version`, `pg_restore --version` | 0 | `psql (PostgreSQL) 17.10`, `pg_dump (PostgreSQL) 17.10`, `pg_restore (PostgreSQL) 17.10` |
| `psql -d postgres -c "SELECT current_database()"` and `SHOW server_version` | 0 | Database `postgres`, server `17.10` |
| `psql -d postgres -c "CREATE DATABASE acomi_backup_rehearsal_0146d29918e4"` | 0 | Source database created |
| `psql` on that source database: create `backup_rehearsal_marker(id integer primary key, marker text not null)` and insert one row | 0 | Count 1. `flyway_schema_history` absent. `occupancies` absent |
| `pg_dump -d acomi_backup_rehearsal_0146d29918e4 --format=custom --no-owner --no-acl --file=<temp dump>` | 0 | Dump file was 1938 bytes |
| `psql -d postgres -c "CREATE DATABASE acomi_backup_rehearsal_a65a85ea6fca"` | 0 | Restore database created |
| `pg_restore -d acomi_backup_rehearsal_a65a85ea6fca --no-owner --exit-on-error <temp dump>` | 0 | `current_database()` returned the restore name. Marker matched the source row. Count 1. `flyway_schema_history` absent. `occupancies` absent |
| `DROP DATABASE` for those two names only, from the maintenance database | 0 | `DISPOSABLE_REMAINING=0` |
| Maintenance query for `acomi_db` | 0 | `ACOMI_DB_STILL_LISTED=1` |
| Delete the temp dump | 0 | `DUMP_DELETED=yes` |

The script's final line was `REHEARSAL_OK=yes` and the process exited 0. The marker value matched on both databases and is not a credential. It is not repeated here.

#### What this does not prove

- It does not prove a backup of the walkthrough database. The source database contained one rehearsal table and no ACOMI schema.
- It does not prove a backup of `acomi_db` or production. Those databases were not dumped.
- It does not provide the production `pg_dump` host, database name, or a place to store a production dump. The live production `DB_HOST` and `DB_NAME` are still unread.
- Recovery of a real target is still: restore that target's own logical dump, then start the backend image that matches the restored schema. There is still no Flyway undo. This rehearsal must not be described as that recovery.
- Owner-web rows W-00 through W-24 and Android rows A-00 through A-20 stay BLOCKED.

#### Evidence matrix for this pass

| Gate | Result | Next action |
|---|---|---|
| Local `pg_dump` and `pg_restore` on two disposable databases | PASS | Names above. Both dropped. Dump deleted |
| Flyway and V156–V158 | NOT RUN | Required before a walkthrough database exists. Not authorized by this rehearsal |
| Connection to `acomi_db` | NOT RUN | The catalog row was counted from the maintenance database |
| Production or shared database backup | BLOCKED | Need the authorized target host and database name, a deployment window, and a stored dump of that target |
| Owner-web and Android walks | BLOCKED | Unchanged from section 33.7. No application database was left behind |
| Section 43 | OPEN | Browser, device, deployment, and production backup evidence are still absent |
| Phase 8 | NOT STARTED | This rehearsal does not start it |

### 33.9 Isolated walkthrough database, 2026-10-30

Phase 8 stays `NOT STARTED`. Section 43 stays `OPEN`. D-05, D-14, D-16, D-17, and D-18 stay `OPEN`. D-08 and D-10 were not changed. No application source or committed configuration was changed.

The local profile still defaults `DB_NAME` to `acomi_db`. This process did not use that default. `DB_NAME` and `--spring.datasource.url` were both set to `acomi_stay_walk_fd1b26b24a1d` before startup. `--spring.flyway.clean-disabled=true` was set for this process only. Flyway clean was not called.

#### Commands and results

| Command | Exit | Result |
|---|---|---|
| Create database `acomi_stay_walk_fd1b26b24a1d` from the maintenance database `postgres` | 0 | `current_database()` returned that name. `acomi_db` was still listed. It was not opened |
| `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:postgresql://localhost:5432/acomi_stay_walk_fd1b26b24a1d --spring.flyway.clean-disabled=true"` with `DB_NAME` set to that database and `ACOMI_MAIL_ENABLED=false` | running | Flyway log: `Database: jdbc:postgresql://localhost:5432/acomi_stay_walk_fd1b26b24a1d (PostgreSQL 17.10)`. `Successfully applied 158 migrations`, versions 156, 157, and 158 `success = true`, zero failed migrations. `Started AcomiBackendApplication` in 44.672 seconds. Tomcat on port 8080 |
| `GET http://localhost:8080/actuator/health` | 200 | Body status `UP` |
| `pg_stat_activity` grouped by `datname` from the maintenance database | 0 | Connections were on `acomi_stay_walk_fd1b26b24a1d` only. `acomi_db` had none |
| `POST /api/v1/auth/send-otp` with purpose `REGISTER`, then `POST /api/v1/auth/register` | 200 | Local OTP skip. Owner mobile `9800000101`, name `STAY-GATE Owner`. Member accounts `9800000102` and `9800000103`. No token or password is recorded here |
| `POST /api/v1/spaces` | 201 | Active space `2f6253d0-e590-4279-a570-2ce55f3205a4`, name `STAY-GATE PG`, type `PG`, `short_stay_enabled` false. A duplicate space created by a retried call, `602291aa-1035-4ab7-8003-7e9d1a3ad2c9`, was deactivated with `DELETE /api/v1/spaces/{id}` status 204 |
| Floor, room, beds, and members through the accommodation and member APIs | 201 | Floor `bcf4d227-15f2-4761-8002-5c0767e0ce6f`, room `9128a6e1-f2fe-4ab8-a616-e59b300559a0`, Bed A `20575276-c023-495c-bd7a-c4b541042e2b`, Bed B `27798c07-ae51-49c6-a5f2-04704f4ff7d0`, members `STAY-GATE Member A` and `STAY-GATE Member B` |
| `adb devices` | 0 | Header only. No serial |
| Browser tool search | n/a | No browser tool is registered. No page was opened |

The owner password is in `acomi-backend/stay-walk.env.local`. That file matches `.gitignore` `*.env.local` and is not part of the documentation. Do not copy it into a commit.

The backend process is still running. Stop that process before starting another backend. A later start must set `DB_NAME` to `acomi_stay_walk_fd1b26b24a1d` again. Starting the local profile without that variable migrates `acomi_db`.

The database was not dropped. It is the environment for the walks. When the walks are finished, drop only `acomi_stay_walk_fd1b26b24a1d` from the maintenance database, then check that `acomi_db` is still listed. That drop was not run.

#### What remains blocked

| Gate | Result | Why |
|---|---|---|
| Isolated schema and test owner | PASS | This section. Not a UI walk |
| Owner-web W-00 through W-24 | NOT RUN | No browser tool. The API and database are ready for a person to open `http://localhost:5173` against this backend, or to use the owner web dev server with its `/api/v1` proxy |
| Android A-00 through A-20 | BLOCKED | No device or emulator. A debug build would call the local backend. A release build would call `https://api.acomi.in` and must not be used |
| Co-living room-versus-bed walk | NOT RUN | The provisioned space is PG. The co-living conflict row stays blocked until a co-living test space exists |
| Production backup and V156–V158 on a shared database | BLOCKED | Unchanged. This migration was only on the disposable database |
| Section 43 | OPEN | Browser, device, and deployment evidence are still absent |
| Phase 8 | NOT STARTED | This environment does not start it |

---

### 33.10 Owner-web browser session, 2026-10-30

Phase 8 stays `NOT STARTED`. Section 43 stays `OPEN`. D-05, D-14, D-16, D-17, and D-18 stay `OPEN`. D-08 and D-10 were not changed. The database `acomi_stay_walk_fd1b26b24a1d` was not dropped and `acomi_db` was not opened.

The owner web was started with `npm run dev` in `K:\AcomiWeb`. Vite 8.1.5 listened on `http://localhost:5173` and proxied `/api` to the already running backend on `http://localhost:8080`. `VITE_API_BASE_URL` is `/api/v1`. No second backend was started for that proxy. Playwright 1.63.0 was already present in the local npm cache and Chromium was already present under the user Playwright cache. Nothing was installed into `AcomiWeb`. The browser was headless Chromium. Screenshots are in `C:\Users\Lenovo\AppData\Local\Temp\acomi-stay-walk\evidence` and are not committed.

The signed-in user was the existing test owner, mobile `9800000101`. The password stayed in `stay-walk.env.local` and is not copied here. The PG space was `2f6253d0-e590-4279-a570-2ce55f3205a4`. A co-living space, `0d79869c-627f-42d8-a73e-5138e9253b84`, was created through the space, building, unit, room, bed, and member APIs so W-10 had a room with beds. No Mess space was created.

#### Walk results

| ID | Result | What the browser showed |
|---|---|---|
| W-00 | PASS | Health 200. A browser call to `/api/v1/spaces/my` was answered by `localhost:5173`, which proxies to the local backend |
| W-01 | PASS | Sign In opened My Spaces and the PG dashboard |
| W-02 | PASS | Monthly allocate for Member A on Bed A. `stayKind` was absent. Toast: Member allocated successfully |
| W-03 | PASS | With Short Stay off, the contract step had no Short stay button, no Until I leave button, and no agreed-total field |
| W-04 | BLOCKED | No Mess space. The PG space was not converted |
| W-05 | PASS | Billing & tax, Turn on Short Stay checked, Changes saved successfully. `shortStayEnabled` true |
| W-06 | PASS | Short stay for Member B, checkout 31 Oct 2026, agreed total 2500. Active `stayKind` SHORT |
| W-07 | PASS | Same-day checkout showed "A short stay checkout must be after the check-in date. The same date is not a stay." No stay was created |
| W-08 | PASS | Checkout 2 Nov 2026 showed "A short stay cannot cover nights in more than one calendar month." No stay was created |
| W-09 | FAIL | After W-06, Bed B was absent from the available allocate list. Opening the wizard with that bed id showed a blank page. The overlap sentence was not shown. No second stay was created |
| W-10 | FAIL | The co-living room click opened Edit room. That sheet has Save and no Allocate button, so the room-versus-bed conflict was not submitted |
| W-11 | NOT RUN | The allocate contract step has Expected move-out and no start-date field |
| W-12 | PASS | Extend stay moved Member B from 31 Oct 2026 to 1 Nov 2026. The Extend stay button was disabled while the one request was pending |
| W-13 | FAIL, then fixed | Before the fix, Extend stay to 2 Nov 2026 was saved. The bed sheet showed Expected exit date 2 Nov 2026 and rent due 3000. After the fix, Extend stay to 3 Nov 2026 was rejected with "A short-stay extension cannot cover nights in more than one calendar month." The planned end stayed 2 Nov 2026 |
| W-14 | PASS | Member A date-only extension to 15 Nov 2026 succeeded. A positive additional amount showed "Monthly and open-ended stays do not take an extension charge. Leave the additional amount empty." The end stayed 15 Nov 2026 |
| W-15 | PASS | Until I leave was allocated for Member C on Bed B. A positive additional amount was rejected with the same monthly sentence |
| W-16 | PASS | One extend request was sent. The button was disabled while it was pending. The same key was not replayed |
| W-17 | BLOCKED | The payments payload is `month` and `payments`. The opened payments page had no Received button, so a paid row was not marked or checked after vacate |
| W-18 | PASS | Vacate was used for Bed A and Bed B. Both cards then appeared on available inventory. The toast was Member vacated |
| W-19 | NOT RUN | Transfer was not clicked. The co-living beds remain vacant |
| W-20 | BLOCKED | No test stay ended before 30 Oct 2026. Notifications had no Stay still active row. Pending actions had no Stays past planned end group. No date was changed with SQL |
| W-21 | BLOCKED | Depends on W-20 |
| W-22 | BLOCKED | No test stay was planned to exit on 30 Oct 2026 |
| W-23 | PASS | Dashboard, the active-list route, payments, notifications, and My Spaces opened. The active-list route lands on occupied beds. No nightly or weekly price text was shown |
| W-24 | PASS | Turn on Short Stay was unchecked and Changes saved successfully. The three PG stays were vacated. The payments page was not opened again after the last vacate |

`.\mvnw.cmd "-Dtest=StayExtensionRulesTest,ShortStayRulesTest" test` exited 0. Tests run: 6, failures: 0. The running backend was stopped so that compile could write `target\classes`, then one backend was started again with `DB_NAME` and `--spring.datasource.url` set to `acomi_stay_walk_fd1b26b24a1d` and `spring.flyway.clean-disabled=true`. Flyway logged that database. `GET /actuator/health` returned 200. Android A-00 through A-20 stay BLOCKED. Production backup stays BLOCKED.

The extension defect was in `OccupancyExtensionService`. It asked whether the newly added nights crossed a month. A stay that started on 30 Oct 2026 could therefore be extended to 2 Nov 2026, because the added night was only 1 Nov 2026. The check now uses the stay start and the new planned end, which is the same whole-stay interval already used when a short stay is created. `StayExtensionRulesTest` records both the allowed checkout on 1 Nov 2026 and the rejected checkout on 2 Nov 2026.

#### Evidence matrix for this pass

| Gate | Result | Next action |
|---|---|---|
| Owner-web W-00 through W-24 | Mixed | Rows above. Screenshots are outside the repo |
| Cross-month short extension | FAIL, then PASS on retest | Service fix above. The 2 Nov 2026 end that was saved before the fix was left in place until vacate |
| Android device walk | BLOCKED | No serial |
| Production backup | BLOCKED | Unchanged |
| Section 43 | OPEN | Device, deployment, production backup, W-09, W-10, W-11, W-17, W-19, W-20, W-21, and W-22 are not satisfied |
| Phase 8 | NOT STARTED | This session does not start it |

---

### 33.11 Remaining owner-web checks, 2026-10-30

Phase 8 stays `NOT STARTED`. Section 43 stays `OPEN`. D-05, D-14, D-16, D-17, and D-18 stay `OPEN`. D-08 and D-10 were not changed. The database `acomi_stay_walk_fd1b26b24a1d` was not dropped and `acomi_db` was not opened. The backend process already listening on port 8080 stayed the only backend. Its command line still names that database. `GET /actuator/health` returned 200 before these checks. No second backend was started.

#### Whole-stay extension tests

`OccupancyExtensionService` rejects a short extension when `StayExtensionRules.crossesCalendarMonth(moveInDate, newEnd)` is true. Short-stay creation already calls the same helper with check-in and checkout. `.\mvnw.cmd "-Dtest=OccupancyExtensionServiceTest,StayExtensionRulesTest,ShortStayRulesTest" "-Dmaven.main.skip=true" test` exited 0 at 2026-10-30T19:04:25+05:30. Tests run: 26. Failures: 0. `OccupancyExtensionServiceTest` was 20, `ShortStayRulesTest` 4, and `StayExtensionRulesTest` 2. Main sources were not recompiled, so the running backend classes were left in place.

The service test now places the default short stay on the 10th of the following month, so a same-month extension still reaches the charge assertions. One new case sets move-in to the last day of a month, the current checkout to the 1st, and the new checkout to the 2nd. The added night is inside one month and the whole stay is not. The service throws `EXTENSION_CROSSES_MONTH` and does not save. An open-ended stay with a positive additional amount throws `AMOUNT_NOT_FOR_MONTHLY`. The overdue short-stay accept case stays inside one calendar month.

#### Walk results added in this pass

| ID | Result | What was shown |
|---|---|---|
| W-04 | PASS | A Mess space was created through `POST /api/v1/spaces`, opened on Edit Space, Billing & tax. The Short Stay checkbox count was 0. `DELETE` returned 204 |
| W-09 | FAIL, no code change | Bed B was `OCCUPIED`, move-in 2026-10-30, no planned end, and absent from the available list. The wizard rendered and Confirm showed "This target is not available." The allocate API returned 400 "Bed is not available for reservation". No second stay was created. The overlap sentence is not returned while the bed status is occupied |
| W-10 | BLOCKED for the UI action | The room name opens Edit room. The page Allocate button opens vacant beds. Room target search returned an empty page. `POST` allocate with `targetType` `ROOM` returned 201. A bed on that room returned 400 "Bed is not available". A room on an occupied bed returned 400 "Room is not available". No room-allocate control was added |
| W-11 | BLOCKED | After Vacate, a new stay on the same bed had move-in 2026-10-30 and status `ACTIVE`. The contract step has Expected move-out and no start-date field, so a start one day earlier cannot be entered |
| W-17 | PASS | Received was clicked on the ₹1,800 Short stay row, then Mark as received. After Vacate the row status was `PAID` and the collected total showed ₹1,800 |
| W-19 | PASS | Transfer was clicked. The toast was "Member transferred." The active stay was Member E on Room CL1 Bed B with no planned end. That member had one security deposit of 3000. The stay was then vacated through the API |
| W-20 | PASS | Expected move-out 2026-10-29 on an active stay. Pending actions showed Stay still active under Stays past planned end. Notifications showed the same title, type `STAY_OVERDUE_ACTIVE`, and View stay. That button opened Occupied beds at `bed-inventory?status=OCCUPIED` |
| W-21 | BLOCKED | Sign-in for mobile `9800000102` showed "Invalid mobile number or password." The owner walk password was not accepted. No other password was tried |
| W-22 | PASS | A stay with expected move-out 2026-10-30 produced Move-out scheduled today in the group Today's Move-outs. Stay still active was not used for that stay |

`adb devices` exited 0 and printed only the header. `emulator -list-avds` listed `Pixel_8`. A start request did not leave an emulator process, and a later `adb devices` was still empty. Android A-00 through A-20 were not run. Production backup stays BLOCKED.

#### Evidence matrix for this pass

| Gate | Result | Next action |
|---|---|---|
| Whole-stay short extension | PASS | 26 tests, exit 0. The browser retest from section 33.10 still stands |
| Owner-web rows still open after 33.10 | Mixed | W-04, W-17, W-19, W-20, and W-22 passed. W-09 stays a status-gate rejection. W-10 and W-11 are UI limits. W-21 needs a member login that accepts a known password |
| Android device walk | BLOCKED | No serial. The installed `Pixel_8` image did not attach |
| Production backup | BLOCKED | Unchanged |
| Section 43 | OPEN | Device, deployment, production backup, and the rows above that are not PASS |
| Phase 8 | NOT STARTED | This session does not start it |

### 33.12 Phase 8 verification pass, 2026-11-02

Phase 8 is `IN PROGRESS` and blocked for release. Section 43 stays `OPEN`. D-05, D-14, D-16, D-17, and D-18 stay `OPEN` and were not implemented. D-08 and D-10 were not changed. Database `acomi_stay_walk_fd1b26b24a1d` was preserved. `acomi_db` was not opened. Backend on port 8080 was restarted once onto that walk database with `--spring.flyway.clean-disabled=true` after a main compile. Vite stayed on `http://localhost:5173`.

#### Code fixes verified in this pass

| Area | Change | Evidence |
|---|---|---|
| W-10 ROOM search | `AllocationTargetSearchRepository.searchRoomTargets` + service case; display path omits bed for ROOM | API `targetType=ROOM` on co-living returned `totalElements=2` |
| W-10 UI detour | `allocateNeedsVacantBedPick` only forces vacant beds when the space allows BED alone; co-living keeps the wizard | Browser URL stayed on `/occupancy/wizard`; Room toggle listed Room CL1 and Room CL2 |
| W-11 allocate start | Optional `AllocateOccupancyRequest.moveInDate`; reject future allocate; UI Move-in date field | API allocate with `moveInDate=2026-09-01` returned 201 and persisted that date. UI allocate for Member D persisted `2026-09-02` |
| W-09 occupied gate | Occupied target rejected before overlap; UI surfaces unavailable | API second allocate on occupied bed returned 400 "Bed is not available for reservation". Occupied inventory with allocate intent did not open Move In today |
| W-21 member login | Existing user `9800000102` password restored through local `RESET_PASSWORD` + DEV OTP (auth not bypassed) | Member sign-in left `/login` |
| Wizard space type | Occupancy wizard prefers `getSpaceById().type` so co-living is not stuck on PG while mySpaces loads | Required for Room toggle after the detour fix |

#### Automated checks

| Check | Result |
|---|---|
| Backend targeted tests `AllocationTargetSearchServiceTest,OccupancyTargetServiceTest,OccupancyServiceTest,OccupancyExtensionServiceTest,StayExtensionRulesTest,ShortStayRulesTest` | Exit 0. Tests run: 68. Failures: 0. Skipped: 1 |
| Owner-web `npx tsc -b` | Exit 0 |
| Android focused Jest stay suites | Exit 0. 8 suites, 27 tests |
| Walk DB `pg_dump -Fc` rehearsal | File written under `%TEMP%\acomi-stay-walk\`. Size 417394 bytes. Not a production backup |

#### Walk matrix (this pass)

| ID | Result | Notes |
|---|---|---|
| W-09 | PASS | Availability rejection, not overlap sentence, while status-gated |
| W-10 | PASS | ROOM search API + Room toggle UI after detour fix |
| W-11 | PASS | Historical move-in via API and UI on dates clear of vacated history |
| W-21 | PASS | Member A login after password reset |
| W-04 / W-17 / W-19 / W-20 / W-22 | NOT RE-RUN | Prior 33.11 PASS stands; not re-clicked in this pass |
| Android A-00–A-20 | BLOCKED | `Pixel_8` AVD exists. Emulator fatal: not enough disk space on `C:` (about 0.2–1.2 GB free during attempts). `adb devices` stayed empty |
| Production deploy / `acomi_db` | NOT RUN | Unauthorized |
| D-05 / D-14 / D-16 / D-17 / D-18 | OPEN | No invented defaults |

Screenshots and `results-13.json` / `W-10-room-search.png` live under `C:\Users\Lenovo\AppData\Local\Temp\acomi-stay-walk\evidence` and are not committed.

#### Evidence matrix for this pass

| Gate | Result | Next action |
|---|---|---|
| Fixable W-09 / W-10 / W-11 / W-21 | PASS on walk DB | Keep in regression |
| Open product decisions | OPEN | Approve or explicitly defer before Phase 8 COMPLETE |
| Android device walk | BLOCKED | Free disk space or attach a physical device, then A-00–A-20 |
| Production backup / deploy | BLOCKED | Unchanged |
| Section 43 | OPEN | Decisions, Android, deploy gates unmet |
| Phase 8 | IN PROGRESS / blocked | Not COMPLETE |

---

## 34. Risks and unresolved questions

| Risk | Why it is real | Mitigation |
|---|---|---|
| Double monthly and extra rent | `buildRow` adds occupancy expected and non-monthly rent | BILL-003, Phase 2 skip, tests B1 and B13 |
| Co-living double occupancy | DEF-001 | Phase 1 |
| Web date dropped | DEF-002 | Phase 1 |
| Paid row destroyed by a refund-in-place | Upsert refuses `PAID`, but a new writer might not | PAY-002 |
| Unique index drop too early | Allows two open stays | INV-003 |
| `btree_gist` missing | No extension migration exists. Version not verified | MIG-5 blocked until checked |
| Due-date finder throws | Lookup omits `due_date` | Test C17 before relying on upsert for new flows |
| Room transfer self-conflict | DEF-001 | Phase 1 exclude-self |
| Clocks differ | JVM date vs `Asia/Kolkata` | Phase 7, do not change proration blindly |
| Stay-charges comment | Says beds, query does not | Do not add a bed filter |

Open questions are the `OPEN` rows in section 10. The ones that block coding are listed under that table.

---

## 35. Evidence appendix

Re-read on 2026-10-29. Line numbers will move. Re-read the symbol before editing.

| Item | Location |
|---|---|
| Occupancy commands | `occupancy/api/controller/OccupancyController.java` |
| Allocate, reserve, move-in, cancel, transfer, vacate, `assertTargetNotHeld`, `assertMemberHasNoCurrentOccupancy`, `closeOccupancy` | `occupancy/application/service/OccupancyService.java` |
| Target rules, room comment | `occupancy/application/service/OccupancyTargetService.java` |
| Bed and room status | `occupancy/application/service/AccommodationStatusSyncService.java` |
| Snapshots, `RENT_SNAPSHOT_REQUIRED`, transfer policies | `occupancy/application/service/OccupancyContractSnapshotService.java` |
| Access | `occupancy/application/service/OccupancyAccessService.java` |
| Entity fields | `occupancy/infrastructure/persistence/entity/OccupancyEntity.java` |
| History check | `db/migration/occupancy/V23__occupancy_lifecycle_phase_4_3a.sql` |
| Room/unit unique indexes | `db/migration/occupancy/V33__fix_room_unit_unique_indexes_by_target_type.sql` |
| Monthly end date | `dashboard/application/support/OccupancyBillingCalculator.java` `resolveOccupancyEndDate` |
| Proration | `payment/application/support/BillingAmountCalculator.java` `prorateConfigured` |
| Generation and upsert | `payment/application/service/SpacePaymentGenerationService.java` |
| Finder vs due date | `payment/infrastructure/persistence/repository/SpacePaymentRepository.java` |
| Generated unique index | `db/migration/payment/V137__manual_payment_description.sql` |
| Category check includes `EXTRA` and `REFUND` | `db/migration/payment/V138__space_payments_manual_charge_checks.sql` |
| Missing monthly heal | `payment/application/service/PaymentMonthSnapshotService.java` `needsMissingGeneratedRent` |
| Ledger composition | `dashboard/application/service/SpaceBillingService.java` `buildRow` |
| Extra totals | `dashboard/application/support/SpacePaymentLedgerSupport.java` |
| Whole-row paid | `payment/application/support/PaymentDueStatusCalculator.java` |
| Refund create rejected, manual categories | `payment/application/service/SpacePaymentService.java` |
| Stay charges query | `SpacePaymentRepository.findActiveStayCharges` |
| Payment routes | `dashboard/api/controller/PaymentController.java` |
| Deposit math and transfer comment | `member/application/support/MemberDepositSummaryCalculator.java` |
| Deposit edit | `member/application/service/MemberMasterService.java` `updateDeposit`, `validateDeposit` |
| Deposit sync onto open row | `member/application/service/MemberDepositLedger.java` `syncContractDeposit` |
| Notifications | `notification/application/service/OccupancyNotificationSyncService.java` |
| Scheduler | `notification/application/job/OperationalNotificationScheduler.java` |
| Space timezone | `meal/application/support/MealPollCloseAtCalculator.java` |
| Dashboard bed counts | `dashboard/application/service/SpaceDashboardSummaryService.java` |
| Web allocate body | `AcomiWeb/src/modules/accommodation/utils/occupancyRules.ts` |
| Mobile allocate body | `AcomiMobile/src/utils/occupancyRules.ts` |
| Tests not run | `OccupancyServiceTest`, `OccupancyBillingCalculatorTest`, `OccupancyContractSnapshotServiceTest`, `AccommodationStatusSyncServiceTest` |

---

## 36. Progress tracker

Documentation file created 2026-10-29. Version 1.7 records Phase 2 as `ACCEPTED` for the authorized scope. Phase 1 remains `ACCEPTED` for its own scope. Phases 0 and 3–8 have not started. The runs below are not a full-suite pass.

Every phase below must also record skipped tests with a reason, impact, and follow-up, per [section 43](#43-phase-readiness-gates) and [section 45](#45-mandatory-requirement-every-phase-must-include-mobile-ui-and-cross-platform-ux-alignment). An empty skip is not allowed when marking a phase complete. Backend evidence alone cannot mark an affected end-user feature complete. A platform with no product change still needs the investigation and justification.

### Phase 0 — Rules and baseline

- Status: `NOT STARTED`
- [ ] Decisions approved
- [ ] Baseline tests recorded
- [ ] Public website investigation recorded
- [ ] Web app baseline inventory recorded
- [ ] Android baseline inventory recorded
- [ ] Deviations: none yet
- Evidence:
- Review date:

### Phase 1 — Defects

- Status: `ACCEPTED` for the authorized scope. Not section 43 `COMPLETE`.
- [x] D-24 approved 2026-10-29 as allow the room. D-01 through D-23 and D-25 through D-26 remain `OPEN`
- [x] Code completed for room/bed exclusion and the allocate date alias
- [x] Migrations: none
- [x] Automated tests passed. 2026-10-29 Surefire, exit 0: `OccupancyServiceTest` 22, `OccupancyTargetServiceTest` 4, `AccommodationStatusSyncServiceTest` 3. Total 29, 0 failures, 0 errors. Not a full-suite pass.
- [x] Web tests passed. `scripts/check-occupancy-wizard-errors.mjs` passed again during the final review. Not an integration test of every web workflow.
- [x] Mobile tests passed. `occupancyErrors.test.ts`, 4 passed in an earlier run on 2026-10-29. Not rerun during the final review.
- [x] Public website: no stay form. Marketing occupancy sections only. No allocate API. No product change.
- [x] Existing web and Android layouts preserved. Request builders unchanged. Error handling uses the existing wizard and sheet patterns.
- [x] Financial reconciliation: not applicable. Monthly billing was not changed and was not part of the 29 tests.
- [x] Acceptance passed for the authorized scope recorded in section 18
- [ ] Rollback verified. Not exercised. Follow-up in section 18.
- Deviations: none in the stay rules. Build and test recovery is listed separately in section 18 and is not part of the accepted feature scope.
- Evidence: section 18 acceptance record. Owner web uses `getErrorMessage`. Android maps room or bed “not available” to unavailable, maintenance and blocked status text to those states, and the unequal planned-end sentence to `plannedEndMismatch`.
- Review date: 2026-10-29

### Phase 2 — Charge identity

- Status: `ACCEPTED` for the authorized scope. Not section 43 `COMPLETE`.
- [x] D-01, D-09, and D-23 approved 2026-10-29. D-05 deferred to Phase 4 and left `OPEN`
- [x] Code completed for the nullable columns and the `SHORT` monthly-rent skip
- [ ] Migrations applied and verified. `V156__occupancy_stay_kind.sql` is written and was not applied to a database in this run
- [x] Automated tests passed. 2026-10-29 Surefire, exit 0: 80 tests, 0 failures, 0 errors. Counts are in section 19. Not a full-suite pass
- [x] Web regression recorded. `scripts/check-stay-kind-absent.mjs` passed. No Short Stay step. Allocate body unchanged
- [x] Android regression recorded by that same source check. Not a device run. No Short Stay step
- [x] Public website: no stay form and no occupancy API. No product change
- [x] End-user billing: no screen reads stay kind. Monthly displays still use `rentSnapshot` and return nothing when it is null
- [ ] Financial reconciliation passed. The fixtures are unit tests, not a posted ledger
- [x] Acceptance passed for the authorized scope recorded in section 19
- [ ] Rollback verified. Not exercised
- Deviations: no cross-month charge. No MIG-3 index. `pricing_basis` has no value list while D-02 is `OPEN`. `EXTENDED` is allowed and not written. Allocate does not accept `stayKind`.
- Evidence: section 19 acceptance record.
- Review date: 2026-10-29

### Phase 3 — Extension and renewal

- Status: `ACCEPTED` for the authorized scope. Not section 43 `COMPLETE`.
- [x] D-11, D-12, D-13, D-15, D-22, D-26, D-03, and D-19 approved 2026-10-29. D-05 stays `OPEN` and deferred. D-14 and D-16 stay `OPEN`
- [x] Code completed for extend, the short-stay extra, free extension, and the effective-month rent change
- [ ] Migrations applied and verified. `V157__stay_extension.sql` is written and was not applied. `V156` was also not applied in this run
- [x] Automated tests passed. 2026-10-29 Surefire, exit 0: 85 tests, 0 failures, 0 errors. Counts are in section 20. Not a full-suite pass
- [x] Web extend action added on the active unit inspector, bed inspector, bed dialog, and member stay panel. Request builder check passed. Not a browser run
- [x] Android extend action added on the bed sheet, unit sheet, quick actions, bed menu, and member stay section. Request builder tests passed, 2 tests. Not a device run
- [x] B9 and B16 recorded in `OccupancyExtensionServiceTest`. An overdue extend does not vacate
- [x] Public website: no extend control. No product change
- [x] Existing allocate, Mess, and rental flows were not given an extend action. Rental uses the unit actions already on that sheet
- [ ] Financial reconciliation passed. The extra row is a unit fixture, not a posted ledger
- [x] Acceptance passed for the authorized scope recorded in section 20
- [ ] Rollback verified. Not exercised
- Deviations: a short-stay interval that crosses a calendar month is rejected. Inventory date ranges were not implemented. A positive extension amount on a monthly stay is rejected. A database race was not executed.
- Evidence: section 20 acceptance record.
- Review date: 2026-10-29

### Phase 4 — Short stay

- Status: `ACCEPTED` for the authorized scope. Not section 43 `COMPLETE`.
- [x] D-02, D-04, D-06, D-07, and D-25 approved 2026-10-29. D-05 stays `OPEN` and deferred. Scenario B is not implemented
- [x] Code completed for the space flag, the fixed total, the required checkout, the same-month limit, and one deposit
- [ ] Migrations applied and verified. `V158__space_short_stay_enabled.sql` is written and was not applied. `V156` and `V157` were also not applied in this run
- [x] Automated tests passed. 2026-10-29 Surefire, exit 0: 92 tests, 0 failures, 0 errors. Counts are in section 21. Not a full-suite pass
- [x] Web Short Stay choice added on the wizard when the space flag is on. Setting is on Edit Space. Builder checks passed. Not a browser run
- [x] Android Short Stay choice added on the wizard when the space flag is on. Setting is on Edit Space. Builder tests passed. Not a device run
- [x] Public website: no short-stay booking. No product change
- [x] Mess has no stay step. Monthly allocate still omits `stayKind`
- [ ] Financial reconciliation passed. The extra and deposit rows are unit fixtures, not a posted ledger
- [x] Acceptance passed for the authorized scope recorded in section 21
- [ ] Rollback verified. Not exercised
- Deviations: a cross-month short stay is rejected. Only one prior rent is retained, and that behavior was not changed. No allocate idempotency key. A second active stay is still stopped by the member unique index.
- Evidence: section 21 acceptance record.
- Review date: 2026-10-29

### Phase 5 — Date-range availability

- Status: `ACCEPTED` for the authorized locking-query scope. Not section 43 `COMPLETE`.
- [x] Decisions approved. D-20 keeps a null end blocking every later date. D-21 keeps one open stay per member
- [ ] PostgreSQL version and `btree_gist` checked. Not queried. MIG-5 stays blocked
- [x] Code completed for the half-open conflict query on allocate, reserve, move-in, transfer, and extend
- [ ] Migrations applied and verified. No new migration was written. `V156`, `V157`, and `V158` were not applied
- [x] Concurrent tests passed on disposable database `acomi_phase5_race_0da8530c1339` only. Surefire 2026-10-30, 5 tests, 0 failures. The database was dropped. `acomi_db` was not migrated. This is not a Spring call to `OccupancyExtensionService` and does not close Section 43
- [x] Old unique indexes still present. The replacement step was not approved
- [x] Web conflict copy tested on existing actions. `scripts/check-stay-overlap-error.mjs` passed. Not a browser run
- [x] Android conflict copy tested on existing actions. `occupancyErrors.test.ts` passed, 5 tests. Not a device run
- [x] Public website justification recorded. No date-range search or booking. No product change
- [x] Acceptance passed for the authorized scope recorded in section 22
- [ ] Rollback verified. No new constraint exists, so there is nothing to drop. Losing transactions on the disposable race database rolled back. That does not exercise migration rollback, and Section 43 stays open
- Deviations: sequence step 2 was not done. A second open row on one target still fails the existing unique indexes. Transfer checks an unbounded new stay because the new row does not copy the previous planned end.
- Evidence: section 22 acceptance record. Surefire 93 tests, 0 failures, finished 2026-10-29T23:38:57+05:30.
- Review date: 2026-10-29

### Phase 6 — Web and mobile

- Status: `ACCEPTED` for the parity recheck. Not section 43 complete.
- [x] Parity recheck only. First delivery of extend, short stay, and conflict copy already recorded in Phases 3, 4, and 5
- [x] Web tests passed. `node scripts/check-phase6-stay-refresh.mjs`, exit 0. The full `npm test` script was not run.
- [x] Mobile tests passed. Jest `occupancyStayRefresh.test.ts`, 2 passed, 0 failed, exit 0. This is a cache test, not a device pass.
- [x] Public website recheck recorded. Marketing occupancy sections do not allocate.
- [x] Displayed totals match API. The wizard labels the agreed total as a preview before save. The stored amount is the server field.
- [x] Existing layouts preserved. Inspector, full-page wizard, and Android sheets were not restyled.
- [x] Acceptance passed for this recheck. Browser and device walks were not run.
- Deviations: none in the authorized recheck. Section 43 stays open.
- Evidence: section 23 acceptance record.
- Review date: 2026-10-30

### Phase 7 — Alerts

- Status: `ACCEPTED` for the overdue-stay alert. Not section 43 complete.
- [x] Decisions approved. D-10 and D-08 are `APPROVED`. D-05, D-14, D-16, D-17, and D-18 stay `OPEN`.
- [x] No automatic vacate verified. The alert tests leave status, planned end, and `vacatedAt` unchanged, and the repository save is not called.
- [x] Scheduler timezone choice recorded. New alert uses the space business date, default `Asia/Kolkata`. Checkout-today stays on JVM `LocalDate.now()`.
- [x] Tests passed. Surefire 20 tests, 0 failures, exit 0, finished 2026-10-30T01:21:58+05:30. Web script exit 0. Android Jest 20 passed. No database, browser, or device pass.
- [x] Web alert surface implemented. Separate deep link and occupancy category. Kept out of the checkout-today filter. Tenants do not see it.
- [x] Android alert surface implemented. Same routing and owner-only filter. Jest covers the route.
- [x] Public website justification recorded. No owner inbox and no allocate API. Unchanged.
- Deviations: none in the authorized alert. Section 43 stays open. Phase 8 was not started.
- Evidence: section 24 acceptance record.
- Review date: 2026-10-30

### Phase 8 — Release

- Status: `IN PROGRESS` / blocked. Stay Management engine covers PG/Hostel/Co-living/Rental/Corporate Stay with Monthly/Short/Until I Leave (v1.33–1.35). Cross-type unit suites PASS (mobile 30, web 12, OccupancyBillingCalculatorTest including ₹250×28/30/31). Full space-type × Allocate/Reserve/Transfer browser and Android device matrix still NOT RUN. Do not mark COMPLETE. Evidence: `STAY_MANAGEMENT_CROSS_SPACE_TYPE_IMPLEMENTATION.md`.
- [ ] Section 33 complete, including section 45 rows. Deployment on `acomi_db`, a posted cross-month Scenario C extra, a browser walk, and a device walk stay open. Android typecheck is now exit 0.
- [ ] Public website smoke recorded. Source search found no allocate or stay-kind API. Section 33.6 found no browser tool, so the live page is still NOT RUN.
- [ ] Web journey pass recorded. Stay scripts exited 0. `npx tsc -b` exited 0. `npm test` exited 0. Section 33.7 adds the click checklist. No browser walk.
- [ ] Android journey pass recorded. Jest stay suites exited 0. `npx tsc --noEmit` exited 0. Section 33.7 reran `adb devices` and again listed no device.
- [ ] No included end-user feature closed from backend evidence alone
- [ ] Financial reconciliation passed. Service-posted rows cover section 33.4. The cross-month short extra was rejected and was not created. A same-month one-night extra was created.
- [ ] Rollback plan reviewed. Section 33.8 rehearsed `pg_dump` and `pg_restore` on two disposable databases, then dropped them. No production dump was taken. There is no Flyway undo.
- Deviations: D-05 still rejects a cross-month short-stay extension. Open decisions stayed unimplemented.
- Evidence: section 33.9.
- Review date: 2026-10-30

---

## 37. Consumer and impact map

Search date 2026-10-29. "No change" means the stay phases must not alter the consumer's current contract. A later phase may still need a regression test because it shares a table.

| Consumer | Current behavior | Expected after the relevant phase | Proposed change | Regression test | Phase |
|---|---|---|---|---|---|
| `OccupancyController` / `OccupancyService` | Six commands plus reads. See section 15 | Extension route in Phase 3. Allocate accepts both end-date names in Phase 1 | Only those | A1, F1, F2, B5 | 1, 3, 4 |
| `OccupancyTargetService` | Target type by space. Room rules unfinished | Room/bed mutual exclusion | `assertTargetNotHeld` and transfer exclude-self | E1–E12 | 1 |
| `AccommodationStatusSyncService` | Status from any active/reserved row, not dates. Room occupied only when all bookable beds are, or a room-target stay exists | Do not mark every bed occupied because one bed is | No status rewrite in Phase 1 except what the new conflict check requires | E3, room with one occupied bed stays not fully occupied | 1 |
| `AccommodationOccupancyAdapter` | Bed/room/unit delete and history checks use active or reserved rows | Same hold until vacate. Date ranges must not let a delete succeed while a future reservation exists | Phase 5 conflict check must include this port | Delete bed with `RESERVED` stay still fails | 5 |
| `BedService` | Refuses status change while the bed has active or reserved occupancy. Responses include `findBedOccupant` | Same. A future non-overlapping stay, once allowed, still blocks a status change for its dates | Do not treat planned end as vacant | Status change rejected while stay is active | 1, 5 |
| `OccupancyContractSnapshotService` | Locks monthly snapshot. `RENT_SNAPSHOT_REQUIRED` | Short stay uses `agreed_total`, not `rentSnapshot`, as the monthly input | Phase 2 | B13 | 2 |
| `OccupancyChargeSnapshotEntity` | Contract lines only. Payment generation does not read them | Still not dues unless a later decision creates payment rows | Do not double-count them as extras | Contract line absent from ledger | 2 |
| `OccupancyBillingCalculator` / `BillingAmountCalculator` | Monthly proration until `vacatedAt` | `SHORT` skipped. Formula otherwise unchanged | Phase 2 | A14, B13, existing billing tests | 2 |
| `SpacePaymentGenerationService` | Rent and deposit on activation and sync. Mess excluded | No monthly rent and no second deposit for extension or short stay | Skip + do not call from extend | B1, B12, C13, E12 | 2, 3, 4 |
| `PaymentMonthSnapshotService.needsMissingGeneratedRent` | Heals a monthly row from a positive snapshot | Ignores `SHORT` | Same skip | B13 | 2 |
| `SpaceBillingService.buildRow` | Occupancy expected, else monthly rows, plus extras and deposits | Fixed total once. Extra once. No monthly rent for short stay | Phase 2 | B1, B15 | 2, 4 |
| `SpacePaymentLedgerSupport` | Extras include non-monthly rent | Extension row included once | No second adder in the UI | B15 | 6 |
| `PaymentDueStatusCalculator` | Whole-row paid or zero. Business date is space-local | Unchanged until D-18 | Do not emit `PARTIALLY_PAID` | C1, C7 | Baseline |
| `SpacePaymentService` manual create, proof, review, received | Whole-row settlement. Refund category rejected. Second security deposit blocked unless `confirmDuplicateDeposit` | Extension does not use manual create. Received stays idempotent when already `PAID` | None for partials | C2, C3, C16 | 3 |
| `PaymentController` | List, stay-charges, summary, ledger, owner-month, overdue, reminders, sync, proof, review, received, timeline | Stay-charges must keep returning unit occupancies | No bed filter | E9, DEF-004 | 6 |
| `SpaceDashboardSummaryService` | Financial snapshot from the payment month. Accommodation counts are bed statuses. Mess branch does not build accommodation ops | Reserved beds stay out of occupied. Rental has no beds in this KPI | Do not add rooms to rental to make the KPI non-zero | Dashboard count test | 6 |
| `SpaceDashboardController` `/api/v1/spaces/{spaceId}` | Serves that summary | Same | None | | 6 |
| `OccupancyNotificationSyncService` | Same-day move-in, reservation start, planned move-out. Inside or beside occupancy writes | Phase 7 adds overdue-active alert and must not vacate | New alert only | A9, D10 | 7 |
| `OperationalNotificationScheduler` | Daily, JVM cron, flag `acomi.notifications.ops-scheduler.enabled` | Can host the overdue scan | No new framework | Job does not call vacate | 7 |
| `PaymentNotificationSyncService` / reminder scheduler | Overdue and proof events from payment rows | Extension row with a due date becomes eligible like any other unpaid row | Set due date on the extra row | C1 includes an extra row | 3 |
| `MealOccupancyBridgeService` | On activate, optional meal participation when food is on. On vacate, stops participation | Short stay and extension must not create a second participation. Extension does not call activate | Vacate still stops meals. Extend does not | Food stay creates one participation. Extend does not add another | 3, 4 |
| `MealParticipation` backfill `V38` | Historical SQL only | Do not edit | None | | |
| `MemberDepositLedger` / `MemberDepositSummaryCalculator` / `MemberMasterService.updateDeposit` | Snapshot is the contract. Paid security rows are cash. Refund is a member field | Extension does not add a security row | None | C9–C16 | 3 |
| `MemberController` deposit route | PUT deposit | Unchanged | | C15 | |
| `Gender` check inside allocate, reserve, move-in, transfer | Existing validator | Extension does not reassign the member | No change | Gender mismatch still rejects allocate | 1 |
| Web wizard, inspector, payments, members, dashboard | Same APIs. Allocate drops planned end (DEF-002). Partial label exists but backend cannot set it | Phase 1 date field. Phases 3–5 ship the matching web actions. Phase 6 rechecks refetch and totals | See section 30 and section 45 | F1–F6 | 1, 3, 4, 5, 6 |
| Mobile wizard, sheets, payments, members, dashboard | Allocate sends `expectedCheckoutDate`. Same partial label | Same phase as the web action. Not deferred to Phase 6 | See section 45 | F2, F4 | 1, 3, 4, 5, 6 |
| Public website | Marketing only. No allocate API found | No stay UI. Every phase records the investigation | Explicit non-goal | | 0–8 |
| Admin bulk property import | Creates registration/space data and parses price text such as per bed or per month. It does not create occupancy rows | Must not gain a stay import in this roadmap | None | Import still creates no `occupancies` row | |
| Admin notification test catalog | Can list occupancy notification types, including `EXPIRED_RESERVATION` | Do not treat the catalog as a live publisher | None | | |
| `SharingOccupancy` | Excel sharing buckets for listing text, not a stay | Do not reuse the name for stay kind | None | | |
| Tests named in section 29 | Cover today's lifecycle and proration | Extend, do not replace, those classes | New tests per scenario | | All |
| Feature flags | No stay flag. Mail and OTP use `@ConditionalOnProperty`. Scheduler has `acomi.notifications.ops-scheduler.enabled` | No new flag service. Short Stay is rejected when no basis is configured | Server rule, not a flag | `SHORT` without basis fails | 4 |
| Deploy | Flyway on backend startup. See section 41 | Migrations are additive and ordered | | Flyway history check | 8 |

Indirect consumers that only display occupancy status or a target label (`OccupancyTargetLabelBuilder`, member occupancy panels, history screens) need a regression when the phase adds a nullable JSON field, and again in Phase 6. No calculator change belongs in those screens.

---

## 38. Charge, settlement, and reconciliation model

The schema does not have a separate charge ledger. One `space_payments` row is both the obligation and the settlement record.

| Concept | What the schema actually is | What it is not |
|---|---|---|
| Stay | `occupancies` row | Not a bill |
| Obligation | `space_payments.amount` plus type, category, month, due date, and, for generated rows, billing period | Not `OccupancyChargeSnapshotEntity` |
| Settlement | `payment_status` on that same row. `paidAmount` in the API is derived: full amount or zero | Not a payment allocation table. Not a stored `paid_amount` column |
| Deposit liability | `deposit_snapshot` on the stay, plus `DEPOSIT` / `SECURITY` rows, plus member `depositPaid` / `depositRefunded` | Not an automatic unused-night refund |
| Refund or adjustment | Member `depositRefunded` and member history. Category `REFUND` cannot be created. A rent change is a new row, or a pending-row upsert that does not touch `PAID` | Not an edit of a paid row |
| Reporting period | `space_payments.month` and the ledger's `YearMonth`. Fixed cross-month totals need D-05 so the amount is not added in two months | Not the occupancy date range by itself |

Identification today:

- Generated rent: space + member + month + `RENT` + `MONTHLY` + due date (`uq_space_payments_generated_period`).
- The Java upsert finder omits due date. Two due dates for one month are unsafe (scenario C17).
- `RENT` / `EXTRA` has no unique key. Duplicate extras are possible until MIG-3.
- Deposit: the same generated unique index, plus the manual `DEPOSIT_ALREADY_EXISTS` check. The generator does not use that check. A later month can insert another security row. Extension must not take that path.
- Idempotency key: none on occupancy or payment.

Settled rows stay intact because `upsertPayment` returns without writing when status is `PAID`, `UNDER_REVIEW`, or `PROOF_UPLOADED`. Any new writer must copy that guard. Adjustments are additional rows with their own period. History is the payment timeline insert on create, not an update of the old timeline event.

Screens and reports that show money, all fed by the ledger or by row status:

| Surface | Figures |
|---|---|
| `GET /payments/summary`, `/ledger`, `/members`, `/owner-month` | expected, collected, pending, under review |
| `GET /payments/overdue`, `/reminder-eligible` | due date before the space-local business date and not `PAID` |
| `GET /payments`, `/stay-charges`, `/{id}` | row amount, derived paid, derived outstanding, settlement status |
| Web and mobile dashboard financial widgets | snapshot `expectedCharges`, collected, pending |
| Web and mobile member dues | ledger row fields |
| Occupied bed/unit payment face | stay-charges rows and `occupantPaymentSummary` |
| Member deposit block | `MemberDepositSummaryCalculator` |
| No accommodation export writer was found | Do not assume a CSV includes extras |

Smallest model change if short stay and extension are approved:

1. Nullable stay kind, basis, and `agreed_total` on `occupancies` (MIG-1).
2. Period dates on the extra row, using columns that already exist.
3. Partial unique index so one occupancy cannot have two extras for the same period (MIG-3).
4. Idempotency storage for the extend request (MIG-4).
5. No paid-amount column and no refund-row writer in the initial release.

That is enough only if `SHORT` is excluded from monthly generation and from occupancy-derived expected charges. A new ledger table is not required for the scenarios in section 12.

---

## 39. Date, calendar, and timezone policy

**Verified storage**

| Value | Java | Database |
|---|---|---|
| `move_in_date`, `expected_exit_date`, `expected_checkout_date` | `LocalDate` | `DATE` (`V20`, `V23`) |
| `actual_move_in_at`, `vacated_at`, `allocated_at`, `reserved_at`, `pricing_locked_at` | `LocalDateTime` | `TIMESTAMP` (without time zone in those migrations) |
| Payment `due_date`, `billing_period_start`, `billing_period_end` | `LocalDate` | `DATE` |
| Space `timezone` | string | `VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata'` (`meal/V78__poll_close_at_and_defaults.sql`) |

`DATE` has no zone. `TIMESTAMP` without time zone stores the JVM local civil time that `LocalDateTime.now()` produced. It does not store an offset.

**Verified clocks**

- Occupancy "today" and the move-out notification use `LocalDate.now()` with no zone.
- Payment overdue and `PaymentDueStatusCalculator.businessDate` use `MealPollCloseAtCalculator.nowInSpace`. Invalid or blank `space.timezone` falls back to `Asia/Kolkata`.
- `OperationalNotificationScheduler` cron is JVM cron, default `0 15 8 * * *`. The JVM zone is not set in `application.yml`. **Unknown** in production.
- `application.yml` has no custom Jackson date format. Spring Boot's default Java time module writes `LocalDate` as `YYYY-MM-DD` and `LocalDateTime` as an ISO local date-time without an offset. Clients that send those strings match the DTOs. Clients that send an instant with a `Z` suffix are not what these DTOs declare.

**Authoritative rule, proposed and not yet a code change**

- Accommodation dates that owners pick (check-in, planned checkout, payment due date, billing period) stay date-only. Do not convert existing `DATE` columns to timestamps.
- Instants (`actualMoveInAt`, `vacatedAt`) stay local timestamps. Do not reinterpret historical rows by applying a new zone. A conversion would change `vacated_at::date` and therefore monthly proration. That is forbidden unless a decision and a migration test prove every stored instant was written in a known zone.
- New occupancy alerts should use the space timezone for the calendar date, matching payment overdue. That is a Phase 7 choice. It must not change `prorateConfigured`.
- Availability, if D-19 is approved, uses date-only half-open ranges. Billing of monthly rent keeps inclusive calendar days inside the month, ending on the `vacatedAt` date. These two conventions are allowed to differ. Tests must show a 1 Nov–8 Nov short stay is 7 nights for the stay charge and that a monthly tenant vacated on 8 Nov is still 8 inclusive days.
- Extension validation uses the space business date from the payment clock, as written in section 15. That does not change the monthly inclusive period and does not vacate. D-26 remains `OPEN`. D-03 and D-19 remain `OPEN` for the short-stay and extension period columns in section 12.4.

**Tests to add with the phase that first depends on them**

| Case | Assert |
|---|---|
| Check-in stored as `2026-11-01` | Column is that date regardless of the client's offset |
| Planned end `2026-11-08` | Not vacated. Inventory unchanged at 00:30 on 9 Nov |
| Month end 31 Jan to 1 Feb | February row follows the monthly formula only for monthly stays |
| 31 Dec to 1 Jan | Same, across the year |
| 1 Feb–1 Mar in a leap year and a non-leap year | `daysInMonth` is 29 or 28. Short-stay total does not use that divisor |
| Scheduler run at 00:10 Asia/Kolkata while the JVM date is still the previous day | Document which date the job used. Do not vacate |
| Web and mobile send `YYYY-MM-DD` | Server stores that date, not the previous or next day |
| End before start | Reject |
| Same-day | Reject while D-04 stands |
| Half-open availability vs inclusive billing | Separate assertions. Do not force them onto one formula |

Do not ship a Jackson or Hibernate timezone change in these phases.

---

## 40. Transactions, idempotency, and failure recovery

`OccupancyService` command methods are `@Transactional`. `createActivationPayments` joins that transaction (`REQUIRED`). **Inferred:** if rent insert fails, the occupancy insert rolls back. This was not executed.

There is no row version and no occupancy `SELECT FOR UPDATE`. Two allocates can both pass `assertTargetNotHeld`. The unique index then fails one of them. `GlobalExceptionHandler` maps `DataIntegrityViolationException` for the known occupancy index names to "Bed/Room/Unit is not available" or "Member already has an active or reserved occupancy", HTTP 409. Other constraint names return a generic conflict. The handler logs the exception message server-side. Phase 3's new unique index needs a mapped message so the client does not only see the generic text.

UI disabling is not idempotency. Current recovery:

| Operation | After a successful commit and a lost response | After a failure before commit | Two devices |
|---|---|---|---|
| Allocate | Second call hits the member or target unique index and returns 409. The stay exists. Client recovery is `GET` member occupancies, not a second insert | No row | One row |
| Reserve | Same | No row | One reservation |
| Move-in | Second call fails because status is no longer `RESERVED` | Stay stays `RESERVED`, no activation payments | One promotion |
| Cancel | Second call fails because it is not `RESERVED` | Stay stays `RESERVED` | One cancel |
| Transfer | Second call fails because the original id is not `ACTIVE`. A new occupancy already exists | Original stays `ACTIVE` if the transaction rolled back | One new stay. The self-conflict on same-room transfer is DEF-001 |
| Vacate | Second call fails "not active" | Stay stays `ACTIVE` | One vacate if the index or status check serializes them. Two concurrent vacates of the same row are last-commit on the same entity. Acceptable only because both set `VACATED`. Do not treat that as an extension design |
| Monthly sync | Upsert updates a pending row or skips a paid row | No partial rent row from a rolled-back activation | Two syncs can race on insert. The generated unique index stops a second identical due date. The finder can throw if due dates differ |
| Manual payment edit vs sync | Sync will not overwrite `PAID`, `UNDER_REVIEW`, or `PROOF_UPLOADED`. It can overwrite `PENDING` and `REJECTED` | | Do not run extension amounts through this upsert |
| Notification after commit | If the notification write is in the same transaction, its failure rolls the stay back. Phase 7 must confirm this before adding work inside `allocate`. Scheduler notifications run later and must not vacate | | |

Extension, which does not exist yet, must be one transaction: lock the occupancy, compare the idempotency key, update the date, insert history, insert at most one charge, commit. Failure after any of those writes rolls all of them back. A retry with the same key returns the first result. A retry with a new key and the same period hits MIG-3. Extension versus vacate: the loser sees the committed status and writes nothing. Extension versus a new reservation is Phase 5 and must use the same lock.

Payment `markReceived` is idempotent when the row is already `PAID`: it returns the row and does not change the amount. Proof submission is not a stay operation.

Duplicate HTTP allocate is safe only because of the unique indexes, and the client sees a conflict rather than the original success body. That is acceptable for allocate. It is not acceptable for extension, because a conflict on the second request could hide whether the charge was created. The extend response for a replay must be the original success.

---

## 41. Deployment compatibility and rollback

**Verified from** `application.yml` and `AcomiMobile/docs/ACOMI-PRODUCTION-DEPLOYMENT-RUNBOOK.md` (runbook last describes a 2026-09-18 release shape). This roadmap did not deploy anything.

- Backend migrations run when the backend process starts. `ddl-auto` is `validate`. `flyway.clean-disabled` is true in `application.yml` and `application-prod.yml`. `application-local.yml` sets `clean-disabled` to false and defaults the database name to `acomi_db`. Section 33.6 forbids starting the local profile for this release. The local file was not changed. Manual SQL during a normal deploy is forbidden by the production runbook.
- The runbook's database client image is `postgres:16-alpine`. That does not prove the server version. `btree_gist` remains **unknown**.
- No GitHub Actions workflow directory was found in `acomi-backend`. Release is the runbook: image, then health and Flyway, then web, then public site, then mobile AAB and Play Console.
- Order in the runbook: backend, web, public site, mobile. Stay work must keep that order. Clients that send `stayKind` ship only after the migration has started cleanly.
- Web is a static build (`VITE_API_BASE_URL` pointing at `https://api.acomi.in/api/v1` in the runbook). Mobile rollback is Play Console, not a database rollback.
- Backend rollback to an older image after a newer Flyway version has been applied can fail Hibernate validation. The runbook says to verify the digest against migration state. There is no down migration.

| Change | Additive? | Old client | Rollback after new rows exist |
|---|---|---|---|
| Phase 1 date alias | Yes. Extra request name | Mobile already works. Web starts persisting the date | Code revert. Stored dates remain. Safe |
| MIG-1 nullable columns | Yes | Omit fields. Null means monthly | Code can ignore the columns. Dropping them is unsafe after `SHORT` rows exist. Forward fix: keep the skip logic |
| MIG-2 history check | Constraint change | Old events still allowed | Restoring the old check fails if any `EXTENDED` row exists. Forward fix: leave the new value in the check |
| MIG-3 extra uniqueness | Yes, stricter | Old extras with duplicate periods would fail the migration. Check for duplicates before applying | Dropping the new index is safe. It does not delete rows |
| Extend route | Additive path | Old apps never call it | Remove the route in a forward release. Do not delete charges |
| `SHORT` rejection when the skip is deployed | Server-side | Old apps do not send `SHORT` | Rejecting `SHORT` after rows exist needs the skip to stay, or those rows gain monthly rent. Do not roll the skip back |
| MIG-5 exclusion | Additive until old unique indexes are dropped | No client change while indexes remain | Drop only the new constraint. Do not drop the old indexes in the same release |
| Phase 6 parity recheck | Client-only recheck after earlier phases already shipped their screens | Older binary keeps the monthly wizard | Redeploy the previous web SHA. Play staged rollout for mobile. Server keeps the data |

Release stops, from the runbook and this work: Flyway failure, Hibernate validation failure, a ledger test that shows a short stay with a `RENT/MONTHLY` row, a deposit count that increased on extend, or a Co-living room and bed both active. Do not repair those with manual SQL.

No stay feature flag exists. Disable Short Stay by rejecting `stayKind=SHORT` in the service. Disable extension by not exposing the route. The scheduler flag `acomi.notifications.ops-scheduler.enabled` can stop the daily occupancy scan. It does not roll back data.

---

## 42. Observability and support

**Verified today**

- Occupancy commands log space id, member id, and caller id at info.
- `GlobalExceptionHandler` logs the integrity exception and returns a mapped sentence for known occupancy indexes.
- Payment snapshot rebuild logs `payment_snapshot_rebuild_start` and `payment_snapshot_expected_rent_heal` with space id and month.
- Notification scheduler logs a start line and per-space failures.
- No HTTP correlation id or MDC request id was found in the occupancy or payment path. Do not block the phases on building one. Include occupancy id in extension logs so support can search.

**Add only these signals, with space id and occupancy id, and without phone, email, proof URL, or payment remarks:**

| Signal | When | Level |
|---|---|---|
| `stay_extend_replay` | Same idempotency key | info |
| `stay_extend_conflict` | Unique period or inventory conflict | warn |
| `stay_extend_rejected_vacated` | Status was not `ACTIVE` | info |
| `stay_charge_duplicate_prevented` | Unique index on the extra row | warn |
| `ledger_short_stay_monthly_row` | A `SHORT` stay has a `RENT/MONTHLY` row | error. Release-stop |
| `deposit_row_created_on_extend` | Extension path inserted `DEPOSIT` | error. This path must be unreachable |
| `occupancy_constraint_conflict` | Existing 409 mapping | already logged |
| `ops_scheduler_space_failed` | Existing per-space catch | keep |
| Overdue active count | Phase 7 summary | info. Not a vacate |

Admin diagnostics stay the existing occupancy GET and payment timeline. The notification test catalog is not a production replay tool. Do not add an admin stay editor in this roadmap.

---

## 43. Phase readiness gates

Sections 17 through 25 are the phase bodies. A phase is not complete when the build succeeds or a screen looks right. Before changing a phase to `COMPLETE` in section 36, record all of the following. If a test is skipped, write the reason, the user-visible impact, and the follow-up. A blank skip is a failed gate.

| Gate | Required evidence |
|---|---|
| Decisions | Every decision that the phase lists as blocking is `APPROVED` or the tracker says it was deferred and the phase text does not implement it |
| Dependencies | Earlier phases in the chain are `COMPLETE` |
| Scope | PR lists the files from that phase and states the non-goals |
| Schema and API | Change matches section 16 and 41. Old allocate body still works |
| Automated tests | Named tests ran. Output or CI link stored in the tracker. Concurrent tests where section 26 says DB concurrent |
| Web and mobile | Every phase records [section 45](#45-mandatory-requirement-every-phase-must-include-mobile-ui-and-cross-platform-ux-alignment) for the public website, web app, and Android app. A platform with no product change still needs the investigation, the justification, and the user-visible impact. "No UI" with a blank note fails the gate. Backend tests alone cannot mark an affected end-user feature complete |
| Ledger | Required when the phase writes or skips charges. Numbers from section 12 |
| Acceptance | Every checkbox in that phase's acceptance list |
| Rollback | The rollback paragraph was reviewed against rows that exist after the test |
| Limitations | Open decisions and skipped tests copied into the tracker |

Phase 0's only evidence is the decision table and a baseline log. It still may not be marked complete with an empty baseline note.

---

## 44. Consistency review

Checked against sections 10, 12, 15, 16, 17–25, 26, and 36–45 on 2026-10-29 for version 1.4. No application code was changed. No product tests were executed. Findings below are documentation checks, not test runs.

| Rule | Where it is enforced in this document | Contradiction found? |
|---|---|---|
| Short stay is not also monthly rent | BILL-003, Phase 2 skip, scenarios B1 and B13, ledger error signal | No. The design forbids storing the total in `rentSnapshot` |
| Monthly renewal is not a second extra | EXT-003, scenario D, Phase 3 step 5 | No, if D-11 stays "no extra for a normal next month". If D-11 is later changed to always add an extra, February double-bills. That decision is `OPEN`. Phase 3 is affected |
| Extension retry is one charge | EXT-005, MIG-3, MIG-4, scenario D4 | No, once those migrations exist. They do not exist yet |
| Paid rows stay | PAY-002, upsert skip, the Phase 3 paragraph after the behavior list | No |
| No second deposit on extend or renew | PAY-003, EXT-002 | No, as long as extend does not call activation. A new allocate after vacate can still create a deposit. That is a different stay |
| Room and bed do not overlap | INV-001, Phase 1 | Phase 1 service exclusion, including transfer exclusion, is `ACCEPTED` for the authorized scope. No database exclusion constraint was added. Not a full-suite claim |
| Rental and Corporate Stay are units | INV-005, E9, E10 | No |
| Mess has no accommodation rent | INV-004, E12 | No |
| Legacy null kind stays monthly | STAY-002, D-23, A14 | D-23 is `APPROVED`: no backfill. Null stays monthly. Phase 2 does not guess Short Stay from old dates |
| Date-range work does not drop indexes early | INV-003, Phase 5 steps 1–2 | No. Non-overlapping second stays stay impossible until a later approved index change |
| Web and mobile show server amounts | API-003, section 30, section 45, F4 | Phase 1 binds allocate `expectedExitDate`. Service tests cover the alias. JSON binding has no controller test. F4 is first required in Phase 4. Phase 6 rechecks it |
| Extend refresh | F5, Phase 3 platforms, Phase 6 acceptance | First delivery is Phase 3. Phase 6 rechecks cache keys. The scenario phase column now says that. It does not defer the Android action |
| Identical screens | Section 45 | No. Web keeps the inspector and full-page wizard. Android keeps the wizard and bottom sheets. Mess stays without stay controls |
| Partial payments | PAY-001 | UI can display `PARTIALLY_PAID` if the status is sent. The calculator cannot send it today. Phase 6 must not start sending it. This is a display trap, not a second ledger |
| Clocks | Section 39 | Occupancy notification dates and payment overdue dates can differ around midnight. Phase 7 still has to record the alert clock. Not resolved by picking a zone in this file. Extension validation uses the existing payment business date and does not change proration |
| Overdue extension date | Sections 10, 15, 26, 29, 36, 39, 44, 45, D-26, B9, B16, Phase 3 | The comparison is written. D-26 is `OPEN`, so Phase 3 must not code it. Success stays `ACTIVE`. Rejection does not vacate. D-10 stays `OPEN` and is not implemented by this rule |
| Billing-period bounds | Section 12.4, Scenarios C and C2, B7, ledger item 6 | Monthly rows are verified inclusive. Short-stay and extension rows are proposed as start inclusive and end exclusive. C2 expected is 10000 and collected is 5600. No shared night. D-03 and D-19 are `OPEN`. Phases 3 and 4 must not write those period columns until they are approved. Monthly proration is unchanged |
| Phase 3 entry | Sections 10 and 20 | Extension behavior waits on D-26, D-03, and D-19 together, plus D-11, D-12, D-13, D-15, D-22, and Phase 2. D-24 is `APPROVED`. D-01 through D-23 and D-25 through D-26 remain `OPEN` |
| `btree_gist` | MIG-5 | Unknown. Phase 5 cannot require the constraint until the server is checked |
| D-05 cross-month month bucket | Scenario B | `OPEN`. The invariant "counted once" is required. Which ledger month shows it is not chosen. Phase 4 is affected |
| D-24 maintenance bed vs room | Phase 1 | `APPROVED` 2026-10-29: allow the room. A child bed in `MAINTENANCE` or `BLOCKED` does not reject a room stay |
| Mobile delivery | Sections 17–25, 36, 43, 45 | Each phase has its own platform tasks. Backend evidence alone cannot complete an affected end-user feature. Phase 6 is a recheck |
| Release gate | Sections 33, 36, 43, 45 | Version 1.23: Phases 1–6 are `ACCEPTED` for recorded scopes. Phase 7 is `ACCEPTED` for the overdue alert. Phase 8 is `NOT STARTED`. Section 33.10 does not close Section 43 |

Phase 1 and the Phase 2 monthly-rent skip are `ACCEPTED` for those scopes. D-05 stays deferred, so the cross-month charge is not implemented. Not safe to implement while their blocking rows are `OPEN`: Phases 3, 4, and 5. Phase 3 extension behavior does not start until D-26, D-03, and D-19 are `APPROVED`, in addition to its other blockers. Those three decisions remain `OPEN`.

No application code, schema, or configuration was changed for versions 1.1 through 1.4. Version 1.6 is a documentation update of the Phase 1 acceptance record. It does not start a later phase.

---

## 45. Mandatory Requirement: Every Phase Must Include Mobile UI and Cross-Platform UX Alignment

This requirement applies to Phases 0 through 8. It does not add a product feature by itself. It decides when a phase is allowed to be called complete.

Editing this roadmap, including this section, does not implement application code, APIs, database migrations, configuration, or tests. A later phase prompt is what implements a phase. Do not treat a documentation edit as permission to change those areas.

Mobile implementation belongs in the phase that changes the user-visible behavior. Phase 6 rechecks parity. It is not a holding area for Android work that earlier phases skipped. A platform that truly needs no change still needs a written investigation and justification in the progress tracker. A silent skip fails the phase.

The server remains authoritative for dates, prices, conflicts, deposits, and inventory. Clients may preview. Confirmation shows the stored result. Web and Android layouts stay different where they already differ: the web app uses a full-page wizard and an inspector; the Android app uses a step wizard and bottom sheets for beds and units. Do not force those surfaces to look the same. Mess does not gain stay controls. Rental and Corporate Stay stay on units, not beds. PG, Hostel, and Co-living bed actions stay on the bed sheet. Co-living room actions stay on the room flow that already exists. Do not add a bed step where the target is a unit.

The seven sections below are the requirement. The feature parity matrix and the mandatory phase completion gate are part of it. Phase bodies in sections 17–25 keep their own platform tasks. This section does not replace those tasks.

### 1. Backend and database

- Include the schema, API, and service change in the phase that owns the rule.
- Keep old allocate bodies working. New fields stay optional until the client phase that sends them.
- Do not drop occupancy unique indexes in the same change as the first overlap check.
- Do not rewrite `PAID`, `UNDER_REVIEW`, or `PROOF_UPLOADED` rows.
- A backend change that affects what an owner or resident sees is not complete until the platform tasks in this section are recorded.
- If the phase has no schema or API change, write that in the tracker. Do not leave the backend line blank.

### 2. Public website assessment and implementation

- Inspect the public website in every phase.
- The current public site shows marketing occupancy content. It does not allocate, reserve, extend, or check out.
- Do not add a public booking flow in this roadmap.
- When the finding is still "no stay form," write the pages inspected and the justification. That note is the phase evidence.
- Do not treat "out of parity scope" in section 30 as permission to skip the note.

### 3. Web app assessment and implementation

- Inspect the owner web app in every phase: wizard, entity inspector, payment list, and dashboard where that phase can change them.
- Ship the web control in the same phase as the API it calls. Extend ships in Phase 3. The Short Stay step ships in Phase 4. Conflict copy ships in Phase 5.
- Keep the full-page wizard and the inspector. Do not rebuild them as bottom sheets.
- Preserve monthly allocate, rental unit-only flow, and Mess screens.
- If a phase must not change the web UI, record the screens inspected and why they stay as they are. Also record the regression that proves the old behavior still holds.

### 4. Android mobile app assessment and implementation

- Inspect the Android app in every phase: `OccupancyWizardScreen`, bed interaction sheet, unit interaction sheet, payment screens, and `occupancyRules.ts`.
- Ship the Android control in the same phase as the web control for that feature. Do not wait for Phase 6 or Phase 8.
- Keep bottom sheets for bed and unit actions. Do not copy the web inspector into the app.
- PG, Hostel, and Co-living bed stays stay on the bed sheet. Rental and Corporate Stay stay on the unit sheet. Mess gains no stay action.
- Phase 1 keeps the existing `expectedCheckoutDate` allocate payload and proves it still submits.
- Phase 2 does not add a Short Stay step. It does prove the current wizard still allocates and does not display a short-stay total as monthly rent.
- Phase 3 adds Extend on the active bed sheet and the active unit sheet.
- Phase 4 adds the Short Stay step on the Android wizard when a price basis exists.
- Phase 5 shows the server overlap error on the existing Android actions.
- Phase 7 records whether the app can show the new alert. If it cannot, name the channel the owner actually uses.
- Android allocate already sends `expectedCheckoutDate`. Phase 1 must keep that payload working. Extend sends the new end as `YYYY-MM-DD` and shows the section 15 error on the existing sheet. A rejected overdue date does not pop a vacate action and does not clear the stay.
- Disable the sheet action while the request is in flight. A timeout retries with the same idempotency key.
- Keep the existing bottom navigation, payment screens, meal screens, and sheet chrome. Match existing ACOMI theme, type, spacing, and buttons if a control is added. Do not introduce a separate visual language.
- If a phase truly changes no Android screen, the tracker still contains the investigation, the justification, and the regression that was run.

### 5. Cross-platform business-rule and UI/UX alignment

- Business rules are shared. Screen structure is not.
- One planned end date, one stay kind, one conflict meaning, one stored total, and one extend route.
- Web may label the field `expectedExitDate`. Android may keep sending `expectedCheckoutDate` until both bind to the same stored date. Phase 1 is what makes that true on the server.
- Clients must not invent a second price, a second overlap rule, or a second deposit.
- Error text may follow each app's existing toast, snackbar, or inline pattern. The outcome must match the API.
- Extend uses one date rule, including the overdue `ACTIVE` case in section 15. Web shows it on the inspector. Android shows it on the bed or unit sheet. Neither client vacates.
- Displayed extension periods follow section 12.4 after D-03 and D-19 are approved. Until then, do not show a night count the server has not stored.
- Do not show Short Stay, Extend, or a date-range booking on a platform that the server cannot enforce yet.

### 6. Existing UI/UX preservation

- Do not restyle unrelated accommodation, payment, meal, or dashboard screens.
- Do not replace a working wizard step to make web and Android identical.
- Do not add a bed step to Rental or Corporate Stay.
- Do not add stay controls to Mess.
- Do not show `PARTIALLY_PAID` as money collected.
- Do not count a reservation as an occupied bed on the dashboard.
- A preservation note is required even when the phase's main work is a new control. Name what was left unchanged.
- Preserve allocate, reserve, move-in, transfer, and vacate. Preserve Until I Leave as a monthly stay with no planned end.
- Preserve existing loading, empty, and error patterns. Do not restyle them to match the other client.

### 7. Platform-specific tests and acceptance criteria

- Each phase lists tests for the layers it can execute.
- Backend service tests do not replace a web or Android test when that phase changes what those apps send or show.
- When the honest result is "no screen change," the acceptance evidence is the investigation note plus the regression that was run, not a blank checkbox.
- Name skipped tests with the reason, the user-visible impact, and the follow-up. A blank skip fails section 43.
- Phase 8 repeats the journeys for every end-user feature included in the release. It does not replace the tests from earlier phases.

### Feature parity matrix

"Same rule" means the same server outcome. It does not mean the same screen.

| Capability | Backend | Public website | Web app | Android | Same rule, different UI |
|---|---|---|---|---|---|
| Planned end on allocate | Phase 1 binds both field names | No form. Record the investigation | Phase 1 sends a field the DTO binds. Wizard layout stays | Phase 1 keeps `expectedCheckoutDate` and proves submit | Yes. One stored date |
| Room and bed conflict | Phase 1 service result | No form | Phase 1 shows the API error on the existing wizard | Phase 1 shows the API error on the existing sheet or wizard | Yes. Layouts stay different |
| Stay kind storage and monthly skip | Phase 2 | No pricing UI | Phase 2: no new step. Confirm monthly allocate unchanged | Phase 2: no new step. Confirm allocate and that rent is not shown as a short-stay monthly total | Yes. Server skips monthly rent |
| Extend Stay | Phase 3 route, one charge, no second deposit. Overdue `ACTIVE` rule is section 15 after D-26 is approved | No extend control | Phase 3 action on the inspector. Rejected date does not vacate | Phase 3 action on the bed sheet and the unit sheet. Same rule | Yes. Sheet is not an inspector |
| Short Stay create | Phase 4, basis required. Periods follow section 12.4 after D-03 and D-19 are approved | No public booking | Phase 4 wizard step | Phase 4 wizard step | Yes. Both wizards, not the same chrome |
| Date-range conflict | Phase 5, indexes stay until the later approved replacement | No availability calendar | Phase 5 conflict copy on existing actions | Phase 5 conflict copy on existing actions | Yes |
| Checkout passed, stay still active | Phase 7 alert, no vacate. Phase 3 may extend it without vacating | No owner inbox | Phase 7: show the alert only on a surface that already shows this class of alert, otherwise write the justification. Phase 3 extend stays on the inspector | Same split on Android: alert investigation, and extend on the existing sheet | Yes. The alert does not end the stay. Extend does not end it either |
| Existing allocate, reserve, move-in, transfer, vacate | Unchanged commands | No form | Keep the wizard and inspector | Keep the wizard and sheets | Yes |
| Until I Leave | Null end, monthly billing | No form | No new end required | No new end required | Yes |
| Submit and retry | Extend idempotency key | No control | Disable while in flight. Retry the same key | Same on the sheet | Yes |
| Payment list after extend | Response includes the charge id | No payment UI for stays | Refetch in Phase 3. Phase 6 rechecks query keys | Same | Yes |
| `PARTIALLY_PAID` | Calculator cannot set it | Not applicable | Do not show it as money collected | Do not show it as money collected | Yes |
| Mess | Rejected by target rules | No stay marketing that allocates | No stay controls | No stay controls | Yes |
| Rental and Corporate Stay | Unit target, null bed | No allocate form | No bed step | Unit sheet, no bed step | Yes |
| Dashboard occupied beds | Beds only, reservations excluded | Not a dashboard | Do not invent rental beds | Do not invent rental beds | Yes |
| Phase 6 | No new stay rule | Recheck only | Parity recheck | Parity recheck | Recheck is not first delivery |
| Phase 8 | Ledger and concurrency evidence | Marketing smoke, still no allocate | Journey pass | Journey pass, including a device or emulator note | Release needs both |

### Mandatory phase completion gate

A phase stays `NOT STARTED` or `IN PROGRESS` until every line below is true. Backend completion alone cannot move an affected end-user feature to `COMPLETE`. Phase 1's tracker status `ACCEPTED` is the authorized-scope record in section 18. It is not this gate's `COMPLETE` mark, because rollback was not exercised.

- The phase's own Platforms block is filled in the tracker with evidence, not a pointer to Phase 6 or Phase 8.
- Backend and database work is done, or the tracker explains why this phase has none.
- Public website assessment is written. Implementation is included only when this roadmap gives that site a change. Otherwise the justification is present.
- Web app assessment is written. Any web control this phase introduces is implemented and tested in this phase.
- Android assessment is written. Any Android control this phase introduces is implemented and tested in this phase.
- Cross-platform rules match the feature parity matrix for the rows this phase touches. Screen differences that the matrix allows are preserved.
- Existing UI that this phase does not own is named and left in place.
- Platform-specific tests or the no-change regression are recorded with command output, or with the reason, impact, and follow-up.
- Section 43's other gates, including ledger and rollback where they apply, are still required. This gate adds to them.

Per-phase evidence required before that phase can leave `NOT STARTED` for `COMPLETE`. These rows point at the Platforms block in that phase. They do not move Android work into Phase 6 or Phase 8.

| Phase | Complete only when |
|---|---|
| 0 | Decisions and baseline log, plus public-site, web, and Android inventory notes. No product code |
| 1 | DEF-001 and DEF-002 tests, web and Android request-builder evidence, public-site justification, layouts preserved |
| 2 | Ledger fixtures, and web and Android regressions or written no-screen-change investigations. A ledger log alone is not enough |
| 3 | B9, B16, and Scenarios C and C2, including no vacate and no shared night. Do not start extension behavior while D-26, D-03, or D-19 is `OPEN`. Web inspector extend and Android bed and unit sheet extend are still required in this phase once those decisions are approved |
| 4 | Short Stay step on web and Android, or hidden when the basis is absent. Public site still has no booking. Period columns follow section 12.4 only after D-03 and D-19 are approved |
| 5 | Overlap tests, plus conflict copy on existing web and Android actions. Indexes not dropped early |
| 6 | Parity recheck of work already delivered. Public-site recheck. Not the first extend or short-stay implementation |
| 7 | Alert does not vacate. Web and Android display evidence, or a written no-surface justification |
| 8 | Section 33, including section 45. Web and Android journeys for every end-user feature in the release |

---
