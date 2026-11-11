package com.uj.graph.dag_trajectory.model;

import java.util.List;

/**
 * Immutable repository graph capture. The branch and HEAD SHA identify the exact
 * repository revision from which the DAG was collected.
 */
public record GithubProjectAnalysis(
        String projectRef,
        String owner,
        String name,
        String clonedPath,
        List<CommitNode> commits,
        String defaultBranch,
        String headSha,
        long analyzedAtEpochSecond) {

    /** Backward-compatible constructor for stored graphs and existing tests. */
    public GithubProjectAnalysis(String projectRef, String owner, String name, String clonedPath, List<CommitNode> commits) {
        this(projectRef, owner, name, clonedPath, commits, null, null, 0L);
    }
}
