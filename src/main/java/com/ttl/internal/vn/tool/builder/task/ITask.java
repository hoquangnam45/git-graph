package com.ttl.internal.vn.tool.builder.task;

import java.util.concurrent.Flow;

public interface ITask extends Flow.Publisher<Task> {
    // Name of the task
    String name();

    // Description of what the task is processing
    String explainTask();

    // How much percentage is done, in the ideal case = doneWork() / totalWork() *
    // 100
    double percentage();

    // Total amount unit of work
    int totalWork();

    // Total amount unit of work done
    int doneWork();

    // Total amount unit of work remaining
    int remainingWork();

    // Scaling the task in relation to other task that exist
    double scaling();

    // What is the task status
    int status();

    boolean isStop();
}
