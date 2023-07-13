package com.ttl.internal.vn.tool.builder.component.input;

import javax.swing.GroupLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

public class ProgressBar extends JPanel {
    private JLabel statusLabel;
    private JProgressBar innerProgressBar;

    public ProgressBar() {
        this(GroupLayout.Alignment.CENTER);
    }

    public ProgressBar(GroupLayout.Alignment alignment) {
        super();
        statusLabel = new JLabel();
        innerProgressBar = new JProgressBar();

        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setAutoCreateGaps(true);

        layout.setHorizontalGroup(layout.createParallelGroup()
                .addComponent(statusLabel, alignment)
                .addComponent(innerProgressBar));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addComponent(statusLabel)
                .addComponent(innerProgressBar));
    }

    public void setMaximum(int maximum) {
        innerProgressBar.setMaximum(maximum);
    }

    public void setValue(int value) {
        innerProgressBar.setValue(value);
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    public void clearStatus() {
        statusLabel.setText("");
    }
}
