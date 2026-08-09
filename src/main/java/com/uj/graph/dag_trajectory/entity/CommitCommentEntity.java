package com.uj.graph.dag_trajectory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "commit_comments")
public class CommitCommentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "commit_hash", nullable = false)
    private String commitHash;

    @Column(name = "project_ref", nullable = false)
    private String projectRef;

    @Column(name = "comment_url", nullable = false)
    private String commentUrl;

    @Column(name = "comment_body", columnDefinition = "text")
    private String commentBody;

    @Column(name = "author_login", nullable = false)
    private String authorLogin;

    @Column(name = "created_at")
    private Long createdAtEpochSecond;

    @Column(name = "related_pr_number")
    private Integer relatedPrNumber;

    @Column(name = "related_issue_number")
    private Integer relatedIssueNumber;

    @Column(name = "body_length")
    private Integer bodyLength;

    @Column(name = "sentiment_score")
    private Double sentimentScore;

    @Column(name = "is_bot_comment")
    private Boolean isBotComment;

    protected CommitCommentEntity() {
    }

    public CommitCommentEntity(String id, String commitHash, String projectRef, String commentUrl, String commentBody, String authorLogin, Long createdAtEpochSecond, Integer relatedPrNumber, Integer relatedIssueNumber, Integer bodyLength, Double sentimentScore, Boolean isBotComment) {
        this.id = id;
        this.commitHash = commitHash;
        this.projectRef = projectRef;
        this.commentUrl = commentUrl;
        this.commentBody = commentBody;
        this.authorLogin = authorLogin;
        this.createdAtEpochSecond = createdAtEpochSecond;
        this.relatedPrNumber = relatedPrNumber;
        this.relatedIssueNumber = relatedIssueNumber;
        this.bodyLength = bodyLength;
        this.sentimentScore = sentimentScore;
        this.isBotComment = isBotComment;
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

    public String getCommentUrl() {
        return commentUrl;
    }

    public String getCommentBody() {
        return commentBody;
    }

    public String getAuthorLogin() {
        return authorLogin;
    }

    public Long getCreatedAtEpochSecond() {
        return createdAtEpochSecond;
    }

    public Integer getRelatedPrNumber() {
        return relatedPrNumber;
    }

    public Integer getRelatedIssueNumber() {
        return relatedIssueNumber;
    }

    public Integer getBodyLength() {
        return bodyLength;
    }

    public Double getSentimentScore() {
        return sentimentScore;
    }

    public Boolean getIsBotComment() {
        return isBotComment;
    }
}

