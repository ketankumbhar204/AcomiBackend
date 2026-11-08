# ACOMI Stay Management — Phase 0 Findings

Later approval, recorded after this inspection: on 2026-10-29 the product owner approved D-24 as “allow the room”. A child bed in `MAINTENANCE` or `BLOCKED` does not reject a room stay. D-01 through D-23 and D-25 through D-26 were still `OPEN` when this inspection was written. The rows below keep that inspection-time status.

Later acceptance, recorded in the roadmap on 2026-10-29: Phase 1 is `ACCEPTED` for the authorized scope. D-01, D-09, and D-23 were approved the same day, and D-05 was deferred to Phase 4. Phase 2 is `ACCEPTED` for the stay-kind column and the monthly-rent skip. Phase 3 is `ACCEPTED` for extend. Phase 4 is `ACCEPTED` for a same-month short stay with one fixed total. Phase 5 is `ACCEPTED` for a locked half-open conflict query. D-02, D-03, D-04, D-06, D-07, D-11, D-12, D-13, D-15, D-19, D-20, D-21, D-22, D-25, and D-26 are approved. D-05 stays open. D-21 keeps the member unique indexes. No exclusion constraint was added, and `btree_gist` was not queried. A second rent change still keeps only one prior rent. On 2026-10-30 Phase 6 was accepted for the parity recheck. Phase 7 is accepted for the overdue-stay alert. D-08 and D-10 are approved. D-05, D-14, D-16, D-17, and D-18 stay open. A release-gate evidence pass the same day did not start Phase 8. A later service-ledger pass the same day also left Phase 8 unstarted. Section 33.3 of roadmap 1.16 recorded another closure pass the same day. Section 33.4 of roadmap 1.17 added short-stay extension races and a co-living room-to-bed transfer. Section 33.5 of roadmap 1.18 records a clean Android typecheck and a rerun of the disposable service and migration tests. Section 33.6 of roadmap 1.19 records the deployment runbook and the unexecuted browser and device checklists. Section 33.7 of roadmap 1.20 adds the executable owner-web and Android checklists. They were not run. Section 33.8 of roadmap 1.21 records a disposable `pg_dump` and `pg_restore` rehearsal. Section 33.9 of roadmap 1.22 records the isolated database `acomi_stay_walk_fd1b26b24a1d`, Flyway v158 on that database only, and a local backend. `acomi_db` was not connected. No migration was run and the backend was not started. Phase 8 stayed unstarted and Section 43 stayed open. Section 33.10 of roadmap 1.23 records the owner-web browser session on that same database. Section 33.11 of roadmap 1.24 records the remaining owner-web checks on that database, including the whole-stay extension tests. Android A-00 through A-20 were not run. This file stays the inspection snapshot. It is not the implementation record.

**Date:** 2026-10-29
**Roadmap:** `docs/implementation/ACOMI_STAY_MANAGEMENT_IMPLEMENTATION_ROADMAP.md` version 1.4
**Scope:** Business-rule readiness and baseline assessment only.
**Decisions:** At inspection time, D-01 through D-26 were `OPEN`. D-24 was approved afterward. See the note at the top. This inspection did not approve the other decisions.
**Application changes:** None. No API, migration, configuration, test, or deployment file was edited.

Evidence labels match the roadmap: **Verified** was read from source or a migration on this pass. **Not in code** means the proposed rule was not found. **Unknown** was not established.

## 1. Current architecture and paths

Stay state is one `occupancies` row. There is no stay-kind column, no extend route, and no date-range availability constraint.

