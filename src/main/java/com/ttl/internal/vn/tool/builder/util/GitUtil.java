package com.ttl.internal.vn.tool.builder.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import com.ttl.internal.vn.tool.builder.git.GitCommit;
import com.ttl.internal.vn.tool.builder.git.GitRef;
import com.ttl.internal.vn.tool.builder.git.GitWalk;

public class GitUtil implements AutoCloseable {
    private final Git git;

    public GitUtil(Git git) {
        this.git = git;
    }

    public Git getGit() {
        return git;
    }

    public Repository getRepository() {
        return git.getRepository();
    }

    public AnyObjectId resolve(String rev) throws RevisionSyntaxException, IOException {
        return git.getRepository().resolve(rev);
    }

    public RevCommit stashChange() throws GitAPIException {
        return git.stashCreate().setIncludeUntracked(true).call();
    }

    public RevWalk getRevWalk() {
        return new RevWalk(git.getRepository());
    }

    public void checkout(RevCommit commit) throws GitAPIException {
        git.checkout().setName(commit.getName()).call();
    }

    public void checkout(String refName) throws GitAPIException {
        git.checkout().setName(refName).call();
    }

    public void checkout(GitCommit gitCommit) throws GitAPIException {
        git.checkout().setName(gitCommit.getHash()).call();
    }

    public RevCommit checkoutAndStash(String target) throws RevisionSyntaxException, IOException, GitAPIException {
        try (RevWalk revWalk = getRevWalk()) {
            RevCommit headRevCommit = revWalk.parseCommit(resolve("HEAD"));
            RevCommit targetRevCommit = revWalk.parseCommit(resolve(target));
            RevCommit newStashedCommit = stashChange();
            if (!headRevCommit.equals(targetRevCommit)) {
                // Checkout the target commit
                checkout(target); // Keep HEAD name ref
            }
            return newStashedCommit;
        }
    }

    public void checkoutAndStashApply(String target) throws GitAPIException, RevisionSyntaxException, MissingObjectException, IncorrectObjectTypeException, IOException {
        RevCommit newStashedCommit = null;
        try (RevWalk revWalk = getRevWalk()) {
            RevCommit headRevCommit = revWalk.parseCommit(resolve("HEAD"));
            RevCommit targetRevCommit = revWalk.parseCommit(resolve(target));
            newStashedCommit = stashChange();
            if (!headRevCommit.equals(targetRevCommit)) {
                // Checkout the target commit
                checkout(target); // Keep HEAD name ref
            }
        } finally {
            if (newStashedCommit != null) {
                stashApply(newStashedCommit);
            }
        }
    }

    public void stashApply(RevCommit stashedCommit) throws GitAPIException {
        if (stashedCommit != null) {
            git.stashApply().setStashRef(stashedCommit.getName()).setRestoreUntracked(true)
                    .setRestoreIndex(true)
                    .ignoreRepositoryState(false).call();
        }
    }

    public GitRef findRef(String revString) throws IOException {
        return new GitRef(git.getRepository().findRef(revString));
    }

    public GitRef getHeadRef() throws IOException {
        return findRef("HEAD");
    }

    public void fetch(String username, String password, ProgressMonitor progressMonitor) throws GitAPIException {
        git.fetch()
            .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
            .setProgressMonitor(progressMonitor)
            .call();
    }

    public void fetch(String username, String password) throws GitAPIException {
        fetch(username, password, new TextProgressMonitor(new PrintWriter(System.out)));
    }

    public GitWalk produceWalker(List<String> selectedBranches) throws IOException {
        return new GitWalk(this, selectedBranches);
    }

    public List<DiffEntry> getDiff(String baseCommitHash, String targetCommitHash) throws IOException {
        try (
                var diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
                var revWalk = new RevWalk(git.getRepository());) {
            diffFormatter.setRepository(git.getRepository());
            diffFormatter.setDetectRenames(true);
            var baseCommit = revWalk.parseCommit(ObjectId.fromString(baseCommitHash));
            var targetCommit = revWalk.parseCommit(ObjectId.fromString(targetCommitHash));
            return diffFormatter.scan(baseCommit, targetCommit);
        }
    }

    public GitCommit fromHash(String hash) throws IOException {
        try (var revWalk = new RevWalk(git.getRepository())) {
            var objectId = ObjectId.fromString(hash);
            var revCommit = revWalk.parseCommit(objectId);
            return comprehendCommit(revCommit);
        }
    }

    public GitCommit comprehendCommit(RevCommit commit) throws IOException {
        PersonIdent author = commit.getAuthorIdent();
        PersonIdent committer = commit.getCommitterIdent();
        List<String> parentHashs = Stream.of(commit.getParents()).map(it -> it.getId().getName())
                .collect(Collectors.toList());
        List<GitRef> refs = git.getRepository().getAllRefsByPeeledObjectId().getOrDefault(commit.getId(), Set.<Ref>of())
                .stream().map(GitRef::new).collect(Collectors.toList());
        
        return GitCommit.builder()
                .hash(commit.getId().getName())
                .message(commit.getFullMessage())
                .author(author)
                .committer(committer)
                .parentHashs(parentHashs)
                .refs(refs)
                .authorTime(author.getWhen())
                .commitTime(committer.getWhen())
                .build();
    }

    public List<String> getBranches(boolean remoteOnly) throws GitAPIException {
        return getBranchRefs(remoteOnly)
                .stream()
                .map(Ref::getName)
                .map(it -> it.split("/"))
                .map(it -> it[it.length - 1])
                .collect(Collectors.toList());
    }

    public List<Ref> getBranchRefs(boolean remoteOnly) throws GitAPIException {
        return git.branchList().setListMode(remoteOnly ? ListMode.REMOTE : ListMode.ALL).call();
    }

    public static Git cloneGitRepo(String uri, File targetDir, String username, String password)
            throws GitAPIException {
        return cloneGitRepo(uri, targetDir, username, password, new TextProgressMonitor(new PrintWriter(System.out)));
    }

    public static Git cloneGitRepo(String uri, File targetDir, String username, String password,
            ProgressMonitor progressMonitor)
            throws GitAPIException {
        CloneCommand command = Git.cloneRepository().setURI(uri)
                .setProgressMonitor(progressMonitor)
                .setDirectory(targetDir);
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            command = command.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password));
        }
        return command.call();
    }

    public static Git openLocalRepo(File gitFolder) throws IOException {
        return Git.open(gitFolder);
    }

    @Override
    public void close() throws IOException {
        git.close();
    }
}