# Amico — Member Management UI Integration Guide

Frontend reference for **Phase 3 Member Management**: status, emergency contact, deposit, documents, notes, and audit history.

> **Prerequisite:** Member Master CRUD and invitations are documented in [membership-ui-integration.md](./membership-ui-integration.md).  
> This guide covers the **extended member profile** APIs added on top of the existing `GET/PUT /members/{memberId}` flow.

---

## Overview

Phase 3 extends each **Member** record with operational lifecycle data. Use these APIs from the **Member Detail** screen (tabs or sections).

| Feature | API | Who can use |
|---------|-----|-------------|
| Update status | `PUT /api/v1/spaces/{spaceId}/members/{memberId}/status` | OWNER, MANAGER |
| Update emergency contact | `PUT /api/v1/spaces/{spaceId}/members/{memberId}/emergency-contact` | OWNER, MANAGER |
| Update deposit | `PUT /api/v1/spaces/{spaceId}/members/{memberId}/deposit` | OWNER, MANAGER |
| Add document | `POST /api/v1/spaces/{spaceId}/members/{memberId}/documents` | OWNER, MANAGER |
| List documents | `GET /api/v1/spaces/{spaceId}/members/{memberId}/documents` | Any active space member |
| Delete document | `DELETE /api/v1/spaces/{spaceId}/members/{memberId}/documents/{documentId}` | OWNER, MANAGER |
| Add note | `POST /api/v1/spaces/{spaceId}/members/{memberId}/notes` | OWNER, MANAGER |
| List notes | `GET /api/v1/spaces/{spaceId}/members/{memberId}/notes` | Any active space member |
| Audit history | `GET /api/v1/spaces/{spaceId}/members/{memberId}/history` | Any active space member |

**Auth:** All endpoints require JWT.

```
Authorization: Bearer <accessToken>
Content-Type: application/json
```

See [auth-ui-integration.md](./auth-ui-integration.md) for login. Use [my-spaces-ui-integration.md](./my-spaces-ui-integration.md) for `spaceId` from the current space context.

---

## Key concepts

### `active` vs `status`

| Field | Meaning | Changed by |
|-------|---------|------------|
| `active` (`isActive`) | Soft delete — member hidden from list | `DELETE /members/{memberId}` (OWNER only) |
| `status` | Operational lifecycle | `PUT /members/{memberId}/status` |

| `status` value | Typical UI label | Suggested badge color |
|----------------|------------------|------------------------|
| `ACTIVE` | Active | Green |
| `VACATED` | Vacated | Gray |
| `SUSPENDED` | Suspended | Orange |
| `BLACKLISTED` | Blacklisted | Red |

New members default to `status: ACTIVE`. Show `status` on the members list and detail header.

### Deposit fields

| Field | Stored | Notes |
|-------|--------|-------|
| `depositAmount` | Yes | Total deposit required |
| `depositPaid` | Yes | Amount collected |
| `depositRefunded` | Yes | Amount returned |
| `depositBalance` | **Computed** | `depositPaid - depositRefunded` — display only, do not send on PUT |

**Client-side validation** (mirror backend rules before submit):

```
depositAmount   >= 0
depositPaid     >= 0
depositRefunded >= 0
depositPaid     <= depositAmount
depositRefunded <= depositPaid
```

### Documents (metadata only)

- No file upload API yet. `fileUrl` is a **placeholder** (e.g. `"pending-upload"`).
- `verificationStatus` defaults to `PENDING` on create.
- There is **no API** to change verification status in Phase 3 — display as read-only.

### History

Automatic audit trail for:

| `action` | Trigger |
|----------|---------|
| `STATUS_CHANGED` | Status update |
| `DEPOSIT_UPDATED` | Deposit update |
| `EMERGENCY_CONTACT_UPDATED` | Emergency contact update |

Notes and document changes are **not** recorded in history (Phase 3).

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
│  Members list            │  GET /members
│  show status badge       │
└────────────┬─────────────┘
             │ tap row
             ▼
┌──────────────────────────┐
│  Member Detail           │  GET /members/{memberId}
│  (profile summary)       │  ← includes status, deposit, emergency contact
└────────────┬─────────────┘
             │
   ┌─────────┼─────────┬──────────┬──────────┐
   ▼         ▼         ▼          ▼          ▼
