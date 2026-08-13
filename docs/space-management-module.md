# Space Management Module

## Goal

Implement complete Space Management functionality for Acomi.

A Space represents a business/workspace.

Supported Space Types:

* PG
* MESS
* HOSTEL
* CO_LIVING
* RENTAL

---

# Existing Functionality

Already Implemented:

* Create Space
* Space Entity
* Space Repository
* Space Membership
* User Ownership

---

# Feature 1: View Space Details

## API

GET /api/v1/spaces/{spaceId}

## Purpose

Fetch complete details of a single space.

## Response

Return:

* id
* name
* type
* address
* contactNumber
* ownerId
* createdAt
* updatedAt

## Validation

* Space must exist.
* Return 404 if not found.

---

# Feature 2: Update Space

## API

PUT /api/v1/spaces/{spaceId}

## Purpose

Allow owner to update space information.

## Request

{
"name": "Sunrise PG",
"address": "Pune",
"contactNumber": "9876543210"
}

## Editable Fields

* name
* address
* contactNumber

## Non Editable Fields

* id
* ownerId
* type
* createdAt

## Validation

* Space must exist.
* Only owner can update.

## Response

Return updated Space DTO.

---

# Feature 3: List User Spaces

## API

GET /api/v1/spaces/user/{userId}

## Purpose

Fetch all spaces associated with a user.

Includes:

* Owned Spaces
* Joined Spaces

## Response

Return:

* spaceId
* spaceName
* spaceType
* membershipRole
* joinedAt

---

# Feature 4: Deactivate Space

## Purpose

Do NOT physically delete space records.

Use soft delete.

## Database Changes

Add:

active BOOLEAN NOT NULL DEFAULT TRUE

## API

DELETE /api/v1/spaces/{spaceId}

## Behaviour

Set:

active = false

## Validation

* Only owner can deactivate.
* Space must exist.

## Response

204 No Content

---

# Repository Methods

Implement methods for:

## SpaceRepository

findByIdAndActiveTrue()

findAllByOwnerId()

findAllActiveSpacesForUser()

---

# Service Layer

Implement:

## SpaceService

createSpace()

getSpaceById()

updateSpace()

getUserSpaces()

deactivateSpace()

---

# DTOs

Create:

## UpdateSpaceRequest

* name
* address
* contactNumber

## SpaceDetailsResponse

* id
* name
* type
* address
* contactNumber
* ownerId
* createdAt
* updatedAt

---

# Controller Endpoints

POST /api/v1/spaces

GET /api/v1/spaces/{spaceId}

PUT /api/v1/spaces/{spaceId}

GET /api/v1/spaces/user/{userId}

DELETE /api/v1/spaces/{spaceId}

---

# Logging

Add INFO logs:

Creating space

Fetching space

Updating space

Fetching user spaces

Deactivating space

---

# Out Of Scope

Do NOT implement:

* Members Module
* Rooms Module
* Meal Module
* Payment Module
* Complaint Module

This document only covers Space Management.
