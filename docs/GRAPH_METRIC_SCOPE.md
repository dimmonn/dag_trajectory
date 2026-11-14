# Graph metric scope and longitudinal cutoffs

## Full-capture graph metrics

The metrics serialized on each `CommitNode` in the root graph describe the complete graph captured at `analyzedAtEpochSecond`. In particular, child/descendant counts, branch-point status, branch depth/width, child lag, branch lifetime, and branch divergence can depend on commits that occur after the node being observed.

Do not use these root-graph node metrics as if they were historical, point-in-time observations in a longitudinal dataset. That would introduce look-ahead leakage.

## Historical observations

For a historical observation with exclusive cutoff `T`:

1. Select only commits whose committer timestamp is strictly before `T`.
2. Remove parent edges whose parent is not in the selected commit set.
3. Rebuild `CommitNode` metrics from that induced subgraph. This recalculates child counts, descendants, branch metrics, time lags, and activity windows using only information available in that snapshot.
4. Persist the resulting subgraph separately, retaining its source graph reference.

Use `POST /github-projects/subgraph` with the source `projectRef` and `cutoffAt` to create this point-in-time graph. The root graph remains the immutable full-capture graph; its stored metrics are not retroactively rewritten.

The quarterly SQL pipeline follows the same scope rule for snapshot-level structural features by calculating degrees and edges from `dag_snapshot_commits` and `dag_snapshot_edges`. Do not join full-capture descendant/child metrics into a historical observation unless they have been recomputed within that snapshot.