┌──────┐ ┌────────┐ ┌─────────┐ ┌────────┐ ┌─────────┐
│Profile│ │Deposit │ │Documents│ │ Notes  │ │ History │
│edit   │ │form    │ │list     │ │thread  │ │timeline │
└───┬───┘ └───┬────┘ └────┬────┘ └───┬────┘ └────┬────┘
    │         │           │          │           │
    │ PUT     │ PUT       │ GET/POST │ GET/POST  │ GET
    │/members │/deposit   │/documents│/notes     │/history
    │         │           │ DELETE   │           │
    │ PUT     │           │          │           │
    │/status  │           │          │           │
    │ PUT     │           │          │           │
    │/emergency-contact   │          │           │
    └─────────┴───────────┴──────────┴───────────┘
```

### Screen: Member detail (extended)

1. `GET /api/v1/spaces/{spaceId}/members/{memberId}` — load summary (all profile fields in one response).
2. Show header: `fullName`, `role`, `status` badge, `linkedUser` badge.
3. Tabs or sections:
   - **Profile** — edit name/mobile/role (`PUT /members/{memberId}`), change status, emergency contact
   - **Deposit** — amounts + computed balance
   - **Documents** — list + add + delete
   - **Notes** — chronological list + add note
   - **History** — audit timeline (read-only)

### Recommended load strategy

**Option A — Single detail call (simplest)**

- Use `GET /members/{memberId}` for profile, deposit, and emergency contact.
- Lazy-load tabs: documents, notes, history on first tab open.

**Option B — Parallel on detail mount**

```typescript
const [details, documents, notes, history] = await Promise.all([
  getMember(spaceId, memberId),
  getMemberDocuments(spaceId, memberId),
  getMemberNotes(spaceId, memberId),
  getMemberHistory(spaceId, memberId),
]);
```

Use Option B if all tabs are visible immediately; Option A reduces initial payload.

---

## Common response envelope

Most endpoints return `ApiResponse<T>`:

```json
{
  "success": true,
  "message": "Optional message",
  "data": {},
  "timestamp": "2026-06-11T16:00:00"
}
```

**Exceptions:** `DELETE /members/{memberId}/documents/{documentId}` returns **`204 No Content`** with an empty body.

### Error response

```json
{
  "success": false,
  "message": "Deposit paid cannot exceed deposit amount",
  "data": null,
  "timestamp": "2026-06-11T16:00:00"
}
```

Validation errors (`400`):

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "depositAmount": "Deposit amount must be zero or greater"
  },
  "timestamp": "2026-06-11T16:00:00"
}
```

### HTTP status codes

| Status | When |
|--------|------|
| `200` | Success (GET, PUT) |
| `201` | Document or note created |
| `204` | Document deleted |
| `400` | Validation or business rule (deposit rules, etc.) |
| `401` | Missing or invalid JWT |
| `403` | Not allowed (wrong role or not a space member) |
| `404` | Space, member, or document not found |

---

## Enums

### MemberStatus

| Value | Meaning |
|-------|---------|
| `ACTIVE` | Member is active in the space |
| `VACATED` | Member has vacated |
| `SUSPENDED` | Temporarily suspended |
| `BLACKLISTED` | Blacklisted |

### MemberDocumentType

| Value | Label (suggested) |
|-------|-------------------|
| `AADHAAR` | Aadhaar |
| `PAN` | PAN |
| `PASSPORT` | Passport |
| `DRIVING_LICENSE` | Driving License |
| `STUDENT_ID` | Student ID |
| `OTHER` | Other |

### DocumentVerificationStatus

| Value | Meaning | UI |
|-------|---------|-----|
| `PENDING` | Not yet verified | Yellow badge |
| `VERIFIED` | Verified | Green badge |
| `REJECTED` | Rejected | Red badge |

Read-only in Phase 3 (set by backend default on create).

### MemberHistoryAction

| Value | Display label |
|-------|---------------|
| `STATUS_CHANGED` | Status changed |
| `DEPOSIT_UPDATED` | Deposit updated |
| `EMERGENCY_CONTACT_UPDATED` | Emergency contact updated |

---

## TypeScript types

