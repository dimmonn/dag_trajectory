package com.uj.graph.dag_trajectory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GitHubGraphQLClient {

    private static final String GITHUB_GRAPHQL_ENDPOINT = "https://api.github.com/graphql";
    private static final int PAGE_SIZE = 100;
    private final ObjectMapper objectMapper;
    private final String githubToken;

    public GitHubGraphQLClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.githubToken = System.getenv("GITHUB_TOKEN");
    }

    public List<PullRequest> getPullRequestsForCommit(String owner, String repo, String commitOid) {
        if (githubToken == null || githubToken.isBlank()) {
            return List.of();
        }
        String query = """
                query {
                  repository(owner: "%s", name: "%s") {
                    object(oid: "%s") {
                      ... on Commit {
                        associatedPullRequests(first: %d) {
                          nodes {
                            number
                            title
                            url
                            state
                            createdAt
                            mergedAt
                            reviews(first: 100) { totalCount }
                            comments { totalCount }
                            additions
                            deletions
                            changedFiles
                          }
                        }
                      }
                    }
                  }
                }
                """.formatted(owner, repo, commitOid, PAGE_SIZE);
        
        JsonNode response = executeQuery(query);
        List<PullRequest> prs = new ArrayList<>();
        
        JsonNode nodes = response.at("/data/repository/object/associatedPullRequests/nodes");
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                prs.add(new PullRequest(
                    node.get("number").asInt(),
                    node.get("title").asText(),
                    node.get("url").asText(),
                    node.get("state").asText(),
                    node.get("createdAt").asText(),
                    node.get("mergedAt").asText(null),
                    node.at("/reviews/totalCount").asInt(0),
                    node.at("/comments/totalCount").asInt(0),
                    node.get("additions").asInt(0),
                    node.get("deletions").asInt(0),
                    node.get("changedFiles").asInt(0)
                ));
            }
        }
        return prs;
    }

    public List<Issue> getIssuesForCommit(String owner, String repo, String commitOid) {
        if (githubToken == null || githubToken.isBlank()) {
            return List.of();
        }
        String query = """
                query {
                  repository(owner: "%s", name: "%s") {
                    object(oid: "%s") {
                      ... on Commit {
                        references(first: %d) {
                          nodes {
                            ... on Issue {
                              number
                              title
                              url
                              state
                              createdAt
                              closedAt
                              comments { totalCount }
                              labels(first: 10) { totalCount }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """.formatted(owner, repo, commitOid, PAGE_SIZE);
        
        JsonNode response = executeQuery(query);
        List<Issue> issues = new ArrayList<>();
        
        JsonNode nodes = response.at("/data/repository/object/references/nodes");
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                if (node.get("number") != null) {
                    issues.add(new Issue(
                        node.get("number").asInt(),
                        node.get("title").asText(),
                        node.get("url").asText(),
                        node.get("state").asText(),
                        node.get("createdAt").asText(),
                        node.get("closedAt").asText(null),
                        node.at("/comments/totalCount").asInt(0),
                        node.at("/labels/totalCount").asInt(0)
                    ));
                }
            }
        }
        return issues;
    }

    public List<Comment> getCommitComments(String owner, String repo, String commitOid) {
        if (githubToken == null || githubToken.isBlank()) {
            return List.of();
        }
        String query = """
                query {
                  repository(owner: "%s", name: "%s") {
                    object(oid: "%s") {
                      ... on Commit {
                        comments(first: %d) {
                          nodes {
                            id
                            url
                            body
                            author {
                              login
                            }
                            createdAt
                          }
                        }
                      }
                    }
                  }
                }
                """.formatted(owner, repo, commitOid, PAGE_SIZE);
        
        JsonNode response = executeQuery(query);
        List<Comment> comments = new ArrayList<>();
        
        JsonNode nodes = response.at("/data/repository/object/comments/nodes");
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                comments.add(new Comment(
                    node.get("id").asText(),
                    node.get("url").asText(),
                    node.get("body").asText(),
                    node.get("author").get("login").asText(),
                    node.get("createdAt").asText()
                ));
            }
        }
        return comments;
    }

    public List<WorkflowRun> getWorkflowRunsForCommit(String owner, String repo, String commitOid) {
        if (githubToken == null || githubToken.isBlank()) {
            return List.of();
        }
        String query = """
                query {
                  repository(owner: "%s", name: "%s") {
                    object(oid: "%s") {
                      ... on Commit {
                        checkSuites(first: %d) {
                          nodes {
                            checkRuns(first: %d) {
                              nodes {
                                name
                                url
                                status
                                conclusion
                                startedAt
                                completedAt
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """.formatted(owner, repo, commitOid, PAGE_SIZE, PAGE_SIZE);
        
        JsonNode response = executeQuery(query);
        List<WorkflowRun> runs = new ArrayList<>();
        
        JsonNode checkSuites = response.at("/data/repository/object/checkSuites/nodes");
        if (checkSuites.isArray()) {
            for (JsonNode suite : checkSuites) {
                JsonNode checkRuns = suite.at("checkRuns/nodes");
                if (checkRuns.isArray()) {
                    for (JsonNode run : checkRuns) {
                        runs.add(new WorkflowRun(
                            run.get("name").asText(),
                            run.get("url").asText(),
                            run.get("status").asText(),
                            run.get("conclusion").asText(null),
                            run.get("startedAt").asText(),
                            run.get("completedAt").asText(null)
                        ));
                    }
                }
            }
        }
        return runs;
    }

    private JsonNode executeQuery(String query) {
        try {
            String payload = objectMapper.writeValueAsString(new QueryRequest(query));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_GRAPHQL_ENDPOINT))
                    .header("Authorization", "Bearer " + githubToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new IllegalStateException("GitHub GraphQL request failed with status " + response.statusCode() + ": " + response.body());
            }
            
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute GitHub GraphQL query", e);
        }
    }

    public record QueryRequest(String query) {}
    
    public record PullRequest(int number, String title, String url, String state, String createdAt, String mergedAt, int reviewCount, int commentCount, int additions, int deletions, int changedFiles) {}
    
    public record Issue(int number, String title, String url, String state, String createdAt, String closedAt, int commentCount, int labelCount) {}
    
    public record Comment(String id, String url, String body, String authorLogin, String createdAt) {}
    
    public record WorkflowRun(String name, String url, String status, String conclusion, String startedAt, String completedAt) {}
}
