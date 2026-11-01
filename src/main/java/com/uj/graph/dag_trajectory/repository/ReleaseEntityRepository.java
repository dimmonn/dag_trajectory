package com.uj.graph.dag_trajectory.repository;

import com.uj.graph.dag_trajectory.entity.ReleaseEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReleaseEntityRepository extends JpaRepository<ReleaseEntity, String> {
    Optional<ReleaseEntity> findByProjectRefAndGithubReleaseId(String projectRef, String githubReleaseId);
}
