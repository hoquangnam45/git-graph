package com.ttl.internal.vn.tool.builder.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Vector;
import java.util.function.Consumer;

@AllArgsConstructor
@NoArgsConstructor
public abstract class Task implements ITask {
    private String name;
    
    @Override
    public String name() {
        return name;
    }

    @Override
    public double percentage() {
        if (status() == TaskStatus.DONE.ordinal()) {
            return 100.;
        }
        int totalWork = totalWork();
        if (totalWork <= 0) {
            return 0;
        }
        int doneWork = doneWork();
        return 100. * doneWork / totalWork;
    }
    
    @Override
    public int remainingWork() {
        return totalWork() - doneWork();
    }

    @Override
    public double scaling() {
        return 1.;
    }
    
    public boolean isStop() {
        TaskStatus status = TaskStatus.values()[status()];
        return status == TaskStatus.ERROR || status == TaskStatus.CANCEL || status == TaskStatus.DONE;
    }

    public enum TaskStatus {
        DONE,
        ERROR,
        CANCEL,
        IN_PROGRESS,
        NOT_START
    }
}