```typescript
export type MemberStatus = 'ACTIVE' | 'VACATED' | 'SUSPENDED' | 'BLACKLISTED';

export type MemberDocumentType =
  | 'AADHAAR'
  | 'PAN'
  | 'PASSPORT'
  | 'DRIVING_LICENSE'
  | 'STUDENT_ID'
  | 'OTHER';

export type DocumentVerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';

export type MemberHistoryAction =
  | 'STATUS_CHANGED'
  | 'DEPOSIT_UPDATED'
  | 'EMERGENCY_CONTACT_UPDATED';

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  timestamp?: string;
}

// --- Extended member detail (GET /members/{memberId} and PUT responses) ---

export interface MemberDetailsResponse {
  memberId: string;
  spaceId: string;
  fullName: string;
  mobileNumber: string;
  role: string; // MembershipRole — see membership-ui-integration.md
  linkedUser: boolean;
  linkedUserId?: string | null;
  membershipId?: string | null;
  active: boolean;
  status: MemberStatus;
  statusUpdatedAt?: string | null;
  emergencyContactName?: string | null;
  emergencyContactRelation?: string | null;
  emergencyContactMobile?: string | null;
  depositAmount: string;   // JSON number as string from backend BigDecimal
  depositPaid: string;
  depositRefunded: string;
  depositBalance: string;  // computed — do not send on update
  createdAt: string;
  updatedAt: string;
}

// --- Phase 3 requests ---

export interface UpdateMemberStatusRequest {
  status: MemberStatus;
}

export interface UpdateEmergencyContactRequest {
  emergencyContactName: string;
  emergencyContactRelation: string;
  emergencyContactMobile: string;
}

export interface UpdateDepositRequest {
  depositAmount: number;
  depositPaid: number;
  depositRefunded: number;
}

export interface CreateMemberDocumentRequest {
  documentType: MemberDocumentType;
  documentNumber: string;
  fileUrl: string;
}

export interface CreateMemberNoteRequest {
  note: string;
}

// --- Phase 3 responses ---

export interface MemberDocumentResponse {
  documentId: string;
  documentType: MemberDocumentType;
  documentNumber: string;
  fileUrl: string;
  verificationStatus: DocumentVerificationStatus;
  uploadedAt: string;
}

export interface MemberNoteResponse {
  noteId: string;
  note: string;
  createdBy: string;
  createdByName: string;
  createdAt: string;
}

export interface MemberHistoryResponse {
  historyId: string;
  action: MemberHistoryAction;
  oldValue?: string | null;
  newValue?: string | null;
  changedBy: string;
  changedByName: string;
  changedAt: string;
}
```

### Parsing deposit amounts

Backend returns `BigDecimal` as JSON numbers (e.g. `10000.00`). Use `number` in forms and `toFixed(2)` for display:

```typescript
export function formatCurrency(amount: number | string): string {
  const n = typeof amount === 'string' ? parseFloat(amount) : amount;
  return `₹${n.toFixed(2)}`;
}

export function validateDeposit(body: UpdateDepositRequest): string | null {
  const { depositAmount, depositPaid, depositRefunded } = body;
  if (depositAmount < 0 || depositPaid < 0 || depositRefunded < 0) {
    return 'Amounts cannot be negative';
  }
  if (depositPaid > depositAmount) {
    return 'Paid amount cannot exceed total deposit';
  }
  if (depositRefunded > depositPaid) {
    return 'Refunded amount cannot exceed paid amount';
  }
  return null;
}
```

---

## Permission matrix (UI visibility)

Use caller's `membershipRole` from `GET /spaces/my` for the current space.

| Action | OWNER | MANAGER | TENANT / CUSTOMER / STAFF |
|--------|-------|---------|----------------------------|
| View member detail (extended fields) | Yes | Yes | Yes |
| View documents / notes / history | Yes | Yes | Yes |
| Update status | Yes | Yes | No |
| Update emergency contact | Yes | Yes | No |
| Update deposit | Yes | Yes | No |
| Add / delete document | Yes | Yes | No |
| Add note | Yes | Yes | No |

Remove member (`DELETE /members/{memberId}`) remains **OWNER only** — see [membership-ui-integration.md](./membership-ui-integration.md).

---

## APIs

### API 1: Update Member Status

| | |
|---|---|
| **Method** | `PUT` |
| **Path** | `/api/v1/spaces/{spaceId}/members/{memberId}/status` |
| **Permission** | OWNER or MANAGER |

#### Request

