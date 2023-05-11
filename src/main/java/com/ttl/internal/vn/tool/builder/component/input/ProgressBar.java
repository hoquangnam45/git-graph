package com.ttl.internal.vn.tool.builder.component.input;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.GroupLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

public class ProgressBar extends JPanel {
    private JLabel statusLabel;
    private JProgressBar progressBar;

    public ProgressBar() {
        super();
        statusLabel = new JLabel();
        progressBar = new JProgressBar();

        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setAutoCreateGaps(true);
        
        layout.setHorizontalGroup(layout.createParallelGroup()
            .addComponent(statusLabel, GroupLayout.Alignment.CENTER)
            .addComponent(progressBar)
        );

        layout.setVerticalGroup(layout.createSequentialGroup()
            .addComponent(statusLabel)
            .addComponent(progressBar));
    }

    public void setMaximum(int maximum) {
        progressBar.setMaximum(maximum);
    }
    
    public void setValue(int value) {
        progressBar.setValue(value);
    }
    
    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    public void clearStatus() {
        statusLabel.setText("");
    }
}
