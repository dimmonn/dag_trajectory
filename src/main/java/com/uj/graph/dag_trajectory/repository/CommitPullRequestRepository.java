package com.uj.graph.dag_trajectory.repository;

import com.uj.graph.dag_trajectory.entity.CommitPullRequestEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommitPullRequestRepository extends JpaRepository<CommitPullRequestEntity, String> {
    List<CommitPullRequestEntity> findByCommitHashAndProjectRef(String commitHash, String projectRef);
    List<CommitPullRequestEntity> findByProjectRef(String projectRef);
}
