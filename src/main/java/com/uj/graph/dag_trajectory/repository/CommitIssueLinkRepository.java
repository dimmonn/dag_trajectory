package com.uj.graph.dag_trajectory.repository;

import com.uj.graph.dag_trajectory.entity.CommitIssueLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommitIssueLinkRepository extends JpaRepository<CommitIssueLinkEntity, String> {
    boolean existsByCommit_IdAndIssue_Id(String commitId, String issueId);
}
