package com.ttl.internal.vn.tool.builder.task;

import com.ttl.internal.vn.tool.builder.util.GitUtil;
import lombok.Getter;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.util.Optional;
import java.util.Vector;
import java.util.concurrent.Flow;

@Getter
public class GitFetchTask extends AbstractGitTask {
    private final String username;
    private final String password;
    private final GitUtil gitUtil;

    public GitFetchTask(GitUtil gitUtil, String username, String password) {
        this.subtasks = new Vector<>();
        this.username = username;
        this.password = password;
        this.gitUtil = gitUtil;
    }

    @Override
    public boolean start() throws GitAPIException {
        gitUtil.fetch(username, password, progressMonitor);
        done();
        return true;
    }
}