| Area | Path | Verified role |
|---|---|---|
| Occupancy API | `occupancy/api/controller/OccupancyController.java` | `POST /occupancies`, `/reserve`, `/{id}/move-in`, `/cancel-reservation`, `/transfer`, `/vacate`, plus GETs. No `/extend`. |
| Commands | `occupancy/application/service/OccupancyService.java` | Allocate, reserve, move-in, cancel, transfer, vacate. `assertTargetNotHeld` is status-based. |
| Target rules | `occupancy/application/service/OccupancyTargetService.java` | PG/Hostel bed. Co-living bed or room. Rental and Corporate Stay unit. Mess throws. |
| Inventory sync | `occupancy/application/service/AccommodationStatusSyncService.java` | Bed status from that bed's occupancy. Room `markOccupied` does not set child beds `OCCUPIED`. |
| Entity | `occupancy/infrastructure/persistence/entity/OccupancyEntity.java` | `expectedCheckoutDate`, `expectedExitDate`, `vacatedAt`. No `stayKind`, `pricingBasis`, or `agreedTotal`. |
| History | `OccupancyHistoryEvent.java`, `V23__occupancy_lifecycle_phase_4_3a.sql` | `ALLOCATED`, `TRANSFERRED`, `VACATED`, `RESERVED`, `MOVE_IN`, `RESERVATION_CANCELLED`. `EXTENDED` is not allowed. |
| Uniqueness | `V20`, `V23`, `V33` | One active and one reserved row per member, and per bed. Room and unit indexes apply only when that target type is set. |
| Monthly period | `payment/application/support/BillingAmountCalculator.java` `resolvePeriod` | Inclusive start and inclusive end. `billableDays` adds 1. |
| Monthly end | `dashboard/application/support/OccupancyBillingCalculator.java` `resolveOccupancyEndDate` | `vacatedAt` date, or null. Ignores `expectedExitDate`. |
| Monthly heal | `PaymentMonthSnapshotService.needsMissingGeneratedRent` | Positive `rentSnapshot` and no `RENT/MONTHLY` row can create a monthly row. |
| Payment business date | `PaymentDueStatusCalculator.businessDate` | Space-local date from `MealPollCloseAtCalculator.nowInSpace`. |
| Occupancy "today" | `OccupancyService`, `OccupancyNotificationSyncService.syncSpace` | `LocalDate.now()` with no space zone. |
| Alerts | `OccupancyNotificationSyncService`, `OperationalNotificationScheduler` | Move-out alert when `expectedExitDate` equals JVM today and status is `ACTIVE`. Cron default `0 15 8 * * *`. The job does not vacate. |
| Owner web | `AcomiWeb/src/modules/accommodation/utils/occupancyRules.ts`, `OccupancyWizardPage.tsx`, `PendingActionsPage.tsx` | Allocate body sends `expectedExitDate` only. Pending actions include `MOVE_OUT_SCHEDULED_TODAY`. |
| Android | `AcomiMobile/src/utils/occupancyRules.ts`, `OccupancyWizardScreen.tsx`, `DashboardPendingActionsScreen.tsx` | Allocate body sends `expectedCheckoutDate` and `expectedExitDate`. Same pending-action type. |
| Public site | `AcomiPublicWebsite` marketing components (`OwnerOccupancySection`, `PropertyRunSection`, and related shots) | Screenshots and copy. No allocate, reserve, extend, or checkout API. |

`AllocateOccupancyRequest` binds `expectedCheckoutDate` only. Reserve and move-in bind `expectedExitDate`. `OccupancyResponse.from` copies the stored planned end into both response fields.

## 2. Existing behavior

### Stays

Allocate creates `ACTIVE` for today (`LocalDate.now()`), writes both end-date columns from `expectedCheckoutDate`, then creates the activation snapshot and payments. Reserve creates `RESERVED`, stores `expectedExitDate` only, and creates no contract and no payments. Move-in promotes `RESERVED` to `ACTIVE`. Cancel and vacate call `closeOccupancy`, which sets `VACATED` and `vacatedAt`. Transfer vacates the current row and inserts a new `ACTIVE` row. It does not call `createActivationPayments`.

Nothing in `OccupancyService` reads `expectedExitDate` to end a stay. A stay whose planned end is before today remains `ACTIVE` until vacate. Inventory stays held. **Verified.** The roadmap's D-26 comparison is **not in code**.

### Extensions

No extend method, request DTO, history value, or client action was found. Changing the planned end of an existing `ACTIVE` stay is not a current operation. A later month of rent is the existing monthly generator, not an extension charge.

### Billing and checkout

`resolvePeriod` clips an occupancy to a calendar month. Both ends are inclusive. `prorateConfigured` is `monthly × billableDays / daysInMonth` when the period is shorter than the month. `resolveOccupancyEndDate` uses `vacatedAt` only, so a planned checkout does not shorten a monthly bill.

Vacate sets `vacatedAt` to `LocalDateTime.now()`. A later monthly calculation can prorate a pending month to that date. This pass did not find an automatic extra charge for nights after the planned end. No code divides monthly rent by 30 or by a night count to invent a late-checkout price.

`needsMissingGeneratedRent` does not know about a short stay. Any billable occupancy with `rentSnapshot > 0` and no monthly rent row is a heal candidate. There is no duration calculator for a fixed stay total.

