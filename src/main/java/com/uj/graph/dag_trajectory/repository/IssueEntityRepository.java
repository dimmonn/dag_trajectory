package com.uj.graph.dag_trajectory.repository;

import com.uj.graph.dag_trajectory.entity.IssueEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueEntityRepository extends JpaRepository<IssueEntity, String> {
    Optional<IssueEntity> findByProjectRefAndIssueNumber(String projectRef, int issueNumber);
}
