# Acomi — Members & Invitations UI Integration Guide

Frontend reference for **Member Master**, **Invitations**, and related flows in React Native (or any mobile/web client).

> **Updated for Member Master module.**  
> `GET /members` now returns **business member records** (`memberId`), not app-user memberships (`userId`).  
> Use `PUT/DELETE /members/{memberId}` for update and remove. The old `PUT /members/{userId}/role` and `DELETE /members/{userId}` endpoints are **removed**.

---

## Overview

| Concept | What it is | Has app login? |
|---------|------------|----------------|
| **Member** | Business record in a space (tenant, customer, staff) | Optional (`linkedUser`) |
| **User** | App account (OTP login) | Yes |
| **Invitation** | Onboarding path → creates app access on accept | — |

Owners and managers can:
- **Add members directly** (no app required) — `POST /members`
- **Invite via mobile** (app onboarding) — `POST /invitations`

| Feature | API | Who can use |
|---------|-----|-------------|
| Add member (direct) | `POST /api/v1/spaces/{spaceId}/members` | OWNER, MANAGER |
| List members | `GET /api/v1/spaces/{spaceId}/members` | Any active space member |
| Member details | `GET /api/v1/spaces/{spaceId}/members/{memberId}` | Any active space member |
| Update member | `PUT /api/v1/spaces/{spaceId}/members/{memberId}` | OWNER, MANAGER |
| Remove member | `DELETE /api/v1/spaces/{spaceId}/members/{memberId}` | OWNER only |
| Invite (app flow) | `POST /api/v1/invitations` | OWNER, MANAGER |
| Accept invitation | `POST /api/v1/invitations/{id}/accept` | Invited user |
| Cancel invitation | `DELETE /api/v1/invitations/{invitationId}` | OWNER, MANAGER |
| Pending invitations | `GET /api/v1/spaces/{spaceId}/invitations` | Any active space member |

**Auth:** All endpoints require JWT.

```
Authorization: Bearer <accessToken>
Content-Type: application/json
```

See [auth-ui-integration.md](./auth-ui-integration.md) for login. Use [my-spaces-ui-integration.md](./my-spaces-ui-integration.md) for `spaceId` from the current space context.

---

## Base URL

| Environment | URL |
|-------------|-----|
| Android emulator | `http://10.0.2.2:8080` |
| iOS simulator | `http://localhost:8080` |
| Physical device | `http://<YOUR_PC_LAN_IP>:8080` |

---

## UI flow

```
┌──────────────────────────┐
│  Members Screen          │
│  (current space)         │
└────────────┬─────────────┘
             │
    ┌────────┼────────┐
    ▼        ▼        ▼
┌────────┐ ┌──────┐ ┌──────────────┐
│Members │ │ Add  │ │ Pending Inv. │
│  list  │ │member│ │     tab      │
└───┬────┘ └──┬───┘ └──────┬───────┘
    │         │            │
    │ GET     │ POST       │ GET /invitations
    │/members │ /members   │
    │         │            │
    ▼         ▼            ▼
┌────────┐ ┌────────┐  Cancel → DELETE /invitations/{id}
│ Detail │ │  Form  │
│ screen │ │name,   │
└───┬────┘ │mobile, │
    │      │role    │
    │      └────────┘
    │
    ├── Edit  → PUT /members/{memberId}
    └── Remove → DELETE /members/{memberId}  (OWNER only)

┌──────────────────────────┐
│  Invite (app onboarding) │  POST /invitations
│  OWNER / MANAGER           │
└──────────────────────────┘

┌──────────────────────────┐
│  Accept Invitation         │  POST /invitations/{id}/accept
└──────────────────────────┘
```

### Screen: Members list

1. Read `spaceId` from current space context.
2. `GET /api/v1/spaces/{spaceId}/members`
3. Show: `fullName`, `mobileNumber`, `role`, `status` badge, `linkedUser` badge, `createdAt`
4. Tap row → member detail screen
5. **Add member** FAB (OWNER/MANAGER) → direct add form
6. **Invite** action (OWNER/MANAGER) → invitation form (separate from direct add)
7. Tab/section: **Pending invitations**

