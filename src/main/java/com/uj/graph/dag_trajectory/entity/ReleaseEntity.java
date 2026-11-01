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
        name = "releases",
        uniqueConstraints = @UniqueConstraint(name = "uk_release_project_github_id", columnNames = {"project_ref", "github_release_id"}))
public class ReleaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "project_ref", nullable = false)
    private String projectRef;

    @Column(name = "github_release_id", nullable = false)
    private String githubReleaseId;

    @Column(name = "release_name")
    private String releaseName;

    @Column(name = "tag_name", nullable = false)
    private String tagName;

    @Column(name = "release_url", nullable = false)
    private String releaseUrl;

    @Column(name = "published_at")
    private Long publishedAtEpochSecond;

    @Column(name = "created_at")
    private Long createdAtEpochSecond;

    @Column(name = "is_draft")
    private Boolean draft;

    @Column(name = "is_prerelease")
    private Boolean prerelease;

    @Column(name = "author_login")
    private String authorLogin;

    @Column(name = "days_since_previous_release")
    private Long daysSincePreviousRelease;

    @Column(name = "commits_since_previous_release")
    private Long commitsSincePreviousRelease;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commit_id", foreignKey = @ForeignKey(name = "fk_release_commit"))
    private CommitEntity commit;

    protected ReleaseEntity() {
    }

    public ReleaseEntity(String id, String projectRef, String githubReleaseId) {
        this.id = id;
        this.projectRef = projectRef;
        this.githubReleaseId = githubReleaseId;
    }

    public void setReleaseName(String releaseName) {
        this.releaseName = releaseName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public void setReleaseUrl(String releaseUrl) {
        this.releaseUrl = releaseUrl;
    }

    public void setPublishedAtEpochSecond(Long publishedAtEpochSecond) {
        this.publishedAtEpochSecond = publishedAtEpochSecond;
    }

    public void setCreatedAtEpochSecond(Long createdAtEpochSecond) {
        this.createdAtEpochSecond = createdAtEpochSecond;
    }

    public void setDraft(Boolean draft) {
        this.draft = draft;
    }

    public void setPrerelease(Boolean prerelease) {
        this.prerelease = prerelease;
    }

    public void setAuthorLogin(String authorLogin) {
        this.authorLogin = authorLogin;
    }

    public void setDaysSincePreviousRelease(Long daysSincePreviousRelease) {
        this.daysSincePreviousRelease = daysSincePreviousRelease;
    }

    public void setCommitsSincePreviousRelease(Long commitsSincePreviousRelease) {
        this.commitsSincePreviousRelease = commitsSincePreviousRelease;
    }

    public void setCommit(CommitEntity commit) {
        this.commit = commit;
    }
}
