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

    public static JPanel createHorizontalPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        return panel;
    }

    public static JPanel createVerticalPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    public static Component createPaddedComponent(Component component, Insets insets) {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right));
        panel.add(component);
        return panel;
    }

    public static JPanel wrapInJPanel(Component component) {
        JPanel panel = new JPanel();
        panel.add(component);
        return panel;
    }

    public static JPanelBuilder panelBuilder() {
        return new JPanelBuilder();
    }

    public static GridBagConstraintsBuilder gbcBuilder() {
        return new GridBagConstraintsBuilder();
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
    
    public static class JPanelBuilder {
        private LayoutManager layoutManager;
        private List<Component> components = new ArrayList<>();
        private List<Object> constraints = new ArrayList<>();
        private Border border;
        private Color background;
        private Dimension maximumSize;
        private Dimension minimumSize;
        private Dimension preferredSize;
        private Integer boxLayoutAxis;
        private Boolean useNullLayout;
        private Rectangle bounds;
        private Dimension size;

        public JPanelBuilder add(Component component, Object constraint) {
            components.add(component);
            constraints.add(constraint);
            return this;
        }

        public JPanelBuilder add(Component component) {
            return add(component, null);
        }

        public JPanelBuilder setLayout(LayoutManager layoutManager) {
            this.layoutManager = layoutManager;
            return this;
        }

        public JPanelBuilder setBoxLayout(int axis) {
            this.boxLayoutAxis = axis;
            this.layoutManager = null;
            return this;
        }

        public JPanelBuilder setBorder(Border border) {
            this.border = border;
            return this;
        }

        public JPanelBuilder setBackground(Color background) {
            this.background = background;
            return this;
        }

        public JPanelBuilder setMaximumSize(Dimension maximumSize) {
            this.maximumSize = maximumSize;
            return this;
        }

        public JPanelBuilder setMinimumSize(Dimension minimumSize) {
            this.minimumSize = minimumSize;
            return this;
        }

        public JPanelBuilder setPreferredSize(Dimension preferredSize) {
            this.preferredSize = preferredSize;
            return this;
        }

        public JPanelBuilder setNullLayout(boolean useNullLayout) {
            this.useNullLayout = useNullLayout;
            return this;
        }

        public JPanelBuilder setBounds(Rectangle r) {
            this.bounds = r;
            return this;
        }
        
        public JPanelBuilder setSize(Dimension size) {
            this.size = size;
            return this;
        }

        public JPanel build() {
            JPanel panel = new JPanel();
            Optional.ofNullable(boxLayoutAxis).map(it -> new BoxLayout(panel, it)).ifPresent(panel::setLayout);
            Optional.ofNullable(background).ifPresent(panel::setBackground);
            Optional.ofNullable(layoutManager).ifPresent(panel::setLayout);
            Optional.ofNullable(useNullLayout).filter(it -> it).ifPresent(it -> panel.setLayout(null));
            Optional.ofNullable(maximumSize).ifPresent(panel::setMaximumSize);
            Optional.ofNullable(minimumSize).ifPresent(panel::setMinimumSize);
            Optional.ofNullable(preferredSize).ifPresent(panel::setPreferredSize);
            Optional.ofNullable(size).ifPresent(panel::setSize);
            Optional.ofNullable(border).ifPresent(panel::setBorder);
            Optional.ofNullable(bounds).ifPresent(panel::setBounds);
            for (int i = 0; i < components.size(); i++) {
                if (constraints.get(i) != null) {
                    panel.add(components.get(i), constraints.get(i));
                } else {
                    panel.add(components.get(i));
                }
            }
                return panel;
        }
    }

    public static class GridBagConstraintsBuilder {
        private Integer gridx;
        private Integer gridy;
        private Insets insets;
        private Integer anchor;
        private Integer gridheight;
        private Integer gridwidth;
        private Integer fill;
        private Integer ipadx;
        private Integer ipady;
        private Double weightx;
        private Double weighty;

        public GridBagConstraintsBuilder gridx(int gridx) {
            this.gridx = gridx;
            return this;
        }

        public GridBagConstraintsBuilder gridy(int gridy) {
            this.gridy = gridy;
            return this;
        }

        public GridBagConstraintsBuilder insets(Insets insets) {
            this.insets = insets;
            return this;
        }

        public GridBagConstraintsBuilder anchor(int anchor) {
            this.anchor = anchor;
            return this;
        }

        public GridBagConstraintsBuilder gridheight(int gridheight) {
            this.gridheight = gridheight;
            return this;
        }

        public GridBagConstraintsBuilder gridwidth(int gridwidth) {
            this.gridwidth = gridwidth;
            return this;
        }

        public GridBagConstraintsBuilder fill(int fill) {
            this.fill = fill;
            return this;
        }

        public GridBagConstraintsBuilder ipadx(int ipadx) {
            this.ipadx = ipadx;
            return this;
        }

        public GridBagConstraintsBuilder ipady(int ipady) {
            this.ipady = ipady;
            return this;
        }

        public GridBagConstraintsBuilder weightx(double weightx) {
            this.weightx = weightx;
            return this;
        }

        public GridBagConstraintsBuilder weighty(double weighty) {
            this.weighty = weighty;
            return this;
        }

        public GridBagConstraints build() {
            GridBagConstraints gbc = new GridBagConstraints();
            Optional.ofNullable(gridx).ifPresent(it -> gbc.gridx = it);
            Optional.ofNullable(gridy).ifPresent(it -> gbc.gridy = it);
            Optional.ofNullable(insets).ifPresent(it -> gbc.insets = it);
            Optional.ofNullable(anchor).ifPresent(it -> gbc.anchor = it);
            Optional.ofNullable(gridheight).ifPresent(it -> gbc.gridheight = it);
            Optional.ofNullable(gridwidth).ifPresent(it -> gbc.gridwidth = it);
            Optional.ofNullable(fill).ifPresent(it -> gbc.fill = it);
            Optional.ofNullable(ipadx).ifPresent(it -> gbc.ipadx = it);
            Optional.ofNullable(ipady).ifPresent(it -> gbc.ipady = it);
            Optional.ofNullable(weightx).ifPresent(it -> gbc.weightx = it);
            Optional.ofNullable(weighty).ifPresent(it -> gbc.weighty = it);
            return gbc;
        }
    }
}
