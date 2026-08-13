# Acomi — Authentication UI Integration Guide

Frontend reference for building **Login / Registration** screens in React Native (or any mobile/web client).

This document covers the complete auth flow, API contracts, token handling, screen mapping, and error handling.

---

## Overview

Acomi uses **Mobile Number + OTP** authentication. There is **no password** and **no separate registration API**.

| Concept | How it works |
|---------|--------------|
| Login | User enters mobile → receives OTP → verifies OTP |
| Registration | **Automatic** — if mobile number is new, user is created on successful OTP verification |
| Session | JWT Bearer token returned after OTP verification |
| Token lifetime | 24 hours (86,400,000 ms) by default |

**MVP OTP:** Use value from `acomi.otp.mvp-code` in backend `application.yml` (default `111111` for development).

---

## Base URL

| Environment | URL |
|-------------|-----|
| Android emulator | `http://10.0.2.2:8080` |
| iOS simulator | `http://localhost:8080` |
| Physical device | `http://<YOUR_PC_LAN_IP>:8080` |

All requests use header: `Content-Type: application/json`

Protected requests also need: `Authorization: Bearer <accessToken>`

---

## Auth flow (UI screens)

```
┌─────────────────┐     POST /auth/send-otp      ┌─────────────────┐
│  Login Screen   │ ─────────────────────────► │  OTP Screen     │
│  (mobile input) │                              │  (6-digit OTP)  │
└─────────────────┘                              └────────┬────────┘
                                                          │
                                              POST /auth/verify-otp
                                                          │
                                                          ▼
                                               ┌─────────────────┐
                                               │  Home / App     │
                                               │  (store token)  │
                                               └─────────────────┘
```

### Screen 1: Login (Mobile Number)

- Single input: 10-digit Indian mobile number
- Validation: must start with 6–9, exactly 10 digits
- Button: **Send OTP**
- API: `POST /api/v1/auth/send-otp`
- On success → navigate to OTP screen, pass `mobileNumber`

### Screen 2: OTP Verification

- Input: 6-digit OTP
- Button: **Verify & Continue**
- API: `POST /api/v1/auth/verify-otp`
- On success → store `accessToken` + `user`, navigate to home
- MVP hint (dev only): show "Use OTP: 123456" on screen

### Screen 3: App bootstrap (on app launch)

- Read stored token from AsyncStorage / SecureStore
- If token exists → call `GET /api/v1/auth/me` to validate session
- If `200` → user is logged in, go to home
- If `401` → clear token, go to login

---

## Common response envelope

Every API returns:

```json
{
  "success": true,
  "message": "Optional message",
  "data": {},
  "timestamp": "2026-06-08T10:30:00"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `success` | boolean | `true` = OK, `false` = error |
| `message` | string | Human-readable message (optional on some GETs) |
| `data` | object | Response payload (shape varies per endpoint) |
| `timestamp` | string | Server timestamp |

### Error response

```json
{
  "success": false,
  "message": "Invalid OTP",
  "timestamp": "2026-06-08T10:30:00"
}
```

Validation errors (`400`) include field map in `data`:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "mobileNumber": "Mobile number must be a valid 10-digit Indian number",
    "otp": "OTP must be 6 digits"
  }
}
```

---

## API 1: Send OTP

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/auth/send-otp` |
| **Auth** | None (public) |

### Request

```json
{
  "mobileNumber": "9876543210"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `mobileNumber` | string | Yes | 10 digits, starts with 6–9 |

### Success — `200`

```json
{
  "success": true,
  "message": "OTP sent successfully",
  "data": {
    "mobileNumber": "9876543210",
    "message": "OTP sent successfully"
  },
  "timestamp": "2026-06-08T10:30:00"
}
```

### Failure examples

| HTTP | `message` | UI action |
|------|-----------|-----------|
| `400` | `Validation failed` | Show field errors under inputs |
| `500` | `An unexpected error occurred...` | Show retry button |

### UI notes

- Show loading spinner on button while request is in flight
- Disable button if mobile number is invalid
- No OTP is sent via SMS in MVP — backend logs `123456` to server console

---

## API 2: Verify OTP (Login + Auto-Register)

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/auth/verify-otp` |
| **Auth** | None (public) |

### Request

```json
{
  "mobileNumber": "9876543210",
  "otp": "123456"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `mobileNumber` | string | Yes | Same as send-otp |
| `otp` | string | Yes | Exactly 6 digits |

### Success — `200`

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400000,
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "mobileNumber": "9876543210",
      "fullName": "User",
      "profilePhotoUrl": null,
      "active": true,
      "createdAt": "2026-06-08T10:30:00"
    }
  },
  "timestamp": "2026-06-08T10:30:00"
}
```

### Response fields to store

| Field | Store where | Purpose |
|-------|-------------|---------|
| `data.accessToken` | AsyncStorage / SecureStore | Attach to all protected API calls |
| `data.user.id` | App state / AsyncStorage | `ownerId` for create space, user context |
| `data.user.mobileNumber` | App state | Display on profile |
| `data.user.fullName` | App state | Display name (default `"User"` for new accounts) |
| `data.expiresIn` | Optional | Token expiry countdown (ms) |

### Failure examples

| HTTP | `message` | UI action |
|------|-----------|-----------|
| `400` | `Invalid OTP` | Show "Wrong OTP, try again" |
| `400` | `Validation failed` | Show field errors |
| `400` | `User account is inactive` | Show "Account disabled" message |
| `500` | Unexpected error | Show retry |

### New vs returning user

Both use the **same flow**. Backend creates the user automatically if the mobile number does not exist:

- **New user:** `fullName` defaults to `"User"` — update later via profile API (not built yet)
- **Returning user:** Same OTP flow, existing profile returned

---

## API 3: Get Current User (Session check)

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/auth/me` |
| **Auth** | **Required** — `Authorization: Bearer <accessToken>` |

