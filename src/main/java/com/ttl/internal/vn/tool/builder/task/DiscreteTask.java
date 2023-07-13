package com.ttl.internal.vn.tool.builder.task;

import java.util.Optional;
import java.util.concurrent.Flow;

public abstract class DiscreteTask extends Task implements ITaskController {
    protected String explainTask;
    protected Flow.Subscriber<? super Task> subscriber;

    public DiscreteTask(String explainTask) {
        this.explainTask = explainTask;
    }

    public DiscreteTask() {
    }

    @Override
    public boolean cancel() {
        cancel = true;
        inProgress = false;
        return true;
    }

    @Override
    public boolean done() {
        done = true;
        inProgress = false;
        Optional.ofNullable(subscriber).ifPresent(Flow.Subscriber::onComplete);
        return true;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Task> subscriber) {
        this.subscriber = subscriber;
    }

    @Override
    public String explainTask() {
        return explainTask;
    }

    @Override
    public int totalWork() {
        return 1;
    }

    @Override
    public int doneWork() {
        return isDone() ? totalWork() : 0;
    }

    @Override
    public boolean stopExceptionally(Throwable e) {
        cancel();
        error = true;
        Optional.ofNullable(subscriber).ifPresent(it -> it.onError(e));
        return true;
    }
}
