package com.uj.graph.dag_trajectory.model;

import java.util.List;

/** Aggregate result for running SZZ over all root project graphs in the database. */
public record SzzBatchAnalysisResult(
        int projectsFound,
        int projectsSucceeded,
        int projectsFailed,
        List<SzzProjectAnalysisOutcome> projects) {
}
