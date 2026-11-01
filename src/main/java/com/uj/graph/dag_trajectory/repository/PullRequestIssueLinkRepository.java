package com.uj.graph.dag_trajectory.repository;

import com.uj.graph.dag_trajectory.entity.PullRequestIssueLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestIssueLinkRepository extends JpaRepository<PullRequestIssueLinkEntity, String> {
    boolean existsByPullRequest_IdAndIssue_Id(String pullRequestId, String issueId);
    long countByIssue_Id(String issueId);
}
