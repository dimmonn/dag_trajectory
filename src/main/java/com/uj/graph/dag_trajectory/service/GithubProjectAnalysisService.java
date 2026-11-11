package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.entity.CommitEntity;
import com.uj.graph.dag_trajectory.model.AllIssueLabelsRefreshResult;
import com.uj.graph.dag_trajectory.model.CommitNode;
import com.uj.graph.dag_trajectory.model.GithubProjectAnalysis;
import com.uj.graph.dag_trajectory.model.GithubProjectRequest;
import com.uj.graph.dag_trajectory.model.IssueLabelRefreshResult;
import com.uj.graph.dag_trajectory.repository.CommitEntityRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GithubProjectAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(GithubProjectAnalysisService.class);

    private final CommitGraphMetricsCalculator metricsCalculator;
    private final GithubProjectGraphStore graphStore;
    private final GitRepositoryCloner repositoryCloner;
    private final CommitDagBuilder commitDagBuilder;
    private final CommitMetadataService metadataService;
    private final CommitEntityRepository commitRepository;

    public GithubProjectAnalysisService(CommitGraphMetricsCalculator metricsCalculator, GithubProjectGraphStore graphStore, GitRepositoryCloner repositoryCloner, CommitDagBuilder commitDagBuilder, CommitMetadataService metadataService, CommitEntityRepository commitRepository) {
        this.metricsCalculator = metricsCalculator;
        this.graphStore = graphStore;
        this.repositoryCloner = repositoryCloner;
        this.commitDagBuilder = commitDagBuilder;
        this.metadataService = metadataService;
        this.commitRepository = commitRepository;
    }

    public List<GithubProjectAnalysis> analyze(List<GithubProjectRequest> projects) {
        repositoryCloner.requireGithubToken();
        long batchStartedAt = System.currentTimeMillis();
        log.info("Starting project analysis batch: projects={}", projects.size());
        Map<String, GithubProjectRequest> upstreamProjects = new LinkedHashMap<>();
        for (GithubProjectRequest requestedProject : projects) {
            GithubProjectRequest upstream = repositoryCloner.resolveUpstream(requestedProject);
            upstreamProjects.putIfAbsent(
                    (upstream.owner() + "/" + upstream.name()).toLowerCase(Locale.ROOT),
                    upstream);
        }
        List<GithubProjectAnalysis> result = upstreamProjects.values().stream()
            .map(project -> {
                String projectName = project.owner() + "/" + project.name();
                String projectRef = UUID.nameUUIDFromBytes(
                        ("github-upstream:" + projectName.toLowerCase(Locale.ROOT))
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                String defaultBranch = repositoryCloner.getUpstreamDefaultBranch(project);
                long startedAt = System.currentTimeMillis();
                log.info("Analyzing project: projectRef={}, project={}", projectRef, projectName);

                GithubProjectAnalysis analysis = commitDagBuilder.build(projectRef, project, repositoryCloner.clone(project), defaultBranch);
                graphStore.save(analysis);
                int commitCount = analysis.commits().size();
                log.info("Graph built and saved: projectRef={}, commits={}", projectRef, commitCount);

                int processed = 0;
                for (CommitNode commit : analysis.commits()) {
                    upsertCommit(projectRef, commit);
                    processed++;
                    if (processed % 1000 == 0) {
                        log.info("Commit persist progress: projectRef={}, processedCommits={}/{}", projectRef, processed, commitCount);
                    }
                }
                var mappingResult = metadataService.fetchAndSaveProjectMappings(
                        project.owner(),
                        project.name(),
                        projectRef,
                        analysis.commits().stream().map(CommitNode::hash).collect(java.util.stream.Collectors.toSet()));
                int projectIssueCount = metadataService.fetchAndSaveProjectIssues(project.owner(), project.name(), projectRef);
                var commitMetadataResult =
                        metadataService.fetchAndSaveProjectCommitMetadata(project.owner(), project.name(), projectRef);
                int releaseCount = metadataService.fetchAndSaveReleasesForProject(project.owner(), project.name(), projectRef);

                long durationMillis = System.currentTimeMillis() - startedAt;
                log.info("Project analysis completed: projectRef={}, project={}, commits={}, prs={}, issues={}, commitPrLinks={}, prIssueLinks={}, commitIssueLinks={}, comments={}, workflowRuns={}, releases={}, durationMs={}",
                        projectRef,
                        projectName,
                        commitCount,
                        mappingResult.prCount(),
                        projectIssueCount,
                        mappingResult.commitPrLinkCount(),
                        mappingResult.prIssueLinkCount(),
                        mappingResult.commitIssueLinkCount(),
                        commitMetadataResult.commentCount(),
                        commitMetadataResult.workflowRunCount(),
                        releaseCount,
                        durationMillis);
                return analysis;
            })
            .toList();
        long batchDurationMillis = System.currentTimeMillis() - batchStartedAt;
        log.info("Project analysis batch completed: projects={}, durationMs={}", result.size(), batchDurationMillis);
        return result;
    }

    public GithubProjectAnalysis subgraph(String projectRef, Instant cutoffAt) {
        long startedAt = System.currentTimeMillis();
        log.info("Building subgraph: sourceProjectRef={}, cutoffAt={}", projectRef, cutoffAt);
        GithubProjectAnalysis graph = graphStore.findByRef(projectRef)
                .orElseThrow(() -> new IllegalArgumentException("Unknown project ref: " + projectRef));
        List<CommitGraphMetricsCalculator.CommitSnapshot> snapshots = graph.commits().stream()
                .map(commit -> new CommitGraphMetricsCalculator.CommitSnapshot(commit.hash(), commit.parents(), Instant.ofEpochSecond(commit.authoredAtEpochSecond()), Instant.ofEpochSecond(commit.committedAtEpochSecond())))
                .toList();
        List<CommitGraphMetricsCalculator.CommitSnapshot> prunedSnapshots = metricsCalculator.sliceByCutoff(snapshots, cutoffAt);
        List<CommitNode> commits = metricsCalculator.buildNodes(prunedSnapshots);
        String subgraphRef = UUID.randomUUID().toString();
        GithubProjectAnalysis subgraph = new GithubProjectAnalysis(subgraphRef, graph.owner(), graph.name(), graph.clonedPath(), commits, graph.defaultBranch(), graph.headSha(), graph.analyzedAtEpochSecond());
        graphStore.save(subgraph, graph.projectRef());
        long durationMillis = System.currentTimeMillis() - startedAt;
        log.info("Subgraph built and saved: subgraphRef={}, sourceProjectRef={}, originalCommits={}, subgraphCommits={}, durationMs={}",
                subgraphRef, projectRef, snapshots.size(), commits.size(), durationMillis);
        return subgraph;
    }

    public IssueLabelRefreshResult refreshIssueLabels(String projectRef) {
        GithubProjectAnalysis graph = graphStore.findByRef(projectRef)
                .orElseThrow(() -> new IllegalArgumentException("Unknown project ref: " + projectRef));
        return metadataService.fetchAndSaveIssueLabels(graph.owner(), graph.name(), projectRef);
    }

    public AllIssueLabelsRefreshResult refreshIssueLabelsForAllProjects() {
        int repositoriesProcessed = 0;
        int issuesUpdated = 0;
        int labelsStored = 0;
        for (GithubProjectAnalysis graph : graphStore.findRootGraphs()) {
            IssueLabelRefreshResult result =
                    metadataService.fetchAndSaveIssueLabels(graph.owner(), graph.name(), graph.projectRef());
            repositoriesProcessed++;
            issuesUpdated += result.issuesUpdated();
            labelsStored += result.labelsStored();
        }
        return new AllIssueLabelsRefreshResult(repositoriesProcessed, issuesUpdated, labelsStored);
    }

    private void upsertCommit(String projectRef, CommitNode commit) {
        CommitEntity entity = commitRepository.findByProjectRefAndCommitHash(projectRef, commit.hash())
                .orElseGet(() -> new CommitEntity(
                        UUID.randomUUID().toString(),
                        projectRef,
                        commit.hash(),
                        commit.authoredAtEpochSecond(),
                        commit.metrics().parentCount(),
                        commit.metrics().mergeParentCount(),
                        commit.metrics().mergeParentCount() > 0));
        // Keep the legacy commits.authored_at column's historical operational meaning (committer time).
        entity.setAuthoredAtEpochSecond(commit.committedAtEpochSecond());
        entity.setParentCount(commit.metrics().parentCount());
        entity.setMergeParentCount(commit.metrics().mergeParentCount());
        entity.setMerge(commit.metrics().mergeParentCount() > 0);
        commitRepository.save(entity);
    }
}
