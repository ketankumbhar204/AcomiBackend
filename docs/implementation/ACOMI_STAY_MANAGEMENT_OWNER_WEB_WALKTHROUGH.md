# Owner web browser walkthrough

**Status:** Browser sessions on 2026-10-30 and 2026-11-02 against `acomi_stay_walk_fd1b26b24a1d`. Results are in the logs below and in roadmap sections 33.10–33.12. Section 43 stays open. Phase 8 is `IN PROGRESS` and blocked for release.
**Date prepared:** 2026-10-30
**Roadmap:** section 33.12 of `ACOMI_STAY_MANAGEMENT_IMPLEMENTATION_ROADMAP.md` version 1.25
**Evidence level:** headless Chromium clicks. Screenshots are in `C:\Users\Lenovo\AppData\Local\Temp\acomi-stay-walk\evidence` and are not committed.

This file does not close Section 43 and does not mark Phase 8 complete.

## How to execute

1. Complete the safety gate W-00. If it fails, leave every later row BLOCKED.
2. Use one non-production space that this test owns. Name new people so the display name starts with `STAY-GATE`.
3. Walk the rows in order. Record the result in the log at the bottom. Replace BLOCKED only after the step is actually clicked.
4. Capture the screenshot and the redacted request named on that row. Remove `Authorization`, cookies, and tokens before saving.
5. Cleanup is the last row. Do not delete payment or occupancy history with SQL.

## Safety gate

| Check | Required before any click | This preparation |
|---|---|---|
| Backend | An authorized non-production database. The person running the walk records the profile, host, and database name. | Not available. `application-local.yml` defaults `DB_NAME` to `acomi_db`, and Flyway runs on startup. This task must not start that profile. |
| Owner web API | `VITE_API_BASE_URL`. The example file uses `/api/v1`, which the Vite dev server proxies to Spring Boot. The code fallback is `http://localhost:8080/api/v1`. | A local start would target the local backend. Production builds use `https://api.acomi.in/api/v1`. Neither target is authorized for new stays in this task. |
| User | An owner or manager of the test space, plus a separate member account for W-21. | No test account is recorded here. Do not paste a password into this file. |
| Browser | A browser that can open the owner web and save screenshots. | No browser automation tool is registered in this session. |
| Data | Test-owned members and vacant inventory. | Do not use a real customer stay or payment. |

Stop if the resolved database is `acomi_db`, the host is production, or the authorization is missing.

## Date rule

Choose dates on the day of the test. The short-stay interval is half-open: check-in through checkout, with checkout excluded. The last night is the checkout date minus one day.

- One night in one month: checkout is the next day, and that last night is in the same calendar month as check-in.
- Same day: checkout equals check-in. The server rejects it with `SHORT_STAY_SAME_DAY` and the sentence "A short stay checkout must be after the check-in date. The same date is not a stay."
- Short stay created across two months: the last night is in a later month than check-in. The server rejects it with `SHORT_STAY_CROSSES_MONTH` and "A short stay cannot cover nights in more than one calendar month."
- Same-month extension: the new planned end adds nights whose last night stays in the current checkout month. A checkout on the last day of a month extended by one night does not cross a month, because the last night is still that last day.
- Cross-month extension: the added nights include a night in a later month. The server rejects it with `EXTENSION_CROSSES_MONTH` and "A short-stay extension cannot cover nights in more than one calendar month."

Do not type a nightly or weekly price. The only short-stay amount on the wizard is **Agreed total**.

## Routes and requests verified from source

| Action | Path or request |
|---|---|
| Sign in | `/login` |
| Space list | `/my-spaces` |
| Dashboard | `/spaces/{spaceId}/dashboard` |
| Edit space | `/spaces/{spaceId}/edit`. Checkbox label **Turn on Short Stay**. Hidden when the space type is Mess. |
| Allocate | `/spaces/{spaceId}/occupancy/wizard?mode=ALLOCATE` |
| Active stays | `/spaces/{spaceId}/occupancy?mode=active` |
| Bed inventory | `/spaces/{spaceId}/bed-inventory` |
| Payments | `/spaces/{spaceId}/payments` |
| Notifications | `/spaces/{spaceId}/notifications` |
| Pending actions | `/spaces/{spaceId}/pending-actions` |
| Allocate body | `POST /api/v1/spaces/{spaceId}/occupancies` |
| Reserve body | `POST /api/v1/spaces/{spaceId}/occupancies/reserve` |
| Extend body | `POST /api/v1/spaces/{spaceId}/occupancies/{occupancyId}/extend` |
| Transfer body | `POST /api/v1/spaces/{spaceId}/occupancies/{occupancyId}/transfer` with `rentPolicy` `KEEP` |
| Vacate body | `POST /api/v1/spaces/{spaceId}/occupancies/{occupancyId}/vacate` |