### Screen: Add member (direct)

- Fields: `fullName`, `mobileNumber`, `role` (not `OWNER`)
- `POST /api/v1/spaces/{spaceId}/members`
- On `201` → refresh members list
- Member may show `linkedUser: true` if mobile already registered in the app

### Screen: Member detail / edit

- `GET /api/v1/spaces/{spaceId}/members/{memberId}`
- Edit → `PUT` with `fullName`, `mobileNumber`, `role`
- Remove → confirm → `DELETE` (OWNER only)
- Hide edit/remove when `role === 'OWNER'`

### Screen: Invite member (app onboarding)

- `POST /api/v1/invitations` with `spaceId`, `invitedByUserId`, `mobileNumber`, `role`
- On `201` → refresh pending invitations tab

---

## Common response envelope

Most endpoints return `ApiResponse<T>`:

```json
{
  "success": true,
  "message": "Optional message",
  "data": {},
  "timestamp": "2026-06-10T22:00:00"
}
```

**Exceptions:** `DELETE` endpoints return **`204 No Content`** with an empty body.

### Error response

```json
{
  "success": false,
  "message": "An active member with this mobile number already exists in the space",
  "data": null,
  "timestamp": "2026-06-10T22:00:00"
}
```

Validation errors (`400`):

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "fullName": "Full name is required",
    "mobileNumber": "Mobile number is required"
  },
  "timestamp": "2026-06-10T22:00:00"
}
```

### HTTP status codes

| Status | When |
|--------|------|
| `200` | Success (GET, PUT, accept invitation) |
| `201` | Member or invitation created |
| `204` | Remove member / cancel invitation |
| `400` | Validation or business rule |
| `401` | Missing or invalid JWT |
| `403` | Not allowed (wrong role or not a space member) |
| `404` | Space, member, or invitation not found |
| `409` | Conflict on invitation accept (already a member) |

---

## Enums

### MembershipRole

| Value | Use |
|-------|-----|
| `OWNER` | Space owner — **cannot** be created/assigned via member APIs |
| `MANAGER` | Manager |
| `TENANT` | PG / hostel tenant |
| `CUSTOMER` | Mess customer |
| `STAFF` | Staff |

**Assignable on create/update/invite:** `MANAGER`, `TENANT`, `CUSTOMER`, `STAFF`

### InvitationStatus

| Value | Meaning |
|-------|---------|
| `PENDING` | Awaiting acceptance |
| `ACCEPTED` | User joined |
| `EXPIRED` | Past expiry |
| `CANCELLED` | Cancelled by owner/manager |

---

## TypeScript types

```typescript
export type MembershipRole =
  | 'OWNER'
  | 'MANAGER'
  | 'TENANT'
  | 'CUSTOMER'
  | 'STAFF';

export type InvitationStatus =
  | 'PENDING'
  | 'ACCEPTED'
  | 'EXPIRED'
  | 'CANCELLED'
  | 'REJECTED';

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  timestamp?: string;
}

// --- Member Master requests ---

export interface CreateMemberRequest {
  fullName: string;
  mobileNumber: string;
  role: MembershipRole;
}

export interface UpdateMemberRequest {
  fullName: string;
  mobileNumber: string;
  role: MembershipRole;
}

// --- Member Master responses ---

export interface MemberResponse {
  memberId: string;
  fullName: string;
  mobileNumber: string;
  role: MembershipRole;
  linkedUser: boolean;
  status: MemberStatus;
  createdAt: string;
}

export type MemberStatus = 'ACTIVE' | 'VACATED' | 'SUSPENDED' | 'BLACKLISTED';

