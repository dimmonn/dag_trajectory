SELECT COUNT(*) AS mismatched_commit_counts
FROM (
         SELECT
             c.snapshot_id,
             c.commit_hash,
             c.parent_count,
             COUNT(e.child_hash) AS actual_parent_count
         FROM dag_snapshot_commits c
                  LEFT JOIN dag_snapshot_edges e
                            ON e.snapshot_id = c.snapshot_id
                                AND e.child_hash = c.commit_hash
         GROUP BY c.snapshot_id, c.commit_hash, c.parent_count
     ) x
WHERE parent_count <> actual_parent_count;