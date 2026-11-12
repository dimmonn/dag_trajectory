package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.model.CommitNode;
import com.uj.graph.dag_trajectory.model.GithubProjectAnalysis;
import com.uj.graph.dag_trajectory.model.GithubProjectRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
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


    /**
     * Reads the epoch-second field directly from a commit identity header.
     *
     * Do not use RevCommit.getAuthorIdent()/getCommitterIdent() here: PersonIdent
     * validates the timezone offset while constructing the identity, and real-world
     * repositories can contain malformed offsets (for example, +51800). The commit
     * timestamp itself remains parseable and is independent of that offset.
     */
    static Instant parseIdentityTimestamp(byte[] rawCommit, String identityHeader) {
        byte[] prefix = (identityHeader + " ").getBytes(StandardCharsets.US_ASCII);
        int lineStart = 0;
        while (lineStart < rawCommit.length) {
            int lineEnd = lineStart;
            while (lineEnd < rawCommit.length && rawCommit[lineEnd] != '\n' && rawCommit[lineEnd] != '\r') {
                lineEnd++;
            }
            boolean matches = lineEnd - lineStart >= prefix.length;
            for (int i = 0; matches && i < prefix.length; i++) {
                matches = rawCommit[lineStart + i] == prefix[i];
            }
            if (matches) {
                int timezoneSeparator = lastWhitespace(rawCommit, lineStart, lineEnd);
                if (timezoneSeparator < 0) {
                    break;
                }
                int timestampSeparator = lastWhitespace(rawCommit, lineStart, timezoneSeparator);
                if (timestampSeparator < 0 || timestampSeparator + 1 >= timezoneSeparator) {
                    break;
                }
                String epochSecond = new String(
                        rawCommit,
                        timestampSeparator + 1,
                        timezoneSeparator - timestampSeparator - 1,
                        StandardCharsets.US_ASCII);
                try {
                    return Instant.ofEpochSecond(Long.parseLong(epochSecond));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Invalid epoch-second value in Git " + identityHeader + " header: " + epochSecond, e);
                }
            }
            lineStart = lineEnd + 1;
        }
        throw new IllegalArgumentException("Missing or invalid Git " + identityHeader + " identity header");
    }

    private static int lastWhitespace(byte[] value, int start, int endExclusive) {
        for (int i = endExclusive - 1; i >= start; i--) {
            if (value[i] == ' ' || value[i] == '\t') {
                return i;
            }
        }
        return -1;
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
                        parseIdentityTimestamp(commit.getRawBuffer(), "author"),
                        parseIdentityTimestamp(commit.getRawBuffer(), "committer")));
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
