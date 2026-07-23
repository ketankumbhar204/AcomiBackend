# Payment Reference ID

Human-readable, immutable payment identifiers for customer and owner support flows.

## Format

`PAY-YYYYMMDD-NNNNNN` (e.g. `PAY-20260720-000123`)

## Implementation

- Migration: `V91__payment_reference.sql`
- Service: `PaymentReferenceService`
- Minted on meal day / bulk proof (`MealPollService`) and universal proof (`SpacePaymentService.submitNewProof`)
- Copied day → space payment in `MealDaySpacePaymentBridge` without overwrite
- Exposed on payment DTOs as `paymentReference`

Never regenerate after creation. Soft-backfill from existing non-UUID `payment_batch_id` values.
