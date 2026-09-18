-- Harden inquiry credit purchase requests: at most one PENDING per user+package.

CREATE UNIQUE INDEX IF NOT EXISTS uq_inquiry_purchase_pending_user_package
    ON inquiry_credit_purchase_requests (user_id, package_id)
    WHERE status = 'PENDING';
