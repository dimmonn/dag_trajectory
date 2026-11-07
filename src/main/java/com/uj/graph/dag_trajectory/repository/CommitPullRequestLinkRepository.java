package com.uj.graph.dag_trajectory.repository;

import com.uj.graph.dag_trajectory.entity.CommitPullRequestLinkEntity;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommitPullRequestLinkRepository extends JpaRepository<CommitPullRequestLinkEntity, String> {
    boolean existsByCommit_IdAndPullRequest_Id(String commitId, String pullRequestId);

    @EntityGraph(attributePaths = {"pullRequest", "commit"})
    List<CommitPullRequestLinkEntity> findAllByPullRequest_ProjectRef(String projectRef);
}
