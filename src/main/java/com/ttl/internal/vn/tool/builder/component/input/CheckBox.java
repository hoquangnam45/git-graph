package com.ttl.internal.vn.tool.builder.component.input;

import java.awt.event.ItemListener;
import java.util.function.BiFunction;

import javax.swing.GroupLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class CheckBox extends JPanel {
    private final JLabel label;
    private final JCheckBox innerCheckBox;

    public CheckBox(String label) {
        super();

        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);

        this.label = new JLabel(label);
        innerCheckBox = new JCheckBox();

        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(this.label)
                .addComponent(innerCheckBox));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addComponent(this.label)
                .addComponent(innerCheckBox));
    }

    public CheckBox(boolean selected, String label) {
        this(label);
        innerCheckBox.setSelected(selected);
    }

    public void addItemListener(ItemListener l) {
        innerCheckBox.addItemListener(l);
    }

    public boolean isSelected() {
        return innerCheckBox.isSelected();
    }

    public void customGroupLayout(BiFunction<JLabel, JCheckBox, GroupLayout> layoutCustomizer) {
        setLayout(layoutCustomizer.apply(label, innerCheckBox));
    }

    public void setSelected(boolean selected) {
        innerCheckBox.setSelected(selected);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        innerCheckBox.setEnabled(enabled);
    }
}
