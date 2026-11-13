package com.uj.graph.dag_trajectory.model;

/** Outcome for one root repository in a batch SZZ run. */
public record SzzProjectAnalysisOutcome(
        String projectRef,
        String owner,
        String name,
        boolean successful,
        SzzAnalysisResult result,
        String error) {
}