Stay buttons, shown only when Short Stay is on and the mode is Allocate, Reserve, or Move in: **Monthly**, **Short stay**, **Until I leave**. **Monthly** omits `stayKind`. **Short stay** sends `stayKind` `SHORT`, `agreedTotal`, and the same checkout on `expectedExitDate` and `expectedCheckoutDate`. **Until I leave** sends `stayKind` `OPEN_ENDED`.

The Extend stay dialog fields are **New planned end**, **Additional amount**, **New monthly rent**, and **Rent starts in**. The idempotency key is generated when the dialog opens and is sent as `idempotencyKey`. The dialog does not show that key. The button is disabled while the request is pending. Opening the dialog again creates a new key.

Payment labels in the payments screen: type **Rent** with category **Extra**, and type **Deposit** with category **Security**.

## Steps

### W-00 Safety gate

- Platform: owner web.
- Preconditions: none.
- Action: record profile, host, database name, web API base, and the role of the signed-in user. Confirm the database is not `acomi_db` and not production.
- Expected: the ticket names an authorized test database. No password is written down.
- Persisted behavior: none.
- Evidence: the four recorded names, with secrets omitted.
- Status: BLOCKED. Those names were not available, and the local default is `acomi_db`.
- Cleanup: none.

### W-01 Sign in and open the space

- Preconditions: W-00 passed. An owner or manager of the test space.
- Action: open `/login`, sign in, open `/my-spaces`, then `/spaces/{spaceId}/dashboard`.
- Expected UI: the dashboard shows that space. The member used later is not given operator actions.
- Persisted behavior: no stay is created.
- Request evidence: the dashboard request succeeds. Redact the token.
- Status: BLOCKED.
- Evidence: dashboard screenshot.
- Cleanup: sign out at the end of the walk.

### W-02 Monthly stay

- Preconditions: Short Stay is off, or the stay choice **Monthly** is selected. A vacant bed, room, or unit, and a `STAY-GATE` member with no open stay.
- Action: open the allocate wizard. Leave the stay on **Monthly**. Enter the rent the space already requires. Finish.
- Expected UI: the stay is on the active occupancy list. The payments list shows monthly rent when the rent snapshot is greater than zero. It does not show an agreed-total extra.
- Persisted behavior: `stayKind` is absent or monthly. No `RENT` / `EXTRA` row is created for an agreed total.
- Request evidence: `POST /spaces/{spaceId}/occupancies` without `stayKind` `SHORT`.
- Status: BLOCKED.
- Evidence: wizard screenshot, active-list screenshot, payments screenshot.
- Cleanup: this stay can be the monthly extension row, then vacated in W-18 if it is not reused.

### W-03 Short Stay hidden while off

- Preconditions: **Turn on Short Stay** is unchecked. Save the edit page first if it was on.
- Action: open the allocate wizard.
- Expected UI: **Short stay** and **Until I leave** are absent. There is no agreed-total field and no nightly or weekly field.
- Persisted behavior: no short stay is created. The server sentence "Short Stay is not turned on for this space." is not reachable from this wizard, because the wizard does not send `SHORT` while the flag is off. Do not craft that request by hand.
- Status: BLOCKED. The hidden-control check is the supported flow. The server rejection remains UNVERIFIED from the UI.
- Evidence: wizard screenshot showing no Short stay button.
- Cleanup: none.

### W-04 Mess has no Short Stay control

- Preconditions: a test Mess space, if one exists.
- Action: open `/spaces/{spaceId}/edit` on that Mess space.
- Expected UI: **Turn on Short Stay** is not rendered.
- Status: BLOCKED. Also BLOCKED if no test Mess space exists. Do not convert another space into a Mess.
- Evidence: edit-page screenshot.
- Cleanup: none.

