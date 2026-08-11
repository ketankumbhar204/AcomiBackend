# Amico Backend Context

## Project Overview

Amico is a WhatsApp-first operations management platform for:

* PG Owners
* Mess Vendors
* Hostels
* Co-Living Spaces
* Shared Accommodation Providers

The platform helps businesses manage:

* Members
* Payments
* Occupancy
* Complaints
* Menus
* Availability Tracking
* Notifications
* Operational Reporting

The primary goal is to reduce operational effort and food wastage through availability tracking and WhatsApp automation.

---

# Core Principles

1. Mobile First
2. WhatsApp First
3. Minimum Clicks
4. Simplicity Over Configuration
5. Owner Controlled Onboarding
6. Multi-Space Support
7. Historical Data Preservation
8. Modular Monolith Architecture
9. PostgreSQL Relational Design
10. MVP First, Scale Later

---

# Architecture

Backend Architecture:

* Spring Boot 3
* Java 17
* PostgreSQL
* Flyway
* JPA / Hibernate
* JWT Authentication

Architecture Style:

Modular Monolith

Do NOT generate microservices.

Modules should remain isolated inside a single Spring Boot application.

---

# Space Concept

The system revolves around Spaces.

Space Types:

* PG
* Mess
* Hostel
* Co-Living

Examples:

* PG-A
* PG-B
* Office Mess
* Hostel-A

A space is the top-level business entity.

---

# User Model

A User is an independent account.

A User can:

* Own multiple spaces
* Join multiple spaces
* Participate in multiple spaces simultaneously

Examples:

Ketan:

* PG-A (Owner)
* PG-B (Owner)
* Mess-A (Owner)

Rahul:

* PG-A (Tenant)
* Mess-A (Customer)
* Mess-B (Customer)

Never restrict a user to a single space.

---

# Membership Model

Use SpaceMembership as the relationship entity.

Relationship:

User
↕
SpaceMembership
↕
Space

Membership Roles:

* OWNER
* MANAGER
* TENANT
* CUSTOMER
* STAFF

Membership Status:

* INVITATION_SENT
* ACCEPTED
* ACTIVE
* INACTIVE
* REMOVED
* VACATED

A membership record must never be deleted.

Historical records must always be preserved.

---

# Authentication

Authentication Method:

* Mobile Number
* OTP Verification

No password-based login.

Future:

* WhatsApp OTP
* SMS OTP

---

# Invitation Model

Users cannot directly join spaces.

Workflow:

Owner creates invitation
↓
Invitation sent
↓
User logs in
↓
User accepts invitation
↓
Membership created

Invitation Status:

* PENDING
* ACCEPTED
* REJECTED
* EXPIRED

---

# Core Business Modules

## User Module

Responsibilities:

* User Profile
* Mobile Number
* Profile Photo
* Profile Updates

---

## Space Module

Responsibilities:

* Create Space
* Update Space
* View Space
* Manage Space Details

---

## Membership Module

Responsibilities:

* Invitations
* Membership Management
* Space Access

---

## Member Module

Responsibilities:

* Add Member
* Update Member
* Member Profile
* Deposit Tracking
* Occupancy Information

Important Fields:

* Name
* Mobile Number
* Joining Date
* Monthly Rent
* Food Included
* Food Charges
* Deposit Amount

---

## Room Module

Responsibilities:

* Building
* Floor
* Room
* Bed
* Occupancy

Status:

* AVAILABLE
* RESERVED
* OCCUPIED
* UNDER_MAINTENANCE

---

## Meal Module

Responsibilities:

* Menu Master
* Meal Combo
* Daily Menu
* Weekly Menu
* Availability Collection

Meal Combo Example:

Combo A

* Dal Rice
* Salad

Combo B

* Paneer Masala
* Chapati
* Rice

---

## Availability Module

Primary USP

Purpose:

Collect meal availability and calculate expected headcount.

Response Examples:

PG:

* Combo 1
* Not Available

Mess:

* Combo 1
* Combo 2
* Combo 3
* Not Available

Output:

* Expected Count
* Option Wise Count
* Not Available Count

---

## Payment Module

**Phase 7 (partial):** Dashboard financial rollup and member payment ledger — see [payments-phase-7-dashboard.md](./payments-phase-7-dashboard.md).

Responsibilities:

* Rent Collection (expected from occupancy ✅; recording ⬜)
* Mess Fees (meal activity + poll payments ✅)
* Maintenance Charges (future)
* Deposit Tracking (member profile ✅; space collections ⬜)
* Payment Proof Verification (meal polls 🔶)

Payment status (ledger rows):

* `PAID` | `PARTIAL` | `PENDING` | `NONE`

Financial cards (all space types):

* Expected Charges · Collected · Pending

---

## Dashboard Module

**Phase 7 (partial):** Unified operator dashboard — see [payments-phase-7-dashboard.md](./payments-phase-7-dashboard.md).

Responsibilities:

* Space financial snapshot (month)
* Mess operational metrics (eligibility, menus, polls, headcount)
* Accommodation operational metrics (beds, move-ins, pending payments)
* Attention items (menu planning, open polls, overdue payments)

APIs:

* `GET /api/v1/spaces/{spaceId}/dashboard-summary`
* `GET /api/v1/spaces/{spaceId}/payments/ledger`

Access: dashboard summary — OWNER/MANAGER/STAFF; ledger — OWNER/MANAGER.

---

## Complaint Module

Responsibilities:

* Raise Complaint
* Assign Complaint
* Resolve Complaint

Complaint Status:

* OPEN
* IN_PROGRESS
* RESOLVED

---

## Notification Module

Responsibilities:

* In-App Notifications
* Notices
* Alerts

---

## WhatsApp Module

Primary USP

Responsibilities:

* Availability Polls
* Payment Reminders
* Invitations
* Notices
* Broadcast Messages

---

## Report Module

Responsibilities:

* Payment Reports
* Occupancy Reports
* Meal Reports
* Complaint Reports

---

# Database Design Rules

Use UUID primary keys for all entities.

Every entity should have:

* id
* createdAt
* updatedAt

Use auditing support.

Use soft status changes instead of deletes.

Avoid hard deletes for business data.

---

# Historical Data Policy

Never delete:

* Users
* Spaces
* Memberships
* Payments
* Complaints
* Meal Responses

Instead update status.

Examples:

* ACTIVE
* INACTIVE
* VACATED

Store:

* Joining Date
* Exit Date

---

# MVP Scope

Build in this order:

1. User
2. Space
3. Space Membership
4. Invitation
5. Authentication
6. Member Management
7. Room Management
8. Meal Management
9. Availability Tracking
10. Payment Management
11. Complaint Management
12. Notifications

Do not implement AI features in MVP.

Do not implement advanced analytics in MVP.

Do not implement microservices in MVP.

---

# Success Criteria

A PG Owner or Mess Vendor should be able to:

* Create Account
* Create Space
* Invite Members
* Manage Occupancy
* Create Menu
* Collect Availability
* Track Payments

within 10 minutes of first using Amico.
