package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.model.CommitNode;
import com.uj.graph.dag_trajectory.model.GithubProjectAnalysis;
import com.uj.graph.dag_trajectory.model.GithubProjectRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.stereotype.Service;

@Service
public class CommitDagBuilder {

    private final CommitGraphMetricsCalculator metricsCalculator;

    public CommitDagBuilder(CommitGraphMetricsCalculator metricsCalculator) {
        this.metricsCalculator = metricsCalculator;
    }

    public GithubProjectAnalysis build(String projectRef, GithubProjectRequest project, Path clonedRepository) {
        return build(projectRef, project, clonedRepository, null);
    }

    public GithubProjectAnalysis build(String projectRef, GithubProjectRequest project, Path clonedRepository, String defaultBranch) {
        try (Git git = Git.open(clonedRepository.toFile())) {
            List<CommitGraphMetricsCalculator.CommitSnapshot> snapshots = new ArrayList<>();
            ObjectId defaultBranchHeadId;
            if (defaultBranch != null && !defaultBranch.isBlank()) {
                String branchRef = Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + defaultBranch;
                defaultBranchHeadId = git.getRepository().resolve(branchRef);
                if (defaultBranchHeadId == null) {
                    throw new IllegalStateException("Upstream default branch '" + defaultBranch
                            + "' is missing from clone " + clonedRepository);
                }
            } else {
                defaultBranchHeadId = git.getRepository().resolve(
                        Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + Constants.HEAD);
                if (defaultBranchHeadId == null) defaultBranchHeadId = git.getRepository().resolve(Constants.HEAD);
                if (defaultBranchHeadId == null) throw new IllegalStateException("Repository has no default-branch HEAD: " + clonedRepository);
            }
            RevCommit defaultBranchHead = git.getRepository().parseCommit(defaultBranchHeadId);
            Iterable<RevCommit> log = git.log().add(defaultBranchHead).call();
            for (RevCommit commit : log) {
                List<String> parents = new ArrayList<>();
                for (RevCommit parent : commit.getParents()) {
                    parents.add(parent.getName());
                }
                snapshots.add(new CommitGraphMetricsCalculator.CommitSnapshot(
                        commit.getName(),
                        parents,
                        commit.getAuthorIdent().getWhen().toInstant(),
                        commit.getCommitterIdent().getWhen().toInstant()));
            }
            List<CommitNode> commits = metricsCalculator.buildNodes(snapshots);
            return new GithubProjectAnalysis(
                    projectRef,
                    project.owner(),
                    project.name(),
                    clonedRepository.toString(),
                    commits,
                    defaultBranch,
                    defaultBranchHeadId.name(),
                    Instant.now().getEpochSecond());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open cloned repository at " + clonedRepository, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build commit DAG for " + project.owner() + "/" + project.name(), e);
        }
    }
}
