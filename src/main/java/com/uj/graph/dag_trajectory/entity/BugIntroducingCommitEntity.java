package com.uj.graph.dag_trajectory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "bug_introducing_commits", uniqueConstraints = @UniqueConstraint(
        name = "uk_szz_project_issue_fix_bic",
        columnNames = {"project_ref", "issue_number", "fix_commit_hash", "bic_commit_hash"}))
public class BugIntroducingCommitEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "project_ref", nullable = false)
    private String projectRef;

    @Column(name = "issue_number", nullable = false)
    private int issueNumber;

    @Column(name = "fix_commit_hash", nullable = false, length = 40)
    private String fixCommitHash;

    @Column(name = "bic_commit_hash", nullable = false, length = 40)
    private String bicCommitHash;

    @Column(name = "file_path", nullable = false, columnDefinition = "text")
    private String filePath;

    @Column(name = "deleted_lines", nullable = false)
    private int deletedLines;

    @Column(name = "algorithm", nullable = false, length = 32)
    private String algorithm;

    @Column(name = "created_at_epoch_second", nullable = false)
    private long createdAtEpochSecond;

    protected BugIntroducingCommitEntity() {}

    public BugIntroducingCommitEntity(String id, String projectRef, int issueNumber,
            String fixCommitHash, String bicCommitHash, String filePath,
            int deletedLines, String algorithm, long createdAtEpochSecond) {
        this.id = id;
        this.projectRef = projectRef;
        this.issueNumber = issueNumber;
        this.fixCommitHash = fixCommitHash;
        this.bicCommitHash = bicCommitHash;
        this.filePath = filePath;
        this.deletedLines = deletedLines;
        this.algorithm = algorithm;
        this.createdAtEpochSecond = createdAtEpochSecond;
    }
}
