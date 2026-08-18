package com.uj.graph.dag_trajectory.repository;

import com.uj.graph.dag_trajectory.entity.CommitCICDEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommitCICDRepository extends JpaRepository<CommitCICDEntity, String> {
    List<CommitCICDEntity> findByCommitHashAndProjectRef(String commitHash, String projectRef);
    List<CommitCICDEntity> findByProjectRef(String projectRef);
    boolean existsByProjectRefAndCommitHashAndWorkflowUrl(String projectRef, String commitHash, String workflowUrl);
}