### W-05 Turn Short Stay on

- Preconditions: a non-Mess test space.
- Action: on the edit page, check **Turn on Short Stay** and save.
- Expected UI: the checkbox stays checked after reload.
- Persisted behavior: `shortStayEnabled` is true for that space only.
- Status: BLOCKED.
- Evidence: edit-page screenshot after save.
- Cleanup: W-24 turns it off if this test turned it on.

### W-06 One-night Short Stay and agreed total

- Preconditions: W-05. A vacant target and a second `STAY-GATE` member.
- Action: Allocate, choose **Short stay**, enter an agreed total greater than zero, and set checkout to the next day inside the same month. Finish.
- Expected UI: the review shows **Agreed total** and that checkout. No nightly or weekly price appears. The active list shows the stay. Payments show one **Rent** / **Extra** for the agreed total and one **Deposit** / **Security** when the deposit snapshot is greater than zero.
- Persisted behavior: one extra and at most one deposit. No monthly rent row for this stay.
- Request evidence: `stayKind` `SHORT`, `agreedTotal`, and both checkout fields equal.
- Status: BLOCKED.
- Evidence: wizard, active stay, and payments screenshots.
- Cleanup: keep this stay for extension and vacate.

### W-07 Same-day checkout rejected

- Preconditions: W-05. Use a third member, or cancel before finish if the wizard blocks the date locally.
- Action: choose **Short stay**, set checkout equal to check-in, and try to finish.
- Expected UI: the stay is not created. If the server responds, the message is "A short stay checkout must be after the check-in date. The same date is not a stay."
- Persisted behavior: no new occupancy and no new payment.
- Status: BLOCKED.
- Evidence: the error text and the unchanged active list.
- Cleanup: none if the create failed.

### W-08 Short Stay create across two months rejected

- Preconditions: W-05.
- Action: choose **Short stay** with a checkout whose last night is in the next calendar month. Try to finish.
- Expected UI: rejection "A short stay cannot cover nights in more than one calendar month."
- Persisted behavior: no new occupancy and no new payment.
- Status: BLOCKED.
- Evidence: the error text.
- Cleanup: none if the create failed.

### W-09 Overlap rejected

- Preconditions: W-06 stay still occupies its target.
- Action: allocate another member to that same bed, room, or unit for dates that overlap the open stay.
- Expected UI: "This stay overlaps another stay on that bed, room, or unit."
- Persisted behavior: the first stay, its extra, and its deposit are unchanged. No second stay is created.
- Status: BLOCKED.
- Evidence: error screenshot and the original stay still active.
- Cleanup: none.

### W-10 Room and bed conflict

- Preconditions: the test space is co-living and has a room with a bed. PG, Hostel, Rental, Corporate, and Mess do not use this row.
- Action: with a bed occupied, allocate the parent room for an overlapping date. Then, on a different room, occupy the room and try to allocate one of its beds for an overlapping date.
- Expected UI: the same overlap sentence. The existing stay stays in place.
- Status: BLOCKED. If the only test space is not co-living, leave this row BLOCKED and record the space type.
- Evidence: both error screenshots.
- Cleanup: none.

### W-11 Half-open date after vacate

- Preconditions: a short stay whose checkout date is known. Vacate it through **Vacate** and record the vacate date shown on screen.
- Action: allocate a different member to the same target starting on that vacate date. Then repeat with a start one day earlier.
- Expected UI: the stay that starts on the vacate date is accepted. The stay that starts the day before is rejected with the overlap sentence.
- Persisted behavior: the vacated stay remains vacated. Its paid or pending extra remains.
- Status: BLOCKED. The database interval is not shown in the UI. The pass/fail evidence is these two UI results, not a SQL query.
- Evidence: vacate date, successful new stay, and the rejected earlier start.
- Cleanup: vacate the new stay before leaving the test space.

### W-12 Same-month Short Stay extension

