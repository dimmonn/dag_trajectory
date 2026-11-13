package com.uj.graph.dag_trajectory.controller;

import com.uj.graph.dag_trajectory.entity.BugIntroducingCommitEntity;
import com.uj.graph.dag_trajectory.model.AllIssueLabelsRefreshResult;
import com.uj.graph.dag_trajectory.model.GithubProjectAnalysis;
import com.uj.graph.dag_trajectory.model.GithubProjectGraphRefRequest;
import com.uj.graph.dag_trajectory.model.GithubProjectRequest;
import com.uj.graph.dag_trajectory.model.IssueLabelRefreshResult;
import com.uj.graph.dag_trajectory.model.SzzAnalysisResult;
import com.uj.graph.dag_trajectory.model.SzzBatchAnalysisResult;
import com.uj.graph.dag_trajectory.service.GithubProjectAnalysisService;
import com.uj.graph.dag_trajectory.service.SzzAnalysisService;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GithubProjectController {

    private final GithubProjectAnalysisService analysisService;
    private final SzzAnalysisService szzAnalysisService;

    public GithubProjectController(GithubProjectAnalysisService analysisService) {
        this(analysisService, null);
    }

    @Autowired
    public GithubProjectController(GithubProjectAnalysisService analysisService, SzzAnalysisService szzAnalysisService) {
        this.analysisService = analysisService;
        this.szzAnalysisService = szzAnalysisService;
    }

    @PostMapping("/github-projects")
    public List<GithubProjectAnalysis> analyze(@RequestBody List<GithubProjectRequest> projects) {
        return analysisService.analyze(projects);
    }

    @PostMapping("/github-projects/subgraph")
    public GithubProjectAnalysis analyzeSubgraph(@RequestBody GithubProjectGraphRefRequest request) {
        return analysisService.subgraph(request.projectRef(), request.cutoffAt());
    }

    @PostMapping("/github-projects/{projectRef}/labels")
    public IssueLabelRefreshResult refreshIssueLabels(@PathVariable String projectRef) {
        return analysisService.refreshIssueLabels(projectRef);
    }

    @PostMapping("/github-projects/labels")
    public AllIssueLabelsRefreshResult refreshIssueLabelsForAllProjects() {
        return analysisService.refreshIssueLabelsForAllProjects();
    }

    @PostMapping("/github-projects/szz")
    public SzzBatchAnalysisResult analyzeSzzForAllProjects() {
        return szzAnalysisService.analyzeAllRootProjects();
    }

    @PostMapping("/github-projects/{projectRef}/szz")
    public SzzAnalysisResult analyzeSzz(@PathVariable String projectRef) {
        return szzAnalysisService.analyze(projectRef);
    }

    @GetMapping("/github-projects/{projectRef}/szz")
    public List<BugIntroducingCommitEntity> getSzzResults(@PathVariable String projectRef) {
        return szzAnalysisService.getResults(projectRef);
    }
}
