package com.uj.graph.dag_trajectory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "commits",
        uniqueConstraints = @UniqueConstraint(name = "uk_commit_project_hash", columnNames = {"project_ref", "commit_hash"}))
public class CommitEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "project_ref", nullable = false)
    private String projectRef;

    @Column(name = "commit_hash", nullable = false)
    private String commitHash;

    // Legacy column retained for compatibility; historically populated with committer time.
    @Column(name = "authored_at")
    private Long authoredAtEpochSecond;

    @Column(name = "author_at")
    private Long authorAtEpochSecond;

    @Column(name = "committed_at")
    private Long committedAtEpochSecond;

    @Column(name = "parent_count")
    private Integer parentCount;

    @Column(name = "merge_parent_count")
    private Integer mergeParentCount;

    @Column(name = "is_merge")
    private Boolean merge;

    protected CommitEntity() {
    }

    public CommitEntity(
            String id,
            String projectRef,
            String commitHash,
            Long authoredAtEpochSecond,
            Integer parentCount,
            Integer mergeParentCount,
            Boolean merge) {
        this.id = id;
        this.projectRef = projectRef;
        this.commitHash = commitHash;
        this.authoredAtEpochSecond = authoredAtEpochSecond;
        this.committedAtEpochSecond = authoredAtEpochSecond;
        this.parentCount = parentCount;
        this.mergeParentCount = mergeParentCount;
        this.merge = merge;
    }

    public String getId() {
        return id;
    }

    public String getProjectRef() {
        return projectRef;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public Long getAuthoredAtEpochSecond() {
        return authoredAtEpochSecond;
    }

    public Long getAuthorAtEpochSecond() {
        return authorAtEpochSecond;
    }

    public Long getCommittedAtEpochSecond() {
        return committedAtEpochSecond;
    }

    public void setAuthorAtEpochSecond(Long authorAtEpochSecond) {
        this.authorAtEpochSecond = authorAtEpochSecond;
    }

    public void setCommittedAtEpochSecond(Long committedAtEpochSecond) {
        this.committedAtEpochSecond = committedAtEpochSecond;
    }

    public Integer getParentCount() {
        return parentCount;
    }

    public Integer getMergeParentCount() {
        return mergeParentCount;
    }

    public Boolean getMerge() {
        return merge;
    }

    public void setAuthoredAtEpochSecond(Long authoredAtEpochSecond) {
        this.authoredAtEpochSecond = authoredAtEpochSecond;
    }

    public void setParentCount(Integer parentCount) {
        this.parentCount = parentCount;
    }

    public void setMergeParentCount(Integer mergeParentCount) {
        this.mergeParentCount = mergeParentCount;
    }

    public void setMerge(Boolean merge) {
        this.merge = merge;
    }
}
