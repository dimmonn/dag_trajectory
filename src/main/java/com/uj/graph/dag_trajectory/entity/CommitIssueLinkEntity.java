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
        name = "commit_issue_links",
        uniqueConstraints = @UniqueConstraint(name = "uk_commit_issue_link", columnNames = {"commit_id", "issue_id"}))
public class CommitIssueLinkEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commit_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commit_issue_link_commit"))
    private CommitEntity commit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false, foreignKey = @ForeignKey(name = "fk_commit_issue_link_issue"))
    private IssueEntity issue;

    protected CommitIssueLinkEntity() {
    }

    public CommitIssueLinkEntity(String id, CommitEntity commit, IssueEntity issue) {
        this.id = id;
        this.commit = commit;
        this.issue = issue;
    }

    public CommitEntity getCommit() {
        return commit;
    }

    public IssueEntity getIssue() {
        return issue;
    }
}
