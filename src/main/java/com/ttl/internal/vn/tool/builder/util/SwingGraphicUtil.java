package com.ttl.internal.vn.tool.builder.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;

public class SwingGraphicUtil {
    private static ExecutorService executorService = Executors.newFixedThreadPool(10);

    private SwingGraphicUtil() {
    }

    public static void updateUI(Runnable runnable) {
        SwingUtilities.invokeLater(runnable);
    }

    public static <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executorService);
    }

    public static CompletableFuture<Void> run(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executorService);
    }
}
