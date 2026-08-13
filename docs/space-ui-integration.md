# Acomi — Space Management UI Integration Guide

Frontend reference for integrating **Space Management** APIs in React Native (or any mobile/web client).

Covers all space endpoints, request/response models, TypeScript types, screen flows, and error handling.

---

## Overview

A **Space** represents a business or workspace (PG, Mess, Hostel, Co-living, Rental). Users can own spaces or join them via memberships.

| Concept | How it works |
|---------|--------------|
| Create space | Owner creates a space; backend auto-creates an `OWNER` membership |
| List spaces | Fetch all active spaces for a user (owned + joined) |
| View details | Fetch full details of one active space |
| Update space | Owner can edit `name`, `address`, `contactNumber` only |
| Deactivate | Owner soft-deletes a space (`isActive = false`); record is not removed |

**Auth:** All space endpoints require a valid JWT.

```
Authorization: Bearer <accessToken>
```

Obtain the token via `POST /api/v1/auth/verify-otp`. See [auth-ui-integration.md](./auth-ui-integration.md).

---

## Base URL

| Environment | URL |
|-------------|-----|
| Android emulator | `http://10.0.2.2:8080` |
| iOS simulator | `http://localhost:8080` |
| Physical device | `http://<YOUR_PC_LAN_IP>:8080` |

All requests use: `Content-Type: application/json`

---

## Common response envelope

Most endpoints return `ApiResponse<T>`:

```json
{
  "success": true,
  "message": "Optional message",
  "data": {},
  "timestamp": "2026-06-10T10:30:00"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `success` | boolean | `true` = OK, `false` = error |
| `message` | string | Human-readable message (optional on some GETs) |
| `data` | object \| array | Response payload |
| `timestamp` | string | ISO-8601 server timestamp |

**Exception:** `DELETE /api/v1/spaces/{spaceId}` returns **`204 No Content`** with an empty body (no envelope).

### Error response

```json
{
  "success": false,
  "message": "Space not found with id: '...'",
  "data": null,
  "timestamp": "2026-06-10T10:30:00"
}
```

Validation errors (`400`) include field map in `data`:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "name": "Space name is required"
  },
  "timestamp": "2026-06-10T10:30:00"
}
```

### HTTP status codes

| Status | When |
|--------|------|
| `200` | Success (GET, PUT) |
| `201` | Space created (POST) |
| `204` | Space deactivated (DELETE) |
| `400` | Validation / business rule |
| `401` | Missing or invalid JWT |
| `403` | Non-owner attempted update or deactivate |
| `404` | Space or user not found (includes deactivated spaces on GET by id) |

---

## Enums

Use exact string values in JSON.

### SpaceType

| Value | Description |
|-------|-------------|
| `PG` | Paying Guest accommodation |
| `MESS` | Meal-providing canteen or mess |
| `HOSTEL` | Hostel-style accommodation |
| `CO_LIVING` | Co-living or shared accommodation |
| `RENTAL` | Rental property or apartment |

### MembershipRole

Returned on user space list. Indicates the user's role in that space.

| Value | Description |
|-------|-------------|
| `OWNER` | Space owner |
| `MANAGER` | Manager |
| `TENANT` | Tenant |
| `CUSTOMER` | Customer |
| `STAFF` | Staff |

---

## TypeScript types

