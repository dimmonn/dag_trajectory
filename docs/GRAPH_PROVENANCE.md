# Graph capture provenance

Every newly captured root graph records:
- `defaultBranch`: upstream default branch used to select the graph head.
- `headSha`: exact commit SHA resolved from that upstream branch at capture time.
- `analyzedAtEpochSecond`: time at which the graph was built.

`DatabaseGithubProjectGraphStore` must preserve these fields when normalizing the project reference and serializing the graph. Reconstructing a graph through the legacy five-argument constructor discards all three fields; the store now copies them explicitly.

Existing stored graph JSON with null/zero provenance is not backfilled from a later clone. A later HEAD cannot prove which revision produced the existing graph. Re-run ingestion for those projects to create new graph captures with accurate provenance.
