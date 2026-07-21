# CountIn Backend — API Reference

API documentation for frontend integration (React Native / web).

**Base URL**

| Environment | URL |
|-------------|-----|
| Android emulator | `http://10.0.2.2:8080` |
| iOS simulator | `http://localhost:8080` |
| Physical device | `http://<YOUR_PC_LAN_IP>:8080` |

**Auth:** JWT Bearer token (`Authorization: Bearer <accessToken>`) on protected endpoints.

**Content-Type:** `application/json`

**Envelope:** Every response uses `ApiResponse<T>`.

---

## Common response envelope

### Success

```json
{
  "success": true,
  "message": "Optional human-readable message",
  "data": {},
  "timestamp": "2026-06-08T03:42:00"
}
```

- `message` may be omitted on some GET responses.
- `data` shape depends on the endpoint.

### Failure

```json
{
  "success": false,
  "message": "Error description",
  "data": null,
  "timestamp": "2026-06-08T03:42:00"
}
```

Validation errors (`400`) may include field errors in `data`:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "name": "Space name is required",
    "ownerId": "Owner ID is required"
  },
  "timestamp": "2026-06-08T03:42:00"
}
```

### HTTP status codes

| Status | When |
|--------|------|
| `200` | Success (GET, accept invitation) |
| `201` | Created (POST space, POST invitation) |
| `400` | Validation / business rule / malformed body |
| `403` | Not allowed (e.g. non-owner sending invitation) |
| `404` | User, space, or invitation not found |
| `409` | Conflict (duplicate membership, DB constraint) |
| `500` | Unexpected server error |

---

## Dashboard & payments (Phase 7)

> Full spec: [payments-phase-7-dashboard.md](./payments-phase-7-dashboard.md)

Financial cards use generic labels everywhere: **Expected Charges**, **Collected**, **Pending** (`Pending = Expected − Collected`; when collected is unknown, pending equals full expected).

### Get dashboard summary

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/spaces/{spaceId}/dashboard-summary` |
| **Query** | `month` — optional `YYYY-MM` (defaults to current month) |
| **Auth** | Bearer JWT |
| **Access** | `OWNER`, `MANAGER`, `STAFF` |

**Success — `200` `data` shape**

```json
{
  "spaceType": "MESS",
  "month": "2026-06",
  "financial": {
    "expectedCharges": 15000,
    "collected": 8000,
    "pending": 7000,
    "currencyCode": "INR",
    "source": "MEAL_ACTIVITY"
  },
  "messOperations": {
    "membersReceivingMeals": 42,
    "menusPublishedThisMonth": 18,
    "openPollsCount": 1,
    "todaysHeadcount": 35,
    "pollRespondedCount": 28,
    "pollEligibleCount": 42
  },
  "accommodationOperations": null,
  "attention": [
    {
      "kind": "poll_open",
      "scheduledCount": 3,
      "totalMeals": 3,
      "missingMealTypes": [],
      "respondedCount": 28,
      "eligibleCount": 42,
      "openPollCount": 1
    }
  ]
}
```

| Field | Notes |
|-------|-------|
| `financial.source` | `MEAL_ACTIVITY` (Mess), `OCCUPANCY` (PG/hostel/rental), `HYBRID` (accommodation + meal participation) |
| `messOperations` | Present for `MESS` spaces only |
| `accommodationOperations` | Present for PG / HOSTEL / CO_LIVING / RENTAL |
| `attention` | Mess menu/poll items + `payments_overdue` when pending member payments exist |

**Expected charges by space type**

| Space type | Source |
|------------|--------|
| Mess | Confirmed meal selections (`MemberMealActivityService`) — not published menus alone |
| PG / Hostel / Co-living / Rental | Active occupancy `rentSnapshot` + food charges when applicable |
| PG + food | Hybrid sum of meal activity + occupancy |

**Collected today:** meal poll payments only. PG rent collection recording is not implemented — `collected` may be `null` for occupancy-only rows.

