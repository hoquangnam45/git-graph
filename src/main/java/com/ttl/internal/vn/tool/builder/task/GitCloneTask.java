package com.ttl.internal.vn.tool.builder.task;

import com.ttl.internal.vn.tool.builder.util.GitUtil;
import com.ttl.internal.vn.tool.builder.util.SwingGraphicUtil;
import lombok.Getter;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.util.Optional;
import java.util.Vector;
import java.util.concurrent.Flow;

@Getter
public class GitCloneTask extends AbstractGitTask {
    private final String username;
    private final String password;
    private final String uri;
    private final File targetFolder;

    public GitCloneTask(String username, String password, String uri, File targetFolder) {
        this.subtasks = new Vector<>();
        this.username = username;
        this.password = password;
        this.uri = uri;
        this.targetFolder = targetFolder;
        boolean isEmptyFolder = Optional.ofNullable(targetFolder.listFiles()).map(files -> files.length).map(l -> l == 0).orElse(true);
        if (!isEmptyFolder) {
            throw new UnsupportedOperationException("Selected folder is not empty");
        }
    }

    @Override
    public boolean start() throws GitAPIException {
        GitUtil.cloneGitRepo(uri, targetFolder, username, password, progressMonitor);
        done();
        return true;
    }
}