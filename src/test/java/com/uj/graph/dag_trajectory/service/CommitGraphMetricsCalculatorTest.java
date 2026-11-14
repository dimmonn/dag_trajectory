package com.uj.graph.dag_trajectory.service;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitGraphMetricsCalculatorTest {

    @Test
    void buildsMetricsWhenInputContainsCycle() {
        var calculator = new CommitGraphMetricsCalculator();
        var t0 = Instant.parse("2026-08-09T10:00:00Z");
        var t1 = Instant.parse("2026-08-09T11:00:00Z");
        var t2 = Instant.parse("2026-08-09T12:00:00Z");

        var snapshots = List.of(
                new CommitGraphMetricsCalculator.CommitSnapshot("a", List.of("c"), t0),
                new CommitGraphMetricsCalculator.CommitSnapshot("b", List.of("a"), t1),
                new CommitGraphMetricsCalculator.CommitSnapshot("c", List.of("b"), t2));

        var nodes = calculator.buildNodes(snapshots);

        assertEquals(3, nodes.size());
        assertTrue(nodes.stream().allMatch(node -> node.metrics().graphDepth() >= 0));
        assertTrue(nodes.stream().allMatch(node -> node.metrics().firstParentDistance() >= 0));
        assertTrue(nodes.stream().allMatch(node -> node.metrics().ancestorCount() >= 0));
        assertTrue(nodes.stream().allMatch(node -> node.metrics().descendantCount() >= 0));
    }

    @Test
    void usesCommitterTimeForTemporalMetricsAndKeepsAuthorTimeSeparate() {
        var calculator = new CommitGraphMetricsCalculator();
        var authorA = Instant.parse("2026-08-09T08:00:00Z");
        var authorB = Instant.parse("2026-08-09T09:00:00Z");
        var committerA = Instant.parse("2026-08-09T10:00:00Z");
        var committerB = Instant.parse("2026-08-09T11:00:00Z");

        var snapshots = List.of(
                new CommitGraphMetricsCalculator.CommitSnapshot("a", List.of(), authorA, committerA),
                new CommitGraphMetricsCalculator.CommitSnapshot("b", List.of("a"), authorB, committerB));

        var nodes = calculator.buildNodes(snapshots);
        var a = nodes.stream().filter(node -> node.hash().equals("a")).findFirst().orElseThrow();
        var b = nodes.stream().filter(node -> node.hash().equals("b")).findFirst().orElseThrow();

        assertEquals(authorA.getEpochSecond(), a.authoredAtEpochSecond());
        assertEquals(committerA.getEpochSecond(), a.committedAtEpochSecond());
        assertEquals(3_600L, a.metrics().childLagSeconds());
        assertEquals(2L, b.metrics().commitsIn7d());
    }

    @Test
    void appliesExclusiveCutoffToCommitterTimestamp() {
        var calculator = new CommitGraphMetricsCalculator();
        var cutoff = Instant.parse("2026-08-09T10:00:00Z");
        var snapshots = List.of(
                new CommitGraphMetricsCalculator.CommitSnapshot(
                        "before", List.of(), Instant.parse("2026-08-09T12:00:00Z"), cutoff.minusSeconds(1)),
                new CommitGraphMetricsCalculator.CommitSnapshot(
                        "at-cutoff", List.of(), Instant.parse("2026-08-09T08:00:00Z"), cutoff));

        var sliced = calculator.sliceByCutoff(snapshots, cutoff);

        assertEquals(List.of("before"), sliced.stream().map(CommitGraphMetricsCalculator.CommitSnapshot::hash).toList());
    }

    @Test
    void recomputesDescendantAndBranchMetricsUsingOnlyCommitsBeforeCutoff() {
        var calculator = new CommitGraphMetricsCalculator();
        var t0 = Instant.parse("2026-01-01T00:00:00Z");
        var t1 = Instant.parse("2026-01-02T00:00:00Z");
        var t2 = Instant.parse("2026-01-03T00:00:00Z");
        var t3 = Instant.parse("2026-01-04T00:00:00Z");
        var snapshots = List.of(
                new CommitGraphMetricsCalculator.CommitSnapshot("root", List.of(), t0, t0),
                new CommitGraphMetricsCalculator.CommitSnapshot("historical-child", List.of("root"), t1, t1),
                new CommitGraphMetricsCalculator.CommitSnapshot("future-child", List.of("root"), t2, t2),
                new CommitGraphMetricsCalculator.CommitSnapshot("future-descendant", List.of("future-child"), t3, t3));

        var historical = calculator.sliceByCutoff(snapshots, t2);
        var nodes = calculator.buildNodes(historical);
        var root = nodes.stream().filter(node -> node.hash().equals("root")).findFirst().orElseThrow();

        assertEquals(List.of("root", "historical-child"), nodes.stream().map(node -> node.hash()).toList());
        assertEquals(1, root.metrics().childCount());
        assertEquals(1, root.metrics().descendantCount());
        assertEquals(2, root.metrics().subtreeSize());
        assertEquals(false, root.metrics().branchPoint());
        assertEquals(86_400L, root.metrics().childLagSeconds());
        assertEquals(86_400L, root.metrics().branchLifetimeSeconds());
    }

}
