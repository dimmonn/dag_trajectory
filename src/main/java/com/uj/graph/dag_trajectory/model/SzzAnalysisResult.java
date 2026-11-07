package com.uj.graph.dag_trajectory.model;

public record SzzAnalysisResult(
        String projectRef,
        int bugIssuesConsidered,
        int bugIssuesMissingReportDate,
        int fixingCommitsAnalyzed,
        int fixingCommitsSkippedNonSingleParent,
        int postReportLineAttributionsExcluded,
        int bugIntroducingCommitCandidates,
        int resultRowsStored) {
}
