# Android device walkthrough

**Status:** PARTIAL. Device A-00 previously PASS; A-01–A-20 still need a walkDebug/local-API device session. On 2026-11-03 implementation audit (tests NOT RUN): ALLOCATE now collects and sends `moveInDate` (owner-web parity); short-stay same-day/cross-month client validation matches D-05 shipping copy. Evidence: `K:\ACOMI-Work\audit-2026-11-03\impl-audit\`. Jest is not device evidence.
**Date prepared:** 2026-10-30; updated 2026-11-03 (roadmap 1.29 implementation audit)
**Roadmap:** section 33.7 of `ACOMI_STAY_MANAGEMENT_IMPLEMENTATION_ROADMAP.md` version 1.29
**Evidence level:** Code updated; device A-01+ still not re-verified in this task.

This file does not close Section 43. Phase 8 is not `COMPLETE`.

## How to execute

1. Complete A-00. If it fails, leave every later row BLOCKED.
2. Use the same kind of test space as the owner-web walk: non-production, test-owned, with people named `STAY-GATE`.
3. Install a debug build only when A-00 names an authorized backend. Record the device model, Android version, application version, and git commit in the result log.
4. Walk the rows in order. A row stays BLOCKED until it is performed on the device.
5. Capture screenshots on the device. If logcat is used, keep the command to the app tag and remove tokens. Do not paste account passwords.

## Safety gate

| Check | Required | This preparation |
|---|---|---|
| Device | `adb devices` shows a serial, or an emulator is already running. | `adb devices` on 2026-10-30 exited 0 and printed only `List of devices attached`. No serial. An emulator was not started. |
| Backend | The debug build uses the local backend. `USE_LOCAL_DEV_BACKEND` is true. An emulator calls `http://10.0.2.2:8080`. A physical device calls `http://192.168.1.105:8080` from `devApiHost.ts`. A release build calls `https://api.acomi.in`. | The local backend defaults to `acomi_db` and runs Flyway on startup. This task must not start it. The production host is not an authorized place to create stays. |
| Account | An owner or manager of the test space, plus a member account. | No test account is recorded here. |
| App identity | Device model, Android version, version name, and commit. | Not recorded. No device was attached. |

Do not point the app at production to finish this checklist. Do not change `USE_LOCAL_DEV_BACKEND` as part of this walk.

## Date rule

Use the same date rule as the owner-web checklist. Short Stay is one owner-entered **Agreed total**. There is no nightly or weekly price field.

The last night of a stay is the planned end minus one day. A one-night extension whose last night stays in the checkout month is allowed. An extension whose added nights include a later calendar month is rejected with "A short-stay extension cannot cover nights in more than one calendar month."

## Screens verified from source

| Action | Screen |
|---|---|
| Sign in | `Login`, then OTP if the app asks for it |
| Spaces | `MySpaces` |
| Space | `SpaceTabs` |
| Edit space | `EditSpace`. Switch label **Turn on Short Stay**. Hidden for a Mess space. |
| Allocate, reserve, transfer, vacate | `OccupancyWizard` |
| Active stays | `DashboardOccupancyList` with `mode` `active` |
| Notifications | `SpaceNotifications` |
| Extend | `ExtendStayModal`, opened from the occupancy actions. Title **Extend stay**. |

The wizard shows **Monthly**, **Short stay**, and **Until I leave** only when `shortStayEnabled` is true and the space is not Mess. Transfer does not show that choice. The extend modal sends `idempotencyKey` from the value created when the action opens. The field is not on screen. A submit while `submitting` is true returns without a second request.

After an occupancy change, `invalidateAfterOccupancyChange` clears the occupancy, accommodation, dashboard, and owner payment-month caches for that space. The payments screen should reload without an application restart. That reload is a device observation. The function name is not the evidence.

Notification **Stay still active** uses action **View stay**. For an operator it opens `DashboardOccupancyList` with `mode` `active`. For anyone else it opens the space dashboard, and the owner-only filter hides the type before navigation. **Move-out scheduled today** uses action **Complete Vacate** and the same active list for an operator.

The exact screen reached by the header back control after that navigation depends on the stack that opened the notification. Record the screen that appears. Do not fill it in from this document.

## Steps

### A-00 Safety gate and device identity

