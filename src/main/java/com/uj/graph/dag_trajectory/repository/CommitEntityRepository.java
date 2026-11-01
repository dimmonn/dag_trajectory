package com.uj.graph.dag_trajectory.repository;

import com.uj.graph.dag_trajectory.entity.CommitEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommitEntityRepository extends JpaRepository<CommitEntity, String> {
    Optional<CommitEntity> findByProjectRefAndCommitHash(String projectRef, String commitHash);
    List<CommitEntity> findByProjectRef(String projectRef);
    long countByProjectRefAndAuthoredAtEpochSecondLessThanEqual(String projectRef, Long authoredAtEpochSecond);
    long countByProjectRefAndAuthoredAtEpochSecondGreaterThanAndAuthoredAtEpochSecondLessThanEqual(
            String projectRef, Long lowerExclusive, Long upperInclusive);
}
