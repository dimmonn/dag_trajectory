package com.uj.graph.dag_trajectory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uj.graph.dag_trajectory.entity.ProjectGraphEntity;
import com.uj.graph.dag_trajectory.model.GithubProjectAnalysis;
import com.uj.graph.dag_trajectory.repository.ProjectGraphRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseGithubProjectGraphStoreTest {

    @Test
    void preservesCaptureProvenanceWhenSavingGraphJson() throws Exception {
        ProjectGraphRepository repository = mock(ProjectGraphRepository.class);
        when(repository.save(any(ProjectGraphEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ObjectMapper objectMapper = new ObjectMapper();
        DatabaseGithubProjectGraphStore store = new DatabaseGithubProjectGraphStore(repository, objectMapper);

        GithubProjectAnalysis graph = new GithubProjectAnalysis(
                "project-ref",
                "octocat",
                "sample",
                "/tmp/sample",
                List.of(),
                "main",
                "0123456789abcdef0123456789abcdef01234567",
                1_800_000_000L);

        store.save(graph);
        var saved = org.mockito.ArgumentCaptor.forClass(ProjectGraphEntity.class);
        org.mockito.Mockito.verify(repository).save(saved.capture());
        GithubProjectAnalysis persisted = objectMapper.readValue(
                saved.getValue().getGraphJson(), GithubProjectAnalysis.class);

        assertEquals("main", persisted.defaultBranch());
        assertEquals("0123456789abcdef0123456789abcdef01234567", persisted.headSha());
        assertEquals(1_800_000_000L, persisted.analyzedAtEpochSecond());
    }
}
