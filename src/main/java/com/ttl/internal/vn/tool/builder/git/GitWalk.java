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
    private List<GitRef> selectedBranchs;
    private final GitUtil gitUtil;
    private Iterator<RevCommit> iterator;

    public GitWalk(GitUtil gitUtil, List<String> selectedBranchs) throws IOException {
        this.gitUtil = gitUtil;
        this.selectedBranchs = getRefs(selectedBranchs);
        resetWalk();
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
            for (GitRef gitRef : selectedBranchs) {
                RevCommit commit = revWalk.parseCommit(gitRef.getRawRef().getObjectId());
                revWalk.markStart(commit);
                revWalk.setRevFilter(null);
                revWalk.sort(RevSort.TOPO);
                revWalk.sort(RevSort.COMMIT_TIME_DESC, true);
            }
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

    public void setGitBranch(List<String> branchNames) throws IOException {
        this.selectedBranchs = getRefs(branchNames);
        resetWalk();
    }
}
