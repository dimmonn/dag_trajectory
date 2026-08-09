package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.model.GithubProjectRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;

@Service
public class GitRepositoryCloner {

    public Path clone(GithubProjectRequest project) {
        try {
            Path cloneDirectory = Files.createTempDirectory("github-project-");
            Git.cloneRepository()
                    .setURI("https://github.com/" + project.owner() + "/" + project.name() + ".git")
                    .setDirectory(cloneDirectory.toFile())
                    .call()
                    .close();
            return cloneDirectory;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create clone directory", e);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to clone repository " + project.owner() + "/" + project.name(), e);
        }
    }
}
