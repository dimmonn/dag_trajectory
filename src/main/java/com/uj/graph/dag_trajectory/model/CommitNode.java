package com.uj.graph.dag_trajectory.model;

import java.util.List;

public record CommitNode(String hash, List<String> parents, long authoredAtEpochSecond, CommitMetrics metrics) {
}
