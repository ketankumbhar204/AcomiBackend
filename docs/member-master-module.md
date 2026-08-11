# Member Master Module

## Goal

Allow owners and managers to manage members even if they do not install the Amico app.

A Member is a business record.

A User is an application account.

A Member may or may not be linked to a User account.

Examples:

Tenant who never installs app
Customer who never installs app
Staff member who never installs app

must still be manageable inside Amico.

---

# Domain Model

Current:

User
Space
SpaceMembership
Invitation

Add:

Member

---

# Member Entity

Create:

Member

Fields:

* id
* spaceId
* userId (nullable)
* fullName
* mobileNumber
* role
* active
* createdAt
* updatedAt

Rules:

userId can be null.

When a member later registers using the same mobile number:

Member can be linked to User.

---

# Roles

Supported:

* OWNER
* MANAGER
* TENANT
* CUSTOMER
* STAFF

OWNER cannot be created using Member APIs.

---

# Feature 1: Add Member Directly

Purpose:

Allow owner to add member without invitation.

API:

POST /api/v1/spaces/{spaceId}/members

Request:

{
"fullName": "Rahul Sharma",
"mobileNumber": "9876543210",
"role": "TENANT"
}

Validation:

* Space must exist
* Mobile number required
* Role required
* OWNER not allowed
* Duplicate active member with same mobile should not exist in same space

Response:

MemberResponse

---

# Feature 2: View Members

API:

GET /api/v1/spaces/{spaceId}/members

Return:

* memberId
* fullName
* mobileNumber
* role
* linkedUser
* createdAt

Example:

{
"memberId": "...",
"fullName": "Rahul Sharma",
"mobileNumber": "9876543210",
"role": "TENANT",
"linkedUser": false
}

---

# Feature 3: Member Details

API:

GET /api/v1/spaces/{spaceId}/members/{memberId}

Return complete member details.

---

# Feature 4: Update Member

API:

PUT /api/v1/spaces/{spaceId}/members/{memberId}

Editable:

* fullName
* mobileNumber
* role

Validation:

OWNER role not allowed.

---

# Feature 5: Remove Member

API:

DELETE /api/v1/spaces/{spaceId}/members/{memberId}

Soft Delete:

active = false

Response:

204 No Content

---

# Feature 6: Link User Automatically

Future Ready Design

When a user registers:

Check:

mobileNumber

If matching active member exists:

Link:

member.userId = user.id

No duplicate member creation.

Do not implement automatic linking now.

Only keep database structure ready.

---

# Repository Layer

Create:

MemberRepository

Methods:

* findByIdAndActiveTrue()
* findBySpaceIdAndActiveTrue()
* findBySpaceIdAndMobileNumber()
* existsBySpaceIdAndMobileNumberAndActiveTrue()

---

# Service Layer

Implement:

* createMember()
* getMembers()
* getMember()
* updateMember()
* removeMember()

---

# DTOs

Create:

CreateMemberRequest

UpdateMemberRequest

MemberResponse

MemberDetailsResponse

---

# Controller Endpoints

POST   /api/v1/spaces/{spaceId}/members

GET    /api/v1/spaces/{spaceId}/members

GET    /api/v1/spaces/{spaceId}/members/{memberId}

PUT    /api/v1/spaces/{spaceId}/members/{memberId}

DELETE /api/v1/spaces/{spaceId}/members/{memberId}

---

# Security

OWNER

* Add Member
* Update Member
* Remove Member

MANAGER

* Add Member
* Update Member

TENANT
CUSTOMER
STAFF

* Read Only

---

# Logging

Add INFO logs:

Creating member

Fetching members

Fetching member details

Updating member

Removing member

---

# Out Of Scope

Do NOT implement:

* Room Allocation
* Deposits
* Rent
* Payments
* Meals
* Complaints

This module only manages Member Master records.
