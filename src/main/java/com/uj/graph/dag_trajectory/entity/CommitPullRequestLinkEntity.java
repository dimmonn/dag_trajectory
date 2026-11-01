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
        name = "commit_pull_request_links",
        uniqueConstraints = @UniqueConstraint(name = "uk_commit_pr_link", columnNames = {"commit_id", "pull_request_id"}))
public class CommitPullRequestLinkEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commit_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commit_pr_link_commit"))
    private CommitEntity commit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pull_request_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commit_pr_link_pr"))
    private PullRequestEntity pullRequest;

    protected CommitPullRequestLinkEntity() {
    }

    public CommitPullRequestLinkEntity(String id, CommitEntity commit, PullRequestEntity pullRequest) {
        this.id = id;
        this.commit = commit;
        this.pullRequest = pullRequest;
    }
}
