package com.ttl.internal.vn.tool.builder.task;

// Control the task
public interface ITaskController {
    // Mark this task as cancelled
    boolean cancel() throws Exception;

    // Cancel this task and mark it as error
    boolean stopExceptionally(Throwable e) throws Throwable;

    // Mark this task as in progress and run it
    boolean start() throws Exception;

    // Mark this task as done
    boolean done() throws Exception;
}
