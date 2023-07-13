package com.ttl.internal.vn.tool.builder.task;

public interface IGitTask extends ITask {
    void beginTask(GitCloneSubTask newSubTask);

    boolean update(double doneWork);
}