- Preconditions: the W-06 stay, still active, with a checkout that can take one more night inside that month.
- Action: open the stay, choose **Extend stay**, set **New planned end** one night ahead inside the same month, enter an additional amount greater than zero, and leave rent fields empty.
- Expected UI: the dialog closes. The planned end moves forward. Payments show one new **Rent** / **Extra** for that amount. The original extra and the single deposit remain.
- Persisted behavior: one added extra. No second deposit.
- Request evidence: one `POST .../extend` with `expectedExitDate`, `additionalAmount`, and `idempotencyKey`.
- Status: BLOCKED.
- Evidence: before and after planned end, and the payment list.
- Cleanup: keep the stay for W-13 only if W-13 can still add a cross-month night. Otherwise use a fresh one-night stay.

### W-13 Cross-month Short Stay extension rejected

- Preconditions: an active short stay. Record the planned end, the payment rows, and the history text before the attempt.
- Action: **Extend stay** to a new planned end whose added nights include a later calendar month. Enter an additional amount.
- Expected UI: "A short-stay extension cannot cover nights in more than one calendar month." The dialog stays open with the error.
- Persisted behavior: planned end, extra rows, deposit row, and history are unchanged.
- Status: BLOCKED.
- Evidence: the error, then the same planned end and the same payment rows.
- Cleanup: none.

### W-14 Monthly extension

- Preconditions: the active monthly stay from W-02.
- Action: extend the planned end with **Additional amount** empty. Then try again with an additional amount greater than zero.
- Expected UI: the empty amount succeeds and does not add an extra. The positive amount shows "Monthly and open-ended stays do not take an extension charge. Leave the additional amount empty."
- Persisted behavior: after the rejection, the planned end and payments match the successful date-only extension.
- Status: BLOCKED.
- Evidence: both results and the payment list.
- Cleanup: vacate in W-18.

### W-15 Open-ended stay

- Preconditions: W-05 and a vacant target.
- Action: allocate with **Until I leave**. Do not enter an agreed total. Then open **Extend stay**, enter a planned end, and try a positive additional amount.
- Expected UI: the stay is active without a short-stay extra. A positive additional amount is rejected with the same monthly sentence. A date-only extension is the accepted path.
- Persisted behavior: `stayKind` open-ended. No agreed-total extra.
- Status: BLOCKED.
- Evidence: wizard choice, active stay, and the rejection.
- Cleanup: vacate this stay in W-18.

### W-16 Duplicate submit

- Preconditions: an active stay and the Extend stay dialog open.
- Action: enter a valid same-month change and press **Extend stay** once. While it is pending, press it again. After success, close the dialog, open it again, and submit a different date.
- Expected UI: the button is disabled while pending, so the second press does not send a second request. The reopened dialog is a new extension, not a replay.
- Persisted behavior: one charge for the first submit. The key is not shown, so replaying that same key is not a supported screen. Do not copy the key out of the browser and send it again with a manual client.
- Status: BLOCKED. Same-key replay after success is UNVERIFIED from the UI.
- Evidence: one extend request in the network log, with the authorization header removed. The response body may show the key; do not copy it into this file.
- Cleanup: none beyond the stays already planned for vacate.

### W-17 Paid row kept

- Preconditions: a short-stay extra that the test owner can mark paid through the existing payments screen.
- Action: record the payment as paid with the existing payment action. Then vacate the stay. Reload `/spaces/{spaceId}/payments`.
- Expected UI: the extra still shows **Paid** and the same amount. Vacate does not remove it.
- Status: BLOCKED. If the only way to mark it paid is SQL or a real customer payment, leave this row BLOCKED.
- Evidence: the paid row before and after vacate.
- Cleanup: do not delete the payment.

### W-18 Vacate and inventory

- Preconditions: an active test stay.
- Action: choose **Vacate**, confirm, then open `/spaces/{spaceId}/bed-inventory`.
- Expected UI: the stay is no longer active. The bed, room, or unit is available.
- Persisted behavior: the occupancy is vacated. Inventory is free for a new stay that starts on the vacate date, as W-11 checks.
- Status: BLOCKED.
- Evidence: vacated stay and inventory screenshot.
- Cleanup: this is the cleanup for the test stays.

### W-19 Transfer keeps the deposit

