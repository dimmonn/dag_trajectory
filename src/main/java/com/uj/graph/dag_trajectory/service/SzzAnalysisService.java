package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.entity.BugIntroducingCommitEntity;
import com.uj.graph.dag_trajectory.entity.IssueEntity;
import com.uj.graph.dag_trajectory.entity.IssueLabelCategory;
import com.uj.graph.dag_trajectory.entity.CommitPullRequestLinkEntity;
import com.uj.graph.dag_trajectory.entity.PullRequestEntity;
import com.uj.graph.dag_trajectory.model.GithubProjectAnalysis;
import com.uj.graph.dag_trajectory.model.GithubProjectRequest;
import com.uj.graph.dag_trajectory.model.SzzAnalysisResult;
import com.uj.graph.dag_trajectory.repository.BugIntroducingCommitRepository;
import com.uj.graph.dag_trajectory.repository.CommitPullRequestLinkRepository;
import com.uj.graph.dag_trajectory.repository.IssueLabelRepository;
import com.uj.graph.dag_trajectory.repository.PullRequestIssueLinkRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SzzAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(SzzAnalysisService.class);
    private static final String ALGORITHM = "SZZ_LINE_BLAME_V2";

    private final GithubProjectGraphStore graphStore;
    private final GitRepositoryCloner repositoryCloner;
    private final IssueLabelRepository issueLabelRepository;
    private final PullRequestIssueLinkRepository pullRequestIssueLinkRepository;
    private final CommitPullRequestLinkRepository commitPullRequestLinkRepository;
    private final SzzLineBlamer lineBlamer;
    private final SzzResultPersistenceService persistenceService;
    private final BugIntroducingCommitRepository resultRepository;

    public SzzAnalysisService(
            GithubProjectGraphStore graphStore,
            GitRepositoryCloner repositoryCloner,
            IssueLabelRepository issueLabelRepository,
            PullRequestIssueLinkRepository pullRequestIssueLinkRepository,
            CommitPullRequestLinkRepository commitPullRequestLinkRepository,
            SzzLineBlamer lineBlamer,
            SzzResultPersistenceService persistenceService,
            BugIntroducingCommitRepository resultRepository) {
        this.graphStore = graphStore;
        this.repositoryCloner = repositoryCloner;
        this.issueLabelRepository = issueLabelRepository;
        this.pullRequestIssueLinkRepository = pullRequestIssueLinkRepository;
        this.commitPullRequestLinkRepository = commitPullRequestLinkRepository;
        this.lineBlamer = lineBlamer;
        this.persistenceService = persistenceService;
        this.resultRepository = resultRepository;
    }

    public SzzAnalysisResult analyze(String projectRef) {
        GithubProjectAnalysis graph = graphStore.findByRef(projectRef)
                .orElseThrow(() -> new IllegalArgumentException("Unknown project ref: " + projectRef));
        if (graphStore.findRootGraphs().stream().noneMatch(root -> root.projectRef().equals(projectRef))) {
            throw new IllegalArgumentException("SZZ must run on a root upstream project graph, not a subgraph: " + projectRef);
        }

        List<IssueEntity> bugIssues = issueLabelRepository
                .findAllByIssue_ProjectRefAndCategory(projectRef, IssueLabelCategory.BUG)
                .stream()
                .map(label -> label.getIssue())
                .collect(java.util.stream.Collectors.toMap(
                        IssueEntity::getId, issue -> issue, (left, right) -> left, LinkedHashMap::new))
                .values().stream().toList();

        // A report date is required to distinguish pre-report suspects from later edits.
        // Do not silently treat issues with missing dates as if they had no time constraint.
        Set<String> bugIssueIds = new HashSet<>();
        Map<String, Integer> issueNumbersById = new HashMap<>();
        Map<String, Long> issueCreatedAtById = new HashMap<>();
        int bugIssuesMissingReportDate = 0;
        for (IssueEntity issue : bugIssues) {
            if (issue.getCreatedAtEpochSecond() == null) {
                bugIssuesMissingReportDate++;
                continue;
            }
            bugIssueIds.add(issue.getId());
            issueNumbersById.put(issue.getId(), issue.getIssueNumber());
            issueCreatedAtById.put(issue.getId(), issue.getCreatedAtEpochSecond());
        }

        Map<String, Set<String>> issueIdsByMergedPrId = new HashMap<>();
        pullRequestIssueLinkRepository.findAllByIssue_ProjectRef(projectRef).forEach(link -> {
            PullRequestEntity pullRequest = link.getPullRequest();
            IssueEntity issue = link.getIssue();
            if (bugIssueIds.contains(issue.getId()) && isMerged(pullRequest)) {
                issueIdsByMergedPrId.computeIfAbsent(pullRequest.getId(), ignored -> new HashSet<>()).add(issue.getId());
            }
        });

        Map<String, Set<String>> issueIdsByFixCommit = new LinkedHashMap<>();
        for (CommitPullRequestLinkEntity link : commitPullRequestLinkRepository.findAllByPullRequest_ProjectRef(projectRef)) {
            PullRequestEntity pullRequest = link.getPullRequest();
            if (!isMerged(pullRequest)) {
                continue;
            }
            Set<String> linkedIssueIds = issueIdsByMergedPrId.get(pullRequest.getId());
            if (linkedIssueIds == null || linkedIssueIds.isEmpty()) {
                continue;
            }
            String fixingCommitHash = link.getCommit().getCommitHash();
            issueIdsByFixCommit.computeIfAbsent(fixingCommitHash, ignored -> new HashSet<>()).addAll(linkedIssueIds);
        }

        var clonePath = repositoryCloner.clone(new GithubProjectRequest(graph.owner(), graph.name()));
        List<BugIntroducingCommitEntity> rows = new ArrayList<>();
        Set<String> candidateHashes = new HashSet<>();
        int skippedNonSingleParentCommits = 0;
        int analyzedFixCommits = 0;
        int postReportLineAttributionsExcluded = 0;
        int failedFixCommits = 0;
        long generatedAt = Instant.now().getEpochSecond();

        for (Map.Entry<String, Set<String>> fixingCommit : issueIdsByFixCommit.entrySet()) {
            SzzLineBlamer.BlameOutcome outcome;
            try {
                outcome = lineBlamer.blame(clonePath, fixingCommit.getKey());
            } catch (RuntimeException e) {
                failedFixCommits++;
                log.error("SZZ could not analyze fixing commit {}; continuing with the remaining candidates",
                        fixingCommit.getKey(), e);
                continue;
            }

            if (outcome.skippedNonSingleParentCommit()) {
                skippedNonSingleParentCommits++;
                continue;
            }
            analyzedFixCommits++;

            for (String issueId : fixingCommit.getValue()) {
                Integer issueNumber = issueNumbersById.get(issueId);
                Long issueCreatedAt = issueCreatedAtById.get(issueId);
                if (issueNumber == null || issueCreatedAt == null) {
                    continue;
                }
                for (SzzLineBlamer.BlamedFile finding : outcome.findings()) {
                    if (!isEligibleCandidate(finding.bicCommitEpochSecond(), issueCreatedAt)) {
                        postReportLineAttributionsExcluded++;
                        continue;
                    }
                    candidateHashes.add(finding.bicCommitHash());
                    rows.add(new BugIntroducingCommitEntity(
                            UUID.randomUUID().toString(), projectRef, issueNumber, fixingCommit.getKey(),
                            finding.bicCommitHash(), finding.bicCommitEpochSecond(), finding.filePath(),
                            finding.deletedLines(), ALGORITHM, generatedAt));
                }
            }
        }

        // Never replace a previous complete result set with a partial run.
        if (failedFixCommits > 0) {
            throw new IllegalStateException("SZZ analysis failed for " + failedFixCommits
                    + " fixing commit(s) in project " + projectRef
                    + "; previous persisted SZZ results were left unchanged. Check the preceding error logs.");
        }

        persistenceService.replaceProjectResults(projectRef, rows);
        int storedCount = Math.toIntExact(resultRepository.countByProjectRef(projectRef));
        log.info("SZZ completed: projectRef={}, bugIssues={}, bugIssuesMissingReportDate={}, fixingCommits={}, skippedNonSingleParentCommits={}, postReportLineAttributionsExcluded={}, bicCandidates={}, resultRows={}",
                projectRef, bugIssues.size(), bugIssuesMissingReportDate, analyzedFixCommits,
                skippedNonSingleParentCommits, postReportLineAttributionsExcluded, candidateHashes.size(), storedCount);
        return new SzzAnalysisResult(projectRef, bugIssues.size(), bugIssuesMissingReportDate,
                analyzedFixCommits, skippedNonSingleParentCommits, postReportLineAttributionsExcluded,
                candidateHashes.size(), storedCount);
    }

    public List<BugIntroducingCommitEntity> getResults(String projectRef) {
        return resultRepository.findAllByProjectRef(projectRef);
    }

    static boolean isEligibleCandidate(long bicCommitEpochSecond, Long issueCreatedAtEpochSecond) {
        return issueCreatedAtEpochSecond != null && bicCommitEpochSecond <= issueCreatedAtEpochSecond;
    }

    private static boolean isMerged(PullRequestEntity pullRequest) {
        return pullRequest.getMergedAtEpochSecond() != null || "MERGED".equalsIgnoreCase(pullRequest.getPrState());
    }
}
