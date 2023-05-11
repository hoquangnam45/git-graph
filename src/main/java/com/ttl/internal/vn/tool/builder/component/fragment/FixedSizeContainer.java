package com.ttl.internal.vn.tool.builder.component.fragment;

import java.awt.Dimension;

import javax.swing.JPanel;

public class FixedSizeContainer extends JPanel {
    public FixedSizeContainer(int width, int height) {
        super();
        setLayout(null);
        setMaximumSize(new Dimension(width, height));
        setMinimumSize(new Dimension(width, height));
        setSize(new Dimension(width, height));
    }
}