### Request

No body. Header only:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### Success — `200`

```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "mobileNumber": "9876543210",
    "fullName": "User",
    "profilePhotoUrl": null,
    "active": true,
    "createdAt": "2026-06-08T10:30:00"
  },
  "timestamp": "2026-06-08T10:30:00"
}
```

### Failure — `401`

```json
{
  "success": false,
  "message": "Authentication required. Please provide a valid JWT token."
}
```

### UI usage

- Call on app launch to restore session
- Call on profile screen to show current user
- On `401` → clear stored token, redirect to login

---

## Protected APIs (require JWT)

After login, **all other APIs** require the Bearer token:

```
Authorization: Bearer <accessToken>
```

| Endpoint | Purpose |
|----------|---------|
| `POST /api/v1/spaces` | Create space |
| `GET /api/v1/spaces/user/{userId}` | List user's spaces |
| `POST /api/v1/invitations` | Send invitation |
| `POST /api/v1/invitations/{id}/accept` | Accept invitation |
| `GET /api/v1/auth/me` | Current user profile |

If token is missing or expired → `401` on any protected endpoint.

---

## TypeScript types

```typescript
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  timestamp?: string;
}

export interface SendOtpRequest {
  mobileNumber: string;
}

export interface SendOtpResponse {
  mobileNumber: string;
  message: string;
}

export interface VerifyOtpRequest {
  mobileNumber: string;
  otp: string;
}

export interface UserResponse {
  id: string;
  mobileNumber: string;
  fullName: string;
  profilePhotoUrl?: string | null;
  active: boolean;
  createdAt: string;
}

export interface AuthTokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: UserResponse;
}
```

---

## React Native integration

### Suggested file structure

```
src/
  api/
    types.ts          ← interfaces above
    client.ts         ← fetch wrapper with token injection
    authApi.ts        ← sendOtp, verifyOtp, getMe
  store/
    authStore.ts      ← Zustand/Context for user + token
  screens/
    LoginScreen.tsx   ← mobile input → send OTP
    OtpScreen.tsx     ← OTP input → verify
  hooks/
    useAuth.ts        ← login, logout, isAuthenticated
```

### API client with token

```typescript
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';
import type { ApiResponse } from './types';

const TOKEN_KEY = 'acomi_access_token';

export const API_BASE_URL =
  Platform.OS === 'android'
    ? 'http://10.0.2.2:8080'
    : 'http://localhost:8080';

export async function getStoredToken(): Promise<string | null> {
  return AsyncStorage.getItem(TOKEN_KEY);
}

export async function setStoredToken(token: string): Promise<void> {
  await AsyncStorage.setItem(TOKEN_KEY, token);
}

export async function clearStoredToken(): Promise<void> {
  await AsyncStorage.removeItem(TOKEN_KEY);
}

export async function apiRequest<T>(
  path: string,
  options: { method?: string; body?: unknown; auth?: boolean } = {},
): Promise<T> {
  const { method = 'GET', body, auth = true } = options;

  const headers: Record<string, string> = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  };

  if (auth) {
    const token = await getStoredToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });

  const json = (await response.json()) as ApiResponse<T>;

  if (!response.ok || !json.success) {
    if (response.status === 401) await clearStoredToken();
    throw { status: response.status, message: json.message, data: json.data };
  }

  return json.data as T;
}
```

### Auth API functions

```typescript
import { apiRequest, setStoredToken } from './client';
import type {
  AuthTokenResponse,
  SendOtpRequest,
  SendOtpResponse,
  UserResponse,
  VerifyOtpRequest,
} from './types';

export const authApi = {
  sendOtp(payload: SendOtpRequest) {
    return apiRequest<SendOtpResponse>('/api/v1/auth/send-otp', {
      method: 'POST',
      body: payload,
      auth: false,
    });
  },

  async verifyOtp(payload: VerifyOtpRequest) {
    const result = await apiRequest<AuthTokenResponse>('/api/v1/auth/verify-otp', {
      method: 'POST',
      body: payload,
      auth: false,
    });
    await setStoredToken(result.accessToken);
    return result;
  },

  getMe() {
    return apiRequest<UserResponse>('/api/v1/auth/me');
  },
};
```

