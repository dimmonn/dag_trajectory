package com.uj.graph.dag_trajectory.model;

public record SzzAnalysisResult(
        String projectRef,
        int bugIssuesConsidered,
        int fixingCommitsAnalyzed,
        int fixingCommitsSkippedAsMerge,
        int bugIntroducingCommitCandidates,
        int resultRowsStored) {
}
