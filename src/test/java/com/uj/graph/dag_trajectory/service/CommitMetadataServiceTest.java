package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.entity.CommitEntity;
import com.uj.graph.dag_trajectory.entity.CommitIssueLinkEntity;
import com.uj.graph.dag_trajectory.entity.CommitPullRequestEntity;
import com.uj.graph.dag_trajectory.entity.CommitPullRequestLinkEntity;
import com.uj.graph.dag_trajectory.entity.IssueEntity;
import com.uj.graph.dag_trajectory.entity.PullRequestEntity;
import com.uj.graph.dag_trajectory.repository.CommitCICDRepository;
import com.uj.graph.dag_trajectory.repository.CommitCommentRepository;
import com.uj.graph.dag_trajectory.repository.CommitEntityRepository;
import com.uj.graph.dag_trajectory.repository.CommitIssueLinkRepository;
import com.uj.graph.dag_trajectory.repository.CommitIssueRepository;
import com.uj.graph.dag_trajectory.repository.CommitPullRequestLinkRepository;
import com.uj.graph.dag_trajectory.repository.CommitPullRequestRepository;
import com.uj.graph.dag_trajectory.repository.IssueEntityRepository;
import com.uj.graph.dag_trajectory.repository.IssueLabelRepository;
import com.uj.graph.dag_trajectory.repository.PullRequestEntityRepository;
import com.uj.graph.dag_trajectory.repository.PullRequestIssueLinkRepository;
import com.uj.graph.dag_trajectory.repository.ReleaseEntityRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommitMetadataServiceTest {

    @Test
    void storesOpenAndClosedUnmergedPullRequestsWithoutCommitLinks() {
        Fixture fixture = new Fixture(List.of(commit("in-upstream", 1)));
        when(fixture.githubClient.getPullRequestsForProject("upstream", "repo")).thenReturn(List.of(
                pr(1, "OPEN", null, false, null, List.of("in-upstream")),
                pr(2, "CLOSED", null, false, null, List.of("in-upstream"))));

        fixture.persist(Set.of("in-upstream"));

        ArgumentCaptor<PullRequestEntity> prs = ArgumentCaptor.forClass(PullRequestEntity.class);
        verify(fixture.pullRequestRepository, times(2)).save(prs.capture());
        assertEquals(List.of("OPEN", "CLOSED"), prs.getAllValues().stream().map(PullRequestEntity::getPrState).toList());
        assertNull(prs.getAllValues().get(1).getMergedAtEpochSecond());
        verify(fixture.pullRequestIssueLinkRepository, times(2)).save(any());
        verify(fixture.commitPrLinkRepository, never()).save(any());
        verify(fixture.commitIssueLinkRepository, never()).save(any());
        verify(fixture.commitPrRepository, never()).save(any());
        verify(fixture.commitIssueRepository, never()).save(any());
        verify(fixture.commitRepository, never()).save(any());
    }

    @Test
    void linksOnlyMatchedUpstreamCommitsForClosedMergedPullRequests() {
        Fixture fixture = new Fixture(List.of(commit("integrated-from-fork", 1)));
        when(fixture.githubClient.getPullRequestsForProject("upstream", "repo")).thenReturn(List.of(
                pr(3, "CLOSED", "2026-10-01T12:00:00Z", true, null,
                        List.of("integrated-from-fork", "commit-not-in-upstream"))));

        fixture.persist(Set.of("integrated-from-fork"));

        ArgumentCaptor<CommitPullRequestLinkEntity> prLinks = ArgumentCaptor.forClass(CommitPullRequestLinkEntity.class);
        verify(fixture.commitPrLinkRepository).save(prLinks.capture());
        assertEquals("integrated-from-fork", prLinks.getValue().getCommit().getCommitHash());
        ArgumentCaptor<PullRequestEntity> prs = ArgumentCaptor.forClass(PullRequestEntity.class);
        verify(fixture.pullRequestRepository).save(prs.capture());
        assertEquals("CLOSED", prs.getValue().getPrState());
        assertEquals(
                java.time.Instant.parse("2026-10-01T12:00:00Z").getEpochSecond(),
                prs.getValue().getMergedAtEpochSecond());
        ArgumentCaptor<CommitIssueLinkEntity> issueLinks = ArgumentCaptor.forClass(CommitIssueLinkEntity.class);
        verify(fixture.commitIssueLinkRepository).save(issueLinks.capture());
        assertEquals("integrated-from-fork", issueLinks.getValue().getCommit().getCommitHash());
        verify(fixture.commitRepository, never()).save(any());
        verify(fixture.commitPrRepository)
                .save(org.mockito.ArgumentMatchers.<CommitPullRequestEntity>argThat(
                        link -> link.getCommitHash().equals("integrated-from-fork")));
        verify(fixture.commitIssueRepository)
                .save(org.mockito.ArgumentMatchers.argThat(link -> link.getCommitHash().equals("integrated-from-fork")));
    }

    @Test
    void usesIntegratedSingleParentMergeCommitForSquashMerges() {
        Fixture fixture = new Fixture(List.of(commit("squash-merge-commit", 1)));
        when(fixture.githubClient.getPullRequestsForProject("upstream", "repo")).thenReturn(List.of(
                pr(4, "CLOSED", "2026-10-02T12:00:00Z", true, "squash-merge-commit",
                        List.of("rewritten-away-source-commit"))));

        fixture.persist(Set.of("squash-merge-commit"));

        ArgumentCaptor<CommitPullRequestLinkEntity> links = ArgumentCaptor.forClass(CommitPullRequestLinkEntity.class);
        verify(fixture.commitPrLinkRepository).save(links.capture());
        assertEquals("squash-merge-commit", links.getValue().getCommit().getCommitHash());
        verify(fixture.commitIssueLinkRepository).save(any());
        verify(fixture.commitRepository, never()).save(any());
    }

    @Test
    void doesNotInventLinksWhenMergedPrCommitsCannotBeMatched() {
        Fixture fixture = new Fixture(List.of(commit("known-upstream-commit", 1)));
        when(fixture.githubClient.getPullRequestsForProject("upstream", "repo")).thenReturn(List.of(
                pr(5, "CLOSED", "2026-10-03T12:00:00Z", true, "absent-merge-commit",
                        List.of("rewritten-away-source-commit"))));

        fixture.persist(Set.of("known-upstream-commit"));

        verify(fixture.commitPrLinkRepository, never()).save(any());
        verify(fixture.commitIssueLinkRepository, never()).save(any());
        verify(fixture.commitPrRepository, never()).save(any());
        verify(fixture.commitIssueRepository, never()).save(any());
        verify(fixture.commitRepository, never()).save(any());
    }

    private static CommitEntity commit(String hash, int parentCount) {
        return new CommitEntity("id-" + hash, "project-ref", hash, 1L, parentCount, Math.max(0, parentCount - 1), parentCount > 1);
    }

    private static GitHubGraphQLClient.PullRequestWithCommits pr(
            int number,
            String state,
            String mergedAt,
            boolean merged,
            String mergeCommitHash,
            List<String> commitHashes) {
        var linkedIssue = new GitHubGraphQLClient.LinkedIssue(
                100 + number,
                "Issue " + number,
                "https://github.com/upstream/repo/issues/" + (100 + number),
                "CLOSED",
                "2026-09-01T12:00:00Z",
                "2026-09-02T12:00:00Z",
                0,
                0);
        return new GitHubGraphQLClient.PullRequestWithCommits(
                number,
                "PR " + number,
                "https://github.com/upstream/repo/pull/" + number,
                state,
                "2026-09-01T12:00:00Z",
                mergedAt,
                merged,
                mergeCommitHash,
                0,
                0,
                0,
                0,
                0,
                List.of(linkedIssue),
                commitHashes);
    }

    private static class Fixture {
        private final GitHubGraphQLClient githubClient = mock(GitHubGraphQLClient.class);
        private final PullRequestEntityRepository pullRequestRepository = mock(PullRequestEntityRepository.class);
        private final IssueEntityRepository issueRepository = mock(IssueEntityRepository.class);
        private final CommitEntityRepository commitRepository = mock(CommitEntityRepository.class);
        private final CommitPullRequestLinkRepository commitPrLinkRepository = mock(CommitPullRequestLinkRepository.class);
        private final CommitIssueLinkRepository commitIssueLinkRepository = mock(CommitIssueLinkRepository.class);
        private final CommitPullRequestRepository commitPrRepository = mock(CommitPullRequestRepository.class);
        private final CommitIssueRepository commitIssueRepository = mock(CommitIssueRepository.class);
        private final PullRequestIssueLinkRepository pullRequestIssueLinkRepository = mock(PullRequestIssueLinkRepository.class);
        private final CommitMetadataService service;

        private Fixture(List<CommitEntity> commits) {
            when(commitRepository.findByProjectRef("project-ref")).thenReturn(commits);
            when(pullRequestRepository.findByProjectRefAndPrNumber(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(Optional.empty());
            when(pullRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(issueRepository.findByProjectRefAndIssueNumber(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(Optional.empty());
            when(issueRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service = new CommitMetadataService(
                    githubClient,
                    pullRequestRepository,
                    issueRepository,
                    mock(IssueLabelRepository.class),
                    commitRepository,
                    commitPrLinkRepository,
                    commitIssueLinkRepository,
                    commitPrRepository,
                    commitIssueRepository,
                    mock(CommitCommentRepository.class),
                    mock(CommitCICDRepository.class),
                    pullRequestIssueLinkRepository,
                    mock(ReleaseEntityRepository.class));
        }

        private void persist(Set<String> dagHashes) {
            service.fetchAndSaveProjectMappings("upstream", "repo", "project-ref", dagHashes);
        }
    }
}
