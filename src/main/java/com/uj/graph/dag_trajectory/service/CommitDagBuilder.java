package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.model.CommitNode;
import com.uj.graph.dag_trajectory.model.GithubProjectAnalysis;
import com.uj.graph.dag_trajectory.model.GithubProjectRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.stereotype.Service;

@Service
public class CommitDagBuilder {

    private final CommitGraphMetricsCalculator metricsCalculator;

    public CommitDagBuilder(CommitGraphMetricsCalculator metricsCalculator) {
        this.metricsCalculator = metricsCalculator;
    }

    public GithubProjectAnalysis build(String projectRef, GithubProjectRequest project, Path clonedRepository) {
        try (Git git = Git.open(clonedRepository.toFile())) {
            List<CommitGraphMetricsCalculator.CommitSnapshot> snapshots = new ArrayList<>();
            Iterable<RevCommit> log = git.log().all().call();
            for (RevCommit commit : log) {
                List<String> parents = new ArrayList<>();
                for (RevCommit parent : commit.getParents()) {
                    parents.add(parent.getName());
                }
                snapshots.add(new CommitGraphMetricsCalculator.CommitSnapshot(commit.getName(), parents, commit.getCommitterIdent().getWhen().toInstant()));
            }
            List<CommitNode> commits = metricsCalculator.buildNodes(snapshots);
            return new GithubProjectAnalysis(projectRef, project.owner(), project.name(), clonedRepository.toString(), commits);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open cloned repository at " + clonedRepository, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build commit DAG for " + project.owner() + "/" + project.name(), e);
        }
    }
}
