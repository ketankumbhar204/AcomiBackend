-- A shared room stays Available while any bookable bed is free.
-- Occupied only when the room itself is allocated, or every bookable bed is occupied.
UPDATE rooms r
SET status = next.next_status
FROM (
    SELECT r2.id,
           CASE
               WHEN EXISTS (
                   SELECT 1
                   FROM occupancies o
                   WHERE o.room_id = r2.id
                     AND o.target_type = 'ROOM'
                     AND o.status = 'ACTIVE'
               ) THEN 'OCCUPIED'
               WHEN EXISTS (
                   SELECT 1
                   FROM occupancies o
                   WHERE o.room_id = r2.id
                     AND o.target_type = 'ROOM'
                     AND o.status = 'RESERVED'
               ) THEN 'RESERVED'
               WHEN EXISTS (
                   SELECT 1
                   FROM beds b
                   WHERE b.room_id = r2.id
                     AND b.is_active = true
                     AND b.status NOT IN ('MAINTENANCE', 'BLOCKED')
               )
               AND NOT EXISTS (
                   SELECT 1
                   FROM beds b
                   WHERE b.room_id = r2.id
                     AND b.is_active = true
                     AND b.status NOT IN ('MAINTENANCE', 'BLOCKED', 'OCCUPIED')
               ) THEN 'OCCUPIED'
               WHEN EXISTS (
                   SELECT 1
                   FROM beds b
                   WHERE b.room_id = r2.id
                     AND b.is_active = true
                     AND b.status = 'RESERVED'
               )
               AND NOT EXISTS (
                   SELECT 1
                   FROM beds b
                   WHERE b.room_id = r2.id
                     AND b.is_active = true
                     AND b.status NOT IN ('MAINTENANCE', 'BLOCKED', 'RESERVED')
               ) THEN 'RESERVED'
               ELSE 'AVAILABLE'
           END AS next_status
    FROM rooms r2
    WHERE r2.status IN ('AVAILABLE', 'OCCUPIED', 'RESERVED')
) next
WHERE r.id = next.id
  AND r.status IS DISTINCT FROM next.next_status;
