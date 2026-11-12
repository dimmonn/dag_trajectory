package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.model.GithubProjectRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.TimeZone;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void includesOnlyDefaultBranchReachableCommitsIncludingIntegratedForkContributions() throws Exception {
        Path repoDir = tempDir.resolve("upstream");
        String defaultCommitHash;
        String forkContributionHash;
        String unmergedForkCommitHash;
        try (Git git = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call()) {
            defaultCommitHash = commitAt(git, repoDir, "upstream base", Instant.parse("2026-08-09T10:00:00Z")).getName();

            git.checkout()
                    .setCreateBranch(true)
                    .setName("fork-pr")
                    .setStartPoint(defaultCommitHash)
                    .call();
            forkContributionHash = commitAt(git, repoDir, "fork contribution", Instant.parse("2026-08-09T11:00:00Z")).getName();

            git.checkout()
                    .setCreateBranch(true)
                    .setName("unmerged-fork")
                    .setStartPoint(defaultCommitHash)
                    .call();
            unmergedForkCommitHash = commitAt(git, repoDir, "unmerged fork contribution", Instant.parse("2026-08-09T12:00:00Z")).getName();
            git.tag().setName("unrelated-tag").call();

            git.checkout().setName("main").call();
            git.merge().include(git.getRepository().resolve(forkContributionHash)).setMessage("Merge fork PR").call();
            var originMain = git.getRepository().updateRef("refs/remotes/origin/main");
            originMain.setNewObjectId(git.getRepository().resolve("HEAD"));
            originMain.update();
            git.getRepository().updateRef("refs/remotes/origin/HEAD").link("refs/remotes/origin/main");
            git.checkout().setName("unmerged-fork").call();
        }

        var analysis = new CommitDagBuilder(new CommitGraphMetricsCalculator())
                .build("ref-1", new GithubProjectRequest("upstream-owner", "repo"), repoDir);
        Set<String> hashes = analysis.commits().stream().map(commit -> commit.hash()).collect(java.util.stream.Collectors.toSet());

        assertTrue(hashes.contains(defaultCommitHash));
        assertTrue(hashes.contains(forkContributionHash));
        assertTrue(!hashes.contains(unmergedForkCommitHash));
    }


    @Test
    void recordsDistinctGitAuthorAndCommitterTimesAndCapturedHead() throws Exception {
        Path repoDir = tempDir.resolve("timestamp-repo");
        String headHash;
        Instant authorTime = Instant.parse("2026-08-09T08:00:00Z");
        Instant committerTime = Instant.parse("2026-08-09T10:00:00Z");
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            Files.writeString(repoDir.resolve("README.md"), "timestamp test");
            git.add().addFilepattern("README.md").call();
            PersonIdent author = new PersonIdent("Author", "author@example.com", Date.from(authorTime), TimeZone.getTimeZone("UTC"));
            PersonIdent committer = new PersonIdent("Committer", "committer@example.com", Date.from(committerTime), TimeZone.getTimeZone("UTC"));
            headHash = git.commit().setMessage("timestamp test").setAuthor(author).setCommitter(committer).call().getName();
        }

        var analysis = new CommitDagBuilder(new CommitGraphMetricsCalculator())
                .build("ref-time", new GithubProjectRequest("octocat", "timestamp-repo"), repoDir);
        var commit = analysis.commits().getFirst();

        assertEquals(authorTime.getEpochSecond(), commit.authoredAtEpochSecond());
        assertEquals(committerTime.getEpochSecond(), commit.committedAtEpochSecond());
        assertEquals(headHash, analysis.headSha());
        assertTrue(analysis.analyzedAtEpochSecond() > 0);
    }


    @Test
    void parsesCommitTimestampsWithoutValidatingMalformedTimezoneOffsets() {
        byte[] rawCommit = ("tree 0123456789012345678901234567890123456789\\n"
                + "author A <a@example.com> 1786262400 +51800\\n"
                + "committer C <c@example.com> 1786269600 +0000\\n"
                + "\\nmessage\\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        assertEquals(
                Instant.ofEpochSecond(1786262400L),
                CommitDagBuilder.parseIdentityTimestamp(rawCommit, "author"));
        assertEquals(
                Instant.ofEpochSecond(1786269600L),
                CommitDagBuilder.parseIdentityTimestamp(rawCommit, "committer"));
        assertThrows(
                IllegalArgumentException.class,
                () -> CommitDagBuilder.parseIdentityTimestamp(rawCommit, "tagger"));
    }

    private static org.eclipse.jgit.revwalk.RevCommit commitAt(Git git, Path repoDir, String message, Instant when) throws Exception {
        Files.writeString(repoDir.resolve("README.md"), message);
        git.add().addFilepattern("README.md").call();
        PersonIdent ident = new PersonIdent(
                "A",
                "a@example.com",
                Date.from(when),
                TimeZone.getTimeZone("UTC"));
        return git.commit()
                .setMessage(message)
                .setAuthor(ident)
                .setCommitter(ident)
                .call();
    }
}
