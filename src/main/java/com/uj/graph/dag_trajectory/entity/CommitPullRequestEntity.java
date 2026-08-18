package com.uj.graph.dag_trajectory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "commit_pull_requests")
public class CommitPullRequestEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "commit_hash", nullable = false)
    private String commitHash;

    @Column(name = "project_ref", nullable = false)
    private String projectRef;

    @Column(name = "pr_number", nullable = false)
    private int prNumber;

    @Column(name = "pr_title", nullable = false, columnDefinition = "text")
    private String prTitle;

    @Column(name = "pr_url", nullable = false, columnDefinition = "text")
    private String prUrl;

    @Column(name = "pr_state", nullable = false)
    private String prState;

    @Column(name = "created_at")
    private Long createdAtEpochSecond;

    @Column(name = "merged_at")
    private Long mergedAtEpochSecond;

    @Column(name = "time_to_merge_seconds")
    private Long timeToMergeSeconds;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "comment_count")
    private Integer commentCount;

    @Column(name = "additions")
    private Integer additions;

    @Column(name = "deletions")
    private Integer deletions;

    @Column(name = "changed_files")
    private Integer changedFiles;

    protected CommitPullRequestEntity() {
    }

    public CommitPullRequestEntity(String id, String commitHash, String projectRef, int prNumber, String prTitle, String prUrl, String prState, Long createdAtEpochSecond, Long mergedAtEpochSecond, Long timeToMergeSeconds, Integer reviewCount, Integer commentCount, Integer additions, Integer deletions, Integer changedFiles) {
        this.id = id;
        this.commitHash = commitHash;
        this.projectRef = projectRef;
        this.prNumber = prNumber;
        this.prTitle = prTitle;
        this.prUrl = prUrl;
        this.prState = prState;
        this.createdAtEpochSecond = createdAtEpochSecond;
        this.mergedAtEpochSecond = mergedAtEpochSecond;
        this.timeToMergeSeconds = timeToMergeSeconds;
        this.reviewCount = reviewCount;
        this.commentCount = commentCount;
        this.additions = additions;
        this.deletions = deletions;
        this.changedFiles = changedFiles;
    }

    public String getId() {
        return id;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public String getProjectRef() {
        return projectRef;
    }

    public int getPrNumber() {
        return prNumber;
    }

    public String getPrTitle() {
        return prTitle;
    }

    public String getPrUrl() {
        return prUrl;
    }

    public String getPrState() {
        return prState;
    }

    public Long getCreatedAtEpochSecond() {
        return createdAtEpochSecond;
    }

    public Long getMergedAtEpochSecond() {
        return mergedAtEpochSecond;
    }

    public Long getTimeToMergeSeconds() {
        return timeToMergeSeconds;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public Integer getCommentCount() {
        return commentCount;
    }

    public Integer getAdditions() {
        return additions;
    }

    public Integer getDeletions() {
        return deletions;
    }

    public Integer getChangedFiles() {
        return changedFiles;
    }
}