```json
{
  "status": "SUSPENDED"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `status` | Yes | One of `MemberStatus` enum values |

#### Success — `200`

Returns full `MemberDetailsResponse` with updated `status` and `statusUpdatedAt`.

```json
{
  "success": true,
  "message": "Member status updated successfully",
  "data": {
    "memberId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "spaceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "fullName": "Rahul Sharma",
    "mobileNumber": "9876543210",
    "role": "TENANT",
    "linkedUser": false,
    "linkedUserId": null,
    "membershipId": null,
    "active": true,
    "status": "SUSPENDED",
    "statusUpdatedAt": "2026-06-11T16:00:00",
    "emergencyContactName": null,
    "emergencyContactRelation": null,
    "emergencyContactMobile": null,
    "depositAmount": 0,
    "depositPaid": 0,
    "depositRefunded": 0,
    "depositBalance": 0,
    "createdAt": "2026-06-10T22:00:00",
    "updatedAt": "2026-06-11T16:00:00"
  },
  "timestamp": "2026-06-11T16:00:00"
}
```

| HTTP | `message` (typical) | UI action |
|------|---------------------|-----------|
| `403` | Only OWNER or MANAGER can perform this action | Hide status picker |
| `404` | Member not found | Navigate back to list |

**UI tip:** Confirm before setting `BLACKLISTED` or `SUSPENDED`. Refresh history tab after success.

---

### API 2: Update Emergency Contact

| | |
|---|---|
| **Method** | `PUT` |
| **Path** | `/api/v1/spaces/{spaceId}/members/{memberId}/emergency-contact` |
| **Permission** | OWNER or MANAGER |

#### Request

```json
{
  "emergencyContactName": "Priya Sharma",
  "emergencyContactRelation": "Mother",
  "emergencyContactMobile": "9988776655"
}
```

| Field | Required |
|-------|----------|
| `emergencyContactName` | Yes |
| `emergencyContactRelation` | Yes |
| `emergencyContactMobile` | Yes |

#### Success — `200`

Returns full `MemberDetailsResponse` with updated emergency contact fields.

```json
{
  "success": true,
  "message": "Emergency contact updated successfully",
  "data": {
    "memberId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "emergencyContactName": "Priya Sharma",
    "emergencyContactRelation": "Mother",
    "emergencyContactMobile": "9988776655",
    "status": "ACTIVE",
    "depositAmount": 10000,
    "depositPaid": 10000,
    "depositRefunded": 0,
    "depositBalance": 10000
  },
  "timestamp": "2026-06-11T16:05:00"
}
```

*(Other `MemberDetailsResponse` fields omitted for brevity.)*

---

### API 3: Update Deposit

| | |
|---|---|
| **Method** | `PUT` |
| **Path** | `/api/v1/spaces/{spaceId}/members/{memberId}/deposit` |
| **Permission** | OWNER or MANAGER |

#### Request

```json
{
  "depositAmount": 10000.00,
  "depositPaid": 10000.00,
  "depositRefunded": 2000.00
}
```

| Field | Required | Validation |
|-------|----------|------------|
| `depositAmount` | Yes | `>= 0` |
| `depositPaid` | Yes | `>= 0`, `<= depositAmount` |
| `depositRefunded` | Yes | `>= 0`, `<= depositPaid` |

Do **not** send `depositBalance` — it is computed server-side.

#### Success — `200`

```json
{
  "success": true,
  "message": "Deposit updated successfully",
  "data": {
    "memberId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "depositAmount": 10000,
    "depositPaid": 10000,
    "depositRefunded": 2000,
    "depositBalance": 8000,
    "status": "ACTIVE"
  },
  "timestamp": "2026-06-11T16:10:00"
}
```

| HTTP | `message` (typical) | UI action |
|------|---------------------|-----------|
| `400` | Deposit paid cannot exceed deposit amount | Highlight paid field |
| `400` | Deposit refunded cannot exceed deposit paid | Highlight refunded field |
| `400` | Deposit amount must be zero or greater | Field-level validation |

**UI tip:** Show summary card: Required / Paid / Refunded / **Balance** (`depositBalance`).

---

### API 4: Add Member Document

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/spaces/{spaceId}/members/{memberId}/documents` |
| **Permission** | OWNER or MANAGER |

#### Request

