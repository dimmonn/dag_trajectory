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
}
