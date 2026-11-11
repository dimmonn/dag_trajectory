# DAG capture provenance and timestamp semantics

Each newly captured root graph stores the upstream default branch, the exact commit SHA at the branch head, and the capture time (`analyzedAtEpochSecond`). These fields identify the repository revision used to construct the graph; a moving branch name alone is not sufficient provenance.

Each newly captured commit stores two distinct Git timestamps:

- `authoredAtEpochSecond`: author timestamp from the Git commit object.
- `committedAtEpochSecond`: committer timestamp from the Git commit object.

Temporal DAG metrics and quarterly snapshot membership use the committer timestamp. The author timestamp remains available as a separate commit attribute and must not be substituted for the committer timestamp in temporal ordering.

## Backward compatibility

Older graph JSON documents do not contain `committedAtEpochSecond` or graph-capture provenance. Before this change, `authoredAtEpochSecond` was populated from JGit's `RevCommit.getCommitTime()`, i.e. the committer timestamp, despite its misleading name. The quarterly snapshot SQL therefore falls back to that legacy field when the new committer field is absent.

The actual author timestamp cannot be reconstructed from legacy graph JSON. Re-run repository analysis to populate it accurately. Existing `commits.authored_at` values are left operationally unchanged and continue to represent the historical committer-time value used by release-count queries.

## Reproduction rule

For an empirical run, record the graph's `headSha`, `defaultBranch`, and `analyzedAtEpochSecond` alongside the exported dataset. Do not combine snapshots from different HEAD captures without recording that distinction. Quarterly snapshots use an exclusive UTC cutoff: a commit is included only when its committer timestamp is strictly earlier than the cutoff.
