package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.model.CommitMetrics;
import com.uj.graph.dag_trajectory.model.CommitNode;
import com.uj.graph.dag_trajectory.model.GithubProjectAnalysis;
import com.uj.graph.dag_trajectory.model.GithubProjectRequest;
import com.uj.graph.dag_trajectory.model.IssueLabelRefreshResult;
import com.uj.graph.dag_trajectory.repository.CommitEntityRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

    @Test
    void refreshesLabelsForEveryRootProjectGraph() {
        var graph = new GithubProjectAnalysis("ref-1", "octocat", "dag", "/tmp/repo", List.of());
        var graphStore = new StubGraphStore(graph);
        var metadataService = mock(CommitMetadataService.class);
        when(metadataService.fetchAndSaveIssueLabels("octocat", "dag", "ref-1"))
                .thenReturn(new IssueLabelRefreshResult(3, 5));
        var service = new GithubProjectAnalysisService(
                new CommitGraphMetricsCalculator(), graphStore, null, null, metadataService, mock(CommitEntityRepository.class));

        var result = service.refreshIssueLabelsForAllProjects();

        assertEquals(1, result.repositoriesProcessed());
        assertEquals(3, result.issuesUpdated());
        assertEquals(5, result.labelsStored());
    }

    @Test
    void analyzesAnUpstreamOnlyOnceWhenBothForkAndUpstreamAreRequested() {
        var fork = new GithubProjectRequest("contributor", "project-fork");
        var upstream = new GithubProjectRequest("original", "project");
        var graph = new GithubProjectAnalysis("ref-1", upstream.owner(), upstream.name(), "/tmp/repo", List.of());
        var cloner = mock(GitRepositoryCloner.class);
        when(cloner.resolveUpstream(fork)).thenReturn(upstream);
        when(cloner.resolveUpstream(upstream)).thenReturn(upstream);
        when(cloner.clone(upstream)).thenReturn(Path.of("/tmp/repo"));
        var dagBuilder = mock(CommitDagBuilder.class);
        when(dagBuilder.build(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(upstream), org.mockito.ArgumentMatchers.any()))
                .thenReturn(graph);
        var metadataService = mock(CommitMetadataService.class);
        when(metadataService.fetchAndSaveProjectMappings(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                anySet()))
                .thenReturn(new CommitMetadataService.ProjectMappingPersistResult(0, 0, 0, 0));
        when(metadataService.fetchAndSaveProjectCommitMetadata(
                org.mockito.ArgumentMatchers.eq("original"),
                org.mockito.ArgumentMatchers.eq("project"),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new CommitMetadataService.ProjectCommitMetadataPersistResult(0, 0));
        var service = new GithubProjectAnalysisService(
                new CommitGraphMetricsCalculator(),
                mock(GithubProjectGraphStore.class),
                cloner,
                dagBuilder,
                metadataService,
                mock(CommitEntityRepository.class));

        var result = service.analyze(List.of(fork, upstream));

        assertEquals(1, result.size());
        assertEquals("original", result.getFirst().owner());
        assertEquals("project", result.getFirst().name());
        verify(cloner).clone(upstream);
        verify(dagBuilder).build(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(upstream), org.mockito.ArgumentMatchers.any());
        verify(metadataService).fetchAndSaveProjectMappings(
                org.mockito.ArgumentMatchers.eq("original"),
                org.mockito.ArgumentMatchers.eq("project"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(java.util.Set.of()));
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

        @Override
        public List<GithubProjectAnalysis> findRootGraphs() {
            return List.of(graph);
        }
    }

    private static CommitMetrics metrics() {
        return new CommitMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
