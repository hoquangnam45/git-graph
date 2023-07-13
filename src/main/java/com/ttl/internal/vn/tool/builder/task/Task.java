package com.ttl.internal.vn.tool.builder.task;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public abstract class Task implements ITask {
    protected boolean cancel;
    protected boolean error;
    protected boolean done;
    protected boolean inProgress;

    @Override
    public double percentage() {
        if (status() == TaskStatus.DONE.ordinal()) {
            return 1.;
        }
        int totalWork = totalWork();
        if (totalWork <= 0.) {
            return 0;
        }
        int doneWork = doneWork();
        return (double) doneWork / totalWork;
    }

    @Override
    public boolean isStop() {
        TaskStatus status = TaskStatus.values()[status()];
        return status == TaskStatus.ERROR || status == TaskStatus.CANCEL || status == TaskStatus.DONE;
    }

    @Override
    public boolean isError() {
        TaskStatus status = TaskStatus.values()[status()];
        return status == TaskStatus.ERROR;
    }

    @Override
    public boolean isInProgress() {
        TaskStatus status = TaskStatus.values()[status()];
        return status == TaskStatus.IN_PROGRESS;
    }

    @Override
    public boolean isCancelled() {
        TaskStatus status = TaskStatus.values()[status()];
        return status == TaskStatus.CANCEL;
    }

    @Override
    public boolean isDone() {
        TaskStatus status = TaskStatus.values()[status()];
        return status == TaskStatus.DONE;
    }

    @Override
    public int status() {
        if (error) {
            return TaskStatus.ERROR.ordinal();
        }
        if (cancel) {
            return TaskStatus.CANCEL.ordinal();
        }
        if (done) {
            return TaskStatus.DONE.ordinal();
        }
        if (inProgress) {
            return TaskStatus.IN_PROGRESS.ordinal();
        }
        return TaskStatus.NOT_START.ordinal();
    }

    @Override
    public double scaling() {
        return 1.;
    }

    @Override
    public Object getResult() {
        return null;
    }

    public enum TaskStatus {
        DONE,
        ERROR,
        CANCEL,
        IN_PROGRESS,
        NOT_START
    }
}
