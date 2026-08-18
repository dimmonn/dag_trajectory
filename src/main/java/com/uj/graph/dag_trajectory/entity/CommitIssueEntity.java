package com.uj.graph.dag_trajectory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "commit_issues")
public class CommitIssueEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "commit_hash", nullable = false)
    private String commitHash;

    @Column(name = "project_ref", nullable = false)
    private String projectRef;

    @Column(name = "issue_number", nullable = false)
    private int issueNumber;

    @Column(name = "issue_title", nullable = false, columnDefinition = "text")
    private String issueTitle;

    @Column(name = "issue_url", nullable = false, columnDefinition = "text")
    private String issueUrl;

    @Column(name = "issue_state", nullable = false)
    private String issueState;

    @Column(name = "created_at")
    private Long createdAtEpochSecond;

    @Column(name = "closed_at")
    private Long closedAtEpochSecond;

    @Column(name = "time_to_close_seconds")
    private Long timeToCloseSeconds;

    @Column(name = "comment_count")
    private Integer commentCount;

    @Column(name = "related_pr_count")
    private Integer relatedPrCount;

    @Column(name = "is_resolved")
    private Boolean isResolved;

    @Column(name = "label_count")
    private Integer labelCount;

    protected CommitIssueEntity() {
    }

    public CommitIssueEntity(String id, String commitHash, String projectRef, int issueNumber, String issueTitle, String issueUrl, String issueState, Long createdAtEpochSecond, Long closedAtEpochSecond, Long timeToCloseSeconds, Integer commentCount, Integer relatedPrCount, Boolean isResolved, Integer labelCount) {
        this.id = id;
        this.commitHash = commitHash;
        this.projectRef = projectRef;
        this.issueNumber = issueNumber;
        this.issueTitle = issueTitle;
        this.issueUrl = issueUrl;
        this.issueState = issueState;
        this.createdAtEpochSecond = createdAtEpochSecond;
        this.closedAtEpochSecond = closedAtEpochSecond;
        this.timeToCloseSeconds = timeToCloseSeconds;
        this.commentCount = commentCount;
        this.relatedPrCount = relatedPrCount;
        this.isResolved = isResolved;
        this.labelCount = labelCount;
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

    public int getIssueNumber() {
        return issueNumber;
    }

    public String getIssueTitle() {
        return issueTitle;
    }

    public String getIssueUrl() {
        return issueUrl;
    }

    public String getIssueState() {
        return issueState;
    }

    public Long getCreatedAtEpochSecond() {
        return createdAtEpochSecond;
    }

    public Long getClosedAtEpochSecond() {
        return closedAtEpochSecond;
    }

    public Long getTimeToCloseSeconds() {
        return timeToCloseSeconds;
    }

    public Integer getCommentCount() {
        return commentCount;
    }

    public Integer getRelatedPrCount() {
        return relatedPrCount;
    }

    public Boolean getIsResolved() {
        return isResolved;
    }

    public Integer getLabelCount() {
        return labelCount;
    }
}
