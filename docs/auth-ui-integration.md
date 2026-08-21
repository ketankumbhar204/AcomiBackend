# Acomi — Authentication UI Integration Guide

Frontend reference for **Login / Registration** in React Native and web.

---

## Current production authentication

ACOMI uses **password authentication**. OTP is **not** part of the current user-facing login or registration flow.

| Concept | How it works |
|---------|--------------|
| Registration | Name + mobile + password + confirm password → `POST /api/v1/auth/register` → user created, password hashed, JWT returned |
| Login | Mobile + password → `POST /api/v1/auth/login` → active user lookup, password verified, JWT returned |
| Session | Existing JWT Bearer token. Default lifetime 24 hours (`86400000` ms) |
| Account deletion (in-app) | Authenticated `DELETE /api/v1/auth/me` |
| Account deletion (web) | `POST /api/v1/auth/account-deletion/password` |

Passwords are stored only as bcrypt hashes. They are never returned to clients. Invalid login credentials always return **401** `"Invalid mobile number or password."`

`mobileVerifiedAt` is **null** for password-only registration. No production login, security, onboarding, or business rule currently requires it to be set. The column is reserved for future OTP.

---

## Future: OTP (reserved)

OTP infrastructure remains in the backend for a later implementation:

- `POST /api/v1/auth/send-otp`
- `POST /api/v1/auth/verify-otp`
- `POST /api/v1/auth/account-deletion` (OTP deletion)

These endpoints must **not** be used as the current production login/register UI. There is **no** hardcoded OTP (`111111`, `123456`). Production OTP sender is `none` (disabled until a real SMS provider is wired).

See [Reserved OTP APIs](#reserved-otp-apis) below.

---

## Base URL

| Environment | URL |
|-------------|-----|
| Production API | `https://api.acomi.in/api/v1` |
| Production web | `https://app.acomi.in` |
| Local backend | `http://localhost:8080/api/v1` |
| Android emulator (local) | `http://10.0.2.2:8080/api/v1` |

All requests: `Content-Type: application/json`  
Protected requests: `Authorization: Bearer <accessToken>`

---

## Current auth flow

```
Registration:
  Name + Mobile + Password + Confirm
        → POST /api/v1/auth/register
        → JWT + user
        → Authenticated app

Login:
  Mobile + Password
        → POST /api/v1/auth/login
        → JWT + user
        → Authenticated app

Bootstrap:
  Stored token → GET /api/v1/auth/me
  200 → stay signed in
  401 → clear token, login
```

No OTP input, countdown, or verification token is required for the current production screens.

---

## Common response envelope

```json
{
  "success": true,
  "message": "Optional message",
  "data": {},
  "timestamp": "2026-06-08T10:30:00"
}
```

Validation errors (`400`) may include a field map in `data`.

---

## API: Register (current production)

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/auth/register` |
| **Auth** | None (public) |

### Request

```json
{
  "fullName": "Priya Sharma",
  "mobileNumber": "9876543210",
  "password": "Secret12",
  "confirmPassword": "Secret12"
}
```

`name` is accepted as an alias for `fullName`.  
`verificationToken` is optional and reserved for future OTP-verified registration. Current clients omit it.

| Field | Validation |
|-------|------------|
| `fullName` | Required, max 255 |
| `mobileNumber` | `^[6-9]\d{9}$` |
| `password` | 8–72 characters |
| `confirmPassword` | Must match `password` |

### Success — `200`

```json
{
  "success": true,
  "message": "Account created successfully",
  "data": {
    "accessToken": "<jwt>",
    "tokenType": "Bearer",
    "expiresIn": 86400000,
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "mobileNumber": "9876543210",
      "fullName": "Priya Sharma",
      "active": true
    }
  }
}
```

`user` never includes `password` or `passwordHash`.

### Failures

| HTTP | Message |
|------|---------|
| `400` | Validation failed / `Passwords do not match` |
| `409` | `This mobile number is already registered.` |

A previously deleted/anonymized mobile can register again (partial unique index on active mobiles).

---

## API: Login (current production)

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/auth/login` |
| **Auth** | None (public) |

### Request

```json
{
  "mobileNumber": "9876543210",
  "password": "Secret12"
}
```

### Success — `200`

Same `AuthTokenResponse` as register. Message: `"Login successful"`.

### Failures

| HTTP | Message |
|------|---------|
| `400` | Validation failed |
| `401` | `Invalid mobile number or password.` |

Wrong password, unknown mobile, inactive, and deleted accounts all use the same 401 message.

---

## API: Get current user

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/auth/me` |
| **Auth** | JWT required |

`200` → user profile. `401` → clear token and return to login.

---

## API: Delete account (in-app)

| | |
|---|---|
| **Method** | `DELETE` |
| **Path** | `/api/v1/auth/me` |
| **Auth** | JWT required. Identity comes from the token. Do not send a `userId`. |

Success: **204 No Content**. Personal login data is deleted/anonymized. Shared property records remain.

---

## API: Delete account (web / password)

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/auth/account-deletion/password` |
| **Auth** | Public |

```json
{
  "mobileNumber": "9876543210",
  "password": "Secret12"
}
```

Success: **204**. Invalid credentials: **401** `"Invalid mobile number or password."`

Public page: `https://app.acomi.in/delete-account`  
Privacy: `https://app.acomi.in/privacy`

Full semantics: [account-deletion.md](./account-deletion.md)

---

## Protected APIs (require JWT)

After login, other APIs require:

```
Authorization: Bearer <accessToken>
```

Missing or expired token → `401`. Deleted/inactive users cannot use leftover JWTs.

---

## TypeScript types (current production)

```typescript
export interface RegisterRequest {
  fullName: string;
  mobileNumber: string;
  password: string;
  confirmPassword: string;
  verificationToken?: string; // reserved for future OTP
}

export interface LoginRequest {
  mobileNumber: string;
  password: string;
}

export interface AuthTokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: UserResponse;
}
```

Store `accessToken` and `user` after register/login. Never store the password.

---

## Client-side validation

| Field | Rule |
|-------|------|
| Mobile | 10 digits, starts with 6–9 |
| Password | 8–72 characters |
| Confirm password | Must match password (register only) |

---

## Error handling

| Scenario | HTTP | UI |
|----------|------|----|
| Invalid mobile / password length | `400` | Inline field errors |
| Wrong password / unknown / deleted | `401` | `Invalid mobile number or password.` |
| Duplicate active mobile | `409` | Already registered |
| Expired token | `401` | Clear session, login |
| Network | — | Connection error |

---

## Reserved OTP APIs

Not used by the current production UI. Kept for a later OTP implementation.

### Send OTP — `POST /api/v1/auth/send-otp`

```json
{ "mobileNumber": "9876543210", "purpose": "REGISTER" }
```

`purpose` is `REGISTER` or `ACCOUNT_DELETION`. Success does **not** return the OTP code. Production sender `none` returns **503**.

### Verify OTP — `POST /api/v1/auth/verify-otp`

```json
{ "mobileNumber": "9876543210", "otp": "482731", "purpose": "REGISTER" }
```

Success returns a short-lived `verificationToken` for a future OTP-gated register. It does **not** return a JWT session.

### OTP account deletion — `POST /api/v1/auth/account-deletion`

Public OTP deletion. Current web UI uses the password deletion endpoint instead.

---

## Related docs

- [account-deletion.md](./account-deletion.md)
- [api-reference.md](./api-reference.md)
- [domain-model.md](./domain-model.md)
