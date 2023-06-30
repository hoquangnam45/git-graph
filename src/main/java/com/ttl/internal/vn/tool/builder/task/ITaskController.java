package com.ttl.internal.vn.tool.builder.task;

public interface ITaskController {
    // Control the task
    default boolean cancel() throws Exception { 
        throw new UnsupportedOperationException(); 
    }
    default boolean pause() throws Exception {
        throw new UnsupportedOperationException();
    }
    default boolean resume() throws Exception {
        throw new UnsupportedOperationException();
    }
    default boolean done() throws Exception {
        throw new UnsupportedOperationException();
    }
    default boolean stopExceptionally(Throwable e) throws Exception {
        throw new UnsupportedOperationException();
    }
    default boolean start() throws Exception {
        throw new UnsupportedOperationException();
    }
    default boolean update(int doneWork) {
        throw new UnsupportedOperationException();
    }
}
