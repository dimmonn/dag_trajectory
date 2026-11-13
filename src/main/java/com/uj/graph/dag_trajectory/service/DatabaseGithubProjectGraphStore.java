package com.uj.graph.dag_trajectory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uj.graph.dag_trajectory.entity.ProjectGraphEntity;
import com.uj.graph.dag_trajectory.model.GithubProjectAnalysis;
import com.uj.graph.dag_trajectory.repository.ProjectGraphRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DatabaseGithubProjectGraphStore implements GithubProjectGraphStore {

    private final ProjectGraphRepository repository;
    private final ObjectMapper objectMapper;

    public DatabaseGithubProjectGraphStore(ProjectGraphRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String save(GithubProjectAnalysis graph) {
        return save(graph, null);
    }

    @Override
    public String save(GithubProjectAnalysis graph, String sourceProjectRef) {
        String projectRef = graph.projectRef() == null || graph.projectRef().isBlank()
                ? UUID.randomUUID().toString()
                : graph.projectRef();
        repository.save(new ProjectGraphEntity(projectRef, writeGraph(new GithubProjectAnalysis(\n                projectRef,\n                graph.owner(),\n                graph.name(),\n                graph.clonedPath(),\n                graph.commits(),\n                graph.defaultBranch(),\n                graph.headSha(),\n                graph.analyzedAtEpochSecond())), sourceProjectRef));
        return projectRef;
    }

    @Override
    public Optional<GithubProjectAnalysis> findByRef(String projectRef) {
        return repository.findById(projectRef)
                .map(entity -> readGraph(entity.getGraphJson()));
    }

    @Override
    public List<GithubProjectAnalysis> findRootGraphs() {
        return repository.findAllBySourceProjectRefIsNull().stream()
                .map(entity -> readGraph(entity.getGraphJson()))
                .toList();
    }

    private String writeGraph(GithubProjectAnalysis graph) {
        try {
            return objectMapper.writeValueAsString(graph);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize graph for project ref " + graph.projectRef(), e);
        }
    }

    private GithubProjectAnalysis readGraph(String json) {
        try {
            return objectMapper.readValue(json, GithubProjectAnalysis.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize stored graph", e);
        }
    }
}