### Cross-month

Monthly rows are per `YearMonth` passed into the snapshot and generator. A stay that crosses a month boundary gets the normal monthly formula in each month until `vacatedAt`. There is no single cross-month fixed charge and no chosen reporting month for one. **D-05 is unresolved** because that charge type does not exist yet. Which ledger month would show it cannot be read from current code.

### Availability

`assertTargetNotHeld` rejects a bed, room, or unit that already has `ACTIVE` or `RESERVED` on that same target id. It does not compare date ranges. A room stay does not query beds in the room. A bed stay does not query a room-target stay. Unique indexes enforce one open row per member and per target id. They do not express overlap of nights.

Migrations under `src/main/resources/db/migration` do not create `btree_gist`. This pass did not query a PostgreSQL server. Do not treat the extension as installed.

### Alerts

`syncSpace` loads `ACTIVE` rows whose `expectedExitDate` equals JVM `LocalDate.now()` and publishes `MOVE_OUT_SCHEDULED_TODAY`. The action label is "Complete Vacate". The publisher does not call vacate. A planned end before today produces no occupancy alert from this method. `EXPIRED_RESERVATION` exists on `NotificationType`. No occupancy publisher for it was found in this service.

Payment overdue uses the space business date. Occupancy "today" uses the JVM date. Those clocks are different. Whether they disagree in production around midnight was not measured.

Owner web `PendingActionsPage` and Android `DashboardPendingActionsScreen` already list `MOVE_OUT_SCHEDULED_TODAY`. This pass did not add a screen and did not find a separate "planned end has passed" inbox.

## 3. Decision table

Every row stays `OPEN` until the product owner decides. "Depends" is the implementation phase that must not invent the rule while the row is open.

| ID | Current implementation | Depends | Product-owner question |
|---|---|---|---|
| D-01 | No stay-kind column. Every occupancy is billed as monthly when it has a rent snapshot. | Phase 2 | Are the kinds `MONTHLY`, `SHORT`, and `OPEN_ENDED`, with null meaning legacy monthly? |
| D-02 | Catalogue and snapshot amounts are monthly. No nightly or weekly accommodation column was found. | Phase 4 | Which configured bases may price a short stay, with no monthly÷30? |
| D-03 | Monthly proration counts inclusive calendar days. No 7-night stay calculator exists. | Phases 3, 4, and 5 | Is 1 Nov–8 Nov seven nights for short stays only, leaving monthly proration unchanged? |
| D-04 | Allocate and reserve accept a planned end without a same-day check. | Phase 4 | Is a same-day stay rejected until a day price exists? |
| D-05 | Each calendar month is billed with the monthly formula. No single cross-month total exists. | Phases 2 and 4 | For one fixed cross-month total, which ledger month shows it, counted once? |
| D-06 | Planned end is optional on allocate. | Phase 4 | Is an end date required for `SHORT`? |
| D-07 | Activation creates `DEPOSIT/SECURITY` from the deposit snapshot. | Phase 4 | Does a short stay use that same one-deposit rule? |
| D-08 | Cancel writes no fee. `EXPIRED_RESERVATION` is an enum value without an occupancy publisher in `OccupancyNotificationSyncService`. | Phase 7 | Are cancellation fees and auto-expiry out of scope? |
| D-09 | `resolveOccupancyEndDate` uses `vacatedAt` only. | Phase 2 | Does monthly rent continue until vacate even if a planned end is set? |
| D-10 | Planned end in the past leaves `ACTIVE` and held inventory. No auto-vacate was found. | Phase 7 | Confirm the stay stays occupied and is not vacated by the date alone. |
| D-11 | No extension API. The next month is whatever the monthly generator writes. | Phase 3 | For a monthly stay, is the next month only `RENT/MONTHLY`, with no `RENT/EXTRA` for those dates? |
| D-12 | No extension API, so no free-extension row exists. | Phase 3 | Is a free extension a date and history change with no zero-amount row? |
| D-13 | A new allocate can create a deposit. Transfer does not call activation payments. | Phase 3 | Must extend and renew skip `createActivationPayments` and skip a second deposit? |
| D-14 | Unpaid rent remains a pending payment row. There is no extend path to block. | Phase 3 | May an owner extend while rent is still unpaid? |
| D-15 | One rent snapshot. Paid rows are skipped by the payment upsert. Pending rows can be overwritten on sync. | Phase 3 | From which month does a changed monthly price apply, without editing `PAID` rows? |
| D-16 | Vacate can change a later pending proration. No automatic late-checkout price was found. | Phases 3 and 7 | Is late-checkout money only an owner-entered charge, never monthly÷nights? |
| D-17 | Category `REFUND` is rejected on manual create. Deposit refund is the member field. | Later than Phase 4 | Confirm there is no automatic early-checkout refund. |
| D-18 | `PaymentDueStatusCalculator` sets paid to the full amount when status is `PAID`, otherwise zero. There is no `paidAmount` column. | Not in Phases 1–5 | Confirm whole-row settlement until a real paid-amount model exists. |
| D-19 | Availability is "target status is active or reserved", not a date interval. | Phase 5 | Is inventory availability half-open `[start, end)`? |
| D-20 | Reserve may omit `expectedExitDate`. The reserved unique index still blocks that target. | Phase 5 | Does a reservation with no end keep blocking all future dates? |
| D-21 | `uq_occupancies_member_active` and `_reserved` forbid a second open stay for the member. | Phase 5 | Is a current stay plus a future reservation still forbidden? |
| D-22 | `assertCanManageOccupancy` allows owner or manager. | Phase 3 | Do only those roles extend? |
| D-23 | Existing rows have no stay kind. They follow the monthly snapshot path. | Phase 2 | Is there no backfill that guesses short stay from old dates? |
| D-24 | Room resolution does not reject a child bed in `MAINTENANCE` or `BLOCKED`. | Phase 1 | Does a maintenance or blocked bed reject a room stay? |
| D-25 | No per-space short-stay price basis. | Phase 4 | Is Short Stay hidden until that space has a saved basis? |
| D-26 | No extend validation. An `ACTIVE` stay with a past planned end is left active. | Phase 3 | When extending that stay, must the new end be on or after the space business date, with rejection not vacating? |

