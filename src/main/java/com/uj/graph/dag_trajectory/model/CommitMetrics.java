package com.uj.graph.dag_trajectory.model;

public record CommitMetrics(
        int parentCount,
        int mergeParentCount,
        int childCount,
        int graphDepth,
        int firstParentDistance,
        int ancestorCount,
        int descendantCount,
        int subtreeSize,
        long primaryParentLagSeconds,
        long ancestorSpanSeconds,
        long childLagSeconds,
        boolean branchPoint,
        int branchDepth,
        int branchWidth,
        long branchLifetimeSeconds,
        long branchDivergenceSpanSeconds,
        long commitsIn7d,
        long commitsIn30d,
        long commitsIn90d,
        long mergeCommitsIn7d,
        long mergeCommitsIn30d,
        long mergeCommitsIn90d) {
}
