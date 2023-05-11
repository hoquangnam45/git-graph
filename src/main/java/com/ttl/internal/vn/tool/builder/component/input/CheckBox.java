package com.ttl.internal.vn.tool.builder.component.input;

import java.awt.event.ActionListener;
import java.awt.event.ItemListener;

import javax.swing.GroupLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class CheckBox extends JPanel {
    private JLabel label;
    private JCheckBox checkBox;

    public CheckBox(String label) {
        super();

        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);

        this.label = new JLabel(label);
        checkBox = new JCheckBox();
        
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(this.label)
            .addComponent(checkBox)
        );

        layout.setVerticalGroup(layout.createSequentialGroup()
            .addComponent(this.label)
            .addComponent(checkBox)
        );
    }

    public CheckBox(boolean selected, String label) {
        this(label);
        checkBox.setSelected(selected);
    }
    
    public void addItemListener(ItemListener l) {
        checkBox.addItemListener(l);
    }

    public boolean isSelected() {
        return checkBox.isSelected();
    }

    public void setSelected(boolean selected) {
        checkBox.setSelected(selected);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        checkBox.setEnabled(enabled);
    }
}
