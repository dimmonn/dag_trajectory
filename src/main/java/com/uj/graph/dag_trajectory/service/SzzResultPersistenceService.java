package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.entity.BugIntroducingCommitEntity;
import com.uj.graph.dag_trajectory.repository.BugIntroducingCommitRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SzzResultPersistenceService {
    private final BugIntroducingCommitRepository repository;

    public SzzResultPersistenceService(BugIntroducingCommitRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void replaceProjectResults(String projectRef, List<BugIntroducingCommitEntity> results) {
        repository.deleteAllByProjectRef(projectRef);
        repository.saveAll(results);
    }
}
