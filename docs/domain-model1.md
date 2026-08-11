# Amico Domain Model

## Purpose

This document defines the core business entities, relationships, statuses, and business rules for Amico.

This document serves as the source of truth for:

* Database Design
* JPA Entities
* Flyway Migrations
* API Design
* Service Layer Design

---

# Entity Relationship Overview

User
↓
SpaceMembership
↓
Space

Space
↓
Member

Space
↓
Building
↓
Floor
↓
Room
↓
Bed

Space
↓
Menu
↓
MenuOption
↓
MealResponse

Member
↓
Payment

Member
↓
Complaint

---

# User

## Purpose

Represents a person using Amico.

A user exists independently of any PG, Mess, or Hostel.

A user can:

* Own multiple spaces
* Join multiple spaces
* Participate in multiple businesses simultaneously

## Fields

* id
* mobileNumber
* fullName
* profilePhotoUrl
* isActive
* createdAt
* updatedAt

## Relationships

User
1 → N
SpaceMembership

## Business Rules

* Mobile number must be unique.
* User account should never be deleted.
* User can belong to multiple spaces.

---

# Space

## Purpose

Represents a business entity.

Examples:

* PG-A
* PG-B
* Office Mess
* Hostel-A

## Fields

* id
* name
* type
* address
* contactNumber
* isActive
* createdAt
* updatedAt

## Space Types

* PG
* MESS
* HOSTEL
* CO_LIVING

## Relationships

Space
1 → N
SpaceMembership

Space
1 → N
Member

## Business Rules

* One user can own multiple spaces.
* One space can contain multiple members.

---

# SpaceMembership

## Purpose

Represents access between User and Space.

## Fields

* id
* userId
* spaceId
* role
* status
* joinedAt
* exitedAt
* createdAt
* updatedAt

## Roles

* OWNER
* MANAGER
* TENANT
* CUSTOMER
* STAFF

## Status

* INVITATION_SENT
* ACCEPTED
* ACTIVE
* INACTIVE
* REMOVED
* VACATED

## Relationships

User
N → 1
Space

## Business Rules

* A user may have multiple memberships.
* Membership records should never be deleted.
* Historical memberships must be preserved.

---

# Invitation

## Purpose

Controls onboarding.

Users cannot directly join spaces.

## Fields

* id
* spaceId
* mobileNumber
* role
* status
* expiresAt
* acceptedAt
* createdAt
* updatedAt

## Status

* PENDING
* ACCEPTED
* REJECTED
* EXPIRED

## Business Rules

Owner invites member.

Member accepts invitation.

Membership gets created.

---

# Member

## Purpose

Represents operational information about a tenant or customer inside a space.

## Fields

* id
* spaceId
* userId
* joiningDate
* monthlyRent
* foodIncluded
* depositAmount
* status
* notes
* createdAt
* updatedAt

## Status

* ACTIVE
* NOTICE_PERIOD
* VACATED
* INACTIVE

## Business Rules

* Member record should never be deleted.
* Historical occupancy must be retained.

---

# Building

## Purpose

Represents a physical building.

## Fields

* id
* spaceId
* name
* createdAt
* updatedAt

## Relationships

Building
1 → N
Floor

---

# Floor

## Purpose

Represents a building floor.

## Fields

* id
* buildingId
* floorNumber
* createdAt
* updatedAt

## Relationships

Floor
1 → N
Room

---

# Room

## Purpose

Represents a room.

## Fields

* id
* floorId
* roomNumber
* capacity
* createdAt
* updatedAt

## Relationships

Room
1 → N
Bed

---

# Bed

## Purpose

Represents a bed allocation.

## Fields

* id
* roomId
* bedNumber
* status
* createdAt
* updatedAt

## Status

* AVAILABLE
* RESERVED
* OCCUPIED
* UNDER_MAINTENANCE

---

# Menu

## Purpose

Represents a published menu.

## Fields

* id
* spaceId
* menuDate
* mealType
* createdAt
* updatedAt

## Meal Types

* BREAKFAST
* LUNCH
* DINNER

---

# MenuOption

## Purpose

Represents a selectable meal option.

## Fields

* id
* menuId
* optionName
* description
* isAvailable
* createdAt
* updatedAt

## Examples

PG

Option 1
Paneer Combo

Option 2
Not Available

Mess

Option 1
Dal Rice

Option 2
Veg Thali

Option 3
Paneer Thali

Option 4
Not Available

---

# MealResponse

## Purpose

Stores customer availability response.

## Fields

* id
* menuOptionId
* memberId
* responseTime
* createdAt
* updatedAt

## Business Rules

One member can select only one option per meal.

---

# Payment

## Purpose

Tracks charges and collections.

## Fields

* id
* memberId
* amount
* paymentType
* status
* paymentDate
* referenceNumber
* screenshotUrl
* createdAt
* updatedAt

## Payment Types

* RENT
* MESS_FEE
* MAINTENANCE
* DEPOSIT

## Status

* PAID
* PARTIAL
* PENDING

---

# Complaint

## Purpose

Tracks member issues.

## Fields

* id
* memberId
* title
* description
* category
* status
* createdAt
* updatedAt

## Categories

* ELECTRICITY
* WATER
* INTERNET
* FOOD
* MAINTENANCE
* HOUSEKEEPING

## Status

* OPEN
* IN_PROGRESS
* RESOLVED

---

# Notification

## Purpose

Stores notifications.

## Fields

* id
* spaceId
* title
* message
* type
* createdAt
* updatedAt

## Types

* NOTICE
* PAYMENT_REMINDER
* MENU_UPDATE
* COMPLAINT_UPDATE

---

# Global Rules

All entities must:

* Use UUID primary keys
* Store createdAt
* Store updatedAt
* Use PostgreSQL
* Use JPA Auditing
* Preserve historical data
* Avoid hard deletes

Status changes should be preferred over physical deletion.
