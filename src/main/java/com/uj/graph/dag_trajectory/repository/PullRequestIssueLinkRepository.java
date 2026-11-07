package com.uj.graph.dag_trajectory.repository;

import com.uj.graph.dag_trajectory.entity.PullRequestIssueLinkEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestIssueLinkRepository extends JpaRepository<PullRequestIssueLinkEntity, String> {
    boolean existsByPullRequest_IdAndIssue_Id(String pullRequestId, String issueId);
    long countByIssue_Id(String issueId);
    List<PullRequestIssueLinkEntity> findAllByIssue_ProjectRef(String projectRef);
}