```json
{
  "documentType": "AADHAAR",
  "documentNumber": "1234-5678-9012",
  "fileUrl": "pending-upload"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `documentType` | Yes | See `MemberDocumentType` |
| `documentNumber` | Yes | ID number as entered |
| `fileUrl` | Yes | Use `"pending-upload"` until file storage exists |

#### Success — `201`

```json
{
  "success": true,
  "message": "Document added successfully",
  "data": {
    "documentId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "documentType": "AADHAAR",
    "documentNumber": "1234-5678-9012",
    "fileUrl": "pending-upload",
    "verificationStatus": "PENDING",
    "uploadedAt": "2026-06-11T16:15:00"
  },
  "timestamp": "2026-06-11T16:15:00"
}
```

---

### API 5: List Member Documents

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/spaces/{spaceId}/members/{memberId}/documents` |
| **Permission** | Any active space member |

#### Success — `200`

```json
{
  "success": true,
  "data": [
    {
      "documentId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "documentType": "AADHAAR",
      "documentNumber": "1234-5678-9012",
      "fileUrl": "pending-upload",
      "verificationStatus": "PENDING",
      "uploadedAt": "2026-06-11T16:15:00"
    }
  ],
  "timestamp": "2026-06-11T16:20:00"
}
```

Sorted by `uploadedAt` descending (newest first).

---

### API 6: Delete Member Document

| | |
|---|---|
| **Method** | `DELETE` |
| **Path** | `/api/v1/spaces/{spaceId}/members/{memberId}/documents/{documentId}` |
| **Permission** | OWNER or MANAGER |

#### Success — `204`

Empty body. Refresh documents list after delete.

| HTTP | When |
|------|------|
| `404` | Document not found or does not belong to member |

---

### API 7: Add Member Note

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/v1/spaces/{spaceId}/members/{memberId}/notes` |
| **Permission** | OWNER or MANAGER |

#### Request

```json
{
  "note": "Member requested early checkout on 15 June."
}
```

#### Success — `201`

```json
{
  "success": true,
  "message": "Note added successfully",
  "data": {
    "noteId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "note": "Member requested early checkout on 15 June.",
    "createdBy": "c3d4e5f6-a7b8-9012-cdef-123456789012",
    "createdByName": "Owner User",
    "createdAt": "2026-06-11T16:25:00"
  },
  "timestamp": "2026-06-11T16:25:00"
}
```

**UI tip:** Render as a chat-style or timeline list with `createdByName` and formatted `createdAt`.

---

### API 8: List Member Notes

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/spaces/{spaceId}/members/{memberId}/notes` |
| **Permission** | Any active space member |

#### Success — `200`

```json
{
  "success": true,
  "data": [
    {
      "noteId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
      "note": "Member requested early checkout on 15 June.",
      "createdBy": "c3d4e5f6-a7b8-9012-cdef-123456789012",
      "createdByName": "Owner User",
      "createdAt": "2026-06-11T16:25:00"
    }
  ],
  "timestamp": "2026-06-11T16:30:00"
}
```

Sorted by `createdAt` descending (newest first).

---

### API 9: Get Member History

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/v1/spaces/{spaceId}/members/{memberId}/history` |
| **Permission** | Any active space member |

#### Success — `200`

```json
{
  "success": true,
  "data": [
    {
      "historyId": "d4e5f6a7-b8c9-0123-def0-234567890123",
      "action": "STATUS_CHANGED",
      "oldValue": "ACTIVE",
      "newValue": "SUSPENDED",
      "changedBy": "c3d4e5f6-a7b8-9012-cdef-123456789012",
      "changedByName": "Owner User",
      "changedAt": "2026-06-11T16:00:00"
    },
    {
      "historyId": "e5f6a7b8-c9d0-1234-ef01-345678901234",
      "action": "DEPOSIT_UPDATED",
      "oldValue": "amount=0, paid=0, refunded=0",
      "newValue": "amount=10000, paid=10000, refunded=0",
      "changedBy": "c3d4e5f6-a7b8-9012-cdef-123456789012",
      "changedByName": "Owner User",
      "changedAt": "2026-06-11T15:30:00"
    }
  ],
  "timestamp": "2026-06-11T16:35:00"
}
```

#### History display helpers

```typescript
const ACTION_LABELS: Record<MemberHistoryAction, string> = {
  STATUS_CHANGED: 'Status changed',
  DEPOSIT_UPDATED: 'Deposit updated',
  EMERGENCY_CONTACT_UPDATED: 'Emergency contact updated',
};

