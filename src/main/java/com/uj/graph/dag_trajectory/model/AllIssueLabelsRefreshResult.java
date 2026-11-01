package com.uj.graph.dag_trajectory.model;

public record AllIssueLabelsRefreshResult(
        int repositoriesProcessed,
        int issuesUpdated,
        int labelsStored) {
}
