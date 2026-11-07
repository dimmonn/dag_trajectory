# SZZ analysis

The application implements its own line-level SZZ analysis using JGit. It does not invoke PySZZ or another external SZZ implementation.

## Run

The project must already be ingested, including the commit DAG, issue labels, merged pull-request mappings, and commit-to-PR links. The full local clone must still be available under `github.clone.root` (default: `~/.dag_trajectory/repos`).

Run the analysis for a root upstream project:

```http
POST /github-projects/{projectRef}/szz
```

Read the persisted candidates:

```http
GET /github-projects/{projectRef}/szz
```

The POST response reports the number of BUG-labelled issues considered, the number skipped because their report date is unavailable, fixing commits analyzed, fixing commits skipped because they do not have exactly one parent, line attributions excluded because their source commit post-dates the issue report, distinct BIC candidate hashes, and persisted result rows. Re-running the endpoint replaces that project's previous SZZ rows only after the analysis completes without commit-level errors.

## Implemented procedure

1. Select distinct issues having at least one issue label categorized as `BUG`.
2. Require an issue creation timestamp for temporal filtering; BUG-labelled issues without one are counted and excluded from SZZ attribution.
3. Follow issue-to-pull-request links, retaining only pull requests with a merge timestamp or `MERGED` state.
4. Follow merged-PR-to-commit links to obtain fixing-commit candidates. Those links are restricted to commits found in the upstream default-branch DAG during ingestion.
5. If no original PR commit SHA matches the upstream DAG, use the PR's reported merge commit SHA only when that exact SHA exists in the DAG. This supports some squash/rebase cases without importing unmerged fork commits. It does not guarantee mapping of every rebased PR.
6. For each fixing commit with exactly one parent, diff the parent against the fixing commit. For every deleted or replaced pre-fix line, run JGit blame on the parent version of that file and attribute the line to its source commit.
7. Exclude a blamed source commit whose committer timestamp is later than the issue creation timestamp. This prevents post-report edits from being labeled as the cause of an already reported defect. The timestamp comparison is second-resolution and relies on Git commit timestamps, which can be inaccurate.
8. Persist one row per project, issue, fixing commit, blamed commit, and file in `bug_introducing_commits`. `deleted_lines` is the number of deleted/replaced lines attributed to that blamed commit in that file.

## Interpretation and limitations

- Persisted rows are **SZZ bug-introducing commit candidates**, not verified defects or ground truth.
- All upstream commits associated with a merged PR linked to a BUG issue are treated as fixing-commit candidates. A PR may contain preparatory or unrelated commits, so this linkage is a candidate-generation heuristic.
- Commits with zero or multiple parents are skipped. In particular, merge commits are not blamed against an arbitrarily selected parent.
- If any fixing commit cannot be analyzed, the request fails and leaves the project's previously persisted result set unchanged. The error is logged; other fixing commits are still attempted so that more than one failure can be reported through logs.
- This is a basic line-level SZZ variant. It does not yet filter comments, whitespace-only changes, or refactor-induced line movement. Results can therefore contain false positives.
- File additions have no pre-fix lines and produce no BIC for that file. Deleted and replaced lines are traced; pure insertions are not.
- A BUG label is the defect-report selection criterion, not proof that the issue describes a confirmed defect.
- Run against root upstream project graphs, not time-cutoff subgraphs. Historical prediction experiments should still construct time-valid features and avoid future-information leakage.

The algorithm identifier stored with each row is `SZZ_LINE_BLAME_V1`.
