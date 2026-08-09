package com.uj.graph.dag_trajectory.model;

import java.time.Instant;

public record GithubProjectGraphRefRequest(String projectRef, Instant cutoffAt) {
}
