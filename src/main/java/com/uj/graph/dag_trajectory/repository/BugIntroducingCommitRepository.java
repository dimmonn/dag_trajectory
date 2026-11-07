package com.uj.graph.dag_trajectory.repository;

import com.uj.graph.dag_trajectory.entity.BugIntroducingCommitEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BugIntroducingCommitRepository extends JpaRepository<BugIntroducingCommitEntity, String> {
    List<BugIntroducingCommitEntity> findAllByProjectRef(String projectRef);
    void deleteAllByProjectRef(String projectRef);
}