```typescript
export type SpaceType = 'PG' | 'MESS' | 'HOSTEL' | 'CO_LIVING' | 'RENTAL';

export type MembershipRole =
  | 'OWNER'
  | 'MANAGER'
  | 'TENANT'
  | 'CUSTOMER'
  | 'STAFF';

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  timestamp?: string;
}

// --- Requests ---

export interface CreateSpaceRequest {
  name: string;
  type: SpaceType;
  address?: string;
  contactNumber?: string;
  ownerId: string; // UUID of logged-in user
}

export interface UpdateSpaceRequest {
  name: string;
  address?: string;
  contactNumber?: string;
}

// --- Responses ---

/** Returned after POST /spaces */
export interface SpaceResponse {
  id: string;
  name: string;
  type: SpaceType;
  address?: string;
  contactNumber?: string;
  isActive: boolean;
  ownerId: string;
  ownerName: string;
  createdAt: string;
}

/** Returned by GET /spaces/{id} and PUT /spaces/{id} */
export interface SpaceDetailsResponse {
  id: string;
  name: string;
  type: SpaceType;
  address?: string;
  contactNumber?: string;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

/** Item in GET /spaces/user/{userId} list */
export interface UserSpaceResponse {
  spaceId: string;
  spaceName: string;
  spaceType: SpaceType;
  membershipRole: MembershipRole;
  joinedAt: string;
}
```

---

## UI flow (suggested screens)

```
┌──────────────────┐   GET /spaces/user/{userId}   ┌──────────────────┐
│  My Spaces List  │ ◄──────────────────────────── │  (after login)   │
│  (home / spaces) │                               └──────────────────┘
└────────┬─────────┘
         │ tap space
         ▼
┌──────────────────┐   GET /spaces/{spaceId}       ┌──────────────────┐
│  Space Details   │ ◄──────────────────────────── │  Show full info  │
└────────┬─────────┘                               └──────────────────┘
         │ owner actions
         ├──► Edit Space  ──► PUT /spaces/{spaceId}
         └──► Deactivate  ──► DELETE /spaces/{spaceId}

┌──────────────────┐   POST /spaces                ┌──────────────────┐
│  Create Space    │ ─────────────────────────────► │  New space form  │
│  (FAB / button)  │   ownerId = current user id    └──────────────────┘
└──────────────────┘
```

### Screen: My Spaces List

- Call `GET /api/v1/spaces/user/{userId}` with the logged-in user's `id` from auth (`GET /auth/me` or stored user after login).
- Show `spaceName`, `spaceType`, `membershipRole`.
- Optionally format `joinedAt` as a date.
- Tap row → navigate to Space Details with `spaceId`.

### Screen: Create Space

- Form fields: `name` (required), `type` (picker), `address`, `contactNumber`.
- Set `ownerId` to the current user's UUID (from `AuthTokenResponse.user.id`).
- On `201` → navigate to Space Details or refresh list.

### Screen: Space Details

- Call `GET /api/v1/spaces/{spaceId}`.
- Show all `SpaceDetailsResponse` fields.
- If `ownerId === currentUser.id` → show **Edit** and **Deactivate** actions.
- `type` is read-only after creation.

### Screen: Edit Space

- Pre-fill from `SpaceDetailsResponse`.
- Submit `PUT /api/v1/spaces/{spaceId}` with `UpdateSpaceRequest`.
- Only send editable fields: `name`, `address`, `contactNumber`.

### Deactivate confirmation

- Confirm dialog → `DELETE /api/v1/spaces/{spaceId}`.
- On `204` → remove from list or navigate back.
- Deactivated spaces no longer appear in user list and return `404` on GET by id.

---

