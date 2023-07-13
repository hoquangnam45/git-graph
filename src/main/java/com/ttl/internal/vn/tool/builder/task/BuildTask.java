package com.ttl.internal.vn.tool.builder.task;

import com.ttl.internal.vn.tool.builder.task.ITask;
import com.ttl.internal.vn.tool.builder.task.ITaskController;
import com.ttl.internal.vn.tool.builder.task.Task;

import java.util.*;
import java.util.concurrent.Flow;

import static java.util.function.Predicate.not;


public abstract class BuildTask extends DiscreteTask {
    private final List<DiscreteTask> tasks = new ArrayList<>();
    private Object buildCtx;
    private DiscreteTask currentTask;

    public void setBuildCtx(Object buildCtx) {
        this.buildCtx = buildCtx;
    }

    public Object getBuildCtx() {
        return buildCtx;
    }

    public List<DiscreteTask> getSubtasks() {
        return tasks;
    }

    public abstract void cleanup() throws Exception;

    public void addSubTask(DiscreteTask task) {
        task.subscribe(new DefaultSubscriber<>() {
            @Override
            public void onNext(Task item) {
                Optional.ofNullable(subscriber).ifPresent(it -> it.onNext(item));
            }

            @Override
            public void onError(Throwable throwable) {
                stopExceptionally(throwable);
            }
        });
        tasks.add(task);
    }

    @Override
    public boolean start() {
        try {
            for (DiscreteTask task : tasks) {
                try {
                    currentTask = task;
                    Optional.ofNullable(subscriber).ifPresent(it -> it.onNext(task));
                    task.start();
                    if (task.isCancelled()) {
                        Optional.ofNullable(subscriber).ifPresent(Flow.Subscriber::onComplete);
                        return false;
                    }
                    task.done();
                } catch (Exception e) {
                    stopExceptionally(e);
                    return false;
                }
            }
            done();
            return true;
        } finally {
            try {
                cleanup();
            } catch (Exception e) {
                stopExceptionally(e);
            }
        }
    }

    @Override
    public boolean stopExceptionally(Throwable e) {
        cancel();
        error = true;
        tasks.stream().filter(not(Task::isStop)).forEach(task -> {
            try {
                task.cancel();
            } catch (Exception ex) {
                throw new IllegalStateException("Stop exceptionally failed for task  [" + task.explainTask() + "]");
            }
        });
        Optional.ofNullable(subscriber).ifPresent(iit -> iit.onError(e));
        return true;
    }

    @Override
    public boolean cancel() {
        tasks.forEach(task -> {
            try {
                task.cancel();
            } catch (Exception e) {
                throw new IllegalStateException("Cancel failed for task [" + task.explainTask() + "]");
            }
        });
        inProgress = false;
        cancel = true;
        return true;
    }

    @Override
    public String explainTask() {
        return currentTask.explainTask();
    }

    @Override
    public double percentage() {
        double totalScaling = tasks.stream().map(Task::scaling).reduce(0., Double::sum);
        double doneScaling = tasks.stream().map(it -> it.scaling() * it.percentage()).reduce(0., Double::sum);
        return doneScaling / totalScaling;
    }

    @Override
    public int totalWork() {
        return tasks.stream().map(DiscreteTask::totalWork)
                .reduce(0, Integer::sum);
    }

    @Override
    public int doneWork() {
        return tasks.stream()
                .map(DiscreteTask::doneWork)
                .reduce(0, Integer::sum);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Task> subscriber) {
        this.subscriber = subscriber;
    }
}
