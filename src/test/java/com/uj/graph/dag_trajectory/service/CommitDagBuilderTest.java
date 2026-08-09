package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.model.GithubProjectRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitDagBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsFullGraphWithMetrics() throws Exception {
        Path repoDir = tempDir.resolve("repo");
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            commitAt(git, repoDir, "first", Instant.parse("2026-08-09T10:00:00Z"));
            commitAt(git, repoDir, "second", Instant.parse("2026-08-09T11:00:00Z"));
            commitAt(git, repoDir, "third", Instant.parse("2026-08-09T12:00:00Z"));
        }

        var analysis = new CommitDagBuilder(new CommitGraphMetricsCalculator()).build("ref-1", new GithubProjectRequest("octocat", "dag"), repoDir);

        assertEquals(3, analysis.commits().size());
        assertTrue(analysis.commits().stream().anyMatch(commit -> commit.metrics().graphDepth() == 2));
        assertTrue(analysis.commits().stream().anyMatch(commit -> commit.metrics().ancestorCount() >= 1));
        assertTrue(analysis.commits().stream().anyMatch(commit -> commit.metrics().commitsIn7d() >= 1));
        assertTrue(analysis.commits().stream().allMatch(commit -> !commit.metrics().branchPoint()));
    }

    private static void commitAt(Git git, Path repoDir, String message, Instant when) throws Exception {
        Files.writeString(repoDir.resolve("README.md"), message);
        git.add().addFilepattern("README.md").call();
        PersonIdent ident = new PersonIdent(
                "A",
                "a@example.com",
                Date.from(when),
                TimeZone.getTimeZone("UTC"));
        git.commit()
                .setMessage(message)
                .setAuthor(ident)
                .setCommitter(ident)
                .call();
    }
}