export interface MemberDetailsResponse {
  memberId: string;
  spaceId: string;
  fullName: string;
  mobileNumber: string;
  role: MembershipRole;
  linkedUser: boolean;
  linkedUserId?: string | null;
  membershipId?: string | null;
  active: boolean;
  status: MemberStatus;
  statusUpdatedAt?: string | null;
  emergencyContactName?: string | null;
  emergencyContactRelation?: string | null;
  emergencyContactMobile?: string | null;
  depositAmount: number;
  depositPaid: number;
  depositRefunded: number;
  depositBalance: number;
  createdAt: string;
  updatedAt: string;
}

// --- Invitation requests / responses ---

export interface CreateInvitationRequest {
  spaceId: string;
  invitedByUserId: string;
  mobileNumber: string;
  role: MembershipRole;
}

export interface AcceptInvitationRequest {
  userId: string;
}

export interface PendingInvitationResponse {
  invitationId: string;
  mobileNumber: string;
  role: MembershipRole;
  status: InvitationStatus;
  invitedBy: string;
  createdAt: string;
}

export interface InvitationResponse {
  id: string;
  spaceId: string;
  spaceName: string;
  invitedByUserId: string;
  mobileNumber: string;
  role: MembershipRole;
  status: InvitationStatus;
  expiresAt: string;
  acceptedAt?: string | null;
  createdAt: string;
}

export interface SpaceMembershipResponse {
  id: string;
  spaceId: string;
  spaceName: string;
  userId: string;
  role: MembershipRole;
  status: string;
  joinedAt: string;
  createdAt: string;
}
```

---

## Permission matrix (UI visibility)

Use caller's `membershipRole` from `GET /spaces/my` for the current space.

| Action | OWNER | MANAGER | TENANT / CUSTOMER / STAFF |
|--------|-------|---------|----------------------------|
| View members / details | Yes | Yes | Yes |
| Add member (direct) | Yes | Yes | No |
| Update member | Yes | Yes | No |
| Remove member | Yes | No | No |
| View pending invitations | Yes | Yes | Yes |
| Send invitation | Yes | Yes | No |
| Cancel invitation | Yes | Yes | No |
| Accept invitation | If invited | If invited | If invited |

---

## Member Master APIs

### API 1: Add Member (direct)

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/spaces/{spaceId}/members` |
| **Permission** | OWNER or MANAGER |

#### Request

```json
{
  "fullName": "Rahul Sharma",
  "mobileNumber": "9876543210",
  "role": "TENANT"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `fullName` | Yes | Display name |
| `mobileNumber` | Yes | 10-digit mobile |
| `role` | Yes | Not `OWNER` |

#### Success — `201`

```json
{
  "success": true,
  "message": "Member created successfully",
  "data": {
    "memberId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "fullName": "Rahul Sharma",
    "mobileNumber": "9876543210",
    "role": "TENANT",
    "linkedUser": false,
    "createdAt": "2026-06-10T22:00:00"
  },
  "timestamp": "2026-06-10T22:00:00"
}
```

| HTTP | `message` (typical) | UI action |
|------|---------------------|-----------|
| `400` | An active member with this mobile number already exists... | Show duplicate mobile error |
| `400` | OWNER role cannot be assigned via member APIs | Exclude OWNER from role picker |
| `403` | Only OWNER or MANAGER can perform this action | Hide add button |

---

### API 2: List Members

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/spaces/{spaceId}/members` |
| **Permission** | Any active space member |

#### Success — `200`

```json
{
  "success": true,
  "data": [
    {
      "memberId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "fullName": "Rahul Sharma",
      "mobileNumber": "9876543210",
      "role": "TENANT",
      "linkedUser": false,
      "createdAt": "2026-06-10T22:00:00"
    },
    {
      "memberId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
      "fullName": "Priya",
      "mobileNumber": "9123456789",
      "role": "MANAGER",
      "linkedUser": true,
      "createdAt": "2026-06-08T09:00:00"
    }
  ],
  "timestamp": "2026-06-10T22:00:00"
}
```

