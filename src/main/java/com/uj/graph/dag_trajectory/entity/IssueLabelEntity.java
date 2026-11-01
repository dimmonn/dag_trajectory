package com.uj.graph.dag_trajectory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "issue_labels",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_issue_label_name",
                columnNames = {"issue_id", "label_name"}))
public class IssueLabelEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", referencedColumnName = "id", nullable = false)
    private IssueEntity issue;

    @Column(name = "label_name", nullable = false, columnDefinition = "text")
    private String labelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private IssueLabelCategory category;

    protected IssueLabelEntity() {
    }

    public IssueLabelEntity(String id, IssueEntity issue, String labelName, IssueLabelCategory category) {
        this.id = id;
        this.issue = issue;
        this.labelName = labelName;
        this.category = category;
    }

    public String getId() {
        return id;
    }

    public IssueEntity getIssue() {
        return issue;
    }

    public String getLabelName() {
        return labelName;
    }

    public IssueLabelCategory getCategory() {
        return category;
    }

    public void setCategory(IssueLabelCategory category) {
        this.category = category;
    }
}
