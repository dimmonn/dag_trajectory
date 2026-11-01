package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.model.GithubProjectAnalysis;
import java.util.List;
import java.util.Optional;

public interface GithubProjectGraphStore {

    String save(GithubProjectAnalysis graph);

    default String save(GithubProjectAnalysis graph, String sourceProjectRef) {
        return save(graph);
    }

    Optional<GithubProjectAnalysis> findByRef(String projectRef);

    default List<GithubProjectAnalysis> findRootGraphs() {
        return List.of();
    }
}
