# Amico — My Spaces UI Integration Guide

Frontend reference for **My Spaces**, **Space Search**, **Space Switcher**, and **Default Space** features in React Native (or any mobile/web client).

---

## Overview

After login, the app needs to know which spaces the user belongs to and which one is **active** in the UI (the "current space").

| Feature | API | Purpose |
|---------|-----|---------|
| List spaces | `GET /api/v1/spaces/my` | Show all spaces (owned + joined) |
| Search spaces | `GET /api/v1/spaces/my?search=` | Filter list by space name |
| Space switcher | `GET /api/v1/spaces/default` + `GET /api/v1/spaces/my` | Pick / display current space |
| Set default | `PUT /api/v1/spaces/{spaceId}/default` | Remember user's preferred space |

**Auth:** All endpoints require JWT. The backend reads the logged-in user from the token — **do not pass `userId` in the request**.

```
Authorization: Bearer <accessToken>
```

See [auth-ui-integration.md](./auth-ui-integration.md) for login flow.

For property structure (buildings, floors, units, rooms, beds), see [accommodation-ui-integration.md](./accommodation-ui-integration.md) — not applicable to `MESS` spaces.

---

## Base URL

| Environment | URL |
|-------------|-----|
| Android emulator | `http://10.0.2.2:8080` |
| iOS simulator | `http://localhost:8080` |
| Physical device | `http://<YOUR_PC_LAN_IP>:8080` |

Header: `Content-Type: application/json`

---

## App bootstrap flow

Call these after a valid token is stored (e.g. on app launch or after OTP verify):

```
┌─────────────────┐
│  App Launch     │
│  (token exists) │
└────────┬────────┘
         │
         ▼
┌─────────────────────────┐     404 = no default yet
│ GET /spaces/default     │ ─────────────────────────► Show "Pick a space" or first from list
└────────┬────────────────┘
         │ 200
         ▼
┌─────────────────────────┐
│ Store currentSpace      │  ← use in header / switcher / API context
│ Navigate to Home        │
└─────────────────────────┘
```

**Recommended startup sequence:**

1. `GET /api/v1/auth/me` — validate session
2. `GET /api/v1/spaces/default` — load current space for switcher
3. If `404` → call `GET /api/v1/spaces/my` and either prompt user to set default or auto-select the first item
4. If user has zero spaces → show onboarding / "Create space"

---

## Common response envelope

```json
{
  "success": true,
  "message": "Optional message",
  "data": {},
  "timestamp": "2026-06-10T16:30:00"
}
```

### Error response

```json
{
  "success": false,
  "message": "Default space not found with userId: '...'",
  "data": null,
  "timestamp": "2026-06-10T16:30:00"
}
```

### HTTP status codes

| Status | When |
|--------|------|
| `200` | Success |
| `401` | Missing or invalid JWT |
| `404` | No default space / membership not found (set default) |

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

/** Item in GET /spaces/my list */
export interface MySpaceResponse {
  spaceId: string;
  spaceName: string;
  address?: string | null;
  spaceType: SpaceType;
  membershipRole: MembershipRole;
  isDefault: boolean;
  joinedAt: string;
}

/** GET /spaces/default — current space for switcher */
export interface DefaultSpaceResponse {
  spaceId: string;
  spaceName: string;
  spaceType: SpaceType;
}

/** PUT /spaces/{spaceId}/default */
export interface SetDefaultSpaceResponse {
  spaceId: string;
  spaceName: string;
  isDefault: boolean;
}
```

---

## UI flows

### 1. My Spaces list screen

- **API:** `GET /api/v1/spaces/my`
- Show cards/rows with: `spaceName`, `address` (subtitle when names duplicate), `spaceType`, `membershipRole`
- Highlight row where `isDefault === true` (badge: "Default")
- Sort is handled by backend: default first, then most recently joined
- Tap row → switch to that space (call set default + update local state)
- Empty list → "Create your first space" CTA

### 2. Search (inline or dedicated)

- **API:** `GET /api/v1/spaces/my?search={query}`
- Debounce input (~300ms) before calling API
- Search is **case-insensitive** and matches **space name** only
- Examples:
  - `sun` → "Sunrise PG"
  - `mess` → "Office Mess"
- Clear search → call `GET /api/v1/spaces/my` without `search` param
- Only searches spaces the user already has access to

### 3. Space switcher (header / drawer)

Typical pattern:

```
┌──────────────────────────────────────┐
│  [PG-A ▼]  Amico                   │  ← shows current default space name
└──────────────────────────────────────┘
         │ tap
         ▼
