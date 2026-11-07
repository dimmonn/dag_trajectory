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

The POST response reports the number of BUG-labelled issues considered, fixing commits analyzed, fixing commits skipped because they do not have exactly one parent, distinct BIC candidate hashes, and persisted result rows. Re-running the endpoint replaces that project's previous SZZ rows after the new analysis has completed.

## Implemented procedure

1. Select distinct issues having at least one issue label categorized as `BUG`.
2. Follow issue-to-pull-request links, retaining only pull requests with a merge timestamp or `MERGED` state.
3. Follow merged-PR-to-commit links to obtain fixing-commit candidates. Those links are restricted to commits found in the upstream default-branch DAG during ingestion.
4. If no original PR commit SHA matches the upstream DAG, use the PR's reported merge commit SHA only when that exact SHA exists in the DAG. This supports squash/rebase cases without importing unmerged fork commits.
5. For each fixing commit with exactly one parent, diff the parent against the fixing commit. For every deleted or replaced pre-fix line, run JGit blame on the parent version of that file and attribute the line to its source commit.
6. Persist one row per project, issue, fixing commit, blamed commit, and file in `bug_introducing_commits`. `deleted_lines` is the number of deleted/replaced lines attributed to that blamed commit in that file.

## Interpretation and limitations

- Persisted rows are **SZZ bug-introducing commit candidates**, not verified defects or ground truth.
- All upstream commits associated with a merged PR linked to a BUG issue are treated as fixing-commit candidates. A PR may contain preparatory or unrelated commits, so this linkage is a candidate-generation heuristic.
- Commits with zero or multiple parents are skipped. In particular, merge commits are not blamed against an arbitrarily selected parent.
- This is a basic line-level SZZ variant. It does not yet filter comments, whitespace-only changes, or refactor-induced line movement. Results can therefore contain false positives.
- File additions have no pre-fix lines and produce no BIC for that file. Deleted and replaced lines are traced; pure insertions are not.
- A BUG label is the defect-report selection criterion, not proof that the issue describes a confirmed defect.
- Run against root upstream project graphs, not time-cutoff subgraphs. Historical prediction experiments should still construct time-valid features and avoid future-information leakage.

The algorithm identifier stored with each row is `SZZ_LINE_BLAME_V1`.
