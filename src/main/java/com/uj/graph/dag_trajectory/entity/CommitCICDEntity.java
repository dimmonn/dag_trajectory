package com.uj.graph.dag_trajectory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "commit_ci_cd")
public class CommitCICDEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "commit_hash", nullable = false)
    private String commitHash;

    @Column(name = "project_ref", nullable = false)
    private String projectRef;

    @Column(name = "workflow_name", nullable = false)
    private String workflowName;

    @Column(name = "workflow_url", nullable = false)
    private String workflowUrl;

    @Column(name = "run_status", nullable = false)
    private String runStatus;

    @Column(name = "run_conclusion")
    private String runConclusion;

    @Column(name = "created_at")
    private Long createdAtEpochSecond;

    @Column(name = "completed_at")
    private Long completedAtEpochSecond;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "is_success")
    private Boolean isSuccess;

    @Column(name = "attempt_number")
    private Integer attemptNumber;

    @Column(name = "total_attempts")
    private Integer totalAttempts;

    protected CommitCICDEntity() {
    }

    public CommitCICDEntity(String id, String commitHash, String projectRef, String workflowName, String workflowUrl, String runStatus, String runConclusion, Long createdAtEpochSecond, Long completedAtEpochSecond, Long durationSeconds, Boolean isSuccess, Integer attemptNumber, Integer totalAttempts) {
        this.id = id;
        this.commitHash = commitHash;
        this.projectRef = projectRef;
        this.workflowName = workflowName;
        this.workflowUrl = workflowUrl;
        this.runStatus = runStatus;
        this.runConclusion = runConclusion;
        this.createdAtEpochSecond = createdAtEpochSecond;
        this.completedAtEpochSecond = completedAtEpochSecond;
        this.durationSeconds = durationSeconds;
        this.isSuccess = isSuccess;
        this.attemptNumber = attemptNumber;
        this.totalAttempts = totalAttempts;
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

    public String getWorkflowName() {
        return workflowName;
    }

    public String getWorkflowUrl() {
        return workflowUrl;
    }

    public String getRunStatus() {
        return runStatus;
    }

    public String getRunConclusion() {
        return runConclusion;
    }

    public Long getCreatedAtEpochSecond() {
        return createdAtEpochSecond;
    }

    public Long getCompletedAtEpochSecond() {
        return completedAtEpochSecond;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public Boolean getIsSuccess() {
        return isSuccess;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public Integer getTotalAttempts() {
        return totalAttempts;
    }
}