## 4. Baseline tests

Named tests are JUnit 5. `OccupancyServiceTest`, `OccupancyContractSnapshotServiceTest`, and `AccommodationStatusSyncServiceTest` use Mockito. `OccupancyBillingCalculatorTest` and `BillingAmountCalculatorTest` are plain unit tests. They do not need PostgreSQL when compilation succeeds.

**Command run on 2026-10-29** from `acomi-backend`, with `JAVA_HOME` set to Android Studio's JBR:

`.\mvnw.cmd "-Dtest=OccupancyServiceTest,OccupancyBillingCalculatorTest,OccupancyContractSnapshotServiceTest,AccommodationStatusSyncServiceTest,BillingAmountCalculatorTest" test`

**Result:** `BUILD FAILURE` during `compiler:compile`. Surefire did not start. The log has no `Tests run` line. About 200 error lines report missing methods and builders (`OccupancyEntityBuilder`, getters, `log`, and similar) across unrelated classes, which is the shape of Lombok output not being generated on that incremental recompile. `pom.xml` does declare the Lombok annotation processor. This pass did not change the pom or sources and did not force a second full rebuild: a Spring Boot process was already running from this backend directory, and rewriting `target/classes` under it was avoided.

**Not claimed:** any of these tests passed or failed on assertions. They did not execute.

| Test | What the source covers | Gap for later phases |
|---|---|---|
| `OccupancyServiceTest` | Allocate today, reserve, occupied bed, early move-in, move-in, cancel, gender, vacate | No extend, no past planned end, no date-field alias |
| `OccupancyBillingCalculatorTest` | Food in rent, reserved, mid-month, vacate proration, tax | No `SHORT` skip, no exclusive stay period |
| `BillingAmountCalculatorTest` | Inclusive `resolvePeriod` and proration | Must stay green when short-stay periods are added. Not executed here |
| `OccupancyContractSnapshotServiceTest` | Snapshot and transfer policies | No `agreedTotal` instead of `rentSnapshot` |
| `AccommodationStatusSyncServiceTest` | Bed and room status derivation | No room-versus-bed mutual exclusion |

Missing before the phase that needs them: allocate with only `expectedExitDate`; room stay blocking a bed and the reverse; `SHORT` producing no `RENT/MONTHLY`; extension idempotency; B9 and B16 overdue dates without vacate; two non-overlapping extension periods and the 10000 / 5600 ledger example; concurrent overlap; web and Android request-builder tests for the end-date field. Do not add those tests in Phase 0.

