package com.uj.graph.dag_trajectory.model;

import java.util.List;

public record GithubProjectAnalysis(String projectRef, String owner, String name, String clonedPath, List<CommitNode> commits) {
}
