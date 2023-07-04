package com.ttl.internal.vn.tool.builder.component.input;

import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.util.List;
import java.util.Vector;

import javax.swing.GroupLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

public class ComboBox<E> extends JPanel {
    private JComboBox<E> innerComboBox;
    private JLabel label;

    public ComboBox(String label, ListCellRenderer<? super E> renderer) {
        this(label, new Vector<>(), renderer);
    }

    public ComboBox(String label, List<E> options, ListCellRenderer<? super E> renderer) {
        super();

        GroupLayout mainLayout = new GroupLayout(this);
        mainLayout.setAutoCreateContainerGaps(true);
        mainLayout.setAutoCreateGaps(true);
        setLayout(mainLayout);
        innerComboBox = new JComboBox<>(new Vector<>(options));
        this.label = new JLabel(label);
        if (renderer != null) {
            innerComboBox.setRenderer(renderer);
        }

        mainLayout.setHorizontalGroup(mainLayout.createParallelGroup()
                .addComponent(this.label)
                .addComponent(innerComboBox, 0, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE));

        mainLayout.setVerticalGroup(mainLayout.createSequentialGroup()
                .addComponent(this.label)
                .addComponent(innerComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.PREFERRED_SIZE));
    }

    public void setRenderer(ListCellRenderer<? super E> renderer) {
        innerComboBox.setRenderer(renderer);
    }

    @SuppressWarnings("unchecked")
    public E getSelectedItem() {
        return (E) innerComboBox.getSelectedItem();
    }

    public int getSelectedIndex() {
        return innerComboBox.getSelectedIndex();
    }

    public void addActionListener(ActionListener l) {
        innerComboBox.addActionListener(l);
    }

    public void addItemListener(ItemListener l) {
        innerComboBox.addItemListener(l);
    }

    public void setSelections(List<E> selections) {
        innerComboBox.removeAllItems();
        for (E item : selections) {
            innerComboBox.addItem(item);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        innerComboBox.setEnabled(enabled);
    }

    public void setSelectedItem(E item) {
        innerComboBox.setSelectedItem(item);
    }

    public void setEditable(boolean editable) {
        innerComboBox.setEditable(editable);
    }

    public void setLabel(String branch) {
        label.setText(branch);
    }
}
