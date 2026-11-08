package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.entity.CommitCICDEntity;
import com.uj.graph.dag_trajectory.entity.CommitCommentEntity;
import com.uj.graph.dag_trajectory.entity.CommitEntity;
import com.uj.graph.dag_trajectory.entity.CommitIssueEntity;
import com.uj.graph.dag_trajectory.entity.CommitIssueLinkEntity;
import com.uj.graph.dag_trajectory.entity.CommitPullRequestEntity;
import com.uj.graph.dag_trajectory.entity.CommitPullRequestLinkEntity;
import com.uj.graph.dag_trajectory.entity.IssueEntity;
import com.uj.graph.dag_trajectory.entity.IssueLabelCategory;
import com.uj.graph.dag_trajectory.entity.IssueLabelEntity;
import com.uj.graph.dag_trajectory.entity.PullRequestEntity;
import com.uj.graph.dag_trajectory.entity.PullRequestIssueLinkEntity;
import com.uj.graph.dag_trajectory.entity.ReleaseEntity;
import com.uj.graph.dag_trajectory.model.IssueLabelRefreshResult;
import com.uj.graph.dag_trajectory.repository.CommitCICDRepository;
import com.uj.graph.dag_trajectory.repository.CommitCommentRepository;
import com.uj.graph.dag_trajectory.repository.CommitEntityRepository;
import com.uj.graph.dag_trajectory.repository.CommitIssueRepository;
import com.uj.graph.dag_trajectory.repository.CommitIssueLinkRepository;
import com.uj.graph.dag_trajectory.repository.CommitPullRequestRepository;
import com.uj.graph.dag_trajectory.repository.CommitPullRequestLinkRepository;
import com.uj.graph.dag_trajectory.repository.IssueEntityRepository;
import com.uj.graph.dag_trajectory.repository.IssueLabelRepository;
import com.uj.graph.dag_trajectory.repository.PullRequestEntityRepository;
import com.uj.graph.dag_trajectory.repository.PullRequestIssueLinkRepository;
import com.uj.graph.dag_trajectory.repository.ReleaseEntityRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommitMetadataService {
    private static final Logger log = LoggerFactory.getLogger(CommitMetadataService.class);

    private final GitHubGraphQLClient graphQLClient;
    private final PullRequestEntityRepository pullRequestRepository;
    private final IssueEntityRepository issueRepository;
    private final IssueLabelRepository issueLabelRepository;
    private final CommitEntityRepository commitRepository;
    private final CommitPullRequestLinkRepository commitPullRequestLinkRepository;
    private final CommitIssueLinkRepository commitIssueLinkRepository;
    private final CommitPullRequestRepository commitPullRequestRepository;
    private final CommitIssueRepository commitIssueRepository;
    private final CommitCommentRepository commentRepository;
    private final CommitCICDRepository cicdRepository;
    private final PullRequestIssueLinkRepository prIssueLinkRepository;
    private final ReleaseEntityRepository releaseRepository;

    public CommitMetadataService(
            GitHubGraphQLClient graphQLClient,
            PullRequestEntityRepository pullRequestRepository,
            IssueEntityRepository issueRepository,
            IssueLabelRepository issueLabelRepository,
            CommitEntityRepository commitRepository,
            CommitPullRequestLinkRepository commitPullRequestLinkRepository,
            CommitIssueLinkRepository commitIssueLinkRepository,
            CommitPullRequestRepository commitPullRequestRepository,
            CommitIssueRepository commitIssueRepository,
            CommitCommentRepository commentRepository,
            CommitCICDRepository cicdRepository,
            PullRequestIssueLinkRepository prIssueLinkRepository,
            ReleaseEntityRepository releaseRepository) {
        this.graphQLClient = graphQLClient;
        this.pullRequestRepository = pullRequestRepository;
        this.issueRepository = issueRepository;
        this.issueLabelRepository = issueLabelRepository;
        this.commitRepository = commitRepository;
        this.commitPullRequestLinkRepository = commitPullRequestLinkRepository;
        this.commitIssueLinkRepository = commitIssueLinkRepository;
        this.commitPullRequestRepository = commitPullRequestRepository;
        this.commitIssueRepository = commitIssueRepository;
        this.commentRepository = commentRepository;
        this.cicdRepository = cicdRepository;
        this.prIssueLinkRepository = prIssueLinkRepository;
        this.releaseRepository = releaseRepository;
    }



    public ProjectMappingPersistResult fetchAndSaveProjectMappings(
            String owner, String repo, String projectRef, Set<String> upstreamDagHashes) {
        var pullRequests = graphQLClient.getPullRequestsForProject(owner, repo);
        Map<String, CommitEntity> upstreamCommits = new HashMap<>();
        for (CommitEntity commit : commitRepository.findByProjectRef(projectRef)) {
            if (upstreamDagHashes.contains(commit.getCommitHash())) {
                upstreamCommits.put(commit.getCommitHash(), commit);
            }
        }
        int prCount = 0;
        int commitPrLinks = 0;
        int prIssueLinks = 0;
        int commitIssueLinks = 0;

        for (var pr : pullRequests) {
            prCount++;
            Long timeToMergeSeconds = null;
            if (pr.mergedAt() != null && pr.createdAt() != null) {
                timeToMergeSeconds =
                        Instant.parse(pr.mergedAt()).getEpochSecond()
                                - Instant.parse(pr.createdAt()).getEpochSecond();
            }

            PullRequestEntity pullRequest = upsertPullRequest(
                    projectRef,
                    new GitHubGraphQLClient.PullRequest(
                            pr.number(),
                            pr.title(),
                            pr.url(),
                            pr.state(),
                            pr.createdAt(),
                            pr.mergedAt(),
                            pr.reviewCount(),
                            pr.commentCount(),
                            pr.additions(),
                            pr.deletions(),
                            pr.changedFiles(),
                            pr.linkedIssues()),
                    timeToMergeSeconds);

            Map<Integer, IssueEntity> linkedIssues = new HashMap<>();
            for (var linkedIssue : pr.linkedIssues()) {
                Long issueCreatedAt = parseToEpochSecond(linkedIssue.createdAt());
                Long issueClosedAt = parseToEpochSecond(linkedIssue.closedAt());
                Long timeToCloseSeconds = null;
                if (issueCreatedAt != null && issueClosedAt != null) {
                    timeToCloseSeconds = issueClosedAt - issueCreatedAt;
                }
                IssueEntity issue = upsertIssue(
                        projectRef,
                        linkedIssue.number(),
                        linkedIssue.title(),
                        linkedIssue.url(),
                        linkedIssue.state(),
                        issueCreatedAt,
                        issueClosedAt,
                        timeToCloseSeconds,
                        linkedIssue.commentCount(),
                        1,
                        "CLOSED".equalsIgnoreCase(linkedIssue.state()),
                        linkedIssue.labelCount());
                linkedIssues.put(linkedIssue.number(), issue);
                if (savePullRequestIssueLink(pullRequest, issue)) {
                    prIssueLinks++;
                    refreshRelatedPrCount(issue);
                }
            }

            if (!pr.merged() && pr.mergedAt() == null) {
                continue;
            }

            List<CommitEntity> integratedCommits = pr.commitHashes().stream()
                    .filter(upstreamDagHashes::contains)
                    .map(upstreamCommits::get)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();

            // Squash-merged and some rebased PRs do not retain the original PR commit SHAs
            // on the upstream default branch. In that case, use GitHub's merge commit SHA
            // only when that exact object is present in the upstream DAG.
            if (integratedCommits.isEmpty()
                    && pr.mergeCommitHash() != null
                    && upstreamDagHashes.contains(pr.mergeCommitHash())) {
                CommitEntity integratedMergeCommit = upstreamCommits.get(pr.mergeCommitHash());
                if (integratedMergeCommit != null) {
                    integratedCommits = List.of(integratedMergeCommit);
                }
            }

            if (integratedCommits.isEmpty()) {
                log.warn(
                        "Merged PR could not be matched to upstream DAG commits: projectRef={}, prNumber={}, prCommitCount={}, mergeCommitHash={}",
                        projectRef,
                        pr.number(),
                        pr.commitHashes().size(),
                        pr.mergeCommitHash());
                continue;
            }
            if (integratedCommits.size() < pr.commitHashes().size()) {
                log.warn(
                        "Some PR commits could not be matched to upstream DAG: projectRef={}, prNumber={}, matched={}, reported={}",
                        projectRef,
                        pr.number(),
                        integratedCommits.size(),
                        pr.commitHashes().size());
            }
            for (CommitEntity commit : integratedCommits) {
                if (saveCommitPullRequestLink(commit, pullRequest)) {
                    commitPrLinks++;
                }
                saveLegacyCommitPullRequest(projectRef, commit.getCommitHash(), pullRequest);
                for (IssueEntity issue : linkedIssues.values()) {
                    if (saveCommitIssueLink(commit, issue)) {
                        commitIssueLinks++;
                    }
                    saveLegacyCommitIssue(projectRef, commit.getCommitHash(), issue);
                }
            }
        }

        log.info(
                "Project mappings persisted: projectRef={}, prs={}, commitPrLinks={}, prIssueLinks={}, commitIssueLinks={}",
                projectRef,
                prCount,
                commitPrLinks,
                prIssueLinks,
                commitIssueLinks);

        return new ProjectMappingPersistResult(prCount, commitPrLinks, prIssueLinks, commitIssueLinks);
    }

    @Transactional
    public int fetchAndSaveProjectIssues(String owner, String repo, String projectRef) {
        var issues = graphQLClient.getIssuesForProject(owner, repo);
        for (var issue : issues) {
            Long createdAt = parseToEpochSecond(issue.createdAt());
            Long closedAt = parseToEpochSecond(issue.closedAt());
            Long timeToCloseSeconds = null;
            if (createdAt != null && closedAt != null) {
                timeToCloseSeconds = Math.max(0, closedAt - createdAt);
            }
            IssueEntity persistedIssue = upsertIssue(
                    projectRef,
                    issue.number(),
                    issue.title(),
                    issue.url(),
                    issue.state(),
                    createdAt,
                    closedAt,
                    timeToCloseSeconds,
                    issue.commentCount(),
                    null,
                    "CLOSED".equalsIgnoreCase(issue.state()),
                    issue.labelNames().size());
            synchronizeIssueLabels(persistedIssue, issue.labelNames());
        }
        return issues.size();
    }

    @Transactional
    public IssueLabelRefreshResult fetchAndSaveIssueLabels(String owner, String repo, String projectRef) {
        if (!graphQLClient.isConfigured()) {
            throw new IllegalStateException("GitHub token is required to refresh issue labels");
        }
        var githubIssues = graphQLClient.getIssuesForProject(owner, repo);
        Map<Integer, GitHubGraphQLClient.ProjectIssue> issuesByNumber = new HashMap<>();
        for (GitHubGraphQLClient.ProjectIssue issue : githubIssues) {
            issuesByNumber.put(issue.number(), issue);
        }

        int issuesUpdated = 0;
        int labelsStored = 0;
        for (IssueEntity issue : issueRepository.findAllByProjectRef(projectRef)) {
            GitHubGraphQLClient.ProjectIssue githubIssue = issuesByNumber.get(issue.getIssueNumber());
            if (githubIssue == null) {
                continue;
            }
            labelsStored += synchronizeIssueLabels(issue, githubIssue.labelNames());
            issuesUpdated++;
        }
        log.info(
                "Issue labels refreshed: projectRef={}, issuesUpdated={}, labelsStored={}",
                projectRef,
                issuesUpdated,
                labelsStored);
        return new IssueLabelRefreshResult(issuesUpdated, labelsStored);
    }

    private int synchronizeIssueLabels(IssueEntity issue, List<String> labelNames) {
        Map<String, IssueLabelEntity> existingByName = new HashMap<>();
        for (IssueLabelEntity existing : issueLabelRepository.findByIssue_Id(issue.getId())) {
            existingByName.put(existing.getLabelName(), existing);
        }

        Set<String> desiredNames = new HashSet<>(labelNames);
        issueLabelRepository.deleteAll(existingByName.entrySet().stream()
                .filter(entry -> !desiredNames.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList());

        var labelsToSave = labelNames.stream()
                .distinct()
                .map(labelName -> {
                    IssueLabelEntity existing = existingByName.get(labelName);
                    var category = IssueLabelCategoryMapper.map(labelName);
                    if (existing != null) {
                        existing.setCategory(category);
                        return existing;
                    }
                    return new IssueLabelEntity(UUID.randomUUID().toString(), issue, labelName, category);
                })
                .toList();
        issueLabelRepository.saveAll(labelsToSave);
        return labelsToSave.size();
    }

    public ProjectCommitMetadataPersistResult fetchAndSaveProjectCommitMetadata(
            String owner, String repo, String projectRef) {
        int commentCount = fetchAndSaveProjectComments(owner, repo, projectRef);
        int workflowRunCount = fetchAndSaveProjectWorkflowRuns(owner, repo, projectRef);
        return new ProjectCommitMetadataPersistResult(commentCount, workflowRunCount);
    }

    public int fetchAndSaveReleasesForProject(String owner, String repo, String projectRef) {
        var releases = graphQLClient.getReleases(owner, repo).stream()
                .sorted(Comparator.comparing(this::releaseSortKey, Comparator.nullsLast(Long::compareTo)))
                .toList();
        if (releases.isEmpty()) {
            log.debug("No releases found for project: projectRef={}, repo={}/{}", projectRef, owner, repo);
            return 0;
        }

        Long previousReleaseEpochSecond = null;
        for (GitHubGraphQLClient.Release release : releases) {
            Long releaseEpochSecond = releaseSortKey(release);
            Long daysSincePreviousRelease = null;
            Long commitsSincePreviousRelease = null;
            if (releaseEpochSecond != null) {
                if (previousReleaseEpochSecond != null) {
                    long diff = Math.max(0, releaseEpochSecond - previousReleaseEpochSecond);
                    daysSincePreviousRelease = diff / 86_400;
                    commitsSincePreviousRelease = commitRepository
                            .countByProjectRefAndAuthoredAtEpochSecondGreaterThanAndAuthoredAtEpochSecondLessThanEqual(
                                    projectRef, previousReleaseEpochSecond, releaseEpochSecond);
                } else {
                    commitsSincePreviousRelease = commitRepository
                            .countByProjectRefAndAuthoredAtEpochSecondLessThanEqual(projectRef, releaseEpochSecond);
                }
            }

            CommitEntity commit = null;
            if (release.commitOid() != null && !release.commitOid().isBlank()) {
                commit = commitRepository.findByProjectRefAndCommitHash(projectRef, release.commitOid()).orElse(null);
            }

            ReleaseEntity entity = releaseRepository
                    .findByProjectRefAndGithubReleaseId(projectRef, release.id())
                    .orElseGet(() -> new ReleaseEntity(UUID.randomUUID().toString(), projectRef, release.id()));
            entity.setReleaseName(release.name());
            entity.setTagName(release.tagName());
            entity.setReleaseUrl(release.url());
            entity.setPublishedAtEpochSecond(parseToEpochSecond(release.publishedAt()));
            entity.setCreatedAtEpochSecond(parseToEpochSecond(release.createdAt()));
            entity.setDraft(release.draft());
            entity.setPrerelease(release.prerelease());
            entity.setAuthorLogin(release.authorLogin());
            entity.setDaysSincePreviousRelease(daysSincePreviousRelease);
            entity.setCommitsSincePreviousRelease(commitsSincePreviousRelease);
            entity.setCommit(commit);
            releaseRepository.save(entity);

            if (releaseEpochSecond != null) {
                previousReleaseEpochSecond = releaseEpochSecond;
            }
        }

        log.info("Release metadata persisted: projectRef={}, releases={}", projectRef, releases.size());
        return releases.size();
    }

    private int fetchAndSaveProjectComments(String owner, String repo, String projectRef) {
        var comments = graphQLClient.getCommitCommentsForProject(owner, repo);
        int persisted = 0;
        for (var comment : comments) {
            if (comment.commitHash() == null || comment.commitHash().isBlank()) {
                continue;
            }
            CommitEntity commit = commitRepository.findByProjectRefAndCommitHash(projectRef, comment.commitHash())
                    .orElse(null);
            if (commit == null) {
                continue;
            }
            if (comment.url() != null
                    && commentRepository.existsByProjectRefAndCommitHashAndCommentUrl(
                    projectRef, comment.commitHash(), comment.url())) {
                continue;
            }
            Integer bodyLength = comment.body() != null ? comment.body().length() : 0;
            Double sentimentScore = calculateSentimentScore(comment.body());
            Boolean isBotComment = isBotAuthor(comment.authorLogin());

            CommitCommentEntity entity = new CommitCommentEntity(
                    UUID.randomUUID().toString(),
                    commit.getCommitHash(),
                    projectRef,
                    comment.url(),
                    comment.body(),
                    comment.authorLogin() == null || comment.authorLogin().isBlank() ? "unknown" : comment.authorLogin(),
                    parseToEpochSecond(comment.createdAt()),
                    null,
                    null,
                    bodyLength,
                    sentimentScore,
                    isBotComment);
            commentRepository.save(entity);
            persisted++;
        }
        return persisted;
    }

    private int fetchAndSaveProjectWorkflowRuns(String owner, String repo, String projectRef) {
        var runs = graphQLClient.getWorkflowRunsForProject(owner, repo);
        int persisted = 0;
        for (var run : runs) {
            if (run.commitHash() == null || run.commitHash().isBlank()) {
                continue;
            }
            CommitEntity commit = commitRepository.findByProjectRefAndCommitHash(projectRef, run.commitHash())
                    .orElse(null);
            if (commit == null) {
                continue;
            }
            if (run.url() != null
                    && cicdRepository.existsByProjectRefAndCommitHashAndWorkflowUrl(
                    projectRef, commit.getCommitHash(), run.url())) {
                continue;
            }
            Long durationSeconds = null;
            if (run.completedAt() != null && run.startedAt() != null) {
                durationSeconds =
                        Instant.parse(run.completedAt()).getEpochSecond()
                                - Instant.parse(run.startedAt()).getEpochSecond();
            }
            Boolean isSuccess =
                    "COMPLETED".equalsIgnoreCase(run.status()) && "SUCCESS".equalsIgnoreCase(run.conclusion());

            // GitHub metadata is external input. Keep values within the database's
            // VARCHAR(255) limits and use a stable fallback for missing/oversized statuses.
            String runStatus = databaseString(run.status(), "unknown", 255);
            String runConclusion = databaseString(run.conclusion(), null, 255);
            CommitCICDEntity entity = new CommitCICDEntity(
                    UUID.randomUUID().toString(),
                    commit.getCommitHash(),
                    projectRef,
                    run.name() == null || run.name().isBlank() ? "unknown" : run.name(),
                    run.url() == null || run.url().isBlank() ? "unknown" : run.url(),
                    runStatus,
                    runConclusion,
                    parseToEpochSecond(run.startedAt()),
                    run.completedAt() != null ? parseToEpochSecond(run.completedAt()) : null,
                    durationSeconds,
                    isSuccess,
                    run.attemptNumber(),
                    run.attemptNumber());
            try {
                // Flush here so a bad row is isolated; the next workflow run can still be saved.
                cicdRepository.saveAndFlush(entity);
                persisted++;
            } catch (DataIntegrityViolationException exception) {
                log.warn(
                        "Skipping invalid CI/CD record without aborting ingestion: projectRef={}, commitHash={}, workflowUrl={}, reason={}",
                        projectRef,
                        commit.getCommitHash(),
                        run.url(),
                        exception.getMostSpecificCause().getMessage());
            }
        }
        return persisted;
    }

    private String databaseString(String value, String fallback, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            if (value != null && value.length() > maxLength) {
                log.warn("Replacing oversized database string (length={}) with fallback", value.length());
            }
            return fallback;
        }
        return value;
    }

    private Long parseToEpochSecond(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isBlank()) {
            return null;
        }
        return Instant.parse(isoDateTime).getEpochSecond();
    }

    private Long releaseSortKey(GitHubGraphQLClient.Release release) {
        Long published = parseToEpochSecond(release.publishedAt());
        if (published != null) {
            return published;
        }
        return parseToEpochSecond(release.createdAt());
    }

    private Double calculateSentimentScore(String text) {
        if (text == null || text.isBlank()) {
            return 0.5;
        }

        int positiveWords =
                countWords(
                        text,
                        new String[] {"good", "great", "excellent", "awesome", "nice", "fix", "solved", "working", "ready", "perfect"});
        int negativeWords =
                countWords(
                        text,
                        new String[] {"bad", "terrible", "awful", "broken", "issue", "problem", "error", "fail", "wrong", "stuck"});
        int totalWords = text.split("\\s+").length;

        if (totalWords == 0) {
            return 0.5;
        }

        double sentiment = 0.5 + (positiveWords - negativeWords) / (double) (totalWords * 2);
        return Math.max(0.0, Math.min(1.0, sentiment));
    }

    private int countWords(String text, String[] keywords) {
        int count = 0;
        String lowerText = text.toLowerCase();
        for (String keyword : keywords) {
            count += countOccurrences(lowerText, keyword);
        }
        return count;
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    private Boolean isBotAuthor(String login) {
        if (login == null) {
            return false;
        }
        String lowerLogin = login.toLowerCase();
        return lowerLogin.contains("bot") || lowerLogin.contains("github") || lowerLogin.contains("dependabot");
    }

    private PullRequestEntity upsertPullRequest(
            String projectRef, GitHubGraphQLClient.PullRequest pr, Long timeToMergeSeconds) {
        PullRequestEntity entity = pullRequestRepository.findByProjectRefAndPrNumber(projectRef, pr.number())
                .orElseGet(() -> new PullRequestEntity(
                        UUID.randomUUID().toString(),
                        projectRef,
                        pr.number(),
                        pr.title(),
                        pr.url(),
                        pr.state(),
                        parseToEpochSecond(pr.createdAt()),
                        pr.mergedAt() != null ? parseToEpochSecond(pr.mergedAt()) : null,
                        timeToMergeSeconds,
                        pr.reviewCount(),
                        pr.commentCount(),
                        pr.additions(),
                        pr.deletions(),
                        pr.changedFiles()));
        entity.setPrTitle(pr.title());
        entity.setPrUrl(pr.url());
        entity.setPrState(pr.state());
        entity.setCreatedAtEpochSecond(parseToEpochSecond(pr.createdAt()));
        entity.setMergedAtEpochSecond(pr.mergedAt() != null ? parseToEpochSecond(pr.mergedAt()) : null);
        entity.setTimeToMergeSeconds(timeToMergeSeconds);
        entity.setReviewCount(pr.reviewCount());
        entity.setCommentCount(pr.commentCount());
        entity.setAdditions(pr.additions());
        entity.setDeletions(pr.deletions());
        entity.setChangedFiles(pr.changedFiles());
        return pullRequestRepository.save(entity);
    }

    private IssueEntity upsertIssue(
            String projectRef,
            int issueNumber,
            String title,
            String url,
            String state,
            Long createdAtEpochSecond,
            Long closedAtEpochSecond,
            Long timeToCloseSeconds,
            Integer commentCount,
            Integer relatedPrCount,
            Boolean isResolved,
            Integer labelCount) {
        IssueEntity issue = issueRepository.findByProjectRefAndIssueNumber(projectRef, issueNumber)
                .orElseGet(() -> new IssueEntity(
                        UUID.randomUUID().toString(),
                        projectRef,
                        issueNumber,
                        title,
                        url,
                        state,
                        createdAtEpochSecond,
                        closedAtEpochSecond,
                        timeToCloseSeconds,
                        commentCount,
                        relatedPrCount,
                        isResolved,
                        labelCount));
        issue.setIssueTitle(title);
        issue.setIssueUrl(url);
        issue.setIssueState(state);
        if (createdAtEpochSecond != null) {
            issue.setCreatedAtEpochSecond(createdAtEpochSecond);
        }
        if (closedAtEpochSecond != null) {
            issue.setClosedAtEpochSecond(closedAtEpochSecond);
        }
        if (timeToCloseSeconds != null) {
            issue.setTimeToCloseSeconds(timeToCloseSeconds);
        }
        if (commentCount != null) {
            issue.setCommentCount(maxInt(issue.getCommentCount(), commentCount));
        }
        if (relatedPrCount != null) {
            issue.setRelatedPrCount(maxInt(issue.getRelatedPrCount(), relatedPrCount));
        }
        if (isResolved != null) {
            issue.setIsResolved(isResolved);
        }
        if (labelCount != null) {
            issue.setLabelCount(maxInt(issue.getLabelCount(), labelCount));
        }
        return issueRepository.save(issue);
    }

    private void refreshRelatedPrCount(IssueEntity issue) {
        int relatedPrCount = (int) prIssueLinkRepository.countByIssue_Id(issue.getId());
        if (issue.getRelatedPrCount() == null || issue.getRelatedPrCount() != relatedPrCount) {
            issue.setRelatedPrCount(relatedPrCount);
            issueRepository.save(issue);
        }
    }

    private int maxInt(Integer existing, Integer incoming) {
        if (existing == null) {
            return incoming;
        }
        return Math.max(existing, incoming);
    }

    private boolean saveCommitPullRequestLink(CommitEntity commit, PullRequestEntity pullRequest) {
        if (commitPullRequestLinkRepository.existsByCommit_IdAndPullRequest_Id(commit.getId(), pullRequest.getId())) {
            return false;
        }
        commitPullRequestLinkRepository.save(new CommitPullRequestLinkEntity(
                UUID.randomUUID().toString(),
                commit,
                pullRequest));
        return true;
    }

    private boolean saveCommitIssueLink(CommitEntity commit, IssueEntity issue) {
        if (commitIssueLinkRepository.existsByCommit_IdAndIssue_Id(commit.getId(), issue.getId())) {
            return false;
        }
        commitIssueLinkRepository.save(new CommitIssueLinkEntity(
                UUID.randomUUID().toString(),
                commit,
                issue));
        return true;
    }

    private boolean savePullRequestIssueLink(PullRequestEntity pullRequest, IssueEntity issue) {
        if (prIssueLinkRepository.existsByPullRequest_IdAndIssue_Id(pullRequest.getId(), issue.getId())) {
            return false;
        }
        prIssueLinkRepository.save(new PullRequestIssueLinkEntity(
                UUID.randomUUID().toString(),
                pullRequest,
                issue));
        return true;
    }

    private void saveLegacyCommitPullRequest(String projectRef, String commitHash, PullRequestEntity pullRequest) {
        if (commitPullRequestRepository.existsByProjectRefAndCommitHashAndPrNumber(
                projectRef, commitHash, pullRequest.getPrNumber())) {
            return;
        }
        commitPullRequestRepository.save(new CommitPullRequestEntity(
                UUID.randomUUID().toString(),
                commitHash,
                projectRef,
                pullRequest.getPrNumber(),
                pullRequest.getPrTitle(),
                pullRequest.getPrUrl(),
                pullRequest.getPrState(),
                pullRequest.getCreatedAtEpochSecond(),
                pullRequest.getMergedAtEpochSecond(),
                pullRequest.getTimeToMergeSeconds(),
                pullRequest.getReviewCount(),
                pullRequest.getCommentCount(),
                pullRequest.getAdditions(),
                pullRequest.getDeletions(),
                pullRequest.getChangedFiles()));
    }

    private void saveLegacyCommitIssue(String projectRef, String commitHash, IssueEntity issue) {
        if (commitIssueRepository.existsByProjectRefAndCommitHashAndIssueNumber(
                projectRef, commitHash, issue.getIssueNumber())) {
            return;
        }
        commitIssueRepository.save(new CommitIssueEntity(
                UUID.randomUUID().toString(),
                commitHash,
                projectRef,
                issue.getIssueNumber(),
                issue.getIssueTitle(),
                issue.getIssueUrl(),
                issue.getIssueState(),
                issue.getCreatedAtEpochSecond(),
                issue.getClosedAtEpochSecond(),
                issue.getTimeToCloseSeconds(),
                issue.getCommentCount(),
                issue.getRelatedPrCount(),
                issue.getIsResolved(),
                issue.getLabelCount()));
    }

    private record PullRequestPersistResult(int prCount, int linkedIssueCount) {}
    public record ProjectMappingPersistResult(int prCount, int commitPrLinkCount, int prIssueLinkCount, int commitIssueLinkCount) {}
    public record ProjectCommitMetadataPersistResult(
            int commentCount,
            int workflowRunCount) {}
}
