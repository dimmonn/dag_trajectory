package com.uj.graph.dag_trajectory.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.springframework.stereotype.Service;

@Service
public class SzzLineBlamer {

    public BlameOutcome blame(Path repositoryPath, String fixingCommitHash) {
        try (Git git = Git.open(repositoryPath.toFile());
             RevWalk walk = new RevWalk(git.getRepository())) {
            RevCommit fixingCommit = walk.parseCommit(git.getRepository().resolve(fixingCommitHash));
            if (fixingCommit.getParentCount() != 1) {
                return new BlameOutcome(true, List.of());
            }

            RevCommit parent = walk.parseCommit(fixingCommit.getParent(0).getId());
            Map<FindingKey, Integer> deletedLineCounts = new HashMap<>();

            try (DiffFormatter formatter = new DiffFormatter(org.eclipse.jgit.util.io.DisabledOutputStream.INSTANCE)) {
                formatter.setRepository(git.getRepository());
                formatter.setDetectRenames(true);

                for (DiffEntry entry : formatter.scan(parent, fixingCommit)) {
                    if (DiffEntry.DEV_NULL.equals(entry.getOldPath())) {
                        continue;
                    }

                    FileHeader fileHeader = formatter.toFileHeader(entry);
                    List<Edit> edits = fileHeader.toEditList();
                    if (edits.isEmpty()) {
                        continue;
                    }

                    var blame = new BlameCommand(git.getRepository())
                            .setStartCommit(parent)
                            .setFilePath(entry.getOldPath())
                            .call();
                    if (blame == null) {
                        continue;
                    }

                    for (Edit edit : edits) {
                        for (int oldLine = edit.getBeginA(); oldLine < edit.getEndA(); oldLine++) {
                            RevCommit source = blame.getSourceCommit(oldLine);
                            if (source != null) {
                                deletedLineCounts.merge(new FindingKey(source.getName(), entry.getOldPath()), 1, Integer::sum);
                            }
                        }
                    }
                }
            }

            List<BlamedFile> findings = deletedLineCounts.entrySet().stream()
                    .map(entry -> new BlamedFile(entry.getKey().bicCommitHash(), entry.getKey().filePath(), entry.getValue()))
                    .sorted(Comparator.comparing(BlamedFile::bicCommitHash).thenComparing(BlamedFile::filePath))
                    .toList();
            return new BlameOutcome(false, findings);
        } catch (IOException e) {
            throw new IllegalStateException("SZZ could not inspect fixing commit " + fixingCommitHash
                    + " in " + repositoryPath, e);
        } catch (Exception e) {
            throw new IllegalStateException("SZZ failed for fixing commit " + fixingCommitHash
                    + " in " + repositoryPath, e);
        }
    }

    private record FindingKey(String bicCommitHash, String filePath) {}
    public record BlamedFile(String bicCommitHash, String filePath, int deletedLines) {}
    public record BlameOutcome(boolean skippedMergeCommit, List<BlamedFile> findings) {}
}
