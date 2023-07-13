package com.ttl.internal.vn.tool.builder.task;

import org.apache.maven.shared.invoker.InvocationOutputHandler;
import org.apache.maven.shared.invoker.SystemOutHandler;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// NOTE: It's kind of hard to figure out how much work percentage maven had done, so the percentage
// of this task will parse the output of maven in the format of to provide some percentage
public abstract class MavenTask extends DiscreteTask {
    private final SystemOutHandler systemOutHandler = new SystemOutHandler();
    protected Object result;

    private int doneWork;
    private int totalWork;

    public InvocationOutputHandler getOutputHandler() {
        return s -> {
            systemOutHandler.consumeLine(s);
            explainTask = s;

            // NOTE: Filter this format [INFO] Building <moduleName> [moduleNumber/totalModule]
            Pattern pattern = Pattern.compile("\\[INFO] (Building .+)\\[(\\d+)/(\\d+)]");
            Matcher matcher = pattern.matcher(s);
            if (matcher.find()) {
                explainTask = "Calculate classpath of project (" + matcher.group(1).trim() + ")";
                doneWork = Integer.parseInt(matcher.group(2));
                totalWork = Integer.parseInt(matcher.group(3));
                Optional.ofNullable(subscriber).ifPresent(it -> it.onNext(this));
            }
        };
    }

    public Object getResult() {
        return result;
    }

    @Override
    public int doneWork() {
        return doneWork;
    }

    @Override
    public int totalWork() {
        return totalWork;
    }

    @Override
    public double percentage() {
        return 1. * doneWork / totalWork;
    }
}
