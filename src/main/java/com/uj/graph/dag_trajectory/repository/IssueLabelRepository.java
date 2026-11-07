package com.uj.graph.dag_trajectory.repository;

import com.uj.graph.dag_trajectory.entity.IssueLabelCategory;
import com.uj.graph.dag_trajectory.entity.IssueLabelEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueLabelRepository extends JpaRepository<IssueLabelEntity, String> {
    List<IssueLabelEntity> findByIssue_Id(String issueId);
    List<IssueLabelEntity> findAllByIssue_ProjectRefAndCategory(String projectRef, IssueLabelCategory category);
}
