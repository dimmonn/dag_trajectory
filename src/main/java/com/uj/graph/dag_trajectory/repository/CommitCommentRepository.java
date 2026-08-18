package com.uj.graph.dag_trajectory.repository;

import com.uj.graph.dag_trajectory.entity.CommitCommentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommitCommentRepository extends JpaRepository<CommitCommentEntity, String> {
    List<CommitCommentEntity> findByCommitHashAndProjectRef(String commitHash, String projectRef);
    List<CommitCommentEntity> findByProjectRef(String projectRef);
    boolean existsByProjectRefAndCommitHashAndCommentUrl(String projectRef, String commitHash, String commentUrl);
}
