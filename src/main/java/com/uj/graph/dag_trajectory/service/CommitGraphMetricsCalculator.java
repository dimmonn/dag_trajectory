package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.model.CommitMetrics;
import com.uj.graph.dag_trajectory.model.CommitNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CommitGraphMetricsCalculator {
    private static final Logger log = LoggerFactory.getLogger(CommitGraphMetricsCalculator.class);
    private static final int NODE_PROGRESS_LOG_INTERVAL = 500;
    private static final int MAX_INTERESTING_NODE_DEBUG_LOGS = 25;

    public List<CommitNode> buildNodes(Collection<CommitSnapshot> snapshots) {
        log.debug("Building commit metrics: snapshots={}", snapshots.size());
        List<CommitSnapshot> orderedSnapshots = List.copyOf(snapshots);
        Map<String, CommitSnapshot> byHash = indexByHash(snapshots);
        Map<String, List<String>> childrenByHash = childrenByHash(snapshots);
        NodeLoggingContext loggingContext = new NodeLoggingContext(orderedSnapshots.size());
        List<CommitNode> nodes = orderedSnapshots.stream()
                .map(snapshot -> toCommitNode(snapshot, byHash, childrenByHash, orderedSnapshots, loggingContext))
                .toList();
        log.debug("Commit metrics built: nodes={}", nodes.size());
        return nodes;
    }

    public List<CommitSnapshot> sliceByCutoff(Collection<CommitSnapshot> snapshots, Instant cutoffAt) {
        if (cutoffAt == null) {
            return List.copyOf(snapshots);
        }
        Map<String, CommitSnapshot> selected = new LinkedHashMap<>();
        for (CommitSnapshot snapshot : snapshots) {
            if (!snapshot.authoredAt().isAfter(cutoffAt)) {
                selected.put(snapshot.hash(), snapshot);
            }
        }
        Set<String> selectedHashes = selected.keySet();
        List<CommitSnapshot> pruned = new ArrayList<>();
        for (CommitSnapshot snapshot : selected.values()) {
            List<String> parents = snapshot.parents().stream()
                    .filter(selectedHashes::contains)
                    .toList();
            pruned.add(snapshot.withParents(parents));
        }
        log.info("Sliced commit graph by cutoff: cutoffAt={}, original={}, sliced={}", cutoffAt, snapshots.size(), pruned.size());
        return pruned;
    }

    public Map<String, CommitSnapshot> indexByHash(Collection<CommitSnapshot> snapshots) {
        Map<String, CommitSnapshot> byHash = new LinkedHashMap<>();
        for (CommitSnapshot snapshot : snapshots) {
            byHash.put(snapshot.hash(), snapshot);
        }
        return byHash;
    }

    private Map<String, List<String>> childrenByHash(Collection<CommitSnapshot> snapshots) {
        Map<String, List<String>> childrenByHash = new LinkedHashMap<>();
        for (CommitSnapshot snapshot : snapshots) {
            childrenByHash.putIfAbsent(snapshot.hash(), new ArrayList<>());
        }
        for (CommitSnapshot snapshot : snapshots) {
            for (String parent : snapshot.parents()) {
                childrenByHash.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(snapshot.hash());
            }
        }
        return childrenByHash;
    }

    private CommitNode toCommitNode(
            CommitSnapshot snapshot,
            Map<String, CommitSnapshot> byHash,
            Map<String, List<String>> childrenByHash,
            Collection<CommitSnapshot> snapshots,
            NodeLoggingContext loggingContext) {
        int parentCount = snapshot.parents().size();
        int mergeParentCount = Math.max(0, parentCount - 1);
        int childCount = (int) snapshots.stream()
                .filter(candidate -> candidate.parents().contains(snapshot.hash()))
                .count();
        int graphDepth = graphDepth(snapshot.hash(), byHash, new HashMap<>());
        int firstParentDistance = firstParentDistance(snapshot.hash(), byHash, new HashMap<>());
        int ancestorCount = ancestorCount(snapshot.hash(), byHash, new HashMap<>());
        int descendantCount = descendantCount(snapshot.hash(), childrenByHash, new HashMap<>());
        int subtreeSize = descendantCount + 1;
        long primaryParentLagSeconds = primaryParentLag(snapshot, byHash);
        long ancestorSpanSeconds = ancestorSpan(snapshot, byHash);
        long childLagSeconds = childLag(snapshot, byHash, childrenByHash);
        boolean branchPoint = childCount > 1;
        int branchDepth = branchDepth(snapshot.hash(), byHash, childrenByHash, new HashMap<>());
        int branchWidth = childCount;
        long branchLifetimeSeconds = branchLifetime(snapshot, byHash, childrenByHash);
        long branchDivergenceSpanSeconds = branchDivergenceSpan(snapshot, byHash);
        long commitsIn7d = activityCount(snapshot, snapshots, 7);
        long commitsIn30d = activityCount(snapshot, snapshots, 30);
        long commitsIn90d = activityCount(snapshot, snapshots, 90);
        long mergeCommitsIn7d = mergeActivityCount(snapshot, snapshots, 7);
        long mergeCommitsIn30d = mergeActivityCount(snapshot, snapshots, 30);
        long mergeCommitsIn90d = mergeActivityCount(snapshot, snapshots, 90);
        CommitMetrics metrics = new CommitMetrics(
                parentCount,
                mergeParentCount,
                childCount,
                graphDepth,
                firstParentDistance,
                ancestorCount,
                descendantCount,
                subtreeSize,
                primaryParentLagSeconds,
                ancestorSpanSeconds,
                childLagSeconds,
                branchPoint,
                branchDepth,
                branchWidth,
                branchLifetimeSeconds,
                branchDivergenceSpanSeconds,
                commitsIn7d,
                commitsIn30d,
                commitsIn90d,
                mergeCommitsIn7d,
                mergeCommitsIn30d,
                mergeCommitsIn90d);
        logNodeMetrics(snapshot, metrics, loggingContext);
        return new CommitNode(snapshot.hash(), snapshot.parents(), snapshot.authoredAt().getEpochSecond(), metrics);
    }

    private void logNodeMetrics(CommitSnapshot snapshot, CommitMetrics metrics, NodeLoggingContext context) {
        context.processed++;
        if (metrics.mergeParentCount() > 0) {
            context.mergeCommits++;
        }
        if (metrics.branchPoint()) {
            context.branchPointCommits++;
        }
        if (metrics.parentCount() == 0) {
            context.rootCommits++;
        }

        if (context.processed % NODE_PROGRESS_LOG_INTERVAL == 0 || context.processed == context.total) {
            log.info(
                    "Commit metrics progress: processed={}/{}, merges={}, branchPoints={}, roots={}",
                    context.processed,
                    context.total,
                    context.mergeCommits,
                    context.branchPointCommits,
                    context.rootCommits);
        }

        if (log.isDebugEnabled()
                && context.interestingDebugLogs < MAX_INTERESTING_NODE_DEBUG_LOGS
                && (metrics.mergeParentCount() > 0 || metrics.branchPoint() || metrics.parentCount() == 0)) {
            context.interestingDebugLogs++;
            log.debug(
                    "Interesting commit metrics: hash={}, parents={}, children={}, depth={}, mergeParents={}, branchPoint={}, ancestorCount={}, descendantCount={}",
                    shortenHash(snapshot.hash()),
                    metrics.parentCount(),
                    metrics.childCount(),
                    metrics.graphDepth(),
                    metrics.mergeParentCount(),
                    metrics.branchPoint(),
                    metrics.ancestorCount(),
                    metrics.descendantCount());
        }
    }

    private String shortenHash(String hash) {
        if (hash == null || hash.length() <= 10) {
            return hash;
        }
        return hash.substring(0, 10);
    }

    private static final class NodeLoggingContext {
        private final int total;
        private int processed;
        private int mergeCommits;
        private int branchPointCommits;
        private int rootCommits;
        private int interestingDebugLogs;

        private NodeLoggingContext(int total) {
            this.total = total;
        }
    }

    private int graphDepth(String hash, Map<String, CommitSnapshot> byHash, Map<String, Integer> memo) {
        Integer cached = memo.get(hash);
        if (cached != null) {
            return cached;
        }
        Set<String> expanded = new HashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();
        stack.push(hash);
        while (!stack.isEmpty()) {
            String current = stack.peek();
            Integer currentCached = memo.get(current);
            if (currentCached != null) {
                stack.pop();
                continue;
            }
            CommitSnapshot snapshot = byHash.get(current);
            if (snapshot == null || snapshot.parents().isEmpty()) {
                memo.put(current, 0);
                stack.pop();
                continue;
            }
            if (expanded.add(current)) {
                for (String parent : snapshot.parents()) {
                    if (memo.containsKey(parent)) {
                        continue;
                    }
                    if (expanded.contains(parent)) {
                        memo.putIfAbsent(parent, 0);
                        continue;
                    }
                    stack.push(parent);
                }
                continue;
            }
            int maxDepth = 0;
            for (String parent : snapshot.parents()) {
                maxDepth = Math.max(maxDepth, memo.getOrDefault(parent, 0));
            }
            memo.put(current, maxDepth + 1);
            stack.pop();
        }
        return memo.getOrDefault(hash, 0);
    }

    private int firstParentDistance(String hash, Map<String, CommitSnapshot> byHash, Map<String, Integer> memo) {
        Integer cached = memo.get(hash);
        if (cached != null) {
            return cached;
        }
        Set<String> seen = new HashSet<>();
        List<String> traversed = new ArrayList<>();
        String current = hash;
        int distance = 0;
        while (true) {
            Integer currentCached = memo.get(current);
            if (currentCached != null) {
                distance += currentCached;
                break;
            }
            if (!seen.add(current)) {
                break;
            }
            traversed.add(current);
            CommitSnapshot snapshot = byHash.get(current);
            if (snapshot == null || snapshot.parents().isEmpty()) {
                break;
            }
            current = snapshot.parents().getFirst();
            distance++;
        }
        int remaining = distance;
        for (String node : traversed) {
            memo.putIfAbsent(node, remaining);
            remaining = Math.max(0, remaining - 1);
        }
        return distance;
    }

    private int ancestorCount(String hash, Map<String, CommitSnapshot> byHash, Map<String, Integer> memo) {
        Integer cached = memo.get(hash);
        if (cached != null) {
            return cached;
        }
        CommitSnapshot snapshot = byHash.get(hash);
        if (snapshot == null || snapshot.parents().isEmpty()) {
            memo.put(hash, 0);
            return 0;
        }
        java.util.Set<String> ancestors = new java.util.LinkedHashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>(snapshot.parents());
        while (!stack.isEmpty()) {
            String parentHash = stack.pop();
            if (!ancestors.add(parentHash)) {
                continue;
            }
            CommitSnapshot parent = byHash.get(parentHash);
            if (parent != null) {
                for (String grandParent : parent.parents()) {
                    stack.push(grandParent);
                }
            }
        }
        int count = ancestors.size();
        memo.put(hash, count);
        return count;
    }

    private int branchDepth(String hash, Map<String, CommitSnapshot> byHash, Map<String, List<String>> childrenByHash, Map<String, Integer> memo) {
        Integer cached = memo.get(hash);
        if (cached != null) {
            return cached;
        }
        Set<String> seen = new HashSet<>();
        String current = hash;
        int depth = 0;
        while (true) {
            Integer currentCached = memo.get(current);
            if (currentCached != null) {
                depth += currentCached;
                break;
            }
            if (!seen.add(current)) {
                break;
            }
            CommitSnapshot snapshot = byHash.get(current);
            if (snapshot == null || snapshot.parents().isEmpty()) {
                break;
            }
            String firstParent = snapshot.parents().getFirst();
            CommitSnapshot parent = byHash.get(firstParent);
            if (parent == null) {
                break;
            }
            depth++;
            if (childrenByHash.getOrDefault(firstParent, List.of()).size() > 1) {
                break;
            }
            current = firstParent;
        }
        memo.put(hash, depth);
        return depth;
    }

    private int descendantCount(String hash, Map<String, List<String>> childrenByHash, Map<String, Integer> memo) {
        Integer cached = memo.get(hash);
        if (cached != null) {
            return cached;
        }
        java.util.Set<String> descendants = new java.util.LinkedHashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>(childrenByHash.getOrDefault(hash, List.of()));
        while (!stack.isEmpty()) {
            String childHash = stack.pop();
            if (!descendants.add(childHash)) {
                continue;
            }
            for (String grandChildHash : childrenByHash.getOrDefault(childHash, List.of())) {
                stack.push(grandChildHash);
            }
        }
        int count = descendants.size();
        memo.put(hash, count);
        return count;
    }

    private long primaryParentLag(CommitSnapshot snapshot, Map<String, CommitSnapshot> byHash) {
        if (snapshot.parents().isEmpty()) {
            return 0;
        }
        CommitSnapshot parent = byHash.get(snapshot.parents().getFirst());
        if (parent == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.SECONDS.between(parent.authoredAt(), snapshot.authoredAt()));
    }

    private long ancestorSpan(CommitSnapshot snapshot, Map<String, CommitSnapshot> byHash) {
        Instant earliest = earliestAncestorTime(snapshot, byHash, new HashMap<>());
        return Math.max(0, ChronoUnit.SECONDS.between(earliest, snapshot.authoredAt()));
    }

    private long childLag(CommitSnapshot snapshot, Map<String, CommitSnapshot> byHash, Map<String, List<String>> childrenByHash) {
        long lag = Long.MAX_VALUE;
        for (String childHash : childrenByHash.getOrDefault(snapshot.hash(), List.of())) {
            CommitSnapshot child = byHash.get(childHash);
            if (child == null) {
                continue;
            }
            long diff = ChronoUnit.SECONDS.between(snapshot.authoredAt(), child.authoredAt());
            if (diff >= 0 && diff < lag) {
                lag = diff;
            }
        }
        return lag == Long.MAX_VALUE ? 0 : lag;
    }

    private long branchLifetime(CommitSnapshot snapshot, Map<String, CommitSnapshot> byHash, Map<String, List<String>> childrenByHash) {
        Instant farthest = farthestDescendantTime(snapshot.hash(), byHash, childrenByHash, new HashMap<>());
        return Math.max(0, ChronoUnit.SECONDS.between(snapshot.authoredAt(), farthest));
    }

    private long branchDivergenceSpan(CommitSnapshot snapshot, Map<String, CommitSnapshot> byHash) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (String childHash : byHash.keySet()) {
            CommitSnapshot child = byHash.get(childHash);
            if (child == null || !child.parents().contains(snapshot.hash())) {
                continue;
            }
            long epoch = child.authoredAt().getEpochSecond();
            min = Math.min(min, epoch);
            max = Math.max(max, epoch);
        }
        if (min == Long.MAX_VALUE) {
            return 0;
        }
        return Math.max(0, max - min);
    }

    private Instant farthestDescendantTime(String hash, Map<String, CommitSnapshot> byHash, Map<String, List<String>> childrenByHash, Map<String, Instant> memo) {
        Instant cached = memo.get(hash);
        if (cached != null) {
            return cached;
        }
        Set<String> expanded = new HashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();
        stack.push(hash);
        while (!stack.isEmpty()) {
            String current = stack.peek();
            Instant currentCached = memo.get(current);
            if (currentCached != null) {
                stack.pop();
                continue;
            }
            CommitSnapshot snapshot = byHash.get(current);
            if (snapshot == null) {
                memo.put(current, Instant.EPOCH);
                stack.pop();
                continue;
            }
            List<String> children = childrenByHash.getOrDefault(current, List.of());
            if (children.isEmpty()) {
                memo.put(current, snapshot.authoredAt());
                stack.pop();
                continue;
            }
            if (expanded.add(current)) {
                for (String childHash : children) {
                    if (memo.containsKey(childHash)) {
                        continue;
                    }
                    if (expanded.contains(childHash)) {
                        CommitSnapshot child = byHash.get(childHash);
                        memo.putIfAbsent(childHash, child != null ? child.authoredAt() : Instant.EPOCH);
                        continue;
                    }
                    stack.push(childHash);
                }
                continue;
            }
            Instant farthest = snapshot.authoredAt();
            for (String childHash : children) {
                Instant descendant = memo.get(childHash);
                if (descendant != null && descendant.isAfter(farthest)) {
                    farthest = descendant;
                }
            }
            memo.put(current, farthest);
            stack.pop();
        }
        return memo.getOrDefault(hash, Instant.EPOCH);
    }

    private long activityCount(CommitSnapshot snapshot, Collection<CommitSnapshot> snapshots, int days) {
        Instant start = snapshot.authoredAt().minus(days, ChronoUnit.DAYS);
        return snapshots.stream()
                .filter(candidate -> !candidate.authoredAt().isBefore(start) && !candidate.authoredAt().isAfter(snapshot.authoredAt()))
                .count();
    }

    private long mergeActivityCount(CommitSnapshot snapshot, Collection<CommitSnapshot> snapshots, int days) {
        Instant start = snapshot.authoredAt().minus(days, ChronoUnit.DAYS);
        return snapshots.stream()
                .filter(candidate -> !candidate.authoredAt().isBefore(start) && !candidate.authoredAt().isAfter(snapshot.authoredAt()))
                .filter(candidate -> candidate.parents().size() > 1)
                .count();
    }

    private Instant earliestAncestorTime(CommitSnapshot snapshot, Map<String, CommitSnapshot> byHash, Map<String, Instant> memo) {
        Instant cached = memo.get(snapshot.hash());
        if (cached != null) {
            return cached;
        }
        Set<String> expanded = new HashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();
        stack.push(snapshot.hash());
        while (!stack.isEmpty()) {
            String current = stack.peek();
            Instant currentCached = memo.get(current);
            if (currentCached != null) {
                stack.pop();
                continue;
            }
            CommitSnapshot currentSnapshot = byHash.get(current);
            if (currentSnapshot == null) {
                memo.put(current, Instant.EPOCH);
                stack.pop();
                continue;
            }
            if (currentSnapshot.parents().isEmpty()) {
                memo.put(current, currentSnapshot.authoredAt());
                stack.pop();
                continue;
            }
            if (expanded.add(current)) {
                for (String parentHash : currentSnapshot.parents()) {
                    if (memo.containsKey(parentHash)) {
                        continue;
                    }
                    if (expanded.contains(parentHash)) {
                        CommitSnapshot parent = byHash.get(parentHash);
                        memo.putIfAbsent(parentHash, parent != null ? parent.authoredAt() : Instant.EPOCH);
                        continue;
                    }
                    stack.push(parentHash);
                }
                continue;
            }
            Instant earliest = currentSnapshot.authoredAt();
            for (String parentHash : currentSnapshot.parents()) {
                Instant parentEarliest = memo.get(parentHash);
                if (parentEarliest != null && parentEarliest.isBefore(earliest)) {
                    earliest = parentEarliest;
                }
            }
            memo.put(current, earliest);
            stack.pop();
        }
        return memo.getOrDefault(snapshot.hash(), snapshot.authoredAt());
    }

    public record CommitSnapshot(String hash, List<String> parents, Instant authoredAt) {
        public CommitSnapshot withParents(List<String> parents) {
            return new CommitSnapshot(hash, parents, authoredAt);
        }
    }
}
