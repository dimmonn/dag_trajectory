package com.uj.graph.dag_trajectory.service;

import com.uj.graph.dag_trajectory.model.CommitMetrics;
import com.uj.graph.dag_trajectory.model.CommitNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class CommitGraphMetricsCalculator {

    public List<CommitNode> buildNodes(Collection<CommitSnapshot> snapshots) {
        Map<String, CommitSnapshot> byHash = indexByHash(snapshots);
        Map<String, List<String>> childrenByHash = childrenByHash(snapshots);
        return snapshots.stream()
                .map(snapshot -> toCommitNode(snapshot, byHash, childrenByHash, snapshots))
                .toList();
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

    private CommitNode toCommitNode(CommitSnapshot snapshot, Map<String, CommitSnapshot> byHash, Map<String, List<String>> childrenByHash, Collection<CommitSnapshot> snapshots) {
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
        return new CommitNode(snapshot.hash(), snapshot.parents(), snapshot.authoredAt().getEpochSecond(), metrics);
    }

    private int graphDepth(String hash, Map<String, CommitSnapshot> byHash, Map<String, Integer> memo) {
        Integer cached = memo.get(hash);
        if (cached != null) {
            return cached;
        }
        CommitSnapshot snapshot = byHash.get(hash);
        if (snapshot == null || snapshot.parents().isEmpty()) {
            memo.put(hash, 0);
            return 0;
        }
        int depth = snapshot.parents().stream()
                .mapToInt(parent -> graphDepth(parent, byHash, memo))
                .max()
                .orElse(0) + 1;
        memo.put(hash, depth);
        return depth;
    }

    private int firstParentDistance(String hash, Map<String, CommitSnapshot> byHash, Map<String, Integer> memo) {
        CommitSnapshot snapshot = byHash.get(hash);
        if (snapshot == null || snapshot.parents().isEmpty()) {
            return 0;
        }
        String firstParent = snapshot.parents().getFirst();
        return 1 + firstParentDistance(firstParent, byHash, memo);
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
        collectAncestors(snapshot, byHash, ancestors);
        int count = ancestors.size();
        memo.put(hash, count);
        return count;
    }

    private int branchDepth(String hash, Map<String, CommitSnapshot> byHash, Map<String, List<String>> childrenByHash, Map<String, Integer> memo) {
        Integer cached = memo.get(hash);
        if (cached != null) {
            return cached;
        }
        CommitSnapshot snapshot = byHash.get(hash);
        if (snapshot == null || snapshot.parents().isEmpty()) {
            memo.put(hash, 0);
            return 0;
        }
        String firstParent = snapshot.parents().getFirst();
        CommitSnapshot parent = byHash.get(firstParent);
        if (parent == null) {
            memo.put(hash, 0);
            return 0;
        }
        int depth = childrenByHash.getOrDefault(firstParent, List.of()).size() > 1
                ? 1
                : 1 + branchDepth(firstParent, byHash, childrenByHash, memo);
        memo.put(hash, depth);
        return depth;
    }

    private void collectAncestors(CommitSnapshot snapshot, Map<String, CommitSnapshot> byHash, java.util.Set<String> ancestors) {
        for (String parentHash : snapshot.parents()) {
            if (ancestors.add(parentHash)) {
                CommitSnapshot parent = byHash.get(parentHash);
                if (parent != null) {
                    collectAncestors(parent, byHash, ancestors);
                }
            }
        }
    }

    private int descendantCount(String hash, Map<String, List<String>> childrenByHash, Map<String, Integer> memo) {
        Integer cached = memo.get(hash);
        if (cached != null) {
            return cached;
        }
        java.util.Set<String> descendants = new java.util.LinkedHashSet<>();
        collectDescendants(hash, childrenByHash, descendants);
        int count = descendants.size();
        memo.put(hash, count);
        return count;
    }

    private void collectDescendants(String hash, Map<String, List<String>> childrenByHash, java.util.Set<String> descendants) {
        for (String childHash : childrenByHash.getOrDefault(hash, List.of())) {
            if (descendants.add(childHash)) {
                collectDescendants(childHash, childrenByHash, descendants);
            }
        }
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
        CommitSnapshot snapshot = byHash.get(hash);
        if (snapshot == null) {
            return Instant.EPOCH;
        }
        List<String> children = childrenByHash.getOrDefault(hash, List.of());
        if (children.isEmpty()) {
            memo.put(hash, snapshot.authoredAt());
            return snapshot.authoredAt();
        }
        Instant farthest = snapshot.authoredAt();
        for (String childHash : children) {
            CommitSnapshot child = byHash.get(childHash);
            if (child == null) {
                continue;
            }
            Instant descendant = farthestDescendantTime(childHash, byHash, childrenByHash, memo);
            if (descendant.isAfter(farthest)) {
                farthest = descendant;
            }
        }
        memo.put(hash, farthest);
        return farthest;
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
        if (snapshot.parents().isEmpty()) {
            memo.put(snapshot.hash(), snapshot.authoredAt());
            return snapshot.authoredAt();
        }
        Instant earliest = snapshot.authoredAt();
        for (String parentHash : snapshot.parents()) {
            CommitSnapshot parent = byHash.get(parentHash);
            if (parent == null) {
                continue;
            }
            Instant parentEarliest = earliestAncestorTime(parent, byHash, memo);
            if (parentEarliest.isBefore(earliest)) {
                earliest = parentEarliest;
            }
        }
        memo.put(snapshot.hash(), earliest);
        return earliest;
    }

    public record CommitSnapshot(String hash, List<String> parents, Instant authoredAt) {
        public CommitSnapshot withParents(List<String> parents) {
            return new CommitSnapshot(hash, parents, authoredAt);
        }
    }
}
