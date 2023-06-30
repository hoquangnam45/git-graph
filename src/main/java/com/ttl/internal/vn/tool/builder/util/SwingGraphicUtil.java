package com.ttl.internal.vn.tool.builder.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

public class SwingGraphicUtil {
    private static ExecutorService executorService = Executors.newFixedThreadPool(10);

    private SwingGraphicUtil() {}

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
