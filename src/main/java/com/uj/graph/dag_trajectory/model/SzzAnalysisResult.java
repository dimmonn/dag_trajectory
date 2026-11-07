package com.uj.graph.dag_trajectory.model;

public record SzzAnalysisResult(
        String projectRef,
        int bugIssuesConsidered,
        int fixingCommitsAnalyzed,
        int fixingCommitsSkippedNonSingleParent,
        int bugIntroducingCommitCandidates,
        int resultRowsStored) {
}