### Get payment ledger

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/spaces/{spaceId}/payments/ledger` |
| **Query** | `month` — optional `YYYY-MM` |
| **Auth** | Bearer JWT |
| **Access** | `OWNER`, `MANAGER` only |

**Success — `200`**

```json
{
  "month": "2026-06",
  "spaceType": "PG",
  "summary": {
    "expectedCharges": 24000,
    "collected": null,
    "pending": 24000,
    "currencyCode": "INR",
    "source": "OCCUPANCY"
  },
  "members": [
    {
      "memberId": "550e8400-e29b-41d4-a716-446655440000",
      "memberName": "Rahul Kumar",
      "expectedCharges": 8000,
      "collected": null,
      "pending": 8000,
      "currencyCode": "INR",
      "status": "PENDING"
    }
  ]
}
```

Members sorted by `pending` descending, then name. Frontend falls back to client aggregation on `404` or network error only.

---

## Shared enums

Use exact string values in request bodies and when parsing responses.

**SpaceType:** `PG` | `MESS` | `HOSTEL` | `CO_LIVING`

**MembershipRole:** `OWNER` | `MANAGER` | `TENANT` | `CUSTOMER` | `STAFF`

**MembershipStatus:** `INVITATION_SENT` | `ACCEPTED` | `ACTIVE` | `INACTIVE` | `REMOVED` | `VACATED`

**InvitationStatus:** `PENDING` | `ACCEPTED` | `REJECTED` | `EXPIRED`

**DashboardFinancialSource:** `API` | `MEAL_ACTIVITY` | `OCCUPANCY` | `HYBRID`

**MemberPaymentStatus:** `PAID` | `PARTIAL` | `PENDING` | `NONE`

**DashboardAttentionKind:** `not_planned` | `partial_planned` | `ready_to_share` | `poll_open` | `payments_overdue`

---

## Endpoints

### 1. Create Space

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/spaces` |
| **Controller** | `SpaceController` |

**Request body**

```json
{
  "name": "PG-A",
  "type": "PG",
  "address": "Pune",
  "contactNumber": "9876543210",
  "ownerId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `name` | string | Yes | Non-blank |
| `type` | SpaceType | Yes | |
| `address` | string | No | |
| `contactNumber` | string | No | |
| `ownerId` | UUID string | Yes | Must exist and be active |

**Success — `201`**

```json
{
  "success": true,
  "message": "Space created successfully",
  "data": {
    "id": "uuid",
    "name": "PG-A",
    "type": "PG",
    "address": "Pune",
    "contactNumber": "9876543210",
    "active": true,
    "ownerId": "uuid",
    "ownerName": "Ketan",
    "createdAt": "2026-06-08T10:30:00"
  },
  "timestamp": "2026-06-08T10:30:00"
}
```

> Jackson serializes `isActive` as `"active"` in JSON.

**Side effect:** Creates an `OWNER` membership with `status: ACTIVE` for `ownerId`.

**Failure examples**

| Status | `message` |
|--------|-----------|
| `400` | `Validation failed` + field map in `data` |
| `404` | `User not found with id: '<ownerId>'` |

---

### 2. Get spaces for a user

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/spaces/user/{userId}` |
| **Controller** | `SpaceController` |

**Path params:** `userId` — UUID

**Request body:** None

**Success — `200`**

```json
{
  "success": true,
  "data": [
    {
      "spaceId": "uuid",
      "spaceName": "PG-A",
      "spaceType": "PG",
      "role": "OWNER",
      "membershipStatus": "ACTIVE"
    }
  ],
  "timestamp": "2026-06-08T10:30:00"
}
```

Returns an empty array if the user has no memberships (not an error).

**Failure examples**

| Status | `message` |
|--------|-----------|
| `400` | Invalid UUID in path (framework) |
| `500` | `An unexpected error occurred. Please try again later.` |

---

