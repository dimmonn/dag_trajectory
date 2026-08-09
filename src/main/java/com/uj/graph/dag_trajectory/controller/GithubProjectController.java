package com.uj.graph.dag_trajectory.controller;

import com.uj.graph.dag_trajectory.model.GithubProjectAnalysis;
import com.uj.graph.dag_trajectory.model.GithubProjectRequest;
import com.uj.graph.dag_trajectory.model.GithubProjectGraphRefRequest;
import com.uj.graph.dag_trajectory.service.GithubProjectAnalysisService;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GithubProjectController {

    private final GithubProjectAnalysisService analysisService;

    public GithubProjectController(GithubProjectAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/github-projects")
    public List<GithubProjectAnalysis> analyze(@RequestBody List<GithubProjectRequest> projects) {
        return analysisService.analyze(projects);
    }

    @PostMapping("/github-projects/subgraph")
    public GithubProjectAnalysis analyzeSubgraph(@RequestBody GithubProjectGraphRefRequest request) {
        return analysisService.subgraph(request.projectRef(), request.cutoffAt());
    }
}