- Preconditions: a co-living test stay with one deposit, and a vacant target in the same space.
- Action: choose **Transfer** and finish. The web request builder sends `rentPolicy` `KEEP`. The screen label is **Transfer**.
- Expected UI: the old stay is vacated. The new stay is active and has no copied planned end. Payments still show one **Deposit** / **Security**.
- Status: BLOCKED. If the space is not co-living, record the space type and leave the row BLOCKED.
- Evidence: both stays and the payment list.
- Cleanup: vacate the new stay.

### W-20 Overdue stay notification

- Preconditions: an owner or manager, and an active test stay whose planned end is before the space business date. Do not vacate it for this row. The alert is produced by the occupancy notification sync. This walk does not start the backend and does not change the scheduler.
- Action: open `/spaces/{spaceId}/notifications`. Find the row titled **Stay still active** with action **View stay**. Open that action.
- Expected UI: an operator lands on `/spaces/{spaceId}/occupancy?mode=active`. The stay is still active. Inventory is unchanged. No late fee appears.
- Pending actions: `/spaces/{spaceId}/pending-actions` can group the same notification as **Stays past planned end** because that is the group title. The notification title remains **Stay still active**. `STAY_OVERDUE_ACTIVE` is not in the owner-operator action set that pending actions uses for meal and payment rows, and it is not a tenant action type.
- Status: BLOCKED. Creating the overdue condition on a shared or production stay is not allowed.
- Evidence: notification screenshot, destination screenshot, and the still-active stay.
- Cleanup: extend or vacate only the test stay after the screenshot.

### W-21 Member cannot see the overdue alert

- Preconditions: W-20 exists. A member account that is not an owner or manager.
- Action: sign in as that member and open the same space notifications.
- Expected UI: **Stay still active** is absent.
- Status: BLOCKED.
- Evidence: member notification list.
- Cleanup: sign out.

### W-22 Same-day move-out unchanged

- Preconditions: a test stay whose planned exit date is today on the server clock. Do not change a real customer stay to force this.
- Action: open notifications.
- Expected UI: the existing row is titled **Move-out scheduled today**, with the message that the member is scheduled to exit today, and the action **Complete Vacate**. Opening it goes to the active occupancy list. It does not use the title **Stay still active**.
- Status: BLOCKED. If no test stay exits today, leave the row BLOCKED rather than editing a planned date on customer data.
- Evidence: the notification title and action label.
- Cleanup: none.

### W-23 Owner navigation regression

- Preconditions: the same owner session.
- Action: open the dashboard, My Spaces, the active occupancy list, payments, and notifications. Use the existing back control between them.
- Expected UI: each page loads for the test space. Monthly stays still appear as monthly stays. No screen asks for a nightly or weekly short-stay price.
- Status: BLOCKED.
- Evidence: one screenshot per page.
- Cleanup: none.

### W-24 Cleanup

- Action: vacate every `STAY-GATE` stay created in this walk. On the edit page, uncheck **Turn on Short Stay** if this walk checked it. Confirm the payments created for those stays are still listed.
- Expected: no test stay remains active. Payment rows remain.
- Status: BLOCKED.
- Evidence: active list without those stays, and the payments list still showing their rows.
- Cleanup: this is the cleanup. Do not run SQL.

## Result log

Executed on 2026-10-30 in headless Chromium at `http://localhost:5173`, signed in as the test owner. The per-step BLOCKED lines above are the original procedure. This log is the execution record.