#### UI notes

- Show **App linked** badge when `linkedUser === true`
- Empty array → "No members yet" + Add member CTA
- `linkedUser: false` → person exists in business records but has not registered in the app

---

### API 3: Member Details

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/spaces/{spaceId}/members/{memberId}` |

#### Success — `200`

```json
{
  "success": true,
  "data": {
    "memberId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "spaceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "fullName": "Rahul Sharma",
    "mobileNumber": "9876543210",
    "role": "TENANT",
    "linkedUser": true,
    "linkedUserId": "c3d4e5f6-a7b8-9012-cdef-123456789012",
    "membershipId": "d4e5f6a7-b8c9-0123-def0-234567890123",
    "active": true,
    "createdAt": "2026-06-10T22:00:00",
    "updatedAt": "2026-06-10T22:00:00"
  },
  "timestamp": "2026-06-10T22:00:00"
}
```

`membershipId` is internal — present when member is linked to an app user with space access. Do not expose internal IDs in UI unless needed for debugging.

---

### API 4: Update Member

| | |
|---|---|
| **Method** | `PUT` |
| **Path** | `/api/v1/spaces/{spaceId}/members/{memberId}` |
| **Permission** | OWNER or MANAGER |

#### Request

```json
{
  "fullName": "Rahul Sharma",
  "mobileNumber": "9876543210",
  "role": "MANAGER"
}
```

#### Success — `200`

```json
{
  "success": true,
  "message": "Member updated successfully",
  "data": {
    "memberId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "spaceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "fullName": "Rahul Sharma",
    "mobileNumber": "9876543210",
    "role": "MANAGER",
    "linkedUser": true,
    "linkedUserId": "c3d4e5f6-a7b8-9012-cdef-123456789012",
    "membershipId": "d4e5f6a7-b8c9-0123-def0-234567890123",
    "active": true,
    "createdAt": "2026-06-10T22:00:00",
    "updatedAt": "2026-06-10T22:30:00"
  },
  "timestamp": "2026-06-10T22:30:00"
}
```

| HTTP | `message` (typical) | UI action |
|------|---------------------|-----------|
| `400` | OWNER role cannot be modified | Disable edit on owner rows |
| `400` | Duplicate mobile in space | Show field error |
| `403` | Only OWNER or MANAGER... | Hide edit UI |

When `linkedUser` is true, role updates also sync app access on the backend internally.

---

### API 5: Remove Member

| | |
|---|---|
| **Method** | `DELETE` |
| **Path** | `/api/v1/spaces/{spaceId}/members/{memberId}` |
| **Permission** | OWNER only |

Soft-deletes the member (`active = false`). Returns **`204 No Content`**.

| HTTP | `message` (typical) | UI action |
|------|---------------------|-----------|
| `400` | OWNER cannot be removed | Never show remove on owner |
| `403` | Only the space owner can perform this action | Hide remove for MANAGER |

---

## Invitation APIs

### API 6: Create Invitation

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/invitations` |
| **Permission** | OWNER or MANAGER |

#### Request

```json
{
  "spaceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "invitedByUserId": "caller-user-uuid",
  "mobileNumber": "9876543210",
  "role": "TENANT"
}
```

#### Success — `201`

Returns `InvitationResponse` with `status: "PENDING"` and `expiresAt` (365 days).

---

### API 7: Accept Invitation

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/invitations/{invitationId}/accept` |

#### Request

```json
{ "userId": "logged-in-user-uuid" }
```

#### Success — `200`

Returns `SpaceMembershipResponse` with `status: "ACTIVE"`. Refresh `GET /spaces/my` after accept.

> **Note:** Member master auto-linking on accept is not yet active. After accept, the user has app access; a separate Member record may be created manually or linked in a future release.

---

### API 8: Cancel Invitation

| | |
|---|---|
| **Method** | `DELETE` |
| **Path** | `/api/v1/invitations/{invitationId}` |
| **Permission** | OWNER or MANAGER |

Returns **`204 No Content`**. Only `PENDING` invitations can be cancelled.

---

### API 9: List Pending Invitations

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/spaces/{spaceId}/invitations` |

