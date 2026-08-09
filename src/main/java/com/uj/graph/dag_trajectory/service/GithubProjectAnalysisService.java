package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.model.CommitNode;
import com.uj.graph.dag_trajectory.model.GithubProjectAnalysis;
import com.uj.graph.dag_trajectory.model.GithubProjectRequest;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GithubProjectAnalysisService {

    private final CommitGraphMetricsCalculator metricsCalculator;
    private final GithubProjectGraphStore graphStore;
    private final GitRepositoryCloner repositoryCloner;
    private final CommitDagBuilder commitDagBuilder;
    private final CommitMetadataService metadataService;
    private final BatchProgressService progressService;

    public GithubProjectAnalysisService(CommitGraphMetricsCalculator metricsCalculator, GithubProjectGraphStore graphStore, GitRepositoryCloner repositoryCloner, CommitDagBuilder commitDagBuilder, CommitMetadataService metadataService, BatchProgressService progressService) {
        this.metricsCalculator = metricsCalculator;
        this.graphStore = graphStore;
        this.repositoryCloner = repositoryCloner;
        this.commitDagBuilder = commitDagBuilder;
        this.metadataService = metadataService;
        this.progressService = progressService;
    }

    public List<GithubProjectAnalysis> analyze(List<GithubProjectRequest> projects) {
        String batchId = UUID.randomUUID().toString();
        progressService.startBatch(batchId, "Analyzing GitHub Projects", projects.size());
        
        try {
            List<GithubProjectAnalysis> results = projects.stream()
                    .map(project -> {
                        String projectRef = UUID.randomUUID().toString();
                        progressService.recordProgress(1, "Cloning: " + project.owner() + "/" + project.name());
                        
                        GithubProjectAnalysis analysis = commitDagBuilder.build(projectRef, project, repositoryCloner.clone(project));
                        graphStore.save(analysis);
                        
                        int commitCount = analysis.commits().size();
                        progressService.recordProgress(1, "Building DAG: " + commitCount + " commits");
                        
                        // Fetch and save GitHub metadata for each commit
                        for (int i = 0; i < analysis.commits().size(); i++) {
                            CommitNode commit = analysis.commits().get(i);
                            metadataService.fetchAndSaveMetadataForCommit(project.owner(), project.name(), commit.hash(), projectRef);
                            
                            if ((i + 1) % 5 == 0) {
                                progressService.recordProgress(0, "Fetched metadata for " + (i + 1) + "/" + commitCount + " commits");
                            }
                        }
                        
                        progressService.recordProgress(0, "Completed: " + project.owner() + "/" + project.name());
                        return analysis;
                    })
                    .toList();
            
            progressService.completeBatch();
            return results;
        } catch (Exception e) {
            progressService.cancelBatch("Error: " + e.getMessage());
            throw e;
        }
    }

    public GithubProjectAnalysis subgraph(String projectRef, Instant cutoffAt) {
        GithubProjectAnalysis graph = graphStore.findByRef(projectRef)
                .orElseThrow(() -> new IllegalArgumentException("Unknown project ref: " + projectRef));
        List<CommitGraphMetricsCalculator.CommitSnapshot> snapshots = graph.commits().stream()
                .map(commit -> new CommitGraphMetricsCalculator.CommitSnapshot(commit.hash(), commit.parents(), Instant.ofEpochSecond(commit.authoredAtEpochSecond())))
                .toList();
        List<CommitGraphMetricsCalculator.CommitSnapshot> prunedSnapshots = metricsCalculator.sliceByCutoff(snapshots, cutoffAt);
        List<CommitNode> commits = metricsCalculator.buildNodes(prunedSnapshots);
        String subgraphRef = UUID.randomUUID().toString();
        GithubProjectAnalysis subgraph = new GithubProjectAnalysis(subgraphRef, graph.owner(), graph.name(), graph.clonedPath(), commits);
        graphStore.save(subgraph, graph.projectRef());
        return subgraph;
    }
}


