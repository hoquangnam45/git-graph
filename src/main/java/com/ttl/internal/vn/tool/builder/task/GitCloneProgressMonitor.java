package com.ttl.internal.vn.tool.builder.task;

import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;

import java.io.Writer;

public class GitCloneProgressMonitor implements ProgressMonitor {
    private final IGitTask gitTask;
    private final TextProgressMonitor textProgressMonitor;

    public GitCloneProgressMonitor(IGitTask gitTask, Writer outputWriter) {
        this.gitTask = gitTask;
        this.textProgressMonitor = new TextProgressMonitor(outputWriter);
    }

    @Override
    public void beginTask(String title, int totalWork) {
        GitCloneSubTask newSubTask = new GitCloneSubTask(title, totalWork);
        gitTask.beginTask(newSubTask);
        textProgressMonitor.beginTask(title, totalWork);
    }

    @Override
    public void update(int completed) {
        gitTask.update(completed);
        textProgressMonitor.update(completed);
    }

    @Override
    public void endTask() {
        textProgressMonitor.endTask();
    }

    @Override
    public void start(int totalTasks) {
        textProgressMonitor.start(totalTasks);
    }

    @Override
    public boolean isCancelled() {
        return gitTask.isCancelled();
    }

    @Override
    public void showDuration(boolean enabled) {
        textProgressMonitor.showDuration(enabled);
    }
}
