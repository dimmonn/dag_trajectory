package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.model.GithubProjectAnalysis;
import com.uj.graph.dag_trajectory.model.SzzAnalysisResult;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class SzzBatchAnalysisTest {

    @Test
    void analyzesEveryRootProjectAndContinuesAfterIndividualFailure() {
        GithubProjectGraphStore graphStore = mock(GithubProjectGraphStore.class);
        GithubProjectAnalysis first = graph("ref-1", "scikit-learn", "scikit-learn");
        GithubProjectAnalysis second = graph("ref-2", "mockito", "mockito");
        when(graphStore.findRootGraphs()).thenReturn(List.of(first, second));

        SzzAnalysisService service = spy(new SzzAnalysisService(
                graphStore,
                mock(GitRepositoryCloner.class),
                mock(com.uj.graph.dag_trajectory.repository.IssueLabelRepository.class),
                mock(com.uj.graph.dag_trajectory.repository.PullRequestIssueLinkRepository.class),
                mock(com.uj.graph.dag_trajectory.repository.CommitPullRequestLinkRepository.class),
                mock(SzzLineBlamer.class),
                mock(SzzResultPersistenceService.class),
                mock(com.uj.graph.dag_trajectory.repository.BugIntroducingCommitRepository.class)));

        SzzAnalysisResult success = new SzzAnalysisResult("ref-1", 2, 0, 3, 0, 0, 4, 5);
        doReturn(success).when(service).analyze("ref-1");
        doThrow(new IllegalStateException("clone failed")).when(service).analyze("ref-2");

        var result = service.analyzeAllRootProjects();

        assertEquals(2, result.projectsFound());
        assertEquals(1, result.projectsSucceeded());
        assertEquals(1, result.projectsFailed());
        assertTrue(result.projects().get(0).successful());
        assertFalse(result.projects().get(1).successful());
        assertEquals("clone failed", result.projects().get(1).error());
    }

    private static GithubProjectAnalysis graph(String ref, String owner, String name) {
        return new GithubProjectAnalysis(ref, owner, name, "/tmp/" + name, List.of(), "main", "head-" + ref, 1L);
    }
}
