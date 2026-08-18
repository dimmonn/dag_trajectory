package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.model.CommitMetrics;
import com.uj.graph.dag_trajectory.model.CommitNode;
import com.uj.graph.dag_trajectory.model.GithubProjectAnalysis;
import com.uj.graph.dag_trajectory.repository.CommitEntityRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class GithubProjectAnalysisServiceTest {

    @Test
    void slicesExistingGraphAndRecomputesMetrics() {
        var graph = new GithubProjectAnalysis(
                "ref-1",
                "octocat",
                "dag",
                "/tmp/repo",
                List.of(
                        new CommitNode("a", List.of(), Instant.parse("2026-08-09T10:00:00Z").getEpochSecond(), metrics()),
                        new CommitNode("b", List.of("a"), Instant.parse("2026-08-09T11:00:00Z").getEpochSecond(), metrics()),
                        new CommitNode("c", List.of("b"), Instant.parse("2026-08-09T12:00:00Z").getEpochSecond(), metrics())));

        var metadataServiceMock = mock(CommitMetadataService.class);
        var commitRepositoryMock = mock(CommitEntityRepository.class);
        var service = new GithubProjectAnalysisService(new CommitGraphMetricsCalculator(), new StubGraphStore(graph), null, null, metadataServiceMock, commitRepositoryMock);

        var result = service.subgraph("ref-1", Instant.parse("2026-08-09T11:30:00Z"));

        assertNotNull(result.projectRef());
        assertEquals(2, result.commits().size());
        assertEquals(0, result.commits().stream().filter(commit -> commit.hash().equals("b")).findFirst().orElseThrow().metrics().childCount());
    }

    private static final class StubGraphStore implements GithubProjectGraphStore {
        private final GithubProjectAnalysis graph;

        private StubGraphStore(GithubProjectAnalysis graph) {
            this.graph = graph;
        }

        @Override
        public String save(GithubProjectAnalysis graph) {
            return graph.projectRef();
        }

        @Override
        public String save(GithubProjectAnalysis graph, String sourceProjectRef) {
            return "sub-ref-1";
        }

        @Override
        public Optional<GithubProjectAnalysis> findByRef(String projectRef) {
            return Optional.of(graph);
        }
    }

    private static CommitMetrics metrics() {
        return new CommitMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
