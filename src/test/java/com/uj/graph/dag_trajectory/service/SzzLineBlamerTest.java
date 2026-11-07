package com.uj.graph.dag_trajectory.service;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SzzLineBlamerTest {

    @TempDir
    Path tempDir;

    @Test
    void tracesDeletedFixLinesToTheirIntroducingCommit() throws Exception {
        Path repositoryPath = tempDir.resolve("repo");
        String introducingCommit;
        String fixingCommit;

        try (Git git = Git.init().setDirectory(repositoryPath.toFile()).call()) {
            introducingCommit = commitFile(git, repositoryPath,
                    "class Example {\n    int faulty = 1;\n    int stable = 1;\n}",
                    "introduce faulty line", "2020-01-01T10:00:00Z").getName();

            commitFile(git, repositoryPath,
                    "class Example {\n    int faulty = 1;\n    int stable = 2;\n}",
                    "unrelated change", "2020-01-02T10:00:00Z");

            fixingCommit = commitFile(git, repositoryPath,
                    "class Example {\n    int stable = 2;\n}",
                    "remove faulty line", "2020-01-03T10:00:00Z").getName();
        }

        var result = new SzzLineBlamer().blame(repositoryPath, fixingCommit);

        assertFalse(result.skippedNonSingleParentCommit());
        assertEquals(1, result.findings().size());
        assertEquals(introducingCommit, result.findings().getFirst().bicCommitHash());
        assertEquals("Example.java", result.findings().getFirst().filePath());
        assertEquals(1, result.findings().getFirst().deletedLines());
        assertEquals(Instant.parse("2020-01-01T10:00:00Z").getEpochSecond(),
                result.findings().getFirst().bicCommitEpochSecond());
    }

    @Test
    void skipsMergeFixingCommitsRatherThanChoosingAnAmbiguousParent() throws Exception {
        Path repositoryPath = tempDir.resolve("merge-repo");
        String mergeCommit;
        try (Git git = Git.init().setDirectory(repositoryPath.toFile()).setInitialBranch("main").call()) {
            String base = commitPath(git, repositoryPath, "base.txt", "base\n",
                    "base", "2020-01-01T10:00:00Z").getName();
            git.checkout().setCreateBranch(true).setName("side").setStartPoint(base).call();
            commitPath(git, repositoryPath, "side.txt", "side\n", "side change", "2020-01-02T10:00:00Z");
            git.checkout().setName("main").call();
            commitPath(git, repositoryPath, "main.txt", "main\n", "main change", "2020-01-03T10:00:00Z");
            var merged = git.merge().include(git.getRepository().resolve("refs/heads/side"))
                    .setMessage("merge side").call();
            assertTrue(merged.getMergeStatus().isSuccessful());
            mergeCommit = git.getRepository().resolve("HEAD").getName();
        }

        var result = new SzzLineBlamer().blame(repositoryPath, mergeCommit);

        assertTrue(result.skippedNonSingleParentCommit());
        assertTrue(result.findings().isEmpty());
    }

    private static org.eclipse.jgit.revwalk.RevCommit commitFile(
            Git git, Path repositoryPath, String content, String message, String timestamp) throws Exception {
        return commitPath(git, repositoryPath, "Example.java", content, message, timestamp);
    }

    private static org.eclipse.jgit.revwalk.RevCommit commitPath(
            Git git, Path repositoryPath, String fileName, String content, String message, String timestamp) throws Exception {
        Files.writeString(repositoryPath.resolve(fileName), content);
        git.add().addFilepattern(fileName).call();
        Instant instant = Instant.parse(timestamp);
        PersonIdent ident = new PersonIdent("Test", "test@example.com", Date.from(instant), TimeZone.getTimeZone("UTC"));
        return git.commit().setMessage(message).setAuthor(ident).setCommitter(ident).call();
    }
}