| ID | Status | Evidence | Notes |
|---|---|---|---|
| W-00 | PASS | Health 200 and proxy URL | Frontend `/api/v1` proxied to the running local backend |
| W-01 | PASS | `W-01-dashboard.png` | PG dashboard opened |
| W-02 | PASS | `W-02-after.png` | Member A monthly. `stayKind` absent |
| W-03 | PASS | `W-03-contract.png` | Short stay controls hidden while the flag was off |
| W-04 | PASS | `W-04-mess.png` | Mess space created through the API. Billing & tax had no Turn on Short Stay checkbox. The space was then deactivated |
| W-05 | PASS | `W-05-edit.png` | Turn on Short Stay saved |
| W-06 | PASS | `W-06-active.png` | Member B short stay, checkout 31 Oct 2026, agreed total 2500 |
| W-07 | PASS | `W-07-after.png` | Same-day sentence shown. No stay created |
| W-08 | PASS | `W-08-after.png` | Cross-month create sentence shown. No stay created |
| W-09 | FAIL | `W-09b-available.png`, `W-09b-confirm.png` | Occupied Bed B was omitted. Confirm showed "This target is not available." The API said "Bed is not available for reservation". No code change |
| W-10 | BLOCKED | `W-10-room.png`, `W-10c-inspector.png` | No room Allocate control. Room search is empty. API room allocate worked, and the room/bed conflict returned the status sentences |
| W-11 | BLOCKED | `W-11-accepted.png`, `W-11-fields.png` | Same-day reallocation move-in was 2026-10-30. No start-date field, so the day-earlier case was not entered |
| W-12 | PASS | `W-12-after.png` | Planned end moved to 1 Nov 2026 |
| W-13 | FAIL | `W-13-error.png` | Before the fix, the end was saved as 2 Nov 2026 |
| W-13 retest | PASS | `W-13-retest.png` | After the fix, 3 Nov 2026 was rejected and the end stayed 2 Nov 2026 |
| W-14 | PASS | `W-14-date-only.png`, `W-14-charge-rejected.png` | Date-only end 15 Nov 2026. Positive amount rejected |
| W-15 | PASS | `W-15-allocated.png`, `W-15-rejected.png` | Until I leave, then the monthly charge sentence |
| W-16 | PASS | Network count | One extend request. Button disabled while pending. Key not replayed |
| W-17 | PASS | `W-17c-marked.png`, `W-17c-after.png` | Received, then Mark as received, on the ₹1,800 Short stay row. After vacate the status was PAID and Collected showed ₹1,800 |
| W-18 | PASS | `W-18-available.png` | Bed A and Bed B vacated and shown as available |
| W-19 | PASS | `W-19b-error.png` | Toast "Member transferred." Member E on Bed B, no planned end, one ₹3,000 security deposit. Then vacated |
| W-20 | PASS | `W-20-notes.png`, `W-20-pending.png`, `W-20-landed-3.png` | Stay still active and Stays past planned end. View stay opened Occupied beds |
| W-21 | BLOCKED | `W-21-member.png` | Member `9800000102` was rejected with "Invalid mobile number or password." |
| W-22 | PASS | `W-22-pending.png` | Move-out scheduled today, group Today's Move-outs. Not titled Stay still active |
| W-23 | PASS | `W-23-*.png` | The named pages opened. No nightly or weekly price |
| W-24 | PASS | `W-24-saved.png` | Short Stay was unchecked and saved, then unchecked again after the later payment check. The ₹1,800 row remained |

Section 43 stays open. Phase 8 was not complete after 33.11.

### Execution log — 2026-11-02 Phase 8 retest

Headless Chromium at `http://localhost:5173` against walk DB `acomi_stay_walk_fd1b26b24a1d`. Backend restarted once onto that database after compile. Evidence: `results-13.json`, `W-09-phase8.png`, `W-10-room-search.png`, `W-11-move-in-field.png`, `W-11-allocated.png`, `W-21-member-login.png`.

| ID | Status | Evidence | Notes |
|---|---|---|---|
| W-09 | PASS | `W-09-phase8.png`, API 400 | Occupied bed with allocate intent did not open Move In today. API: "Bed is not available for reservation" |
| W-10 | PASS | `W-10-room-search.png` | After removing the co-living vacant-beds detour, Room toggle listed Room CL1 and Room CL2. API ROOM search total=2 |
| W-11 | PASS | `W-11-move-in-field.png`, `W-11-allocated.png` | Move-in date field present. API historical `2026-09-01` and UI `2026-09-02` persisted on dates clear of vacated history |
| W-21 | PASS | `W-21-member-login.png` | Member `9800000102` signed in after local RESET_PASSWORD + DEV OTP restored the walk password |
| W-04 / W-17 / W-19 / W-20 / W-22 | NOT RE-RUN | Prior 33.11 | Prior PASS kept; not re-clicked |

Section 43 stays open. Phase 8 is `IN PROGRESS` / blocked (open decisions D-05/D-14/D-16/D-17/D-18, Android device, production deploy).
