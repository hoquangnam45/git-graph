package com.ttl.internal.vn.tool.builder.task;

import java.util.Optional;
import java.util.concurrent.Flow;

import static org.eclipse.jgit.lib.ProgressMonitor.UNKNOWN;

public class GitCloneSubTask extends Task implements IGitTask {
    private int doneWork;

    private Flow.Subscriber<? super Task> subscriber;
    private TaskStatus status;
    private final int totalWork;
    private final String title;

    public GitCloneSubTask(String title, int totalWork) {
        super();
        this.totalWork = totalWork;
        this.title = title;
        this.status = TaskStatus.IN_PROGRESS;
    }

    @Override
    public void beginTask(GitCloneSubTask newSubTask) {
        /* noop */
    }

    @Override
    public boolean update(double doneWork) {
        if (isStop()) {
            return false;
        }
        this.doneWork += doneWork;
        if (this.doneWork == totalWork) {
            status = TaskStatus.DONE;
        }
        Optional.ofNullable(subscriber).ifPresent(l -> l.onNext(this));
        return true;
    }

    @Override
    public String explainTask() {
        return title;
    }

    @Override
    public int totalWork() {
        if (totalWork != UNKNOWN) {
            return totalWork;
        } else {
            return 1;
        }
    }

    @Override
    public int doneWork() {
        if (totalWork != UNKNOWN) {
            return doneWork;
        } else {
            return 0;
        }
    }

    @Override
    public int status() {
        return status.ordinal();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Task> subscriber) {
        this.subscriber = subscriber;
    }
}