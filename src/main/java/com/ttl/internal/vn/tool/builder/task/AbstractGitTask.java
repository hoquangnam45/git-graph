package com.ttl.internal.vn.tool.builder.task;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;

@Getter
public abstract class AbstractGitTask extends DiscreteTask implements ITaskController, IGitTask {
    protected GitCloneProgressMonitor progressMonitor;
    protected List<GitCloneSubTask> subtasks = new ArrayList<>();
    protected GitCloneSubTask currentTask;

    @Override
    public void beginTask(GitCloneSubTask currentTask) {
        this.currentTask = currentTask;
        this.subtasks.add(currentTask);
    }

    public void setProgressMonitor(GitCloneProgressMonitor progressMonitor) {
        this.progressMonitor = progressMonitor;
    }

    @Override
    public String explainTask() {
        return Optional.ofNullable(currentTask).map(Task::explainTask).orElse(null);
    }

    @Override
    public int totalWork() {
        return subtasks.stream().map(GitCloneSubTask::totalWork).reduce(0, Integer::sum);
    }

    @Override
    public int doneWork() {
        return subtasks.stream()
                .map(GitCloneSubTask::doneWork)
                .reduce(0, Integer::sum);
    }

    @Override
    public boolean cancel() {
        // NOTE: Settting cancel will cause side-effect to ProgressMonitor
        cancel = true;
        inProgress = false;
        return true;
    }

    @Override
    public double percentage() {
        double totalScaling = subtasks.stream().map(Task::scaling).reduce(0., Double::sum);
        double doneScaling = subtasks.stream().map(it -> it.scaling() * it.percentage()).reduce(0., Double::sum);
        return doneScaling / totalScaling;
    }

    @Override
    public boolean update(double doneWork) {
        Optional.ofNullable(currentTask).ifPresent(it -> {
            it.update(doneWork);
            Optional.ofNullable(subscriber).ifPresent(iit -> iit.onNext(it));
        });
        return true;
    }
}
