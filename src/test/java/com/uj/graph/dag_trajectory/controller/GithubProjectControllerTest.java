package com.uj.graph.dag_trajectory.controller;

import com.uj.graph.dag_trajectory.model.CommitMetrics;
import com.uj.graph.dag_trajectory.model.CommitNode;
import com.uj.graph.dag_trajectory.model.GithubProjectAnalysis;
import com.uj.graph.dag_trajectory.model.GithubProjectGraphRefRequest;
import com.uj.graph.dag_trajectory.model.GithubProjectRequest;
import com.uj.graph.dag_trajectory.repository.CommitEntityRepository;
import com.uj.graph.dag_trajectory.service.GithubProjectAnalysisService;
import com.uj.graph.dag_trajectory.service.CommitMetadataService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class GithubProjectControllerTest {

    @Test
    void analyzesSubmittedProjects() {
        var metadataServiceMock = mock(CommitMetadataService.class);
        var commitRepositoryMock = mock(CommitEntityRepository.class);
        var controller = new GithubProjectController(new GithubProjectAnalysisService(
                new com.uj.graph.dag_trajectory.service.CommitGraphMetricsCalculator(),
                new StubGraphStore(),
                null,
                null,
                metadataServiceMock,
                commitRepositoryMock) {
            @Override
            public List<GithubProjectAnalysis> analyze(List<GithubProjectRequest> projects) {
                return List.of(new GithubProjectAnalysis(
                        "ref-1",
                        "octocat",
                        "dag",
                        "/tmp/repo",
                        List.of(new CommitNode("abc", List.of(), 1L, metrics()))));
            }
        });

        var result = controller.analyze(List.of(new GithubProjectRequest("octocat", "dag")));

        assertEquals(1, result.size());
        assertEquals("ref-1", result.getFirst().projectRef());
    }

    @Test
    void analyzesSubgraphByGraphRef() {
        var metadataServiceMock = mock(CommitMetadataService.class);
        var commitRepositoryMock = mock(CommitEntityRepository.class);
        var controller = new GithubProjectController(new GithubProjectAnalysisService(
                new com.uj.graph.dag_trajectory.service.CommitGraphMetricsCalculator(),
                new StubGraphStore(),
                null,
                null,
                metadataServiceMock,
                commitRepositoryMock) {
            @Override
            public GithubProjectAnalysis subgraph(String projectRef, Instant cutoffAt) {
                return new GithubProjectAnalysis(
                        projectRef,
                        "octocat",
                        "dag",
                        "/tmp/repo",
                        List.of(new CommitNode("abc", List.of(), 1L, metrics())));
            }
        });

        var result = controller.analyzeSubgraph(new GithubProjectGraphRefRequest("ref-1", Instant.parse("2026-08-09T11:30:00Z")));

        assertEquals("ref-1", result.projectRef());
    }

    private static final class StubGraphStore implements com.uj.graph.dag_trajectory.service.GithubProjectGraphStore {
        @Override
        public String save(GithubProjectAnalysis graph) {
            return "ref-1";
        }

        @Override
        public java.util.Optional<GithubProjectAnalysis> findByRef(String projectRef) {
            return java.util.Optional.empty();
        }
    }

    private static CommitMetrics metrics() {
        return new CommitMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