┌──────────────────────────────────────┐
│  PG-A          ✓ Default             │
│  Mess-A                              │
│  Rental-A                            │
└──────────────────────────────────────┘
```

- Load list: `GET /api/v1/spaces/my`
- Load current: `GET /api/v1/spaces/default` (or use `isDefault` from list)
- On select: `PUT /api/v1/spaces/{spaceId}/default` then update local `currentSpace`

### 4. Set default space

- **API:** `PUT /api/v1/spaces/{spaceId}/default`
- No request body
- Backend clears any previous default and sets the new one (only one default per user)
- User must have an **active membership** in that space

---

## API 1: List My Spaces

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/spaces/my` |
| **Auth** | Bearer JWT required |

### Query parameters

| Param | Required | Description |
|-------|----------|-------------|
| `search` | No | Case-insensitive filter on space name. Omit for full list. |

### Success — `200` (full list)

```json
{
  "success": true,
  "data": [
    {
      "spaceId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "spaceName": "Sunrise PG",
      "spaceType": "PG",
      "membershipRole": "OWNER",
      "isDefault": true,
      "joinedAt": "2026-06-10T10:30:00"
    },
    {
      "spaceId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
      "spaceName": "Office Mess",
      "spaceType": "MESS",
      "membershipRole": "TENANT",
      "isDefault": false,
      "joinedAt": "2026-06-08T09:00:00"
    }
  ],
  "timestamp": "2026-06-10T16:30:00"
}
```

### Success — `200` (search)

`GET /api/v1/spaces/my?search=sun`

Same response shape; only matching spaces returned.

### What's included

- Owned spaces (`membershipRole: "OWNER"`)
- Joined spaces (TENANT, MANAGER, etc.)
- Only **active** memberships on **active** spaces
- Deactivated spaces are excluded

### UI notes

- `data` is always an array (may be empty `[]`)
- Use `isDefault` for checkmark / badge in switcher
- Use `membershipRole === 'OWNER'` to show owner-only actions elsewhere

---

## API 2: Get Default Space

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/spaces/default` |
| **Auth** | Bearer JWT required |

Used on app open to restore the space switcher selection.

### Success — `200`

```json
{
  "success": true,
  "data": {
    "spaceId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "spaceName": "PG-A",
    "spaceType": "PG"
  },
  "timestamp": "2026-06-10T16:30:00"
}
```

### Failure — `404`

User has not set a default space yet.

```json
{
  "success": false,
  "message": "Default space not found with userId: '...'",
  "data": null,
  "timestamp": "2026-06-10T16:30:00"
}
```

### UI handling for `404`

| Scenario | Suggested UX |
|----------|--------------|
| User has spaces, no default | Show space picker; optionally auto-call `PUT .../default` for first list item |
| User has no spaces | Navigate to create-space onboarding |
| New user just created first space | Prompt "Set as default?" → `PUT .../default` |

---

## API 3: Set Default Space

| | |
|---|---|
| **Method** | `PUT` |
| **Path** | `/api/v1/spaces/{spaceId}/default` |
| **Auth** | Bearer JWT required |
| **Body** | None |

### Path parameters

| Param | Type | Description |
|-------|------|-------------|
| `spaceId` | UUID | Space to set as default |

### Success — `200`

```json
{
  "success": true,
  "message": "Default space updated",
  "data": {
    "spaceId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "spaceName": "PG-A",
    "isDefault": true
  },
  "timestamp": "2026-06-10T16:30:00"
}
```

### Failure examples

| HTTP | Cause | UI action |
|------|-------|-----------|
| `401` | Invalid token | Redirect to login |
| `404` | User is not a member of this space | Refresh list; show error toast |

### Business rules (for UI)

- Only one default per user; backend clears the old default automatically
- User must belong to the space (active membership)
- After success, update local `currentSpace` and refresh list if visible

---

## API 4: Search My Spaces

Same endpoint as list — use query param:

```
GET /api/v1/spaces/my?search=mess
```

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/spaces/my` |
| **Query** | `search` (string, case-insensitive) |

### Examples

| Query | Matches |
|-------|---------|
| `?search=sun` | "Sunrise PG", "Sunset Hostel" |
| `?search=MESS` | "Office Mess" (case-insensitive) |
| `?search=xyz` | `[]` empty array |

### UI notes

- Trim whitespace before sending
- Don't call API on every keystroke — debounce 300–500ms
- Show "No spaces match your search" when `data.length === 0`

---

## React Native integration

### Suggested file structure

```
src/
  api/
    types.ts           ← MySpaceResponse, DefaultSpaceResponse, etc.
    client.ts          ← fetch wrapper with Bearer token
    mySpacesApi.ts     ← getMySpaces, searchMySpaces, getDefaultSpace, setDefaultSpace
  store/
    spaceStore.ts      ← currentSpace, mySpaces, load / switch actions
  screens/
    MySpacesScreen.tsx
    SpaceSwitcherModal.tsx
  hooks/
    useCurrentSpace.ts
```

### `mySpacesApi.ts`