## 5. Cross-platform parity

| Workflow | Public website | Owner web | Android | Server |
|---|---|---|---|---|
| Allocate | No form | Wizard sends `expectedExitDate` only | Wizard sends `expectedCheckoutDate` and `expectedExitDate` | Persists allocate end from `expectedCheckoutDate` only |
| Reserve / move-in / vacate | No form | Wizard and inspector use the existing routes | Wizard and bed or unit sheets use the same routes | Implemented |
| Extend | No control | No action found | No action found | No route |
| Short stay | No booking | No stay-kind step | No stay-kind step | No kind column |
| Planned end already passed | No inbox | No separate alert found | No separate alert found | Stay stays `ACTIVE`. Alert only when the date is JVM today |
| Move-out today | No owner inbox | `PendingActionsPage` | `DashboardPendingActionsScreen` | `MOVE_OUT_SCHEDULED_TODAY`, no vacate |
| Mess stay | Marketing only | Must not gain stay controls | Must not gain stay controls | Mess allocation throws |
| Rental / Corporate Stay | No allocate form | Unit wizard, no bed step | Unit sheet, no bed step | Unit target required |

Web and Android layouts stay different. The server is the source of dates and amounts. Phase 0 did not change either client.

## 6. Blockers before later phases

| Phase | Do not start until | Why this inspection matters |
|---|---|---|
| 1 | D-24 approved or explicitly deferred in writing | Mutual exclusion is not in `assertTargetNotHeld`. Do not invent the maintenance-bed rule. The date-alias fix does not need pricing decisions. |
| 2 | D-01, D-09, D-23 approved, and Phase 1's tracker updated if that phase was done | Monthly heal will bill a positive `rentSnapshot`. A short-stay total must not be stored there until the skip exists. |
| 3 | Phase 2 exit, plus D-11, D-12, D-13, D-15, D-22, and extension behavior also D-26, D-03, and D-19 | There is no extend path. Past planned end does not vacate. Exclusive periods must not be written while D-03 and D-19 are open. |
| 4 | Phase 2 and Phase 3 exits, plus D-02, D-03, D-04, D-05, D-06, D-07, D-25 | No short-stay duration or charge exists. D-05's ledger month is still unanswered. |
| 5 | Phase 1 hierarchy, plus D-03, D-19, D-20, D-21, and a recorded PostgreSQL / `btree_gist` check | Current protection is status unique indexes. Do not drop them and do not require an exclusion constraint yet. |
| 6 | The APIs from earlier phases | Parity recheck only. Web still drops the allocate end date. Mobile already sends the bound field. |
| 7 | D-08 and D-10 handled as written, and the alert clock chosen | Today's alert uses the JVM date and only the equal-today case. Payment business date is a different clock. |
| 8 | Section 33 of the roadmap, including platform evidence | Backend green is not enough. Phase 0 did not produce that evidence. |

## 7. Recommended order after Phase 0

1. Product owner answers the questions in section 3. Leave each row `OPEN` until that answer is recorded in the roadmap register.
2. Phase 1 can be the first code phase for Co-living mutual exclusion and the allocate date alias, after D-24 is approved or explicitly deferred. It still needs its own web and Android checks. It was not started here.
3. Phase 2 only after D-01, D-09, and D-23. Do not persist a short-stay total in `rentSnapshot`.
4. Phase 3 extension behavior only after its decision list, including D-26, D-03, and D-19. Monthly proration stays inclusive.
5. Phases 4 and 5 stay behind their own open decisions. Phase 5 also needs a real database check before any exclusion constraint.
6. Phase 6 rechecks clients. It is not the first mobile implementation.
7. Phase 7 chooses the alert clock without vacating and without changing `prorateConfigured`.

## Unanswered without a further decision or a server check

- D-08, D-10, D-14, D-16, D-17, and D-18. D-20 and D-21 were approved on 2026-10-29: a null end keeps blocking every later date, and one member still has one open stay. D-02, D-04, D-06, D-07, and D-25 were approved for Phase 4. D-01, D-03, D-09, D-11, D-12, D-13, D-15, D-19, D-22, D-23, D-24, and D-26 were approved earlier. D-05 stays open. See the note at the top.
- Whether JVM `LocalDate.now()` and the space business date differ in production around midnight. Not measured.
- Whether `btree_gist` can be created on the deployment server. Not queried.
- The Maven baseline did not execute, so current assertion results are unknown.
