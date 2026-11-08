package com.uj.graph.dag_trajectory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "pull_request_issue_links",
        uniqueConstraints = @UniqueConstraint(name = "uk_pr_issue_link", columnNames = {"pull_request_id", "issue_id"}))
public class PullRequestIssueLinkEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pull_request_id", nullable = false, foreignKey = @ForeignKey(name = "fk_pr_issue_link_pr"))
    private PullRequestEntity pullRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false, foreignKey = @ForeignKey(name = "fk_pr_issue_link_issue"))
    private IssueEntity issue;

    protected PullRequestIssueLinkEntity() {
    }

    public PullRequestIssueLinkEntity(String id, PullRequestEntity pullRequest, IssueEntity issue) {
        this.id = id;
        this.pullRequest = pullRequest;
        this.issue = issue;
    }

    public PullRequestEntity getPullRequest() { return pullRequest; }
    public IssueEntity getIssue() { return issue; }
}
