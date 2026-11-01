package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.model.GithubProjectRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitRepositoryClonerTest {

    @Test
    void resolvesForkToOriginalSourceRepository() {
        var client = mock(GitHubGraphQLClient.class);
        when(client.getRepositoryMetadata("contributor", "project-fork"))
                .thenReturn(new GitHubGraphQLClient.RepositoryMetadata(
                        "contributor",
                        "project-fork",
                        true,
                        new GitHubGraphQLClient.RepositoryIdentity("original", "project"),
                        new GitHubGraphQLClient.RepositoryIdentity("parent-org", "project")));
        var cloner = new GitRepositoryCloner("/tmp/clones", client);

        var upstream = cloner.resolveUpstream(new GithubProjectRequest("contributor", "project-fork"));

        assertEquals(new GithubProjectRequest("original", "project"), upstream);
    }

    @Test
    void fallsBackToParentWhenSourceMetadataIsUnavailable() {
        var client = mock(GitHubGraphQLClient.class);
        when(client.getRepositoryMetadata("contributor", "project-fork"))
                .thenReturn(new GitHubGraphQLClient.RepositoryMetadata(
                        "contributor",
                        "project-fork",
                        true,
                        null,
                        new GitHubGraphQLClient.RepositoryIdentity("parent-org", "project")));
        var cloner = new GitRepositoryCloner("/tmp/clones", client);

        var upstream = cloner.resolveUpstream(new GithubProjectRequest("contributor", "project-fork"));

        assertEquals(new GithubProjectRequest("parent-org", "project"), upstream);
    }
}