export function formatHistoryEntry(entry: MemberHistoryResponse): string {
  switch (entry.action) {
    case 'STATUS_CHANGED':
      return `${entry.oldValue} → ${entry.newValue}`;
    case 'DEPOSIT_UPDATED':
    case 'EMERGENCY_CONTACT_UPDATED':
      return entry.newValue ?? '';
    default:
      return entry.newValue ?? '';
  }
}
```

---

## Extended list item (`MemberResponse`)

Members list now includes `status`:

```json
{
  "memberId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "fullName": "Rahul Sharma",
  "mobileNumber": "9876543210",
  "role": "TENANT",
  "linkedUser": false,
  "status": "ACTIVE",
  "createdAt": "2026-06-10T22:00:00"
}
```

Show a status chip next to each row. Filter or sort by `status` client-side if needed (no server filter in Phase 3).

---

## Client API module

Add to your existing `memberApi.ts` (extends helpers from [membership-ui-integration.md](./membership-ui-integration.md)):

```typescript
import type {
  ApiResponse,
  CreateMemberDocumentRequest,
  CreateMemberNoteRequest,
  MemberDetailsResponse,
  MemberDocumentResponse,
  MemberHistoryResponse,
  MemberNoteResponse,
  UpdateDepositRequest,
  UpdateEmergencyContactRequest,
  UpdateMemberStatusRequest,
} from './types';

const API_BASE = 'http://10.0.2.2:8080'; // adjust per environment

async function parseResponse<T>(res: Response): Promise<T> {
  if (res.status === 204) return undefined as T;
  const json: ApiResponse<T> = await res.json();
  if (!res.ok || !json.success) {
    throw new ApiError(res.status, json.message ?? 'Request failed', json.data);
  }
  return json.data as T;
}

function authFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const token = getAccessToken(); // your auth store
  return fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });
}

export async function updateMemberStatus(
  spaceId: string,
  memberId: string,
  body: UpdateMemberStatusRequest,
): Promise<MemberDetailsResponse> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/members/${memberId}/status`,
    { method: 'PUT', body: JSON.stringify(body) },
  );
  return parseResponse<MemberDetailsResponse>(res);
}

export async function updateEmergencyContact(
  spaceId: string,
  memberId: string,
  body: UpdateEmergencyContactRequest,
): Promise<MemberDetailsResponse> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/members/${memberId}/emergency-contact`,
    { method: 'PUT', body: JSON.stringify(body) },
  );
  return parseResponse<MemberDetailsResponse>(res);
}

export async function updateDeposit(
  spaceId: string,
  memberId: string,
  body: UpdateDepositRequest,
): Promise<MemberDetailsResponse> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/members/${memberId}/deposit`,
    { method: 'PUT', body: JSON.stringify(body) },
  );
  return parseResponse<MemberDetailsResponse>(res);
}

export async function addMemberDocument(
  spaceId: string,
  memberId: string,
  body: CreateMemberDocumentRequest,
): Promise<MemberDocumentResponse> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/members/${memberId}/documents`,
    { method: 'POST', body: JSON.stringify(body) },
  );
  return parseResponse<MemberDocumentResponse>(res);
}

export async function getMemberDocuments(
  spaceId: string,
  memberId: string,
): Promise<MemberDocumentResponse[]> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/members/${memberId}/documents`,
  );
  return parseResponse<MemberDocumentResponse[]>(res);
}

export async function deleteMemberDocument(
  spaceId: string,
  memberId: string,
  documentId: string,
): Promise<void> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/members/${memberId}/documents/${documentId}`,
    { method: 'DELETE' },
  );
  await parseResponse<void>(res);
}

export async function addMemberNote(
  spaceId: string,
  memberId: string,
  body: CreateMemberNoteRequest,
): Promise<MemberNoteResponse> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/members/${memberId}/notes`,
    { method: 'POST', body: JSON.stringify(body) },
  );
  return parseResponse<MemberNoteResponse>(res);
}

export async function getMemberNotes(
  spaceId: string,
  memberId: string,
): Promise<MemberNoteResponse[]> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/members/${memberId}/notes`,
  );
  return parseResponse<MemberNoteResponse[]>(res);
}