#### Success — `200`

```json
{
  "success": true,
  "data": [
    {
      "invitationId": "d4e5f6a7-b8c9-0123-def0-234567890123",
      "mobileNumber": "9988776655",
      "role": "TENANT",
      "status": "PENDING",
      "invitedBy": "Ketan",
      "createdAt": "2026-06-10T12:00:00"
    }
  ],
  "timestamp": "2026-06-10T22:00:00"
}
```

---

## Direct add vs Invite — when to use which

| Scenario | Use |
|----------|-----|
| Tenant/customer will **not** install the app | **Add member** (`POST /members`) |
| Person should **log in** and use the app | **Invite** (`POST /invitations`) |
| Person already in members list, now gets app | Invite them; future versions may auto-link |

---

## Migration guide (updating existing UI)

| Old (removed) | New |
|---------------|-----|
| `GET /members` returned `userId`, `joinedAt` | `GET /members` returns `memberId`, `linkedUser`, `createdAt` |
| `PUT /members/{userId}/role` | `PUT /members/{memberId}` with full `UpdateMemberRequest` |
| `DELETE /members/{userId}` | `DELETE /members/{memberId}` |
| Role change OWNER-only | Update member: OWNER **or** MANAGER |
| Remove member OWNER-only | Unchanged (OWNER only) |

Update list row keys from `userId` → `memberId`. Update navigation params accordingly.

---

## Error handling (React Native)

```typescript
import type { ApiResponse } from './types';

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public fieldErrors?: Record<string, string>,
  ) {
    super(message);
  }
}

export async function parseResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }
  const json = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !json.success) {
    const fieldErrors =
      json.data && typeof json.data === 'object' && !Array.isArray(json.data)
        ? (json.data as Record<string, string>)
        : undefined;
    throw new ApiError(json.message ?? 'Request failed', response.status, fieldErrors);
  }
  return json.data as T;
}
```

---

## React Native — `memberApi.ts`

```typescript
import type {
  AcceptInvitationRequest,
  CreateInvitationRequest,
  CreateMemberRequest,
  InvitationResponse,
  MemberDetailsResponse,
  MemberResponse,
  PendingInvitationResponse,
  SpaceMembershipResponse,
  UpdateMemberRequest,
} from './types';
import { API_BASE_URL, getStoredToken } from './client';
import { parseResponse } from './errors';

async function authFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const token = await getStoredToken();
  return fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });
}

// --- Member Master ---

export async function createMember(
  spaceId: string,
  body: CreateMemberRequest,
): Promise<MemberResponse> {
  const res = await authFetch(`/api/v1/spaces/${spaceId}/members`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  return parseResponse<MemberResponse>(res);
}

export async function getMembers(spaceId: string): Promise<MemberResponse[]> {
  const res = await authFetch(`/api/v1/spaces/${spaceId}/members`);
  return parseResponse<MemberResponse[]>(res);
}

export async function getMember(
  spaceId: string,
  memberId: string,
): Promise<MemberDetailsResponse> {
  const res = await authFetch(`/api/v1/spaces/${spaceId}/members/${memberId}`);
  return parseResponse<MemberDetailsResponse>(res);
}

export async function updateMember(
  spaceId: string,
  memberId: string,
  body: UpdateMemberRequest,
): Promise<MemberDetailsResponse> {
  const res = await authFetch(`/api/v1/spaces/${spaceId}/members/${memberId}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
  return parseResponse<MemberDetailsResponse>(res);
}

export async function removeMember(spaceId: string, memberId: string): Promise<void> {
  const res = await authFetch(`/api/v1/spaces/${spaceId}/members/${memberId}`, {
    method: 'DELETE',
  });
  await parseResponse<void>(res);
}

// --- Invitations ---

