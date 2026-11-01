package com.uj.graph.dag_trajectory.repository;

import com.uj.graph.dag_trajectory.entity.PullRequestEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestEntityRepository extends JpaRepository<PullRequestEntity, String> {
    Optional<PullRequestEntity> findByProjectRefAndPrNumber(String projectRef, int prNumber);
}