### 3. Create invitation

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/invitations` |
| **Controller** | `InvitationController` |

**Request body**

```json
{
  "spaceId": "uuid",
  "invitedByUserId": "uuid",
  "mobileNumber": "9123456789",
  "role": "TENANT"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `spaceId` | UUID | Yes | Space must exist and be active |
| `invitedByUserId` | UUID | Yes | Must be OWNER or MANAGER in that space |
| `mobileNumber` | string | Yes | Non-blank |
| `role` | MembershipRole | Yes | Role assigned on accept |

**Success — `201`**

```json
{
  "success": true,
  "message": "Invitation sent successfully",
  "data": {
    "id": "uuid",
    "spaceId": "uuid",
    "spaceName": "PG-A",
    "invitedByUserId": "uuid",
    "mobileNumber": "9123456789",
    "role": "TENANT",
    "status": "PENDING",
    "expiresAt": "2026-06-15T10:30:00",
    "acceptedAt": null,
    "createdAt": "2026-06-08T10:30:00"
  },
  "timestamp": "2026-06-08T10:30:00"
}
```

Invitations expire after **365 days** (`expiresAt`).

**Failure examples**

| Status | `message` |
|--------|-----------|
| `400` | `Validation failed` |
| `400` | `A pending invitation already exists for this mobile number in the space` |
| `403` | `Only OWNER or MANAGER can send invitations` |
| `404` | `Space not found with id: '...'` |
| `404` | `User not found with id: '...'` |

---

### 4. Accept invitation

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/invitations/{id}/accept` |
| **Controller** | `InvitationController` |

**Path params:** `id` — invitation UUID

**Request body**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440001"
}
```

| Field | Type | Required |
|-------|------|----------|
| `userId` | UUID | Yes |

**Success — `200`**

```json
{
  "success": true,
  "message": "Invitation accepted successfully",
  "data": {
    "id": "uuid",
    "spaceId": "uuid",
    "spaceName": "PG-A",
    "userId": "uuid",
    "role": "TENANT",
    "status": "ACTIVE",
    "joinedAt": "2026-06-08T10:35:00",
    "createdAt": "2026-06-08T10:35:00"
  },
  "timestamp": "2026-06-08T10:35:00"
}
```

**Side effects:** Creates `SpaceMembership`; sets invitation to `ACCEPTED` and `acceptedAt`.

**Failure examples**

| Status | `message` |
|--------|-----------|
| `400` | `Invitation is no longer valid. Status: ACCEPTED` |
| `400` | `Invitation has expired` |
| `404` | `Invitation not found with id: '...'` |
| `404` | `User not found with id: '...'` |
| `409` | `User already has an active membership in this space` |

---

## Endpoints not fully documented here

This file documents core space/membership flows and Phase 7 dashboard APIs. Additional implemented modules (auth, members, accommodation, occupancy, meals, polls, headcount) are covered in module-specific docs under `docs/`.

| Resource | Doc |
|----------|-----|
| Auth (OTP + JWT) | [auth-ui-integration.md](./auth-ui-integration.md) |
| Meals & polls | [meals-phase-5-backend.md](./meals-phase-5-backend.md), [meals-phase-6-handoff.md](./meals-phase-6-handoff.md) |
| Dashboard & payments | [payments-phase-7-dashboard.md](./payments-phase-7-dashboard.md) |
| Permissions | [permissions-backend-spec.md](./permissions-backend-spec.md) |
| Accommodation & occupancy | [accommodation-domain-model.md](./accommodation-domain-model.md), [occupancy-phase-4.3b-backend.md](./occupancy-phase-4.3b-backend.md) |

---

## Test data prerequisite

Insert users in PostgreSQL before calling space APIs:

```sql
INSERT INTO users (id, mobile_number, full_name, is_active, created_at, updated_at)
VALUES
  ('550e8400-e29b-41d4-a716-446655440000', '9876543210', 'Ketan', true, NOW(), NOW()),
  ('550e8400-e29b-41d4-a716-446655440001', '9123456789', 'Rahul', true, NOW(), NOW());
```

---

## Suggested frontend flow

```
1. (Future) Register / login user → get userId
2. POST /api/v1/spaces              → owner creates space
3. GET  /api/v1/spaces/user/{id}   → list "My Spaces"
4. POST /api/v1/invitations        → owner invites tenant
5. POST /api/v1/invitations/{id}/accept → tenant accepts
6. GET  /api/v1/spaces/user/{id}   → tenant sees new space
```

---

## cURL examples

```bash
# Create space
curl -X POST http://localhost:8080/api/v1/spaces \
  -H "Content-Type: application/json" \
  -d '{"name":"PG-A","type":"PG","address":"Pune","contactNumber":"9876543210","ownerId":"550e8400-e29b-41d4-a716-446655440000"}'

# Get user spaces
curl http://localhost:8080/api/v1/spaces/user/550e8400-e29b-41d4-a716-446655440000

# Create invitation
curl -X POST http://localhost:8080/api/v1/invitations \
  -H "Content-Type: application/json" \
  -d '{"spaceId":"<SPACE_UUID>","invitedByUserId":"550e8400-e29b-41d4-a716-446655440000","mobileNumber":"9123456789","role":"TENANT"}'

# Accept invitation
curl -X POST http://localhost:8080/api/v1/invitations/<INVITATION_UUID>/accept \
  -H "Content-Type: application/json" \
  -d '{"userId":"550e8400-e29b-41d4-a716-446655440001"}'
```

---

## TypeScript types (React Native)

```typescript
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  timestamp?: string;
}

export type SpaceType = 'PG' | 'MESS' | 'HOSTEL' | 'CO_LIVING';
export type MembershipRole = 'OWNER' | 'MANAGER' | 'TENANT' | 'CUSTOMER' | 'STAFF';
export type MembershipStatus =
  | 'INVITATION_SENT' | 'ACCEPTED' | 'ACTIVE' | 'INACTIVE' | 'REMOVED' | 'VACATED';
export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED';

export interface SpaceResponse {
  id: string;
  name: string;
  type: SpaceType;
  address?: string;
  contactNumber?: string;
  active: boolean;
  ownerId: string;
  ownerName: string;
  createdAt: string;
}

export interface UserSpaceResponse {
  spaceId: string;
  spaceName: string;
  spaceType: SpaceType;
  role: MembershipRole;
  membershipStatus: MembershipStatus;
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
  acceptedAt?: string;
  createdAt: string;
}

export interface SpaceMembershipResponse {
  id: string;
  spaceId: string;
  spaceName: string;
  userId: string;
  role: MembershipRole;
  status: MembershipStatus;
  joinedAt?: string;
  createdAt: string;
}

export interface CreateSpaceRequest {
  name: string;
  type: SpaceType;
  address?: string;
  contactNumber?: string;
  ownerId: string;
}

export interface CreateInvitationRequest {
  spaceId: string;
  invitedByUserId: string;
  mobileNumber: string;
  role: MembershipRole;
}

export interface AcceptInvitationRequest {
  userId: string;
}
```

---

## React Native integration prompt

Copy into Cursor or your AI assistant when wiring the mobile app:

```
Integrate CountIn Spring Boot REST APIs into a React Native (Expo or bare) app.

Backend base URL:
- Android emulator: http://10.0.2.2:8080
- iOS simulator: http://localhost:8080
- Physical device: http://<LAN_IP>:8080

All APIs return this envelope:
{
  "success": boolean,
  "message"?: string,
  "data"?: T,
  "timestamp"?: string
}

On failure: success=false, check HTTP status and message. Validation errors (400) may have data as { fieldName: errorMessage }.

Implement:

1. src/api/types.ts
   - ApiResponse<T>
   - SpaceType, MembershipRole, MembershipStatus, InvitationStatus enums
   - SpaceResponse, UserSpaceResponse, InvitationResponse, SpaceMembershipResponse
   - CreateSpaceRequest, CreateInvitationRequest, AcceptInvitationRequest

2. src/api/client.ts
   - fetch wrapper with JSON headers
   - throw typed ApiError on !response.ok || !json.success
   - export API_BASE_URL using Platform.OS

3. src/api/spaceApi.ts
   - createSpace(payload) → POST /api/v1/spaces → SpaceResponse
   - getUserSpaces(userId) → GET /api/v1/spaces/user/{userId} → UserSpaceResponse[]

4. src/api/invitationApi.ts
   - createInvitation(payload) → POST /api/v1/invitations → InvitationResponse
   - acceptInvitation(id, { userId }) → POST /api/v1/invitations/{id}/accept → SpaceMembershipResponse

5. Screens or hooks:
   - useCreateSpace(ownerId, form)
   - useMySpaces(userId)
   - useCreateInvitation(spaceId, invitedByUserId, mobileNumber, role)
   - useAcceptInvitation(invitationId, userId)

Handle these business errors in UI:
- 404 User/Space/Invitation not found
- 403 Only OWNER or MANAGER can send invitations
- 400 Pending invitation already exists
- 400 Invitation expired or no longer PENDING
- 409 User already has active membership

Store userId in AsyncStorage after login (placeholder until auth API exists).
Use TypeScript strict mode. Prefer fetch unless axios is already in the project.

Example create space payload:
{ "name": "PG-A", "type": "PG", "address": "Pune", "contactNumber": "9876543210", "ownerId": "<uuid>" }

Example create invitation:
{ "spaceId": "<uuid>", "invitedByUserId": "<owner-uuid>", "mobileNumber": "9123456789", "role": "TENANT" }

Example accept invitation:
POST /api/v1/invitations/<invitationId>/accept with body { "userId": "<uuid>" }
```

Refer to [domain-model.md](./domain-model.md) for entity rules and [backend-context.md](./backend-context.md) for product context.

IMPORTANT:

Do not rewrite existing screens.

Do not change navigation.

Do not change styling.

Only integrate APIs into existing UI.

Preserve existing components and user experience.