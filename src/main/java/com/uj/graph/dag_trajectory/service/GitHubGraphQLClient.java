package com.uj.graph.dag_trajectory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GitHubGraphQLClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubGraphQLClient.class);
    private static final String GITHUB_GRAPHQL_ENDPOINT = "https://api.github.com/graphql";
    private static final int PAGE_SIZE = 100;
    private static final int PR_PAGE_SIZE = 20;
    private static final int PR_COMMITS_PAGE_SIZE = 20;
    private static final int PR_ISSUES_PAGE_SIZE = 20;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration RETRY_WINDOW = Duration.ofMinutes(120);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(15);
    private static final Duration SECONDARY_RATE_LIMIT_DELAY = Duration.ofMinutes(2);
    private static final Duration RATE_LIMIT_BUFFER = Duration.ofSeconds(5);
    private final ObjectMapper objectMapper;
    private final String githubToken;
    private final HttpClient httpClient;

    public GitHubGraphQLClient(ObjectMapper objectMapper, @Value("${github.token:}") String githubToken) {
        this.objectMapper = objectMapper;
        this.githubToken = githubToken;
        this.httpClient = HttpClient.newHttpClient();
    }

    public boolean isConfigured() {
        return githubToken != null && !githubToken.isBlank();
    }

    public RepositoryMetadata getRepositoryMetadata(String owner, String repo) {
        JsonNode response = executeRestGet(
                "https://api.github.com/repos/%s/%s".formatted(owner, repo));
        JsonNode source = response.path("source");
        JsonNode parent = response.path("parent");
        return new RepositoryMetadata(
                response.path("owner").path("login").asText(owner),
                response.path("name").asText(repo),
                response.path("fork").asBoolean(false),
                repositoryIdentity(source),
                repositoryIdentity(parent));
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
                            reviews(first: 1) { totalCount }
                            comments { totalCount }
                            additions
                            deletions
                            changedFiles
                            closingIssuesReferences(first: %d) {
                              pageInfo {
                                hasNextPage
                                endCursor
                              }
                              nodes {
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
                }
                """.formatted(owner, repo, commitOid, PAGE_SIZE, PAGE_SIZE);

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
                        node.get("changedFiles").asInt(0),
                        parseLinkedIssues(node.at("/closingIssuesReferences/nodes"))));
            }
        }
        return prs;
    }

    public List<PullRequestWithCommits> getPullRequestsForProject(String owner, String repo) {
        if (githubToken == null || githubToken.isBlank()) {
            return List.of();
        }
        List<PullRequestWithCommits> pullRequests = new ArrayList<>();
        String cursor = null;
        boolean hasNextPage = true;
        while (hasNextPage) {
            String afterClause = cursor == null ? "" : ", after: \"" + cursor + "\"";
            String query = """
                    query {
                      repository(owner: "%s", name: "%s") {
                        pullRequests(first: %d%s, states: [OPEN, CLOSED, MERGED], orderBy: {field: CREATED_AT, direction: ASC}) {
                          pageInfo {
                            hasNextPage
                            endCursor
                          }
                          nodes {
                            number
                            title
                            url
                            state
                            createdAt
                            mergedAt
                            merged
                            mergeCommit { oid }
                            reviews(first: 100) { totalCount }
                            comments { totalCount }
                            additions
                            deletions
                            changedFiles
                            closingIssuesReferences(first: %d) {
                              pageInfo {
                                hasNextPage
                                endCursor
                              }
                              nodes {
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
                            commits(first: %d) {
                              pageInfo {
                                hasNextPage
                                endCursor
                              }
                              nodes {
                                commit {
                                  oid
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                    """.formatted(owner, repo, PR_PAGE_SIZE, afterClause, PR_ISSUES_PAGE_SIZE, PR_COMMITS_PAGE_SIZE);
            JsonNode response = executeQuery(query);
            JsonNode prRoot = response.at("/data/repository/pullRequests");
            JsonNode nodes = prRoot.at("/nodes");
            if (nodes.isArray()) {
                for (JsonNode node : nodes) {
                    Set<String> commitHashes = new LinkedHashSet<>(parsePullRequestCommitHashes(node.at("/commits/nodes")));
                    if (node.at("/commits/pageInfo/hasNextPage").asBoolean(false)) {
                        String commitCursor = node.at("/commits/pageInfo/endCursor").asText(null);
                        commitHashes.addAll(getAdditionalPullRequestCommitHashes(owner, repo, node.path("number").asInt(), commitCursor));
                    }
                    Map<Integer, LinkedIssue> issuesByNumber = new LinkedHashMap<>();
                    for (LinkedIssue issue : parseLinkedIssues(node.at("/closingIssuesReferences/nodes"))) {
                        issuesByNumber.put(issue.number(), issue);
                    }
                    if (node.at("/closingIssuesReferences/pageInfo/hasNextPage").asBoolean(false)) {
                        String issueCursor = node.at("/closingIssuesReferences/pageInfo/endCursor").asText(null);
                        for (LinkedIssue issue : getAdditionalPullRequestLinkedIssues(
                                owner, repo, node.path("number").asInt(), issueCursor)) {
                            issuesByNumber.put(issue.number(), issue);
                        }
                    }
                    pullRequests.add(new PullRequestWithCommits(
                            node.get("number").asInt(),
                            node.get("title").asText(),
                            node.get("url").asText(),
                            node.get("state").asText(),
                            node.get("createdAt").asText(),
                            node.get("mergedAt").asText(null),
                            node.path("merged").asBoolean(false),
                            node.at("/mergeCommit/oid").asText(null),
                            node.at("/reviews/totalCount").asInt(0),
                            node.at("/comments/totalCount").asInt(0),
                            node.get("additions").asInt(0),
                            node.get("deletions").asInt(0),
                            node.get("changedFiles").asInt(0),
                            new ArrayList<>(issuesByNumber.values()),
                            new ArrayList<>(commitHashes)));
                }
            }
            hasNextPage = prRoot.at("/pageInfo/hasNextPage").asBoolean(false);
            cursor = prRoot.at("/pageInfo/endCursor").asText(null);
        }
        return pullRequests;
    }

    public List<ProjectIssue> getIssuesForProject(String owner, String repo) {
        if (githubToken == null || githubToken.isBlank()) {
            return List.of();
        }
        List<ProjectIssue> issues = new ArrayList<>();
        String cursor = null;
        boolean hasNextPage = true;
        while (hasNextPage) {
            String afterClause = cursor == null ? "" : ", after: \"" + cursor + "\"";
            String query = """
                    query {
                      repository(owner: "%s", name: "%s") {
                        issues(first: %d%s, states: [OPEN, CLOSED], orderBy: {field: CREATED_AT, direction: ASC}) {
                          pageInfo {
                            hasNextPage
                            endCursor
                          }
                          nodes {
                            number
                            title
                            url
                            state
                            createdAt
                            closedAt
                            comments { totalCount }
                            labels(first: 100) {
                              nodes { name }
                              pageInfo { hasNextPage endCursor }
                            }
                          }
                        }
                      }
                    }
                    """.formatted(owner, repo, PAGE_SIZE, afterClause);
            JsonNode response = executeQuery(query);
            JsonNode issueRoot = response.at("/data/repository/issues");
            JsonNode nodes = issueRoot.at("/nodes");
            if (nodes.isArray()) {
                for (JsonNode node : nodes) {
                    issues.add(new ProjectIssue(
                            node.path("number").asInt(),
                            node.path("title").asText(""),
                            node.path("url").asText(""),
                            node.path("state").asText(""),
                            node.path("createdAt").asText(null),
                            node.path("closedAt").asText(null),
                            node.at("/comments/totalCount").asInt(0),
                            parseLabelNames(node.at("/labels/nodes"),
                                    node.at("/labels/pageInfo/hasNextPage").asBoolean(false)
                                            ? getAdditionalIssueLabels(
                                                    owner,
                                                    repo,
                                                    node.path("number").asInt(),
                                                    node.at("/labels/pageInfo/endCursor").asText(null))
                                            : List.of())));
                }
            }
            hasNextPage = issueRoot.at("/pageInfo/hasNextPage").asBoolean(false);
            cursor = issueRoot.at("/pageInfo/endCursor").asText(null);
        }
        return issues;
    }

    public List<ProjectComment> getCommitCommentsForProject(String owner, String repo) {
        if (githubToken == null || githubToken.isBlank()) {
            return List.of();
        }
        List<ProjectComment> comments = new ArrayList<>();
        int page = 1;
        while (true) {
            String url = "https://api.github.com/repos/%s/%s/comments?per_page=%d&page=%d"
                    .formatted(owner, repo, PAGE_SIZE, page);
            JsonNode response = executeRestGet(url);
            if (!response.isArray() || response.isEmpty()) {
                break;
            }
            for (JsonNode node : response) {
                String commitHash = node.path("commit_id").asText(null);
                if (commitHash == null || commitHash.isBlank()) {
                    continue;
                }
                comments.add(new ProjectComment(
                        commitHash,
                        node.path("html_url").asText(null),
                        node.path("body").asText(null),
                        node.path("user").path("login").asText(null),
                        node.path("created_at").asText(null)));
            }
            if (response.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        return comments;
    }

    public List<ProjectWorkflowRun> getWorkflowRunsForProject(String owner, String repo) {
        if (githubToken == null || githubToken.isBlank()) {
            return List.of();
        }
        List<ProjectWorkflowRun> runs = new ArrayList<>();
        int page = 1;
        while (true) {
            String url = "https://api.github.com/repos/%s/%s/actions/runs?per_page=%d&page=%d"
                    .formatted(owner, repo, PAGE_SIZE, page);
            JsonNode response = executeRestGet(url);
            JsonNode nodes = response.path("workflow_runs");
            if (!nodes.isArray() || nodes.isEmpty()) {
                break;
            }
            for (JsonNode node : nodes) {
                String commitHash = node.path("head_sha").asText(null);
                if (commitHash == null || commitHash.isBlank()) {
                    continue;
                }
                String workflowUrl = node.path("html_url").asText(null);
                if (workflowUrl == null || workflowUrl.isBlank()) {
                    workflowUrl = node.path("url").asText(null);
                }
                runs.add(new ProjectWorkflowRun(
                        commitHash,
                        node.path("name").asText("workflow"),
                        workflowUrl,
                        node.path("status").asText("UNKNOWN"),
                        node.path("conclusion").asText(null),
                        node.path("run_started_at").asText(null),
                        node.path("updated_at").asText(null),
                        node.path("run_attempt").asInt(1)));
            }
            if (nodes.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        return runs;
    }

    private JsonNode executeRestGet(String url) {
        long deadlineEpochMillis = System.currentTimeMillis() + RETRY_WINDOW.toMillis();
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Accept", "application/vnd.github+json")
                        .GET();
                if (isConfigured()) {
                    requestBuilder.header("Authorization", "Bearer " + githubToken);
                }
                HttpRequest request = requestBuilder.build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                if (statusCode == 200) {
                    return objectMapper.readTree(response.body());
                }
                if (isSecondaryRateLimitResponse(statusCode, response.body())) {
                    Duration retryDelay = resolveSecondaryRateLimitDelay(response.headers());
                    log.warn(
                            "GitHub REST secondary rate limit hit, will retry: status={}, attempt={}, retryDelaySeconds={}",
                            statusCode,
                            attempt,
                            retryDelay.toSeconds());
                    sleep(retryDelay);
                    continue;
                }
                if (isPaginationLimitResponse(statusCode, response.body())) {
                    log.warn(
                            "GitHub REST pagination limit reached, stopping pagination for url={}, status={}",
                            url,
                            statusCode);
                    return objectMapper.createObjectNode();
                }
                if (isRetryableStatus(statusCode) && System.currentTimeMillis() < deadlineEpochMillis) {
                    Duration retryDelay = statusCode == 429 ? resolveRateLimitDelay(response.headers()) : RETRY_DELAY;
                    log.warn(
                            "Transient GitHub REST failure, will retry: status={}, attempt={}, retryDelaySeconds={}",
                            statusCode,
                            attempt,
                            retryDelay.toSeconds());
                    sleep(retryDelay);
                    continue;
                }
                throw new IllegalStateException("GitHub REST request failed with status "
                        + statusCode + " for " + url + ": " + response.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("GitHub REST retry interrupted", e);
            } catch (java.io.IOException e) {
                if (System.currentTimeMillis() < deadlineEpochMillis) {
                    log.warn(
                            "GitHub REST I/O failure, will retry: attempt={}, retryDelaySeconds={}",
                            attempt,
                            RETRY_DELAY.toSeconds(),
                            e);
                    try {
                        sleep(RETRY_DELAY);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("GitHub REST retry interrupted", interruptedException);
                    }
                    continue;
                }
                throw new IllegalStateException("GitHub REST request failed after retry window for " + url, e);
            } catch (Exception e) {
                if (System.currentTimeMillis() < deadlineEpochMillis) {
                    log.warn(
                            "Unexpected GitHub REST failure, will retry: attempt={}, retryDelaySeconds={}",
                            attempt,
                            RETRY_DELAY.toSeconds(),
                            e);
                    try {
                        sleep(RETRY_DELAY);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("GitHub REST retry interrupted", interruptedException);
                    }
                    continue;
                }
                throw new IllegalStateException("Failed to execute GitHub REST request for " + url, e);
            }
        }
    }

    public List<Release> getReleases(String owner, String repo) {
        if (githubToken == null || githubToken.isBlank()) {
            return List.of();
        }
        List<Release> releases = new ArrayList<>();
        String cursor = null;
        boolean hasNextPage = true;
        while (hasNextPage) {
            String afterClause = cursor == null ? "" : ", after: \"" + cursor + "\"";
            String query = """
                    query {
                      repository(owner: "%s", name: "%s") {
                        releases(first: %d%s, orderBy: {field: CREATED_AT, direction: ASC}) {
                          pageInfo {
                            hasNextPage
                            endCursor
                          }
                          nodes {
                            id
                            name
                            tagName
                            url
                            publishedAt
                            createdAt
                            isDraft
                            isPrerelease
                            author {
                              login
                            }
                            tag {
                              target {
                                ... on Commit {
                                  oid
                                }
                                ... on Tag {
                                  target {
                                    ... on Commit {
                                      oid
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                    """.formatted(owner, repo, PAGE_SIZE, afterClause);
            JsonNode response = executeQuery(query);
            JsonNode releaseRoot = response.at("/data/repository/releases");
            JsonNode nodes = releaseRoot.at("/nodes");
            if (nodes.isArray()) {
                for (JsonNode node : nodes) {
                    releases.add(new Release(
                            node.path("id").asText(),
                            node.path("name").asText(null),
                            node.path("tagName").asText(),
                            node.path("url").asText(),
                            node.path("publishedAt").asText(null),
                            node.path("createdAt").asText(null),
                            node.path("isDraft").asBoolean(false),
                            node.path("isPrerelease").asBoolean(false),
                            node.path("author").path("login").asText(null),
                            extractReleaseCommitOid(node)));
                }
            }
            hasNextPage = releaseRoot.at("/pageInfo/hasNextPage").asBoolean(false);
            cursor = releaseRoot.at("/pageInfo/endCursor").asText(null);
        }
        return releases;
    }

    private List<String> getAdditionalPullRequestCommitHashes(String owner, String repo, int pullRequestNumber, String initialCursor) {
        List<String> commitHashes = new ArrayList<>();
        String cursor = initialCursor;
        boolean hasNextPage = true;
        while (hasNextPage && cursor != null && !cursor.isBlank()) {
            String query = """
                    query {
                      repository(owner: "%s", name: "%s") {
                        pullRequest(number: %d) {
                          commits(first: %d, after: "%s") {
                            pageInfo {
                              hasNextPage
                              endCursor
                            }
                            nodes {
                              commit {
                                oid
                              }
                            }
                          }
                        }
                      }
                    }
                    """.formatted(owner, repo, pullRequestNumber, PR_COMMITS_PAGE_SIZE, cursor);
            JsonNode response = executeQuery(query);
            JsonNode commitsRoot = response.at("/data/repository/pullRequest/commits");
            commitHashes.addAll(parsePullRequestCommitHashes(commitsRoot.at("/nodes")));
            hasNextPage = commitsRoot.at("/pageInfo/hasNextPage").asBoolean(false);
            cursor = commitsRoot.at("/pageInfo/endCursor").asText(null);
        }
        return commitHashes;
    }

    private List<String> getAdditionalIssueLabels(String owner, String repo, int issueNumber, String initialCursor) {
        List<String> labels = new ArrayList<>();
        String cursor = initialCursor;
        boolean hasNextPage = true;
        while (hasNextPage && cursor != null && !cursor.isBlank()) {
            String query = """
                    query {
                      repository(owner: "%s", name: "%s") {
                        issue(number: %d) {
                          labels(first: 100, after: "%s") {
                            nodes { name }
                            pageInfo { hasNextPage endCursor }
                          }
                        }
                      }
                    }
                    """.formatted(owner, repo, issueNumber, cursor);
            JsonNode response = executeQuery(query);
            JsonNode labelsRoot = response.at("/data/repository/issue/labels");
            labels.addAll(parseLabelNames(labelsRoot.at("/nodes"), List.of()));
            hasNextPage = labelsRoot.at("/pageInfo/hasNextPage").asBoolean(false);
            cursor = labelsRoot.at("/pageInfo/endCursor").asText(null);
        }
        return labels;
    }

    private List<LinkedIssue> getAdditionalPullRequestLinkedIssues(
            String owner, String repo, int pullRequestNumber, String initialCursor) {
        List<LinkedIssue> issues = new ArrayList<>();
        String cursor = initialCursor;
        boolean hasNextPage = true;
        while (hasNextPage && cursor != null && !cursor.isBlank()) {
            String query = """
                    query {
                      repository(owner: "%s", name: "%s") {
                        pullRequest(number: %d) {
                          closingIssuesReferences(first: %d, after: "%s") {
                            pageInfo {
                              hasNextPage
                              endCursor
                            }
                            nodes {
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
                    """.formatted(owner, repo, pullRequestNumber, PR_ISSUES_PAGE_SIZE, cursor);
            JsonNode response = executeQuery(query);
            JsonNode issuesRoot = response.at("/data/repository/pullRequest/closingIssuesReferences");
            issues.addAll(parseLinkedIssues(issuesRoot.at("/nodes")));
            hasNextPage = issuesRoot.at("/pageInfo/hasNextPage").asBoolean(false);
            cursor = issuesRoot.at("/pageInfo/endCursor").asText(null);
        }
        return issues;
    }

    private JsonNode executeQuery(String query) {
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(new QueryRequest(query));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize GitHub GraphQL request", e);
        }

        long deadlineEpochMillis = System.currentTimeMillis() + RETRY_WINDOW.toMillis();
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(GITHUB_GRAPHQL_ENDPOINT))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Authorization", "Bearer " + githubToken)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                if (statusCode == 200) {
                    JsonNode responseBody = objectMapper.readTree(response.body());
                    JsonNode errors = responseBody.path("errors");
                    if (errors.isArray() && !errors.isEmpty()) {
                        String errorSummary = summarizeGraphQlErrors(errors);
                        if (containsRateLimitError(errors)) {
                            Duration retryDelay = resolveRateLimitDelay(response.headers());
                            log.warn(
                                    "GitHub GraphQL rate limit hit, will retry: attempt={}, retryDelaySeconds={}, error={}",
                                    attempt,
                                    retryDelay.toSeconds(),
                                    errorSummary);
                            sleep(retryDelay);
                            continue;
                        }
                        throw new IllegalStateException("GitHub GraphQL returned errors: " + errorSummary);
                    }
                    return responseBody;
                }

                if (isSecondaryRateLimitResponse(statusCode, response.body())) {
                    Duration retryDelay = resolveSecondaryRateLimitDelay(response.headers());
                    log.warn(
                            "GitHub GraphQL secondary rate limit hit, will retry: status={}, attempt={}, retryDelaySeconds={}",
                            statusCode,
                            attempt,
                            retryDelay.toSeconds());
                    sleep(retryDelay);
                    continue;
                }

                if (isRetryableStatus(statusCode) && System.currentTimeMillis() < deadlineEpochMillis) {
                    Duration retryDelay = statusCode == 429 ? resolveRateLimitDelay(response.headers()) : RETRY_DELAY;
                    log.warn(
                            "Transient GitHub GraphQL failure, will retry: status={}, attempt={}, retryDelaySeconds={}",
                            statusCode,
                            attempt,
                            retryDelay.toSeconds());
                    sleep(retryDelay);
                    continue;
                }

                log.error("GitHub GraphQL request failed with status {}: {}", statusCode, response.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("GitHub GraphQL retry interrupted", e);
            } catch (java.io.IOException e) {
                if (System.currentTimeMillis() < deadlineEpochMillis) {
                    log.warn(
                            "GitHub GraphQL I/O failure, will retry: attempt={}, retryDelaySeconds={}",
                            attempt,
                            RETRY_DELAY.toSeconds(),
                            e);
                    try {
                        sleep(RETRY_DELAY);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("GitHub GraphQL retry interrupted", interruptedException);
                    }
                    continue;
                }
                throw new IllegalStateException("GitHub GraphQL request failed after retry window", e);
            } catch (Exception e) {
                if (System.currentTimeMillis() < deadlineEpochMillis) {
                    log.warn(
                            "Unexpected GitHub GraphQL failure, will retry in {} sec: attempt={}, retryDelaySeconds={}",
                            RETRY_DELAY,
                            attempt,
                            RETRY_DELAY.toSeconds(),
                            e);
                    try {
                        sleep(RETRY_DELAY);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("GitHub GraphQL retry interrupted", interruptedException);
                    }
                    continue;
                }
                log.error(e.getMessage(), e);
            }
        }
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private boolean containsRateLimitError(JsonNode errors) {
        for (JsonNode error : errors) {
            String message = error.path("message").asText("");
            if (message.toLowerCase().contains("rate limit")) {
                return true;
            }
        }
        return false;
    }

    private boolean isSecondaryRateLimitResponse(int statusCode, String responseBody) {
        if (statusCode != 403 || responseBody == null) {
            return false;
        }
        String body = responseBody.toLowerCase();
        return body.contains("secondary rate limit") || body.contains("rate limit");
    }

    private boolean isPaginationLimitResponse(int statusCode, String responseBody) {
        if (statusCode != 422 || responseBody == null) {
            return false;
        }
        return responseBody.toLowerCase().contains("pagination is limited for this resource");
    }

    private Duration resolveRateLimitDelay(HttpHeaders headers) {
        return headers.firstValue("x-ratelimit-reset")
                .map(this::toRateLimitDelay)
                .orElse(RETRY_DELAY);
    }

    private Duration resolveSecondaryRateLimitDelay(HttpHeaders headers) {
        return headers.firstValue("retry-after")
                .flatMap(this::parseRetryAfterSeconds)
                .map(Duration::ofSeconds)
                .orElse(SECONDARY_RATE_LIMIT_DELAY)
                .plus(RATE_LIMIT_BUFFER);
    }

    private java.util.Optional<Long> parseRetryAfterSeconds(String retryAfterHeader) {
        try {
            return java.util.Optional.of(Long.parseLong(retryAfterHeader));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    private Duration toRateLimitDelay(String resetEpochSeconds) {
        try {
            long resetMillis = Long.parseLong(resetEpochSeconds) * 1000L;
            long delayMillis = Math.max(RETRY_DELAY.toMillis(), resetMillis - Instant.now().toEpochMilli());
            return Duration.ofMillis(delayMillis).plus(RATE_LIMIT_BUFFER);
        } catch (NumberFormatException e) {
            return RETRY_DELAY;
        }
    }

    private boolean canRetry(long deadlineEpochMillis, Duration retryDelay) {
        return System.currentTimeMillis() + retryDelay.toMillis() < deadlineEpochMillis;
    }

    private void sleep(Duration duration) throws InterruptedException {
        Thread.sleep(duration.toMillis());
    }

    private String summarizeGraphQlErrors(JsonNode errors) {
        List<String> messages = new ArrayList<>();
        for (JsonNode error : errors) {
            String message = error.path("message").asText(null);
            if (message == null || message.isBlank()) {
                message = error.toString();
            }
            JsonNode path = error.path("path");
            if (path.isArray() && !path.isEmpty()) {
                message = message + " @ " + path.toString();
            }
            messages.add(message);
        }
        return String.join(" | ", messages);
    }

    private List<LinkedIssue> parseLinkedIssues(JsonNode nodes) {
        List<LinkedIssue> linkedIssues = new ArrayList<>();
        if (!nodes.isArray()) {
            return linkedIssues;
        }

        for (JsonNode node : nodes) {
            if (node.get("number") == null) {
                continue;
            }
            linkedIssues.add(new LinkedIssue(
                    node.get("number").asInt(),
                    node.path("title").asText(""),
                    node.path("url").asText(""),
                    node.path("state").asText(""),
                    node.path("createdAt").asText(null),
                    node.path("closedAt").asText(null),
                    node.at("/comments/totalCount").asInt(0),
                    node.at("/labels/totalCount").asInt(0)));
        }
        return linkedIssues;
    }

    private RepositoryIdentity repositoryIdentity(JsonNode repositoryNode) {
        String owner = repositoryNode.path("owner").path("login").asText(null);
        String name = repositoryNode.path("name").asText(null);
        if (owner == null || owner.isBlank() || name == null || name.isBlank()) {
            return null;
        }
        return new RepositoryIdentity(owner, name);
    }

    private List<String> parseLabelNames(JsonNode nodes, List<String> additionalNames) {
        List<String> names = new ArrayList<>();
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                String name = node.path("name").asText(null);
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        }
        names.addAll(additionalNames);
        return names.stream().distinct().toList();
    }

    private List<String> parsePullRequestCommitHashes(JsonNode nodes) {
        List<String> commitHashes = new ArrayList<>();
        if (!nodes.isArray()) {
            return commitHashes;
        }
        for (JsonNode node : nodes) {
            String hash = node.at("/commit/oid").asText(null);
            if (hash != null && !hash.isBlank()) {
                commitHashes.add(hash);
            }
        }
        return commitHashes;
    }

    private String extractReleaseCommitOid(JsonNode releaseNode) {
        JsonNode direct = releaseNode.at("/tag/target/oid");
        if (!direct.isMissingNode() && !direct.isNull()) {
            return direct.asText(null);
        }
        JsonNode nested = releaseNode.at("/tag/target/target/oid");
        if (!nested.isMissingNode() && !nested.isNull()) {
            return nested.asText(null);
        }
        return null;
    }

    public record QueryRequest(String query) {}

    public record RepositoryIdentity(String owner, String name) {}

    public record RepositoryMetadata(
            String owner,
            String name,
            boolean fork,
            RepositoryIdentity source,
            RepositoryIdentity parent) {
        public RepositoryIdentity upstream() {
            if (!fork) {
                return new RepositoryIdentity(owner, name);
            }
            if (source != null) {
                return source;
            }
            return parent;
        }
    }

    public record PullRequest(
            int number,
            String title,
            String url,
            String state,
            String createdAt,
            String mergedAt,
            int reviewCount,
            int commentCount,
            int additions,
            int deletions,
            int changedFiles,
            List<LinkedIssue> linkedIssues) {}

    public record PullRequestWithCommits(
            int number,
            String title,
            String url,
            String state,
            String createdAt,
            String mergedAt,
            boolean merged,
            String mergeCommitHash,
            int reviewCount,
            int commentCount,
            int additions,
            int deletions,
            int changedFiles,
            List<LinkedIssue> linkedIssues,
            List<String> commitHashes) {}

    public record LinkedIssue(
            int number,
            String title,
            String url,
            String state,
            String createdAt,
            String closedAt,
            int commentCount,
            int labelCount) {}

    public record ProjectComment(
            String commitHash,
            String url,
            String body,
            String authorLogin,
            String createdAt) {}

    public record ProjectWorkflowRun(
            String commitHash,
            String name,
            String url,
            String status,
            String conclusion,
            String startedAt,
            String completedAt,
            int attemptNumber) {}

    public record ProjectIssue(
            int number,
            String title,
            String url,
            String state,
            String createdAt,
            String closedAt,
            int commentCount,
            List<String> labelNames) {}

    public record Release(
            String id,
            String name,
            String tagName,
            String url,
            String publishedAt,
            String createdAt,
            boolean draft,
            boolean prerelease,
            String authorLogin,
            String commitOid) {}
}
