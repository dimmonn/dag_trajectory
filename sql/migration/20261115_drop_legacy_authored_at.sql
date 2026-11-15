-- Preserve the historical committer timestamp before removing the misleading column.
BEGIN;
ALTER TABLE commits ADD COLUMN IF NOT EXISTS committed_at bigint;
UPDATE commits SET committed_at = authored_at
WHERE committed_at IS NULL AND authored_at IS NOT NULL;
ALTER TABLE commits DROP COLUMN IF EXISTS authored_at;
COMMIT;