- Preconditions: none.
- Action: run `adb devices`. Record model, Android version, app version, and commit only from the attached device. Record which API host the installed build uses.
- Expected: one authorized test backend, and a serial in `adb devices`.
- Status: PASS (2026-11-03). Serial `emulator-5554`. Model `sdk_gphone64_x86_64`. Android 15. App `1.0.1`. Commit `cce4bdd`. Walk backend health UP. Device attached for stay walk.
- Evidence: `K:\ACOMI-Work\audit-2026-11-03\evidence\android\`.
- Cleanup: none.

### A-01 Launch, sign in, and open the space

- Preconditions: A-00 passed.
- Action: launch the app, sign in on `Login`, open `MySpaces`, and open the test space.
- Expected UI: the space tabs load. A loading failure shows the existing error state, not an empty success screen.
- Persisted behavior: no stay is created.
- Status: BLOCKED. Release APK signs in against production API, not the walk DB. Debug+Metro hits host ANR. A local API flavor or stable debug session is required.
- Evidence: `A-01-relogin.png`, `ui-debug.xml` (ANR / login against wrong host).
- Cleanup: sign out at the end.

### A-02 Monthly allocation

- Preconditions: a vacant target and a `STAY-GATE` member with no open stay.
- Action: open `OccupancyWizard` for allocation. Leave the choice on **Monthly** when the choice is visible. Finish.
- Expected UI: the stay appears on the active list. Payments show monthly rent when rent is required. They do not show an agreed-total extra.
- Status: BLOCKED.
- Evidence: wizard and active-list screenshots.
- Cleanup: vacate in A-16.

### A-03 Short Stay hidden while off

- Preconditions: the edit-space switch is off.
- Action: open the allocation wizard.
- Expected UI: **Short stay**, **Until I leave**, and **Agreed total** are absent.
- Status: BLOCKED. The server rejection "Short Stay is not turned on for this space." is not sent by this screen. Do not hand-build that request. Mark the server sentence UNVERIFIED from the device.
- Evidence: wizard screenshot.
- Cleanup: none.

### A-04 Turn Short Stay on

- Preconditions: a non-Mess test space.
- Action: open `EditSpace`, turn on **Turn on Short Stay**, and save. Reopen the screen.
- Expected UI: the switch stays on. A Mess space does not render the switch.
- Status: BLOCKED. If no Mess space is available, record that and do not change a space into a Mess.
- Evidence: edit-space screenshot.
- Cleanup: A-20 turns the switch off if this walk turned it on.

### A-05 One-night Short Stay

- Preconditions: A-04. A vacant target and a `STAY-GATE` member.
- Action: choose **Short stay**, enter **Agreed total**, set checkout to the next day inside the same month, and finish.
- Expected UI: the review shows the agreed total. The active list shows the stay. Payments show one Rent / Extra for that total and one Deposit / Security when a deposit was entered. No nightly or weekly price is shown.
- Status: BLOCKED.
- Evidence: review, active list, and payments screenshots.
- Cleanup: keep the stay for extension.

### A-06 Same-day checkout rejected

- Action: set checkout equal to check-in and try to finish.
- Expected UI: no stay is created. The server message, when the request is sent, is "A short stay checkout must be after the check-in date. The same date is not a stay."
- Status: BLOCKED.
- Evidence: the error text.
- Cleanup: none.

### A-07 Short Stay across two months rejected

- Action: set a checkout whose last night is in the next calendar month and try to finish.
- Expected UI: "A short stay cannot cover nights in more than one calendar month." No new stay.
- Status: BLOCKED.
- Evidence: the error text.
- Cleanup: none.

### A-08 Overlap, including room and bed where the space is co-living

- Preconditions: the A-05 stay still occupies its target. For the room-and-bed part, the space must be co-living.
- Action: allocate a second member to the same target on an overlapping date. On a co-living space, also allocate the parent room while a bed is occupied, and a bed while its room is occupied.
- Expected UI: "This stay overlaps another stay on that bed, room, or unit." The first stay remains.
- Status: BLOCKED. The room-and-bed part stays BLOCKED when the test space is not co-living.
- Evidence: the error and the original stay.
- Cleanup: none.

### A-09 Same-month extension and payment refresh

- Preconditions: the active short stay can take one more night inside its checkout month.
- Action: open **Extend stay**, set the new planned end, enter an additional amount, and leave the rent fields empty. Then open Payments without restarting the app.
- Expected UI: the planned end moves. Payments show one new Extra for the additional amount and still one deposit. The list reloads after the occupancy change.
- Status: BLOCKED.
- Evidence: before and after screenshots of the planned end and the payment list.
- Cleanup: keep or vacate according to A-10.

### A-10 Cross-month extension rejected

- Action: extend so the added nights include a later calendar month.
- Expected UI: "A short-stay extension cannot cover nights in more than one calendar month." Planned end, extras, deposit, and history stay as they were before the attempt.
- Status: BLOCKED.
- Evidence: the error, then the unchanged stay and payments.
- Cleanup: none.

### A-11 Monthly and open-ended extension

- Action: extend a monthly stay with no additional amount, then with a positive amount. Allocate **Until I leave** and try a positive additional amount on that stay.
- Expected UI: the date-only monthly change succeeds and adds no extra. The positive amount shows "Monthly and open-ended stays do not take an extension charge. Leave the additional amount empty."
- Status: BLOCKED.
- Evidence: both results.
- Cleanup: vacate both stays in A-16.

### A-12 Duplicate submit

- Action: submit a valid extension once. Tap again while the first request is in progress. Close the modal and open it again for a later date.
- Expected UI: the in-progress tap does not send a second request. The reopened modal uses a new key and is a new extension.
- Persisted behavior: same-key replay is not a control on this screen. Do not replay a captured request with another client.
- Status: BLOCKED. Same-key replay after success is UNVERIFIED from the UI.
- Evidence: logcat or a proxy log showing one extend request, with tokens removed.
- Cleanup: none.

### A-13 Paid row, vacate, and inventory

- Preconditions: a test extra that can be marked paid through the existing payment screen.
- Action: mark it paid, vacate the stay, and open inventory.
- Expected UI: the extra remains **Paid**. The stay is vacated. The target is available.
- Status: BLOCKED. Do not use SQL to mark a row paid.
- Evidence: payment row before and after vacate, and the inventory screen.
- Cleanup: do not delete the payment.

### A-14 Half-open date

- Preconditions: a vacated test stay and the vacate date shown on the device.
- Action: allocate another member to the same target starting on that date, then try the day before.
- Expected UI: the start on the vacate date succeeds. The earlier start shows the overlap sentence.
- Status: BLOCKED.
- Evidence: both results.
- Cleanup: vacate the new stay.

### A-15 Transfer

- Preconditions: a co-living stay with one deposit and a vacant target.
- Action: run the transfer wizard.
- Expected UI: the old stay is vacated, the new stay is active, the planned end is not copied, and one deposit remains.
- Status: BLOCKED when the space is not co-living or the device is unavailable.
- Evidence: both stays and the payment list.
- Cleanup: vacate the new stay.

### A-16 Vacate remaining test stays

- Action: vacate every remaining `STAY-GATE` stay.
- Expected UI: the active list no longer shows them. Their payment rows remain.
- Status: BLOCKED.
- Evidence: active list and payments.
- Cleanup: this is the stay cleanup.

### A-17 Overdue notification and View stay

- Preconditions: an active test stay whose planned end is before the space business date. Do not create that condition on a customer stay.
- Action: open `SpaceNotifications`, find **Stay still active**, and tap **View stay**.
- Expected UI: the operator sees `DashboardOccupancyList` in active mode. The stay is still active. Inventory is unchanged. No late fee is shown.
- Status: BLOCKED.
- Evidence: notification screenshot and destination screenshot.
- Cleanup: vacate or extend only the test stay afterward.

### A-18 Member does not see it

- Action: sign in as a member and open the same space notifications.
- Expected UI: **Stay still active** is absent. Tapping a different owner-only row does not open the operator occupancy list.
- Status: BLOCKED.
- Evidence: member notification list.
- Cleanup: sign out.

### A-19 Back navigation and screen state

- Preconditions: A-17 was opened from the notification list.
- Action: use the header back control. Note the screen name. Open the stay again and return.
- Expected UI: record the actual screen. The stay list still shows the same active stay. This document does not name the back destination, because the stack that opened the notification decides it.
- Status: BLOCKED. The landing screen is UNVERIFIED until a device run records it.
- Evidence: screenshot after back.
- Cleanup: none.

### A-20 Move-out today and navigation regression

- Action: if a test stay exits today, confirm the notification title is **Move-out scheduled today** and the action is **Complete Vacate**. Then open the dashboard, stay list, payments, and notifications, and return from each.
- Expected UI: the move-out row is distinct from **Stay still active**. Monthly stays still display as monthly stays. Loading and error states use the existing screens.
- Status: BLOCKED. If no test stay exits today, do not change a customer date to create one.
- Evidence: the move-out notification and one screenshot per navigation stop.
- Cleanup: A-16 covers stays. Turn **Turn on Short Stay** off if this walk turned it on.

## Result log

| ID | Status | Evidence location | Notes |
|---|---|---|---|
| A-00 | BLOCKED | `adb devices` exit 0, header only. `Pixel_8` is installed and did not attach | No model, Android version, app version, or commit |
| A-01 through A-20 | BLOCKED | Not captured | No attached device. The isolated walk backend was running and was not used for this walk |

No Android step passed. No Android step failed on a device, because no device step was run. Jest and `tsc` results from section 33.5 are earlier automated evidence. They are not this walk.

### Update — 2026-11-02

| ID | Status | Evidence location | Notes |
|---|---|---|---|
| A-00 | BLOCKED | Emulator log: insufficient disk space for AVD `Pixel_8`. `C:` free ≈ 0.2–1.2 GB. `adb devices` empty | Roadmap 33.12 |
| A-01 through A-20 | BLOCKED | Not captured | Same infrastructure block |
| Focused Jest stay suites | PASS (not device) | 8 suites / 27 tests | `extendStay|shortStay|occupancyStay|stayTrail|stayCharge|occupiedBedStay|memberListStay` |