### Login hook example

```typescript
import { useState } from 'react';
import { authApi } from '../api/authApi';

export function useLogin() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const sendOtp = async (mobileNumber: string) => {
    setLoading(true);
    setError(null);
    try {
      await authApi.sendOtp({ mobileNumber });
      return true;
    } catch (e: any) {
      setError(e.message ?? 'Failed to send OTP');
      return false;
    } finally {
      setLoading(false);
    }
  };

  const verifyOtp = async (mobileNumber: string, otp: string) => {
    setLoading(true);
    setError(null);
    try {
      const result = await authApi.verifyOtp({ mobileNumber, otp });
      return result; // { accessToken, user, ... }
    } catch (e: any) {
      setError(e.message ?? 'Invalid OTP');
      return null;
    } finally {
      setLoading(false);
    }
  };

  return { sendOtp, verifyOtp, loading, error };
}
```

---

## cURL test sequence

```bash
# Step 1: Send OTP
curl -X POST http://localhost:8080/api/v1/auth/send-otp \
  -H "Content-Type: application/json" \
  -d '{"mobileNumber":"9876543210"}'

# Step 2: Verify OTP (creates user if new)
curl -X POST http://localhost:8080/api/v1/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{"mobileNumber":"9876543210","otp":"123456"}'

# Step 3: Get current user (replace TOKEN)
curl http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer <TOKEN>"

# Step 4: Use token on protected APIs
curl http://localhost:8080/api/v1/spaces/user/<USER_ID> \
  -H "Authorization: Bearer <TOKEN>"
```

---

## UI validation rules (client-side)

Match these before calling the API to avoid unnecessary 400 errors.

| Field | Rule | Example valid | Example invalid |
|-------|------|---------------|-----------------|
| Mobile | 10 digits, starts 6–9 | `9876543210` | `5876543210`, `98765` |
| OTP | Exactly 6 digits | `123456` | `12345`, `abcdef` |

---

## Error handling checklist

| Scenario | HTTP | What to show |
|----------|------|--------------|
| Invalid mobile format | `400` | Inline error under mobile field |
| Invalid OTP | `400` | "Incorrect OTP. Please try again." |
| Expired / invalid token | `401` | Redirect to login, clear stored token |
| Account inactive | `400` | "Your account has been disabled." |
| Network failure | — | "Unable to connect. Check your internet." |
| Server error | `500` | "Something went wrong. Try again." |

---

## Post-login navigation

After successful `verify-otp`:

1. Store `accessToken` and `user` in app state + AsyncStorage
2. Use `user.id` as `ownerId` when creating a space
3. Call `GET /api/v1/spaces/user/{user.id}` to load "My Spaces"
4. Navigate to Home / Dashboard

---

## MVP limitations (for UI team)

| Feature | Status | UI impact |
|---------|--------|-----------|
| Real SMS / WhatsApp OTP | Not built | Show dev hint: OTP is `123456` |
| Separate registration screen | Not needed | Login screen handles both new + returning users |
| Profile update API | Not built | `fullName` stays `"User"` until profile API is added |
| Logout API | Not built | Clear token locally on logout button |
| Token refresh | Not built | Re-login when token expires (24h) |
| Password login | Not supported | OTP only |

---

## Cursor / AI prompt for UI implementation

Copy this when building auth screens:

```
Build Acomi login flow in React Native using the auth APIs documented in docs/auth-ui-integration.md.

Screens:
1. LoginScreen - mobile number input (10 digit Indian), Send OTP button
2. OtpScreen - 6 digit OTP input, Verify button, dev hint "Use 123456"

APIs (base URL from Platform.OS):
- POST /api/v1/auth/send-otp  body: { mobileNumber }
- POST /api/v1/auth/verify-otp body: { mobileNumber, otp }
- GET  /api/v1/auth/me header: Authorization: Bearer <token>

On verify success:
- Store accessToken in AsyncStorage
- Store user object in Zustand auth store
- Navigate to Home

On app launch:
- If token exists, call GET /auth/me
- 200 → Home, 401 → Login

All other APIs require Authorization: Bearer header.

Use TypeScript. Handle 400 validation errors and 401 auth errors.
Match validation: mobile ^[6-9]\d{9}$, otp ^\d{6}$.
```

---

## Related docs

- [api-reference.md](./api-reference.md) — All backend endpoints
- [domain-model.md](./domain-model.md) — User entity and business rules
- [backend-context.md](./backend-context.md) — Product context
