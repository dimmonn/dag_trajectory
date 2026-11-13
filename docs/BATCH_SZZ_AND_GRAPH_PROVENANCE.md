# Batch SZZ API

Run SZZ sequentially for every root project graph stored in `project_graphs`:

```http
POST /github-projects/szz
```

No request body is required. The endpoint reads root graphs from the database and invokes the existing per-project SZZ analysis for each one.

The response contains:
- `projectsFound`: number of root project graphs selected.
- `projectsSucceeded`: projects whose SZZ run completed and whose result set was persisted.
- `projectsFailed`: projects whose run failed.
- `projects`: one outcome per project, including its `projectRef`, owner/name, success flag, per-project SZZ result or error message.

A failure for one project does not stop processing the remaining projects. A failed project's previous complete SZZ result set is retained by the existing per-project persistence behavior.

The existing single-project endpoint remains available:

```http
POST /github-projects/{projectRef}/szz
```

## Graph provenance persistence

A new graph capture includes `defaultBranch`, `headSha`, and `analyzedAtEpochSecond`. The database graph store must serialize these fields without rebuilding the graph through the legacy five-argument constructor.

This correction applies to new saves. Existing graph JSON documents with null/zero provenance are not retroactively assigned a HEAD SHA: doing so from a later clone would incorrectly attribute the current repository revision to an older graph capture. Re-run ingestion for those projects to create new graph captures with truthful provenance.