## API 1: Create Space

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/spaces` |
| **Auth** | Bearer JWT required |

### Request

```json
{
  "name": "Sunrise Apartments",
  "type": "RENTAL",
  "address": "Pune",
  "contactNumber": "9876543210",
  "ownerId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `name` | string | Yes | Non-blank |
| `type` | SpaceType | Yes | One of enum values |
| `address` | string | No | |
| `contactNumber` | string | No | |
| `ownerId` | string (UUID) | Yes | Use logged-in user's id |

### Success — `201`

```json
{
  "success": true,
  "message": "Space created successfully",
  "data": {
    "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "name": "Sunrise Apartments",
    "type": "RENTAL",
    "address": "Pune",
    "contactNumber": "9876543210",
    "isActive": true,
    "ownerId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "ownerName": "Ketan",
    "createdAt": "2026-06-10T10:30:00"
  },
  "timestamp": "2026-06-10T10:30:00"
}
```

### Failure examples

| HTTP | Cause | UI action |
|------|-------|-----------|
| `400` | Missing name or type | Show field errors |
| `401` | No / invalid token | Redirect to login |
| `404` | `ownerId` user not found | Show error, refresh user session |

---

## API 2: Get Space Details

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/spaces/{spaceId}` |
| **Auth** | Bearer JWT required |

### Path parameters

| Param | Type | Description |
|-------|------|-------------|
| `spaceId` | UUID | Space to fetch |

### Success — `200`

```json
{
  "success": true,
  "data": {
    "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "name": "Sunrise PG",
    "type": "PG",
    "address": "Pune",
    "contactNumber": "9876543210",
    "ownerId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "createdAt": "2026-06-10T10:30:00",
    "updatedAt": "2026-06-10T11:00:00"
  },
  "timestamp": "2026-06-10T11:00:00"
}
```

### Failure examples

| HTTP | Cause | UI action |
|------|-------|-----------|
| `401` | No / invalid token | Redirect to login |
| `404` | Space not found or deactivated | Show "Space unavailable" |

---

## API 3: Update Space

| | |
|---|---|
| **Method** | `PUT` |
| **Path** | `/api/v1/spaces/{spaceId}` |
| **Auth** | Bearer JWT required (caller must be owner) |

Only the **JWT user** is checked as owner — no `ownerId` in the request body.

### Request

```json
{
  "name": "Sunrise PG",
  "address": "Pune",
  "contactNumber": "9876543210"
}
```

| Field | Type | Required | Editable |
|-------|------|----------|----------|
| `name` | string | Yes | Yes |
| `address` | string | No | Yes |
| `contactNumber` | string | No | Yes |
| `type` | — | — | **No** (immutable after create) |
| `ownerId` | — | — | **No** |

### Success — `200`

```json
{
  "success": true,
  "message": "Space updated successfully",
  "data": {
    "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "name": "Sunrise PG",
    "type": "PG",
    "address": "Pune",
    "contactNumber": "9876543210",
    "ownerId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "createdAt": "2026-06-10T10:30:00",
    "updatedAt": "2026-06-10T12:00:00"
  },
  "timestamp": "2026-06-10T12:00:00"
}
```

### Failure examples

| HTTP | Cause | UI action |
|------|-------|-----------|
| `400` | Validation failed | Show field errors |
| `401` | No / invalid token | Redirect to login |
| `403` | Caller is not owner | Hide edit UI or show "Not allowed" |
| `404` | Space not found | Navigate back to list |

---

## API 4: List User Spaces

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/spaces/user/{userId}` |
| **Auth** | Bearer JWT required |

Returns **active** spaces where the user has an **ACTIVE** membership (owned + joined).

### Path parameters

| Param | Type | Description |
|-------|------|-------------|
| `userId` | UUID | Typically the logged-in user's id |

### Success — `200`

```json
{
  "success": true,
  "data": [
    {
      "spaceId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "spaceName": "Sunrise PG",
      "spaceType": "PG",
      "membershipRole": "OWNER",
      "joinedAt": "2026-06-10T10:30:00"
    },
    {
      "spaceId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
      "spaceName": "City Mess",
      "spaceType": "MESS",
      "membershipRole": "TENANT",
      "joinedAt": "2026-06-08T09:00:00"
    }
  ],
  "timestamp": "2026-06-10T10:30:00"
}
```

### UI notes

- Empty array `[]` → show empty state ("No spaces yet" + Create button).
- `membershipRole === 'OWNER'` → show owner badge and edit/deactivate actions on details screen.

---

## API 5: Deactivate Space

| | |
|---|---|
| **Method** | `DELETE` |
| **Path** | `/api/v1/spaces/{spaceId}` |
| **Auth** | Bearer JWT required (caller must be owner) |

Soft delete: sets `isActive = false`. Does not remove the database record.

### Success — `204 No Content`

Empty response body. No `ApiResponse` envelope.

### Failure examples

| HTTP | Cause | UI action |
|------|-------|-----------|
| `401` | No / invalid token | Redirect to login |
| `403` | Caller is not owner | Show "Only owner can deactivate" |
| `404` | Space not found or already deactivated | Refresh list |

---

## React Native integration

### Suggested additions to `src/api/`

```
src/api/
  types.ts       ← add Space types above (or spaceTypes.ts)
  spaceApi.ts    ← createSpace, getSpaceById, updateSpace, getUserSpaces, deactivateSpace
```

### `spaceApi.ts` example

```typescript
import type {
  ApiResponse,
  CreateSpaceRequest,
  SpaceDetailsResponse,
  SpaceResponse,
  UpdateSpaceRequest,
  UserSpaceResponse,
} from './types';
import { apiRequest, API_BASE_URL, getStoredToken } from './client';

export async function createSpace(
  body: CreateSpaceRequest,
): Promise<SpaceResponse> {
  return apiRequest<SpaceResponse>('/api/v1/spaces', {
    method: 'POST',
    body,
  });
}

export async function getSpaceById(spaceId: string): Promise<SpaceDetailsResponse> {
  return apiRequest<SpaceDetailsResponse>(`/api/v1/spaces/${spaceId}`);
}

export async function updateSpace(
  spaceId: string,
  body: UpdateSpaceRequest,
): Promise<SpaceDetailsResponse> {
  return apiRequest<SpaceDetailsResponse>(`/api/v1/spaces/${spaceId}`, {
    method: 'PUT',
    body,
  });
}

export async function getUserSpaces(userId: string): Promise<UserSpaceResponse[]> {
  return apiRequest<UserSpaceResponse[]>(`/api/v1/spaces/user/${userId}`);
}

/** DELETE returns 204 with no JSON body */
export async function deactivateSpace(spaceId: string): Promise<void> {
  const token = await getStoredToken();
  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: token ? `Bearer ${token}` : '',
  };

  const response = await fetch(`${API_BASE_URL}/api/v1/spaces/${spaceId}`, {
    method: 'DELETE',
    headers,
  });

  if (response.status === 204) return;

  const json = (await response.json()) as ApiResponse<unknown>;
  throw new Error(json.message ?? `Request failed (${response.status})`);
}
```

### Owner check on the client

```typescript
function isSpaceOwner(space: SpaceDetailsResponse, currentUserId: string): boolean {
  return space.ownerId === currentUserId;
}
```

Use this to show/hide Edit and Deactivate buttons. The backend still enforces owner checks on `PUT` and `DELETE`.

### Space type picker labels (UI)

```typescript
export const SPACE_TYPE_OPTIONS: { value: SpaceType; label: string }[] = [
  { value: 'PG', label: 'PG' },
  { value: 'MESS', label: 'Mess' },
  { value: 'HOSTEL', label: 'Hostel' },
  { value: 'CO_LIVING', label: 'Co-living' },
  { value: 'RENTAL', label: 'Rental' },
];
```

---

## Quick reference

| Method | Path | Response `data` type | Notes |
|--------|------|----------------------|-------|
| `POST` | `/api/v1/spaces` | `SpaceResponse` | `201` |
| `GET` | `/api/v1/spaces/{spaceId}` | `SpaceDetailsResponse` | Active spaces only |
| `PUT` | `/api/v1/spaces/{spaceId}` | `SpaceDetailsResponse` | Owner only |
| `GET` | `/api/v1/spaces/user/{userId}` | `UserSpaceResponse[]` | Active memberships only |
| `DELETE` | `/api/v1/spaces/{spaceId}` | *(none)* | `204`, owner only |

---

## Related docs

- [auth-ui-integration.md](./auth-ui-integration.md) — Login, JWT, `GET /auth/me`
- [api-reference.md](./api-reference.md) — Full backend API catalog
- [domain-model.md](./domain-model.md) — Space and membership domain rules
