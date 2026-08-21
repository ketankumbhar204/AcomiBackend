# Account deletion

ACOMI deletes the authenticated login and personal account data. It does **not** only set `is_active = false`.

## Paths

| Surface | How |
|---------|-----|
| Mobile app | Profile & Settings → Delete account → confirm → `DELETE /api/v1/auth/me` |
| Web (no app required) | https://app.acomi.in/delete-account → mobile + password → confirm → `POST /api/v1/auth/account-deletion/password` |

| Privacy policy | https://app.acomi.in/privacy |

Both surfaces call the same backend service: `AccountDeletionService`.

## Verification

- **In-app:** JWT of the signed-in user. The client never sends a `userId`.
- **Web:** Password (`POST /auth/account-deletion/password` with mobile + password). OTP deletion (`POST /auth/account-deletion`) remains implemented for future use and is not linked from the current production page. Neither path creates a new account if none exists.

## DELETE

- Login credentials / mobile number on `users`
- Profile fields: name, email, photo, gender, date of birth, address, KYC/profile flags
- Identity documents on member records linked to that login
- Emergency-contact fields on those linked member records
- The user's notification inbox
- Pending invitations **sent by** that user (cancelled)
- Active space memberships (`REMOVED`)

The `users` row is kept (anonymized) because other tables reference `users.id` with RESTRICT foreign keys.

## ANONYMIZE

- `users.mobile_number` → `deleted_{userId}` so the real number can register again
- `users.full_name` → `Deleted user`
- Linked `members` name/mobile → generic deleted-member values; `user_id` unlinked

## RETAIN

| Record | Why |
|--------|-----|
| Spaces, including spaces this user owned | Shared property. There is no ownership-transfer feature. `owner_id` keeps pointing at the anonymized user row so FKs stay valid. The space is not deleted. Remaining managers can keep operating. Owner-only actions are unavailable until a future transfer feature exists. |
| Buildings, floors, units, rooms, beds | Space inventory, not personal account data |
| Occupancy, meals, payments, complaints | Business/operational history of the property |
| Member rows (anonymized) | Needed so occupancy/payment FKs do not break |
| Actor columns (`created_by`, `reviewed_by`, etc.) | Audit FKs to the anonymized user id, not to personal profile fields |
| Invitation **invitee** mobile numbers | Belong to the invited person, not the deleted account |

These are retained because they are shared business records or database integrity references, not because a specific statute was identified.

## Same mobile number

After deletion, password registration on that mobile creates a **new** user. The old anonymized row no longer holds the number (partial unique index on active mobiles).

## Tokens

Inactive/anonymized users are not loaded by `CustomUserDetailsService`. Leftover JWTs are treated as unauthenticated (`401`).

## Errors

| Status | When |
|--------|------|
| `401` | `DELETE /me` without a valid JWT; web password deletion with invalid mobile/password |
| `400` | Invalid OTP on the unused web OTP deletion endpoint |
| `204` | Success. OTP web deletion also returns 204 for already deleted or unknown mobile after a valid OTP (does not enumerate accounts). Password web deletion uses the same generic 401 as login when credentials are wrong. |
| `500` | Unexpected failure; transaction rolls back |
