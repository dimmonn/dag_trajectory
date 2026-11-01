package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.model.GithubProjectRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GitRepositoryCloner {

    private static final Logger log = LoggerFactory.getLogger(GitRepositoryCloner.class);
    private static final String GIT_DIR_NAME = ".git";
    private final Path cloneRoot;
    private final GitHubGraphQLClient graphQLClient;

    public GitRepositoryCloner(
            @Value("${github.clone.root:${user.home}/.dag_trajectory/repos}") String cloneRoot,
            GitHubGraphQLClient graphQLClient) {
        this.cloneRoot = Path.of(cloneRoot);
        this.graphQLClient = graphQLClient;
    }

    public GithubProjectRequest resolveUpstream(GithubProjectRequest project) {
        GitHubGraphQLClient.RepositoryMetadata metadata =
                graphQLClient.getRepositoryMetadata(project.owner(), project.name());
        GitHubGraphQLClient.RepositoryIdentity upstream = metadata.upstream();
        if (upstream == null) {
            throw new IllegalStateException(
                    "GitHub marks " + project.owner() + "/" + project.name()
                            + " as a fork but provides neither source nor parent repository metadata");
        }
        return new GithubProjectRequest(upstream.owner(), upstream.name());
    }

    public String getUpstreamDefaultBranch(GithubProjectRequest upstream) {
        GitHubGraphQLClient.RepositoryMetadata metadata =
                graphQLClient.getRepositoryMetadata(upstream.owner(), upstream.name());
        String branch = metadata.defaultBranch();
        if (branch == null || branch.isBlank()) {
            throw new IllegalStateException("GitHub did not return default_branch for upstream "
                    + upstream.owner() + "/" + upstream.name());
        }
        return branch;
    }

    public Path clone(GithubProjectRequest project) {
        String projectName = project.owner() + "/" + project.name();
        Path cloneDirectory = cloneRoot.resolve(project.owner()).resolve(project.name());
        try {
            if (Files.isDirectory(cloneDirectory.resolve(GIT_DIR_NAME))) {
                log.info("Reusing existing clone for {} at {}", projectName, cloneDirectory);
                try (Git git = Git.open(cloneDirectory.toFile())) {
                    git.fetch().setRemote("origin").call();
                }
                return cloneDirectory;
            }
            if (Files.exists(cloneDirectory)) {
                throw new IllegalStateException(
                        "Path exists but is not a git repository: " + cloneDirectory);
            }
            Files.createDirectories(cloneDirectory.getParent());
            log.info("Cloning {} into {}", projectName, cloneDirectory);

            long startTime = System.currentTimeMillis();
            Git git = Git.cloneRepository()
                    .setURI("https://github.com/" + project.owner() + "/" + project.name() + ".git")
                    .setDirectory(cloneDirectory.toFile())
                    .setProgressMonitor(new CloneProgressMonitor())
                    .call();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Clone completed in {} ms", duration);
            git.close();

            return cloneDirectory;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create clone directory", e);
        } catch (Exception e) {
            log.error("Failed to clone repository {}", projectName, e);
            throw new IllegalStateException(
                    "Failed to clone repository " + project.owner() + "/" + project.name(), e);
        }
    }
}
