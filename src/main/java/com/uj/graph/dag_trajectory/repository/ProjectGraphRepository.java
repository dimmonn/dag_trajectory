package com.uj.graph.dag_trajectory.repository;

import com.uj.graph.dag_trajectory.entity.ProjectGraphEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectGraphRepository extends JpaRepository<ProjectGraphEntity, String> {
}
