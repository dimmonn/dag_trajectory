package com.uj.graph.dag_trajectory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_graphs")
public class ProjectGraphEntity {

    @Id
    @Column(name = "project_ref", nullable = false, updatable = false)
    private String projectRef;

    @Column(name = "graph_json", nullable = false, columnDefinition = "text")
    private String graphJson;

    @Column(name = "source_project_ref")
    private String sourceProjectRef;

    protected ProjectGraphEntity() {
    }

    public ProjectGraphEntity(String projectRef, String graphJson, String sourceProjectRef) {
        this.projectRef = projectRef;
        this.graphJson = graphJson;
        this.sourceProjectRef = sourceProjectRef;
    }

    public String getProjectRef() {
        return projectRef;
    }

    public String getGraphJson() {
        return graphJson;
    }

    public String getSourceProjectRef() {
        return sourceProjectRef;
    }
}
