package com.uj.graph.dag_trajectory.repository;

import com.uj.graph.dag_trajectory.entity.CommitIssueEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommitIssueRepository extends JpaRepository<CommitIssueEntity, String> {
    List<CommitIssueEntity> findByCommitHashAndProjectRef(String commitHash, String projectRef);
    List<CommitIssueEntity> findByProjectRef(String projectRef);
}
