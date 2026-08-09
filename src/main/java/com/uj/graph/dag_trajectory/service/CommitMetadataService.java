package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.entity.CommitCICDEntity;
import com.uj.graph.dag_trajectory.entity.CommitCommentEntity;
import com.uj.graph.dag_trajectory.entity.CommitIssueEntity;
import com.uj.graph.dag_trajectory.entity.CommitPullRequestEntity;
import com.uj.graph.dag_trajectory.repository.CommitCICDRepository;
import com.uj.graph.dag_trajectory.repository.CommitCommentRepository;
import com.uj.graph.dag_trajectory.repository.CommitIssueRepository;
import com.uj.graph.dag_trajectory.repository.CommitPullRequestRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CommitMetadataService {

    private final GitHubGraphQLClient graphQLClient;
    private final CommitPullRequestRepository prRepository;
    private final CommitIssueRepository issueRepository;
    private final CommitCommentRepository commentRepository;
    private final CommitCICDRepository cicdRepository;

    public CommitMetadataService(GitHubGraphQLClient graphQLClient,
                                CommitPullRequestRepository prRepository,
                                CommitIssueRepository issueRepository,
                                CommitCommentRepository commentRepository,
                                CommitCICDRepository cicdRepository) {
        this.graphQLClient = graphQLClient;
        this.prRepository = prRepository;
        this.issueRepository = issueRepository;
        this.commentRepository = commentRepository;
        this.cicdRepository = cicdRepository;
    }

    public void fetchAndSaveMetadataForCommit(String owner, String repo, String commitHash, String projectRef) {
        try {
            fetchAndSavePullRequests(owner, repo, commitHash, projectRef);
            fetchAndSaveIssues(owner, repo, commitHash, projectRef);
            fetchAndSaveComments(owner, repo, commitHash, projectRef);
            fetchAndSaveWorkflowRuns(owner, repo, commitHash, projectRef);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch metadata for commit " + commitHash, e);
        }
    }

    private void fetchAndSavePullRequests(String owner, String repo, String commitHash, String projectRef) {
        var prs = graphQLClient.getPullRequestsForCommit(owner, repo, commitHash);
        for (var pr : prs) {
            Long timeToMergeSeconds = null;
            if (pr.mergedAt() != null && pr.createdAt() != null) {
                timeToMergeSeconds = Instant.parse(pr.mergedAt()).getEpochSecond() - Instant.parse(pr.createdAt()).getEpochSecond();
            }
            
            CommitPullRequestEntity entity = new CommitPullRequestEntity(
                UUID.randomUUID().toString(),
                commitHash,
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
                pr.changedFiles()
            );
            prRepository.save(entity);
        }
    }

    private void fetchAndSaveIssues(String owner, String repo, String commitHash, String projectRef) {
        var issues = graphQLClient.getIssuesForCommit(owner, repo, commitHash);
        for (var issue : issues) {
            Long timeToCloseSeconds = null;
            if (issue.closedAt() != null && issue.createdAt() != null) {
                timeToCloseSeconds = Instant.parse(issue.closedAt()).getEpochSecond() - Instant.parse(issue.createdAt()).getEpochSecond();
            }
            Boolean isResolved = "CLOSED".equalsIgnoreCase(issue.state());
            
            CommitIssueEntity entity = new CommitIssueEntity(
                UUID.randomUUID().toString(),
                commitHash,
                projectRef,
                issue.number(),
                issue.title(),
                issue.url(),
                issue.state(),
                parseToEpochSecond(issue.createdAt()),
                issue.closedAt() != null ? parseToEpochSecond(issue.closedAt()) : null,
                timeToCloseSeconds,
                issue.commentCount(),
                0,
                isResolved,
                issue.labelCount()
            );
            issueRepository.save(entity);
        }
    }

    private void fetchAndSaveComments(String owner, String repo, String commitHash, String projectRef) {
        var comments = graphQLClient.getCommitComments(owner, repo, commitHash);
        for (var comment : comments) {
            Integer bodyLength = comment.body() != null ? comment.body().length() : 0;
            Double sentimentScore = calculateSentimentScore(comment.body());
            Boolean isBotComment = isBotAuthor(comment.authorLogin());
            
            CommitCommentEntity entity = new CommitCommentEntity(
                UUID.randomUUID().toString(),
                commitHash,
                projectRef,
                comment.url(),
                comment.body(),
                comment.authorLogin(),
                parseToEpochSecond(comment.createdAt()),
                null,
                null,
                bodyLength,
                sentimentScore,
                isBotComment
            );
            commentRepository.save(entity);
        }
    }

    private void fetchAndSaveWorkflowRuns(String owner, String repo, String commitHash, String projectRef) {
        var runs = graphQLClient.getWorkflowRunsForCommit(owner, repo, commitHash);
        for (var run : runs) {
            Long durationSeconds = null;
            if (run.completedAt() != null && run.startedAt() != null) {
                durationSeconds = Instant.parse(run.completedAt()).getEpochSecond() - Instant.parse(run.startedAt()).getEpochSecond();
            }
            Boolean isSuccess = "COMPLETED".equalsIgnoreCase(run.status()) && "SUCCESS".equalsIgnoreCase(run.conclusion());
            
            CommitCICDEntity entity = new CommitCICDEntity(
                UUID.randomUUID().toString(),
                commitHash,
                projectRef,
                run.name(),
                run.url(),
                run.status(),
                run.conclusion(),
                parseToEpochSecond(run.startedAt()),
                run.completedAt() != null ? parseToEpochSecond(run.completedAt()) : null,
                durationSeconds,
                isSuccess,
                1,
                1
            );
            cicdRepository.save(entity);
        }
    }

    private Long parseToEpochSecond(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isBlank()) {
            return null;
        }
        return Instant.parse(isoDateTime).getEpochSecond();
    }

    private Double calculateSentimentScore(String text) {
        if (text == null || text.isBlank()) {
            return 0.5;
        }
        
        int positiveWords = countWords(text, new String[]{"good", "great", "excellent", "awesome", "nice", "fix", "solved", "working", "ready", "perfect"});
        int negativeWords = countWords(text, new String[]{"bad", "terrible", "awful", "broken", "issue", "problem", "error", "fail", "wrong", "stuck"});
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
}
