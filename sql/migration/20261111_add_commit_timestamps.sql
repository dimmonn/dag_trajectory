-- Preserve the legacy authored_at column while exposing unambiguous Git timestamps.
ALTER TABLE commits ADD COLUMN IF NOT EXISTS author_at bigint;
ALTER TABLE commits ADD COLUMN IF NOT EXISTS committed_at bigint;

-- Historical authored_at values were populated from JGit getCommitTime(),
-- which is the committer timestamp. They can safely backfill committed_at.
UPDATE commits
SET committed_at = authored_at
WHERE committed_at IS NULL AND authored_at IS NOT NULL;

-- author_at cannot be reconstructed from legacy rows; leave it NULL until
-- the repository is re-analyzed from Git commit objects.
