package com.ttl.internal.vn.tool.builder.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

import com.ttl.internal.vn.tool.builder.util.GitUtil;

public class GitWalk implements Iterator<GitCommit> {
    private List<GitRef> selectedBranches;
    private List<GitCommit> selectedCommits;
    private final GitUtil gitUtil;
    private Iterator<RevCommit> iterator;

    public GitWalk(GitUtil gitUtil) {
        this.gitUtil = gitUtil;
    }

    public GitCommit walk() {
        return next();
    }

    public GitCommit next() {
        if (hasNext()) {
            try {
                return gitUtil.comprehendCommit(iterator.next());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public boolean hasNext() {
        return iterator.hasNext();
    }

    public void resetWalk() throws IOException {
        try (RevWalk revWalk = gitUtil.getRevWalk()) {
            for (GitRef gitRef : selectedBranches) {
                RevCommit commit = revWalk.parseCommit(gitUtil.resolve(gitRef.getRawRef().getName()));
                revWalk.markStart(commit);
            }
            for (GitCommit gitCommit: selectedCommits) {
                RevCommit commit =  revWalk.parseCommit(gitUtil.resolve(gitCommit.getHash()));
                revWalk.markStart(commit);
            }
            revWalk.setRevFilter(null);
            revWalk.sort(RevSort.TOPO);
            revWalk.sort(RevSort.COMMIT_TIME_DESC, true);
            this.iterator = revWalk.iterator();
        }
    }

    private List<GitRef> getRefs(List<String> branchNames) throws IOException {
        List<GitRef> refs = new ArrayList<>();
        for (String branchName : branchNames) {
            refs.add(gitUtil.findRef(branchName));
        }
        return refs;
    }
    
    private List<GitCommit> getCommits(List<String> commitHashes) throws IOException {
        List<GitCommit> commits = new ArrayList<>();
        for (String commitHash: commitHashes) {
            commits.add(gitUtil.fromHash(commitHash));
        }
        return commits;
    }

    public void setGitBranch(List<String> branchNames) throws IOException {
        this.selectedBranches = getRefs(branchNames);
        this.selectedCommits = List.of();
        resetWalk();
    }
    
    public void setGitCommit(List<String> commitHashes) throws IOException {
        this.selectedBranches = List.of();
        this.selectedCommits = getCommits(commitHashes);
        resetWalk();
    }
}