```typescript
import type {
  ApiResponse,
  DefaultSpaceResponse,
  MySpaceResponse,
  SetDefaultSpaceResponse,
} from './types';
import { apiRequest } from './client';

export async function getMySpaces(): Promise<MySpaceResponse[]> {
  return apiRequest<MySpaceResponse[]>('/api/v1/spaces/my');
}

export async function searchMySpaces(query: string): Promise<MySpaceResponse[]> {
  const encoded = encodeURIComponent(query.trim());
  return apiRequest<MySpaceResponse[]>(`/api/v1/spaces/my?search=${encoded}`);
}

export async function getDefaultSpace(): Promise<DefaultSpaceResponse | null> {
  const token = await getStoredToken();
  const response = await fetch(`${API_BASE_URL}/api/v1/spaces/default`, {
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${token}`,
    },
  });

  if (response.status === 404) return null;

  const json = (await response.json()) as ApiResponse<DefaultSpaceResponse>;
  if (!response.ok || !json.success || !json.data) {
    throw new Error(json.message ?? 'Failed to load default space');
  }
  return json.data;
}

export async function setDefaultSpace(
  spaceId: string,
): Promise<SetDefaultSpaceResponse> {
  return apiRequest<SetDefaultSpaceResponse>(
    `/api/v1/spaces/${spaceId}/default`,
    { method: 'PUT' },
  );
}
```

### `useCurrentSpace` hook (example)

```typescript
import { useCallback, useEffect, useState } from 'react';
import {
  getDefaultSpace,
  getMySpaces,
  setDefaultSpace,
} from '../api/mySpacesApi';
import type { DefaultSpaceResponse, MySpaceResponse } from '../api/types';

export function useCurrentSpace() {
  const [currentSpace, setCurrentSpace] = useState<DefaultSpaceResponse | null>(null);
  const [mySpaces, setMySpaces] = useState<MySpaceResponse[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [spaces, defaultSpace] = await Promise.all([
        getMySpaces(),
        getDefaultSpace(),
      ]);
      setMySpaces(spaces);

      if (defaultSpace) {
        setCurrentSpace(defaultSpace);
      } else if (spaces.length > 0) {
        // No default yet — use first (already sorted: default first if any)
        setCurrentSpace({
          spaceId: spaces[0].spaceId,
          spaceName: spaces[0].spaceName,
          spaceType: spaces[0].spaceType,
        });
      } else {
        setCurrentSpace(null);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  const switchSpace = useCallback(async (spaceId: string) => {
    const result = await setDefaultSpace(spaceId);
    setCurrentSpace({
      spaceId: result.spaceId,
      spaceName: result.spaceName,
      spaceType: mySpaces.find((s) => s.spaceId === spaceId)?.spaceType ?? 'PG',
    });
    await load();
  }, [load, mySpaces]);

  useEffect(() => {
    load();
  }, [load]);

  return { currentSpace, mySpaces, loading, switchSpace, refresh: load };
}
```

### Debounced search (example)

```typescript
import { useEffect, useState } from 'react';
import { getMySpaces, searchMySpaces } from '../api/mySpacesApi';
import type { MySpaceResponse } from '../api/types';

export function useMySpacesSearch(debounceMs = 350) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<MySpaceResponse[]>([]);
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    const timer = setTimeout(async () => {
      setSearching(true);
      try {
        const data = query.trim()
          ? await searchMySpaces(query)
          : await getMySpaces();
        setResults(data);
      } finally {
        setSearching(false);
      }
    }, debounceMs);

    return () => clearTimeout(timer);
  }, [query, debounceMs]);

  return { query, setQuery, results, searching };
}
```

---

## Space type & role display labels

```typescript
export const SPACE_TYPE_LABELS: Record<SpaceType, string> = {
  PG: 'PG',
  MESS: 'Mess',
  HOSTEL: 'Hostel',
  CO_LIVING: 'Co-living',
  RENTAL: 'Rental',
};

export const MEMBERSHIP_ROLE_LABELS: Record<MembershipRole, string> = {
  OWNER: 'Owner',
  MANAGER: 'Manager',
  TENANT: 'Tenant',
  CUSTOMER: 'Customer',
  STAFF: 'Staff',
};
```

---

## Quick reference

| Method | Path | Response `data` | Notes |
|--------|------|-----------------|-------|
| `GET` | `/api/v1/spaces/my` | `MySpaceResponse[]` | Full list, sorted |
| `GET` | `/api/v1/spaces/my?search=` | `MySpaceResponse[]` | Filtered list |
| `GET` | `/api/v1/spaces/default` | `DefaultSpaceResponse` | `404` if unset |
| `PUT` | `/api/v1/spaces/{spaceId}/default` | `SetDefaultSpaceResponse` | No body |

---

## Related docs

- [auth-ui-integration.md](./auth-ui-integration.md) — Login, JWT, session bootstrap
- [space-ui-integration.md](./space-ui-integration.md) — Create, update, view, deactivate space
- [domain-model.md](./domain-model.md) — Space & membership domain rules
