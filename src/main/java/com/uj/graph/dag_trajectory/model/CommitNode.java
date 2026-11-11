package com.uj.graph.dag_trajectory.model;

import java.util.List;

/**
 * A commit in the analyzed DAG. authoredAtEpochSecond is the Git author timestamp;
 * committedAtEpochSecond is the Git committer timestamp used for temporal ordering.
 */
public record CommitNode(
        String hash,
        List<String> parents,
        long authoredAtEpochSecond,
        Long committedAtEpochSecond,
        CommitMetrics metrics) {

    /** Backward-compatible constructor for existing callers and legacy graph fixtures. */
    public CommitNode(String hash, List<String> parents, long authoredAtEpochSecond, CommitMetrics metrics) {
        this(hash, parents, authoredAtEpochSecond, authoredAtEpochSecond, metrics);
    }
}