export async function createInvitation(
  body: CreateInvitationRequest,
): Promise<InvitationResponse> {
  const res = await authFetch('/api/v1/invitations', {
    method: 'POST',
    body: JSON.stringify(body),
  });
  return parseResponse<InvitationResponse>(res);
}

export async function acceptInvitation(
  invitationId: string,
  body: AcceptInvitationRequest,
): Promise<SpaceMembershipResponse> {
  const res = await authFetch(`/api/v1/invitations/${invitationId}/accept`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  return parseResponse<SpaceMembershipResponse>(res);
}

export async function cancelInvitation(invitationId: string): Promise<void> {
  const res = await authFetch(`/api/v1/invitations/${invitationId}`, {
    method: 'DELETE',
  });
  await parseResponse<void>(res);
}

export async function getPendingInvitations(
  spaceId: string,
): Promise<PendingInvitationResponse[]> {
  const res = await authFetch(`/api/v1/spaces/${spaceId}/invitations`);
  return parseResponse<PendingInvitationResponse[]>(res);
}
```

### Role picker (add / update / invite)

```typescript
export const ASSIGNABLE_ROLES = [
  { value: 'MANAGER', label: 'Manager' },
  { value: 'TENANT', label: 'Tenant' },
  { value: 'CUSTOMER', label: 'Customer' },
  { value: 'STAFF', label: 'Staff' },
] as const;
```

### `useMembers` hook

```typescript
import { useCallback, useEffect, useState } from 'react';
import { getMembers } from '../api/memberApi';
import type { MemberResponse } from '../api/types';
import { ApiError, getMembershipErrorMessage } from '../api/errors';

export function useMembers(spaceId: string | null) {
  const [members, setMembers] = useState<MemberResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!spaceId) return;
    setLoading(true);
    setError(null);
    try {
      setMembers(await getMembers(spaceId));
    } catch (e) {
      setError(
        e instanceof ApiError ? getMembershipErrorMessage(e) : 'Failed to load members',
      );
    } finally {
      setLoading(false);
    }
  }, [spaceId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { members, loading, error, refresh };
}
```

---

## Quick reference

| Method | Path | Body | Response `data` | Status |
|--------|------|------|-----------------|--------|
| `POST` | `/api/v1/spaces/{spaceId}/members` | `CreateMemberRequest` | `MemberResponse` | `201` |
| `GET` | `/api/v1/spaces/{spaceId}/members` | — | `MemberResponse[]` | `200` |
| `GET` | `/api/v1/spaces/{spaceId}/members/{memberId}` | — | `MemberDetailsResponse` | `200` |
| `PUT` | `/api/v1/spaces/{spaceId}/members/{memberId}` | `UpdateMemberRequest` | `MemberDetailsResponse` | `200` |
| `DELETE` | `/api/v1/spaces/{spaceId}/members/{memberId}` | — | *(none)* | `204` |
| `POST` | `/api/v1/invitations` | `CreateInvitationRequest` | `InvitationResponse` | `201` |
| `POST` | `/api/v1/invitations/{id}/accept` | `AcceptInvitationRequest` | `SpaceMembershipResponse` | `200` |
| `DELETE` | `/api/v1/invitations/{invitationId}` | — | *(none)* | `204` |
| `GET` | `/api/v1/spaces/{spaceId}/invitations` | — | `PendingInvitationResponse[]` | `200` |

---

## Related docs

- [member-management-ui-integration.md](./member-management-ui-integration.md) — Status, deposit, documents, notes, history (Phase 3)
- [auth-ui-integration.md](./auth-ui-integration.md) — Login, JWT
- [my-spaces-ui-integration.md](./my-spaces-ui-integration.md) — Current space context
- [space-ui-integration.md](./space-ui-integration.md) — Space management
- [member-master-module.md](./member-master-module.md) — Backend spec
- [domain-model.md](./domain-model.md) — Domain rules
accommodation-flow-redesign.md