export async function getMemberHistory(
  spaceId: string,
  memberId: string,
): Promise<MemberHistoryResponse[]> {
  const res = await authFetch(
    `/api/v1/spaces/${spaceId}/members/${memberId}/history`,
  );
  return parseResponse<MemberHistoryResponse[]>(res);
}
```

### Status picker

```typescript
export const MEMBER_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active', color: '#22c55e' },
  { value: 'VACATED', label: 'Vacated', color: '#6b7280' },
  { value: 'SUSPENDED', label: 'Suspended', color: '#f97316' },
  { value: 'BLACKLISTED', label: 'Blacklisted', color: '#ef4444' },
] as const;
```

### Document type picker

```typescript
export const DOCUMENT_TYPE_OPTIONS = [
  { value: 'AADHAAR', label: 'Aadhaar' },
  { value: 'PAN', label: 'PAN' },
  { value: 'PASSPORT', label: 'Passport' },
  { value: 'DRIVING_LICENSE', label: 'Driving License' },
  { value: 'STUDENT_ID', label: 'Student ID' },
  { value: 'OTHER', label: 'Other' },
] as const;
```

### `useMemberDetail` hook (example)

```typescript
import { useCallback, useEffect, useState } from 'react';
import { getMember } from '../api/memberApi';
import type { MemberDetailsResponse } from '../api/types';

export function useMemberDetail(spaceId: string | null, memberId: string | null) {
  const [member, setMember] = useState<MemberDetailsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!spaceId || !memberId) return;
    setLoading(true);
    setError(null);
    try {
      setMember(await getMember(spaceId, memberId));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load member');
    } finally {
      setLoading(false);
    }
  }, [spaceId, memberId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { member, loading, error, refresh };
}
```

After any PUT (status, emergency contact, deposit), call `refresh()` to sync the detail screen and optionally reload history.

---

## Quick reference

| Method | Path | Body | Response `data` | Status |
|--------|------|------|-----------------|--------|
| `PUT` | `/api/v1/spaces/{spaceId}/members/{memberId}/status` | `UpdateMemberStatusRequest` | `MemberDetailsResponse` | `200` |
| `PUT` | `/api/v1/spaces/{spaceId}/members/{memberId}/emergency-contact` | `UpdateEmergencyContactRequest` | `MemberDetailsResponse` | `200` |
| `PUT` | `/api/v1/spaces/{spaceId}/members/{memberId}/deposit` | `UpdateDepositRequest` | `MemberDetailsResponse` | `200` |
| `POST` | `/api/v1/spaces/{spaceId}/members/{memberId}/documents` | `CreateMemberDocumentRequest` | `MemberDocumentResponse` | `201` |
| `GET` | `/api/v1/spaces/{spaceId}/members/{memberId}/documents` | — | `MemberDocumentResponse[]` | `200` |
| `DELETE` | `/api/v1/spaces/{spaceId}/members/{memberId}/documents/{documentId}` | — | *(none)* | `204` |
| `POST` | `/api/v1/spaces/{spaceId}/members/{memberId}/notes` | `CreateMemberNoteRequest` | `MemberNoteResponse` | `201` |
| `GET` | `/api/v1/spaces/{spaceId}/members/{memberId}/notes` | — | `MemberNoteResponse[]` | `200` |
| `GET` | `/api/v1/spaces/{spaceId}/members/{memberId}/history` | — | `MemberHistoryResponse[]` | `200` |

**Also used on the same screen:**

| Method | Path | See |
|--------|------|-----|
| `GET` | `/api/v1/spaces/{spaceId}/members/{memberId}` | [membership-ui-integration.md](./membership-ui-integration.md) — full profile |
| `PUT` | `/api/v1/spaces/{spaceId}/members/{memberId}` | Profile edit (name, mobile, role) |
| `DELETE` | `/api/v1/spaces/{spaceId}/members/{memberId}` | Remove member (OWNER only) |

---

## Related docs

- [membership-ui-integration.md](./membership-ui-integration.md) — Member CRUD, invitations, `MemberResponse` list
- [auth-ui-integration.md](./auth-ui-integration.md) — Login, JWT
- [my-spaces-ui-integration.md](./my-spaces-ui-integration.md) — Current space context
- [space-ui-integration.md](./space-ui-integration.md) — Space management
