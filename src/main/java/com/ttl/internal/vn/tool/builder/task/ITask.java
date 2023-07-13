package com.ttl.internal.vn.tool.builder.task;

import java.util.concurrent.Flow;

public interface ITask extends Flow.Publisher<Task> {
    // Description of what the task is processing
    String explainTask();

    // How much percentage is done, in the ideal case = doneWork() / totalWork() * 100
    double percentage();

    // Total amount unit of work
    int totalWork();

    // Total amount unit of work done
    int doneWork();

    // Scaling the percentage of this task in relation to other tasks
    double scaling();

    // What is the task status
    int status();

    boolean isStop();
    boolean isInProgress();
    boolean isCancelled();
    boolean isDone();
    boolean isError();

    // When the task is not done, this should return null
    // Or if the task is used for pure side-effects then it should also return null
    Object getResult();
}
